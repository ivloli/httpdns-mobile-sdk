package httpdns

import "testing"

func TestBuildResolveURL(t *testing.T) {
	cfg := DefaultConfig()
	cfg.AccountID = "test-account"
	cfg.AESKey = "0123456789abcdef0123456789abcdef"
	b := NewRequestBuilder(cfg)
	u, err := b.BuildSingleResolvePath("www.aliyun.com", "1.2.3.4", QueryBoth)
	if err != nil {
		t.Fatalf("build resolve path failed: %v", err)
	}
	if u == "" {
		t.Fatal("empty resolve url")
	}
}
