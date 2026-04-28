package main

import (
	"context"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/tls"
	"encoding/hex"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"
)

type config struct {
	Mode             string
	UseHTTPS         bool
	Scheme           string
	AccountID        string
	AESKey           string
	BootstrapHost    string
	BootstrapIP      string
	ResolveHost      string
	ResolveIP        string
	Region           string
	ExpSeconds       int
	DN               string
	Q                string
	CIP              string
	SdnsOS           string
	TimeoutSeconds   int
	DisableProxy     bool
	AutoFallbackHTTP bool
	ShowHTTPOnly     bool
}

func envOrDefault(name, defaultValue string) string {
	value := os.Getenv(name)
	if value == "" {
		return defaultValue
	}
	return value
}

func parseBool(raw string) bool {
	switch strings.ToLower(strings.TrimSpace(raw)) {
	case "1", "true", "yes", "on":
		return true
	default:
		return false
	}
}

func parseInt(raw string, defaultValue int) int {
	v, err := strconv.Atoi(strings.TrimSpace(raw))
	if err != nil {
		return defaultValue
	}
	return v
}

func parseArgs() config {
	defaultMode := envOrDefault("MODE", "dispatch")
	defaultUseHTTPS := parseBool(envOrDefault("HTTPS", "true"))
	defaultScheme := envOrDefault("SCHEME", "https")
	defaultAccountID := os.Getenv("ACCOUNT_ID")
	defaultAESKey := os.Getenv("AES_KEY")
	defaultBootstrapHost := envOrDefault("BOOTSTRAP_HOST", "r.pp.fgnlo.com")
	defaultBootstrapIP := envOrDefault("BOOTSTRAP_IP", "")
	defaultResolveHost := envOrDefault("RESOLVE_HOST", "")
	defaultResolveIP := envOrDefault("RESOLVE_IP", "")
	defaultRegion := envOrDefault("REGION", "global")
	defaultExpSeconds := parseInt(envOrDefault("EXP_SECONDS", "600"), 600)
	defaultDN := envOrDefault("DN", "www.example.com")
	defaultQ := envOrDefault("Q", "4,6")
	defaultCIP := envOrDefault("CIP", "")
	defaultSdnsOS := envOrDefault("SDNS_OS", "")
	defaultTimeout := parseInt(envOrDefault("TIMEOUT_SECONDS", "10"), 10)
	defaultDisableProxy := parseBool(envOrDefault("DISABLE_PROXY", "true"))
	defaultAutoFallback := parseBool(envOrDefault("AUTO_FALLBACK_HTTP", "false"))
	defaultShowHTTPOnly := parseBool(envOrDefault("SHOW_HTTP_ONLY", "false"))

	mode := flag.String("mode", defaultMode, "dispatch or resolve")
	useHTTPS := flag.Bool("https", defaultUseHTTPS, "use https (false means http)")
	scheme := flag.String("scheme", defaultScheme, "https or http")
	accountID := flag.String("account-id", defaultAccountID, "account id")
	aesKey := flag.String("aes-key", defaultAESKey, "aes key")
	bootstrapHost := flag.String("bootstrap-host", defaultBootstrapHost, "dispatch host")
	bootstrapIP := flag.String("bootstrap-ip", defaultBootstrapIP, "dispatch connect ip")
	resolveHost := flag.String("resolve-host", defaultResolveHost, "resolve host")
	resolveIP := flag.String("resolve-ip", defaultResolveIP, "resolve connect ip")
	region := flag.String("region", defaultRegion, "dispatch region")
	expSeconds := flag.Int("exp-seconds", defaultExpSeconds, "exp offset seconds")
	dn := flag.String("dn", defaultDN, "domain for resolve")
	q := flag.String("q", defaultQ, "query type, e.g. 4,6")
	cip := flag.String("cip", defaultCIP, "optional cip")
	sdnsOS := flag.String("sdns-os", defaultSdnsOS, "optional sdns-os")
	timeoutSeconds := flag.Int("timeout-seconds", defaultTimeout, "request timeout seconds")
	disableProxy := flag.Bool("disable-proxy", defaultDisableProxy, "disable proxy env")
	autoFallbackHTTP := flag.Bool("auto-fallback-http", defaultAutoFallback, "fallback to http when https request fails")
	showHTTPOnly := flag.Bool("show-http-only", defaultShowHTTPOnly, "print request and exit")

	flag.Parse()

	finalScheme := strings.ToLower(strings.TrimSpace(*scheme))
	if !*useHTTPS {
		finalScheme = "http"
	} else if finalScheme != "http" && finalScheme != "https" {
		finalScheme = "https"
	}

	return config{
		Mode:             *mode,
		UseHTTPS:         *useHTTPS,
		Scheme:           finalScheme,
		AccountID:        *accountID,
		AESKey:           *aesKey,
		BootstrapHost:    *bootstrapHost,
		BootstrapIP:      *bootstrapIP,
		ResolveHost:      *resolveHost,
		ResolveIP:        *resolveIP,
		Region:           *region,
		ExpSeconds:       *expSeconds,
		DN:               *dn,
		Q:                *q,
		CIP:              *cip,
		SdnsOS:           *sdnsOS,
		TimeoutSeconds:   *timeoutSeconds,
		DisableProxy:     *disableProxy,
		AutoFallbackHTTP: *autoFallbackHTTP,
		ShowHTTPOnly:     *showHTTPOnly,
	}
}

