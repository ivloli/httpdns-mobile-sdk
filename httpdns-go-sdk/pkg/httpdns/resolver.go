package httpdns

import (
	"context"
	"encoding/json"
	"io"
	"net"
	"strings"
	"sync"
	"time"
)

type cacheEntry struct {
	result    *ResolveResult
	expiresAt time.Time
}

type Resolver struct {
	httpClient *HTTPDNSClient
	config     *Config
	metrics    MetricsCollector

	cacheMu sync.RWMutex
	cache   map[string]cacheEntry
}

func NewResolver(config *Config) *Resolver {
	return &Resolver{httpClient: NewHTTPDNSClient(config), config: config, metrics: NewMetricsCollector(config.EnableMetrics), cache: map[string]cacheEntry{}}
}

func (r *Resolver) ResolveSingle(ctx context.Context, domain string, opts ...ResolveOption) (*ResolveResult, error) {
	start := time.Now()
	if strings.TrimSpace(domain) == "" {
		return nil, NewHTTPDNSError("resolve_single", domain, ErrInvalidDomain)
	}
	options := &ResolveOptions{QueryType: QueryBoth, Timeout: r.config.Timeout}
	for _, opt := range opts {
		opt(options)
	}
	if r.config.EnableMemoryCache {
		if cached, ok := r.getCache(domain, options); ok {
			r.metrics.RecordResolve(true, time.Since(start))
			return cached, nil
		}
	}
	if options.Timeout > 0 {
		var cancel context.CancelFunc
		ctx, cancel = context.WithTimeout(ctx, options.Timeout)
		defer cancel()
	}
	if err := r.httpClient.UpdateServiceIPsIfNeeded(ctx); err != nil {
		r.metrics.RecordResolve(false, time.Since(start))
		return nil, NewHTTPDNSError("resolve_single", domain, err)
	}
	builder := NewRequestBuilder(r.config)
	resp, err := r.httpClient.DoRequestWithRetry(ctx, func(_ Endpoint) (string, error) {
		return builder.BuildSingleResolvePath(domain, options.ClientIP, options.QueryType)
	})
	if err != nil {
		r.metrics.RecordResolve(false, time.Since(start))
		return nil, NewHTTPDNSError("resolve_single", domain, err)
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, NewHTTPDNSError("resolve_single", domain, err)
	}
	result, err := parseDirectResponse(domain, options.ClientIP, r.config.AESKey, body)
	if err != nil {
		r.metrics.RecordResolve(false, time.Since(start))
		return nil, NewHTTPDNSError("resolve_single", domain, err)
	}
	result.Source = SourceHTTPDNS
	if r.config.EnableMemoryCache {
		r.setCache(domain, options, result)
	}
	r.metrics.RecordResolve(true, time.Since(start))
	return result, nil
}

func (r *Resolver) ResolveBatch(ctx context.Context, domains []string, opts ...ResolveOption) ([]*ResolveResult, error) {
	if len(domains) == 0 {
		return nil, NewHTTPDNSError("resolve_batch", "", ErrInvalidDomain)
	}
	if len(domains) > 5 {
		return nil, NewHTTPDNSError("resolve_batch", "", ErrTooManyDomains)
	}
	results := make([]*ResolveResult, 0, len(domains))
	for _, domain := range domains {
		item, err := r.ResolveSingle(ctx, domain, opts...)
		if err != nil {
			return nil, err
		}
		results = append(results, item)
	}
	return results, nil
}

func (r *Resolver) ResolveAsync(ctx context.Context, domain string, callback func(*ResolveResult, error), opts ...ResolveOption) {
	go func() { callback(r.ResolveSingle(ctx, domain, opts...)) }()
}

func parseDirectResponse(domain, clientIP, aesKey string, body []byte) (*ResolveResult, error) {
	var direct struct {
		Domain   string   `json:"domain"`
		ClientIP string   `json:"client_ip"`
		IPv4     []string `json:"ipv4"`
		IPv6     []string `json:"ipv6"`
		TTL      int      `json:"ttl"`
	}
	if err := json.Unmarshal(body, &direct); err != nil {
		return parseEncryptedResponse(domain, clientIP, aesKey, body)
	}
	if direct.Domain == "" && len(direct.IPv4) == 0 && len(direct.IPv6) == 0 {
		return parseEncryptedResponse(domain, clientIP, aesKey, body)
	}
	return &ResolveResult{Domain: firstNonEmpty(direct.Domain, domain), ClientIP: firstNonEmpty(direct.ClientIP, clientIP), IPv4: parseIPs(direct.IPv4), IPv6: parseIPs(direct.IPv6), TTL: time.Duration(direct.TTL) * time.Second, Timestamp: time.Now()}, nil
}

func parseEncryptedResponse(domain, clientIP, aesKey string, body []byte) (*ResolveResult, error) {
	var root struct {
		Data string `json:"data"`
	}
	if err := json.Unmarshal(body, &root); err != nil {
		return nil, err
	}
	key, err := parseAESKey(aesKey, "hex")
	if err != nil {
		return nil, err
	}
	plain, err := decryptHex(key, root.Data)
	if err != nil {
		return nil, err
	}
	var payload struct {
		Answers []struct {
			DN  string `json:"dn"`
			TTL int    `json:"ttl"`
			V4  struct {
				IPs []string `json:"ips"`
			} `json:"v4"`
			V6 struct {
				IPs []string `json:"ips"`
			} `json:"v6"`
		} `json:"answers"`
	}
	if err := json.Unmarshal([]byte(plain), &payload); err != nil {
		return nil, err
	}
	if len(payload.Answers) == 0 {
		return nil, ErrServiceUnavailable
	}
	a := payload.Answers[0]
	return &ResolveResult{
		Domain:    firstNonEmpty(a.DN, domain),
		ClientIP:  clientIP,
		IPv4:      parseIPs(a.V4.IPs),
		IPv6:      parseIPs(a.V6.IPs),
		TTL:       time.Duration(a.TTL) * time.Second,
		Timestamp: time.Now(),
	}, nil
}


func parseIPs(values []string) []net.IP {
	out := make([]net.IP, 0, len(values))
	for _, value := range values {
		if ip := net.ParseIP(strings.TrimSpace(value)); ip != nil {
			out = append(out, ip)
		}
	}
	return out
}

func firstNonEmpty(a, b string) string {
	if strings.TrimSpace(a) != "" {
		return a
	}
	return b
}

func cacheKey(domain string, options *ResolveOptions) string {
	return strings.ToLower(domain) + "|" + string(options.QueryType) + "|" + options.ClientIP
}

func (r *Resolver) getCache(domain string, options *ResolveOptions) (*ResolveResult, bool) {
	k := cacheKey(domain, options)
	r.cacheMu.RLock()
	entry, ok := r.cache[k]
	r.cacheMu.RUnlock()
	if !ok {
		return nil, false
	}
	if time.Now().After(entry.expiresAt) && !r.config.AllowExpiredCache {
		return nil, false
	}
	cp := *entry.result
	cp.Source = SourceCache
	return &cp, true
}

func (r *Resolver) setCache(domain string, options *ResolveOptions, result *ResolveResult) {
	if result.TTL <= 0 {
		return
	}
	r.cacheMu.Lock()
	r.cache[cacheKey(domain, options)] = cacheEntry{result: result, expiresAt: time.Now().Add(result.TTL)}
	r.cacheMu.Unlock()
}

func (r *Resolver) GetMetrics() MetricsStats { return r.metrics.GetStats() }
func (r *Resolver) ResetMetrics()           { r.metrics.Reset() }
