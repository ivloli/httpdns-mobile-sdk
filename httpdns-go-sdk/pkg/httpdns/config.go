package httpdns

import "time"

type Config struct {
	AccountID string
	SecretKey string
	AESKey    string

	SignAlgorithm string
	SignParamName string
	DispatchHost  string
	ResolveHost   string
	DefaultSDNSOS string

	BootstrapIPs []string
	Timeout      time.Duration
	MaxRetries   int

	EnableHTTPS           bool
	EnableMetrics         bool
	EnableMemoryCache     bool
	EnablePersistentCache bool
	AllowExpiredCache     bool
	CacheExpireThreshold  time.Duration

	Logger Logger

	DispatchURL string
	ResolveURL  string
}

func DefaultConfig() *Config {
	return &Config{
		SignAlgorithm:         "hmac-sha1",
		SignParamName:         "sign",
		DispatchHost:          "",
		ResolveHost:           "",
		DefaultSDNSOS:         "ios",
		Timeout:               5 * time.Second,
		MaxRetries:            0,
		EnableHTTPS:           false,
		EnableMetrics:         false,
		EnableMemoryCache:     true,
		EnablePersistentCache: false,
		AllowExpiredCache:     false,
		CacheExpireThreshold:  0,
		ResolveURL:            "http://127.0.0.1:8088/dnps-apis/v1/online-experience/resolve",
	}
}

func (c *Config) Validate() error {
	if c.ResolveURL == "" {
		return ErrInvalidConfig
	}
	if c.Timeout <= 0 {
		c.Timeout = 5 * time.Second
	}
	if c.MaxRetries < 0 {
		c.MaxRetries = 0
	}
	if c.EnablePersistentCache && !c.EnableMemoryCache {
		c.EnablePersistentCache = false
	}
	if c.CacheExpireThreshold < 0 {
		c.CacheExpireThreshold = 0
	}
	return nil
}