func parseAESKey(raw string) ([]byte, error) {
	utf8Bytes := []byte(raw)
	if len(utf8Bytes) == 16 {
		return utf8Bytes, nil
	}
	if len(raw) == 32 {
		decoded, err := hex.DecodeString(raw)
		if err == nil && len(decoded) == 16 {
			return decoded, nil
		}
	}
	return nil, errors.New("AES_KEY invalid: expect 16-byte plain text or 32-hex string")
}

func encryptHex(aesKey []byte, plaintext string) (string, error) {
	block, err := aes.NewCipher(aesKey)
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	iv := make([]byte, 12)
	if _, err := io.ReadFull(rand.Reader, iv); err != nil {
		return "", err
	}
	cipherText := gcm.Seal(nil, iv, []byte(plaintext), nil)
	out := append(iv, cipherText...)
	return hex.EncodeToString(out), nil
}

func decryptHex(aesKey []byte, hexData string) (string, error) {
	raw, err := hex.DecodeString(hexData)
	if err != nil {
		return "", err
	}
	if len(raw) <= 12 {
		return "", errors.New("invalid encrypted payload")
	}
	iv := raw[:12]
	cipherText := raw[12:]
	block, err := aes.NewCipher(aesKey)
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	plain, err := gcm.Open(nil, iv, cipherText, nil)
	if err != nil {
		return "", err
	}
	return string(plain), nil
}

func buildPayload(cfg config, expTS int64) (string, string, string, error) {
	if cfg.Mode == "dispatch" {
		m := map[string]any{"region": cfg.Region, "exp": expTS}
		bytes, err := json.Marshal(m)
		if err != nil {
			return "", "", "", err
		}
		return string(bytes), cfg.BootstrapHost, cfg.BootstrapIP, nil
	}

	if cfg.Mode == "resolve" {
		if strings.TrimSpace(cfg.ResolveHost) == "" {
			return "", "", "", errors.New("RESOLVE_HOST is required when mode=resolve")
		}
		m := map[string]any{"exp": expTS, "dn": cfg.DN, "q": cfg.Q}
		if strings.TrimSpace(cfg.CIP) != "" {
			m["cip"] = cfg.CIP
		}
		if strings.TrimSpace(cfg.SdnsOS) != "" {
			m["sdns-os"] = cfg.SdnsOS
		}
		bytes, err := json.Marshal(m)
		if err != nil {
			return "", "", "", err
		}
		return string(bytes), cfg.ResolveHost, cfg.ResolveIP, nil
	}

	return "", "", "", errors.New("mode must be dispatch or resolve")
}

