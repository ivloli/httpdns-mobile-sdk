package httpdns

import (
	"context"
	"net"
	"time"
)

type Client interface {
	Resolve(ctx context.Context, domain string, opts ...ResolveOption) (*ResolveResult, error)
	ResolveBatch(ctx context.Context, domains []string, opts ...ResolveOption) ([]*ResolveResult, error)
	ResolveAsync(ctx context.Context, domain string, callback func(*ResolveResult, error), opts ...ResolveOption)
	Close() error
	GetMetrics() MetricsStats
	ResetMetrics()
	UpdateServiceIPs(ctx context.Context) error
	GetServiceIPs() []string
	IsHealthy() bool
}

type ResolveResult struct {
	Domain    string
	ClientIP  string
	IPv4      []net.IP
	IPv6      []net.IP
	TTL       time.Duration
	Source    ResolveSource
	Timestamp time.Time
	Error     error
}

type ResolveSource int

const (
	SourceHTTPDNS ResolveSource = iota
	SourceCache
)

type ResolveOption func(*ResolveOptions)

type ResolveOptions struct {
	QueryType QueryType
	Timeout   time.Duration
	ClientIP  string
}

type QueryType string

const (
	QueryIPv4 QueryType = "4"
	QueryIPv6 QueryType = "6"
	QueryBoth QueryType = "4,6"
)

func WithIPv4Only() ResolveOption { return func(opts *ResolveOptions) { opts.QueryType = QueryIPv4 } }
func WithIPv6Only() ResolveOption { return func(opts *ResolveOptions) { opts.QueryType = QueryIPv6 } }
func WithBothIP() ResolveOption   { return func(opts *ResolveOptions) { opts.QueryType = QueryBoth } }
func WithTimeout(timeout time.Duration) ResolveOption {
	return func(opts *ResolveOptions) { opts.Timeout = timeout }
}
func WithClientIP(ip string) ResolveOption { return func(opts *ResolveOptions) { opts.ClientIP = ip } }

type MetricsStats struct {
	TotalResolves int64
	SuccessCount  int64
	ErrorCount    int64
	AvgLatency    time.Duration
	SuccessRate   float64
}

type Logger interface {
	Printf(format string, v ...any)
}
