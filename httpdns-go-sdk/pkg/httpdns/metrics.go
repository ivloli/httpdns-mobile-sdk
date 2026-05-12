package httpdns

import (
	"sync"
	"time"
)

type MetricsCollector interface {
	RecordResolve(success bool, latency time.Duration)
	GetStats() MetricsStats
	Reset()
}

type metricsCollector struct {
	enabled bool
	mu      sync.RWMutex
	stats   MetricsStats
	latency time.Duration
}

func NewMetricsCollector(enabled bool) MetricsCollector {
	return &metricsCollector{enabled: enabled}
}

func (m *metricsCollector) RecordResolve(success bool, latency time.Duration) {
	if !m.enabled {
		return
	}
	m.mu.Lock()
	defer m.mu.Unlock()
	m.stats.TotalResolves++
	if success {
		m.stats.SuccessCount++
	} else {
		m.stats.ErrorCount++
	}
	m.latency += latency
	if m.stats.TotalResolves > 0 {
		m.stats.AvgLatency = m.latency / time.Duration(m.stats.TotalResolves)
		m.stats.SuccessRate = float64(m.stats.SuccessCount) / float64(m.stats.TotalResolves)
	}
}

func (m *metricsCollector) GetStats() MetricsStats {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.stats
}

func (m *metricsCollector) Reset() {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.stats = MetricsStats{}
	m.latency = 0
}