func buildPath(cfg config, accountID, enc string) string {
	encodedAccount := url.QueryEscape(accountID)
	encodedEnc := url.QueryEscape(enc)
	if cfg.Mode == "dispatch" {
		return fmt.Sprintf("/dnps-apis/v1/httpdns/endpoints?account_id=%s&enc=%s", encodedAccount, encodedEnc)
	}
	return fmt.Sprintf("/v1/d?id=%s&enc=%s", encodedAccount, encodedEnc)
}

func makeClient(cfg config, host, connectIP string) *http.Client {
	proxyFunc := http.ProxyFromEnvironment
	if cfg.DisableProxy {
		proxyFunc = nil
	}

	dialer := &net.Dialer{Timeout: time.Duration(cfg.TimeoutSeconds) * time.Second}

	transport := &http.Transport{
		Proxy: proxyFunc,
		DialContext: func(ctx context.Context, network, address string) (net.Conn, error) {
			target := address
			if strings.TrimSpace(connectIP) != "" {
				_, port, err := net.SplitHostPort(address)
				if err != nil {
					port = "443"
				}
				target = net.JoinHostPort(connectIP, port)
			}
			return dialer.DialContext(ctx, network, target)
		},
		TLSHandshakeTimeout: time.Duration(cfg.TimeoutSeconds) * time.Second,
		TLSClientConfig: &tls.Config{
			ServerName: host,
			MinVersion: tls.VersionTLS12,
		},
	}

	return &http.Client{
		Transport: transport,
		Timeout:   time.Duration(cfg.TimeoutSeconds) * time.Second,
	}
}

func doRequest(cfg config, host, pathWithQuery, connectIP string) (int, string, http.Header, string, error) {
	if strings.TrimSpace(connectIP) != "" {
		fmt.Printf("attempt endpoint: host=%s ip=%s\n", host, connectIP)
		return doSingleRequest(cfg, host, pathWithQuery, connectIP)
	}

	resolvedIPs, err := resolveHostIPs(host)
	if err != nil || len(resolvedIPs) == 0 {
		fmt.Printf("attempt endpoint: host=%s ip=<dns>\n", host)
		return doSingleRequest(cfg, host, pathWithQuery, "")
	}

	var lastErr error
	for _, ip := range resolvedIPs {
		fmt.Printf("attempt endpoint: host=%s ip=%s\n", host, ip)
		statusCode, statusText, headers, body, reqErr := doSingleRequest(cfg, host, pathWithQuery, ip)
		if reqErr == nil {
			return statusCode, statusText, headers, body, nil
		}
		fmt.Printf("attempt failed: ip=%s err=%v\n", ip, reqErr)
		lastErr = reqErr
	}

	if lastErr == nil {
		lastErr = errors.New("all endpoint attempts failed")
	}
	return 0, "", nil, "", lastErr
}

func doSingleRequest(cfg config, host, pathWithQuery, connectIP string) (int, string, http.Header, string, error) {
	baseURL := fmt.Sprintf("%s://%s%s", cfg.Scheme, host, pathWithQuery)
	requestURL, err := url.Parse(baseURL)
	if err != nil {
		return 0, "", nil, "", err
	}

	if strings.TrimSpace(connectIP) != "" {
		requestURL.Host = connectIP
	}

	req, err := http.NewRequest(http.MethodGet, requestURL.String(), nil)
	if err != nil {
		return 0, "", nil, "", err
	}
	if strings.TrimSpace(connectIP) != "" {
		req.Host = host
	}

	client := makeClient(cfg, host, connectIP)
	resp, err := client.Do(req)
	if err != nil {
		return 0, "", nil, "", err
	}
	defer resp.Body.Close()

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return resp.StatusCode, resp.Status, resp.Header, "", err
	}
	body := string(bodyBytes)
	return resp.StatusCode, resp.Status, resp.Header, body, nil
}

func resolveHostIPs(host string) ([]string, error) {
	ips, err := net.LookupIP(host)
	if err != nil {
		return nil, err
	}

	seen := make(map[string]struct{})
	out := make([]string, 0, len(ips))
	for _, ip := range ips {
		if ip == nil {
			continue
		}
		s := ip.String()
		if s == "" {
			continue
		}
		if _, exists := seen[s]; exists {
			continue
		}
		seen[s] = struct{}{}
		out = append(out, s)
	}
	return out, nil
}

