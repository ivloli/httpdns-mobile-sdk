package main

import (
	"context"
	"fmt"
	"log"
	"os"
	"strings"
	"time"

	"scloud/httpdns-go-sdk-demo/internal/common"
	"scloud/httpdns-go-sdk/pkg/httpdns"
)

func main() {
	fileCfg, err := common.LoadConfigOrDefault("config.local.yaml")
	if err != nil {
		log.Fatalf("load config failed: %v", err)
	}

	dispatchHost := strings.TrimSpace(os.Getenv("HTTPDNS_DISPATCH_HOST"))
	if dispatchHost == "" {
		dispatchHost = strings.TrimSpace(fileCfg.HTTPDNS.DispatchHost)
	}
	resolveHost := strings.TrimSpace(os.Getenv("HTTPDNS_RESOLVE_HOST"))
	if resolveHost == "" {
		resolveHost = strings.TrimSpace(fileCfg.HTTPDNS.ResolveHost)
	}
	if resolveHost == "" {
		resolveHost = "r.dp.dgovl.com"
	}
	resolverURL := "https://" + resolveHost + "/v1/d"

	config := httpdns.DefaultConfig()
	config.AccountID = firstNonEmpty(strings.TrimSpace(os.Getenv("HTTPDNS_ACCOUNT_ID")), strings.TrimSpace(fileCfg.HTTPDNS.AccountID))
	config.AESKey = firstNonEmpty(strings.TrimSpace(os.Getenv("HTTPDNS_AES_KEY")), strings.TrimSpace(fileCfg.HTTPDNS.AESKey))
	config.DispatchHost = dispatchHost
	config.ResolveHost = resolveHost
	config.ResolveURL = resolverURL
	config.Logger = log.New(os.Stdout, "[HTTPDNS SDK] ", log.LstdFlags)

	client, err := httpdns.NewClient(config)
	if err != nil {
		log.Fatal(err)
	}
	defer client.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	result, err := client.Resolve(ctx, "www.aliyun.com", httpdns.WithBothIP())
	if err != nil {
		log.Fatalf("resolve failed: %v", err)
	}

	fmt.Printf("domain=%s ttl=%s ipv4=%v ipv6=%v\n", result.Domain, result.TTL, result.IPv4, result.IPv6)

	batch, err := client.ResolveBatch(ctx, []string{"www.aliyun.com", "www.taobao.com"}, httpdns.WithIPv4Only())
	if err != nil {
		log.Fatalf("batch resolve failed: %v", err)
	}
	for _, item := range batch {
		fmt.Printf("batch domain=%s ipv4=%v\n", item.Domain, item.IPv4)
	}

	done := make(chan struct{})
	client.ResolveAsync(ctx, "www.tmall.com", func(asyncResult *httpdns.ResolveResult, asyncErr error) {
		defer close(done)
		if asyncErr != nil {
			log.Printf("async resolve failed: %v", asyncErr)
			return
		}
		fmt.Printf("async domain=%s ipv4=%v\n", asyncResult.Domain, asyncResult.IPv4)
	})

	<-done
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if strings.TrimSpace(value) != "" {
			return value
		}
	}
	return ""
}
