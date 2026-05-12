package httpdns

import (
	"context"
	"sync"
	"time"
)

type client struct {
	resolver *Resolver
	config   *Config
	stopCh   chan struct{}
	wg       sync.WaitGroup
	started  bool
	mu       sync.RWMutex
}

func NewClient(config *Config) (Client, error) {
	if err := config.Validate(); err != nil {
		return nil, err
	}
	resolver := NewResolver(config)
	if err := preloadDispatch(resolver, config.Timeout); err != nil {
		return nil, NewHTTPDNSError("startup_dispatch_preload", "", err)
	}
	c := &client{
		resolver: resolver,
		config:   config,
		stopCh:   make(chan struct{}),
		started:  true,
	}
	c.wg.Add(1)
	go c.periodicUpdateServiceIPs()
	return c, nil
}

func preloadDispatch(resolver *Resolver, timeout time.Duration) error {
	if timeout <= 0 {
		timeout = 5 * time.Second
	}
	var lastErr error
	for i := 0; i < 3; i++ {
		if resolver.config.Logger != nil {
			resolver.config.Logger.Printf("[preload] attempt=%d/3 timeout=%s", i+1, timeout)
		}
		ctx, cancel := context.WithTimeout(context.Background(), timeout)
		err := resolver.httpClient.FetchServiceIPs(ctx)
		cancel()
		if err == nil {
			if resolver.config.Logger != nil {
				resolver.config.Logger.Printf("[preload] success attempt=%d", i+1)
			}
			return nil
		}
		if resolver.config.Logger != nil {
			resolver.config.Logger.Printf("[preload] failed attempt=%d err=%v", i+1, err)
		}
		lastErr = err
		time.Sleep(1 * time.Second)
	}
	return lastErr
}

func (c *client) periodicUpdateServiceIPs() {
	defer c.wg.Done()
	ticker := time.NewTicker(8 * time.Hour)
	defer ticker.Stop()
	for {
		select {
		case <-ticker.C:
			ctx, cancel := context.WithTimeout(context.Background(), c.config.Timeout)
			_ = c.resolver.httpClient.FetchServiceIPs(ctx)
			cancel()
		case <-c.stopCh:
			return
		}
	}
}

func (c *client) Resolve(ctx context.Context, domain string, opts ...ResolveOption) (*ResolveResult, error) {
	if !c.IsHealthy() {
		return nil, NewHTTPDNSError("client_stopped", domain, ErrServiceUnavailable)
	}
	return c.resolver.ResolveSingle(ctx, domain, opts...)
}

func (c *client) ResolveBatch(ctx context.Context, domains []string, opts ...ResolveOption) ([]*ResolveResult, error) {
	if !c.IsHealthy() {
		return nil, NewHTTPDNSError("client_stopped", "", ErrServiceUnavailable)
	}
	return c.resolver.ResolveBatch(ctx, domains, opts...)
}

func (c *client) ResolveAsync(ctx context.Context, domain string, callback func(*ResolveResult, error), opts ...ResolveOption) {
	if !c.IsHealthy() {
		callback(nil, NewHTTPDNSError("client_stopped", domain, ErrServiceUnavailable))
		return
	}
	c.resolver.ResolveAsync(ctx, domain, callback, opts...)
}

func (c *client) Close() error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if !c.started {
		return nil
	}
	c.started = false
	close(c.stopCh)
	c.wg.Wait()
	return nil
}

func (c *client) GetMetrics() MetricsStats { return c.resolver.GetMetrics() }
func (c *client) ResetMetrics()           { c.resolver.ResetMetrics() }

func (c *client) UpdateServiceIPs(ctx context.Context) error {
	if !c.IsHealthy() {
		return NewHTTPDNSError("client_stopped", "", ErrServiceUnavailable)
	}
	return c.resolver.httpClient.FetchServiceIPs(ctx)
}

func (c *client) GetServiceIPs() []string {
	if !c.IsHealthy() {
		return nil
	}
	return c.resolver.httpClient.GetServiceIPs()
}

func (c *client) IsHealthy() bool {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.started
}