func printRequestContext(cfg config, host, connectIP, payload, path string) {
	fmt.Println("=== Request Context ===")
	fmt.Printf("mode       : %s\n", cfg.Mode)
	fmt.Printf("scheme     : %s\n", cfg.Scheme)
	fmt.Printf("host       : %s\n", host)
	if strings.TrimSpace(connectIP) == "" {
		fmt.Println("connect_ip : <none>")
	} else {
		fmt.Printf("connect_ip : %s\n", connectIP)
	}
	fmt.Printf("payload    : %s\n", payload)
	fmt.Println()
	fmt.Println("=== HTTP Request ===")
	fmt.Printf("GET %s HTTP/1.1\n", path)
	fmt.Printf("Host: %s\n", host)
	fmt.Printf("URL : %s://%s%s\n", cfg.Scheme, host, path)
}

func printResponse(statusCode int, status string, headers http.Header, body string) {
	fmt.Println()
	fmt.Println("=== Raw Response ===")
	fmt.Printf("status: %d %s\n", statusCode, status)
	fmt.Println("headers:")
	for key, values := range headers {
		for _, value := range values {
			fmt.Printf("  %s: %s\n", key, value)
		}
	}
	fmt.Println("body:")
	fmt.Println(body)
}

func main() {
	cfg := parseArgs()

	if strings.TrimSpace(cfg.AccountID) == "" || strings.TrimSpace(cfg.AESKey) == "" {
		fmt.Println("ERROR: ACCOUNT_ID and AES_KEY are required")
		os.Exit(1)
	}

	aesKey, err := parseAESKey(cfg.AESKey)
	if err != nil {
		fmt.Printf("ERROR: %v\n", err)
		os.Exit(1)
	}

	expTS := time.Now().Unix() + int64(cfg.ExpSeconds)
	payload, host, connectIP, err := buildPayload(cfg, expTS)
	if err != nil {
		fmt.Printf("ERROR: %v\n", err)
		os.Exit(1)
	}

	enc, err := encryptHex(aesKey, payload)
	if err != nil {
		fmt.Printf("ERROR: encrypt failed: %v\n", err)
		os.Exit(1)
	}

	path := buildPath(cfg, cfg.AccountID, enc)
	printRequestContext(cfg, host, connectIP, payload, path)

	if cfg.ShowHTTPOnly {
		return
	}

	statusCode, statusText, headers, body, err := doRequest(cfg, host, path, connectIP)
	if err != nil && cfg.Scheme == "https" && cfg.AutoFallbackHTTP {
		fmt.Println()
		fmt.Printf("HTTPS request failed (%v), retrying with HTTP...\n", err)
		cfg.Scheme = "http"
		statusCode, statusText, headers, body, err = doRequest(cfg, host, path, connectIP)
	}
	if err != nil {
		fmt.Printf("ERROR: request failed: %v\n", err)
		os.Exit(1)
	}

	printResponse(statusCode, statusText, headers, body)

	fmt.Println()
	fmt.Println("=== Parsed Response ===")
	var root map[string]any
	if err := json.Unmarshal([]byte(body), &root); err != nil {
		fmt.Println("Response is not JSON.")
		return
	}

	dataAny, ok := root["data"]
	if !ok {
		fmt.Println("No data field found in JSON response.")
		return
	}

	dataHex, ok := dataAny.(string)
	if !ok || strings.TrimSpace(dataHex) == "" {
		fmt.Println("No data field found in JSON response.")
		return
	}

	fmt.Printf("data(hex): %s\n", dataHex)
	fmt.Println()
	fmt.Println("=== Decrypted Data ===")
	decrypted, err := decryptHex(aesKey, dataHex)
	if err != nil {
		fmt.Printf("ERROR: decrypt failed: %v\n", err)
		os.Exit(1)
	}
	fmt.Println(decrypted)
}
