package httpdns

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestResolve(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("id") != "test-account" {
			t.Fatalf("unexpected id: %s", r.URL.Query().Get("id"))
		}
		payload := `{"answers":[{"dn":"www.aliyun.com","ttl":20,"v4":{"ips":["1.1.1.1"]},"v6":{"ips":["2400:3200::1"]}}]}`
		key, _ := parseAESKey("0123456789abcdef0123456789abcdef", "hex")
		enc, _ := encryptHex(key, []byte(payload))
		_ = json.NewEncoder(w).Encode(map[string]any{"data": enc})
	}))
	defer ts.Close()

	cfg := DefaultConfig()
	cfg.AccountID = "test-account"
	cfg.AESKey = "0123456789abcdef0123456789abcdef"
	cfg.ResolveURL = ts.URL
	client, err := NewClient(cfg)
	if err != nil {
		t.Fatalf("new client failed: %v", err)
	}
	defer client.Close()

	result, err := client.Resolve(context.Background(), "www.aliyun.com", WithIPv4Only())
	if err != nil {
		t.Fatalf("resolve failed: %v", err)
	}
	if result.Domain != "www.aliyun.com" || len(result.IPv4) != 1 {
		t.Fatalf("invalid resolve result: %+v", result)
	}
	if result.TTL != 20*time.Second {
		t.Fatalf("unexpected ttl: %v", result.TTL)
	}
}

func TestResolveBatchLimit(t *testing.T) {
	cfg := DefaultConfig()
	cfg.AccountID = "test-account"
	cfg.AESKey = "0123456789abcdef0123456789abcdef"
	client, err := NewClient(cfg)
	if err != nil {
		t.Fatalf("new client failed: %v", err)
	}
	defer client.Close()

	_, err = client.ResolveBatch(context.Background(), []string{"a", "b", "c", "d", "e", "f"})
	if err == nil {
		t.Fatal("expected too many domains error")
	}
}

func TestResolveAsync(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		payload := `{"answers":[{"dn":"www.tmall.com","ttl":8,"v4":{"ips":["2.2.2.2"]},"v6":{"ips":[]}}]}`
		key, _ := parseAESKey("0123456789abcdef0123456789abcdef", "hex")
		enc, _ := encryptHex(key, []byte(payload))
		_ = json.NewEncoder(w).Encode(map[string]any{"data": enc})
	}))
	defer ts.Close()

	cfg := DefaultConfig()
	cfg.AccountID = "test-account"
	cfg.AESKey = "0123456789abcdef0123456789abcdef"
	cfg.ResolveURL = ts.URL
	client, err := NewClient(cfg)
	if err != nil {
		t.Fatalf("new client failed: %v", err)
	}
	defer client.Close()

	done := make(chan struct{})
	client.ResolveAsync(context.Background(), "www.tmall.com", func(result *ResolveResult, err error) {
		defer close(done)
		if err != nil {
			t.Fatalf("resolve async failed: %v", err)
		}
		if result == nil || result.Domain != "www.tmall.com" {
			t.Fatalf("unexpected async result: %+v", result)
		}
	})

	select {
	case <-done:
	case <-time.After(2 * time.Second):
		t.Fatal("timeout waiting async callback")
	}
}
