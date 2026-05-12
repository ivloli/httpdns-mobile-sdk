package httpdns

import "testing"

func TestConfigValidateRequiresResolveURL(t *testing.T) {
	cfg := DefaultConfig()
	cfg.ResolveURL = ""
	if err := cfg.Validate(); err == nil {
		t.Fatal("expected invalid config error")
	}
}

func TestConfigValidateSuccess(t *testing.T) {
	cfg := DefaultConfig()
	cfg.ResolveURL = "http://127.0.0.1:8088/dnps-apis/v1/online-experience/resolve"
	if err := cfg.Validate(); err != nil {
		t.Fatalf("validate failed: %v", err)
	}
}
