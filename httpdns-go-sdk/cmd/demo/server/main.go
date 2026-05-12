package main

import (
	"context"
	"encoding/json"
	"log"
	"net"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"

	"scloud/httpdns-go-sdk-demo/internal/common"
	"scloud/httpdns-go-sdk/pkg/httpdns"
)

type server struct {
	client httpdns.Client
}

func main() {
	fileCfg, err := common.LoadConfigOrDefault("config.local.yaml")
	if err != nil {
		log.Fatalf("load config failed: %v", err)
	}

	host := firstNonEmpty(strings.TrimSpace(os.Getenv("SERVICE_HOST")), fileCfg.Service.Host, "0.0.0.0")
	port := firstNonEmpty(strings.TrimSpace(os.Getenv("SERVICE_PORT")), intToString(fileCfg.Service.Port), "38088")

	cfg := httpdns.DefaultConfig()
	cfg.AccountID = firstNonEmpty(strings.TrimSpace(os.Getenv("HTTPDNS_ACCOUNT_ID")), fileCfg.HTTPDNS.AccountID)
	cfg.AESKey = firstNonEmpty(strings.TrimSpace(os.Getenv("HTTPDNS_AES_KEY")), fileCfg.HTTPDNS.AESKey)
	cfg.SecretKey = firstNonEmpty(strings.TrimSpace(os.Getenv("HTTPDNS_SIGN_KEY")), fileCfg.HTTPDNS.SignKey)
	cfg.SignAlgorithm = firstNonEmpty(strings.TrimSpace(os.Getenv("HTTPDNS_SIGN_ALGORITHM")), fileCfg.HTTPDNS.SignAlg, "hmac-sha1")
	cfg.SignParamName = firstNonEmpty(strings.TrimSpace(os.Getenv("HTTPDNS_SIGN_PARAM_NAME")), fileCfg.HTTPDNS.SignParam, "sign")
	cfg.DispatchHost = firstNonEmpty(strings.TrimSpace(os.Getenv("HTTPDNS_DISPATCH_HOST")), fileCfg.HTTPDNS.DispatchHost, "r.pp.fgnlo.com")
	cfg.ResolveHost = firstNonEmpty(strings.TrimSpace(os.Getenv("HTTPDNS_RESOLVE_HOST")), fileCfg.HTTPDNS.ResolveHost, "r.dp.dgovl.com")
	cfg.DefaultSDNSOS = firstNonEmpty(strings.TrimSpace(os.Getenv("HTTPDNS_DEFAULT_SDNS_OS")), fileCfg.HTTPDNS.DefaultOS, "ios")
	cfg.ResolveURL = "https://" + cfg.ResolveHost + "/v1/d"
	cfg.Timeout = 10 * time.Second
	cfg.EnableMemoryCache = true
	cfg.EnableMetrics = true
	cfg.Logger = log.New(os.Stdout, "[SDK] ", log.LstdFlags)

	client, err := httpdns.NewClient(cfg)
	if err != nil {
		log.Fatalf("init sdk failed: %v", err)
	}
	defer client.Close()

	s := &server{client: client}
	mux := http.NewServeMux()
	mux.HandleFunc("/health", s.health)
	mux.HandleFunc("/resolve", s.resolve)

	addr := net.JoinHostPort(host, port)
	log.Printf("demo server listening on %s", addr)
	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatal(err)
	}
}

func (s *server) health(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"ok":          true,
		"status":      "ok",
		"service_ips": s.client.GetServiceIPs(),
		"metrics":     s.client.GetMetrics(),
	})
}

func (s *server) resolve(w http.ResponseWriter, r *http.Request) {
	start := time.Now()
	host := strings.TrimSpace(r.URL.Query().Get("host"))
	if host == "" {
		writeJSON(w, http.StatusBadRequest, map[string]any{"ok": false, "error": "validation failed", "validation_errors": map[string]string{"host": "请输入解析域名"}})
		return
	}

	resolveType := strings.TrimSpace(r.URL.Query().Get("resolve_type"))
	q := strings.TrimSpace(r.URL.Query().Get("q"))
	if q == "" && resolveType != "" {
		q = parseQ(resolveType)
	}
	if q == "" {
		q = "4,6"
	}
	if q != "4" && q != "6" && q != "4,6" {
		writeJSON(w, http.StatusBadRequest, map[string]any{"ok": false, "error": "validation failed", "validation_errors": map[string]string{"q": "q 仅支持 4 / 6 / 4,6"}})
		return
	}

	cip := strings.TrimSpace(r.URL.Query().Get("cip"))
	ctx, cancel := context.WithTimeout(r.Context(), 8*time.Second)
	defer cancel()

	opts := []httpdns.ResolveOption{httpdns.WithTimeout(8 * time.Second)}
	if cip != "" {
		opts = append(opts, httpdns.WithClientIP(cip))
	}
	switch q {
	case "4":
		opts = append(opts, httpdns.WithIPv4Only())
	case "6":
		opts = append(opts, httpdns.WithIPv6Only())
	default:
		opts = append(opts, httpdns.WithBothIP())
	}

	result, err := s.client.Resolve(ctx, host, opts...)
	if err != nil {
		writeJSON(w, http.StatusBadGateway, map[string]any{"ok": false, "error": err.Error(), "elapsed_ms": time.Since(start).Milliseconds()})
		return
	}

	ttl := int(result.TTL / time.Second)
	writeJSON(w, http.StatusOK, map[string]any{
		"ok":         true,
		"elapsed_ms": time.Since(start).Milliseconds(),
		"data": map[string]any{
			"display": map[string]any{
				"summary": map[string]any{
					"client_ip": cip,
					"domain":    result.Domain,
				},
				"table_groups": []map[string]any{
					{"domain": result.Domain, "record_type": "A", "rows": rowsFromIPs(result.IPv4, ttl)},
					{"domain": result.Domain, "record_type": "AAAA", "rows": rowsFromIPs(result.IPv6, ttl)},
				},
			},
			"raw_response": map[string]any{
				"answers": []map[string]any{{
					"dn":  result.Domain,
					"ttl": ttl,
					"v4":  map[string]any{"ips": stringifyIPs(result.IPv4), "ttl": ttl},
					"v6":  map[string]any{"ips": stringifyIPs(result.IPv6), "ttl": ttl},
				}},
				"cip": cip,
			},
			"cache": map[string]any{
				"hit": result.Source == httpdns.SourceCache,
				"ttl": ttl,
			},
		},
	})
}

func rowsFromIPs(ips []net.IP, ttl int) []map[string]any {
	rows := make([]map[string]any, 0, len(ips))
	for _, ip := range ips {
		rows = append(rows, map[string]any{"ip": ip.String(), "ttl": ttl})
	}
	return rows
}

func stringifyIPs(ips []net.IP) []string {
	out := make([]string, 0, len(ips))
	for _, ip := range ips {
		out = append(out, ip.String())
	}
	return out
}

func parseQ(v string) string {
	s := strings.ToUpper(strings.TrimSpace(v))
	switch s {
	case "A":
		return "4"
	case "AAAA":
		return "6"
	case "A+AAAA", "A+AAAAA":
		return "4,6"
	default:
		return ""
	}
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if strings.TrimSpace(value) != "" {
			return value
		}
	}
	return ""
}

func intToString(value int) string {
	if value <= 0 {
		return ""
	}
	return strconv.Itoa(value)
}

func writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(payload)
}
