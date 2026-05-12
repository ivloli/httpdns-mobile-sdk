package httpdns

import (
	"context"
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha1"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"sync"
	"time"
)

type Endpoint struct {
	Host      string
	ConnectIP string
}

type HTTPDNSClient struct {
	client    *http.Client
	config    *Config
	endpoints []Endpoint
	updatedAt time.Time
	mu        sync.RWMutex
}

func NewHTTPDNSClient(config *Config) *HTTPDNSClient {
	return &HTTPDNSClient{client: &http.Client{Timeout: config.Timeout}, config: config}
}

type RequestBuilder struct{ config *Config }

func NewRequestBuilder(config *Config) *RequestBuilder { return &RequestBuilder{config: config} }

func (b *RequestBuilder) BuildSingleResolvePath(domain, clientIP string, queryType QueryType) (string, error) {
	return b.buildResolvePath(domain, clientIP, queryType)
}

func (b *RequestBuilder) BuildBatchResolvePath(domains []string, clientIP string, queryType QueryType) (string, error) {
	return b.buildResolvePath(strings.Join(domains, ","), clientIP, queryType)
}

func (b *RequestBuilder) buildResolvePath(domain, clientIP string, queryType QueryType) (string, error) {
	u, err := url.Parse(b.config.ResolveURL)
	if err != nil {
		return "", err
	}
	key, err := parseAESKey(b.config.AESKey, "hex")
	if err != nil {
		return "", err
	}
	payload := map[string]any{"exp": time.Now().Unix() + 600, "dn": domain, "q": string(queryType), "sdns-os": b.config.DefaultSDNSOS}
	if clientIP != "" {
		payload["cip"] = clientIP
	}
	plain, err := json.Marshal(payload)
	if err != nil {
		return "", err
	}
	enc, err := encryptHex(key, plain)
	if err != nil {
		return "", err
	}

	params := map[string]string{"id": b.config.AccountID, "enc": enc}
	if strings.TrimSpace(b.config.SecretKey) != "" {
		signParam := b.config.SignParamName
		if signParam == "" {
			signParam = "sign"
		}
		params[signParam] = sign("GET", "/v1/d", params, b.config.SecretKey, b.config.SignAlgorithm)
	}
	keys := make([]string, 0, len(params))
	for k := range params {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	q := u.Query()
	for _, k := range keys {
		q.Set(k, params[k])
	}
	u.RawQuery = q.Encode()
	return u.String(), nil
}

func (c *HTTPDNSClient) FetchServiceIPs(_ context.Context) error {
	c.mu.Lock()
	c.endpoints = []Endpoint{{Host: c.config.ResolveURL}}
	c.updatedAt = time.Now()
	c.mu.Unlock()
	return nil
}

func (c *HTTPDNSClient) UpdateServiceIPsIfNeeded(ctx context.Context) error {
	c.mu.RLock()
	should := len(c.endpoints) == 0 || time.Since(c.updatedAt) > 8*time.Hour
	c.mu.RUnlock()
	if should {
		return c.FetchServiceIPs(ctx)
	}
	return nil
}

func (c *HTTPDNSClient) DoRequestWithRetry(ctx context.Context, buildPath func(endpoint Endpoint) (string, error)) (*http.Response, error) {
	endpoints := c.getEndpointsSnapshot()
	if len(endpoints) == 0 {
		return nil, NewHTTPDNSError("request_retry_failed", "", ErrServiceUnavailable)
	}
	var lastErr error
	maxAttempts := c.config.MaxRetries + 1
	for i := 0; i < maxAttempts; i++ {
		for _, endpoint := range endpoints {
			u, err := buildPath(endpoint)
			if err != nil {
				lastErr = err
				continue
			}
			req, err := http.NewRequestWithContext(ctx, http.MethodGet, u, nil)
			if err != nil {
				lastErr = err
				continue
			}
			resp, err := c.client.Do(req)
			if err == nil && resp.StatusCode == http.StatusOK {
				return resp, nil
			}
			if resp != nil {
				body, _ := io.ReadAll(resp.Body)
				resp.Body.Close()
				lastErr = fmt.Errorf("http status: %s body=%s", resp.Status, string(body))
			} else {
				lastErr = err
			}
		}
	}
	return nil, NewHTTPDNSError("request_retry_failed", "", lastErr)
}

func (c *HTTPDNSClient) GetServiceIPs() []string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	out := make([]string, 0, len(c.endpoints))
	for _, endpoint := range c.endpoints {
		out = append(out, endpoint.Host)
	}
	return out
}

func (c *HTTPDNSClient) getEndpointsSnapshot() []Endpoint {
	c.mu.RLock()
	defer c.mu.RUnlock()
	out := make([]Endpoint, len(c.endpoints))
	copy(out, c.endpoints)
	return out
}

func parseAESKey(raw, mode string) ([]byte, error) {
	utf8 := []byte(raw)
	switch mode {
	case "utf8":
		if l := len(utf8); l == 16 || l == 24 || l == 32 {
			return utf8, nil
		}
		return nil, fmt.Errorf("invalid aes key for utf8 mode")
	case "hex":
		if l := len(raw); l == 32 || l == 48 || l == 64 {
			return hex.DecodeString(raw)
		}
		return nil, fmt.Errorf("invalid aes key for hex mode")
	default:
		return nil, fmt.Errorf("unknown key mode")
	}
}

func encryptHex(key, plain []byte) (string, error) {
	blk, err := aes.NewCipher(key)
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(blk)
	if err != nil {
		return "", err
	}
	iv := make([]byte, 12)
	if _, err := rand.Read(iv); err != nil {
		return "", err
	}
	ct := gcm.Seal(nil, iv, plain, nil)
	return hex.EncodeToString(append(iv, ct...)), nil
}

func decryptHex(key []byte, hexData string) (string, error) {
	raw, err := hex.DecodeString(hexData)
	if err != nil {
		return "", err
	}
	if len(raw) <= 12 {
		return "", fmt.Errorf("invalid encrypted payload")
	}
	iv, ct := raw[:12], raw[12:]
	blk, err := aes.NewCipher(key)
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(blk)
	if err != nil {
		return "", err
	}
	pt, err := gcm.Open(nil, iv, ct, nil)
	if err != nil {
		return "", err
	}
	return string(pt), nil
}

func sign(method, path string, params map[string]string, key, alg string) string {
	keys := make([]string, 0, len(params))
	for k := range params {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	parts := make([]string, 0, len(keys))
	for _, k := range keys {
		parts = append(parts, rfc3986(k)+"="+rfc3986(params[k]))
	}
	canonical := strings.Join(parts, "&")
	stringToSign := strings.ToUpper(method) + "&" + rfc3986(path) + "&" + rfc3986(canonical)
	if strings.EqualFold(alg, "hmac-sha256") {
		h := hmac.New(sha256.New, []byte(key))
		h.Write([]byte(stringToSign))
		return base64.StdEncoding.EncodeToString(h.Sum(nil))
	}
	h := hmac.New(sha1.New, []byte(key))
	h.Write([]byte(stringToSign))
	return base64.StdEncoding.EncodeToString(h.Sum(nil))
}

func rfc3986(s string) string {
	u := url.QueryEscape(s)
	u = strings.ReplaceAll(u, "+", "%20")
	u = strings.ReplaceAll(u, "*", "%2A")
	u = strings.ReplaceAll(u, "%7E", "~")
	return u
}
