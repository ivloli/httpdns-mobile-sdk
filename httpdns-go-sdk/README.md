# SCloud HTTPDNS Go SDK

## 一、快速入门

### 1.1 项目说明

本 SDK 用于在 Go 应用中接入 SCloud HTTPDNS 服务，提供：

- 同步解析
- 异步解析
- 批量解析（单次最多 5 个域名）
- 基础缓存与指标能力

SDK 请求协议参考了当前服务端实现，解析请求通过 `id + enc (+sign)` 访问 `/v1/d`。

### 1.2 安装

```bash
go get scloud/httpdns-go-sdk/pkg/httpdns
```

## 二、配置与初始化

### 2.1 默认配置

```go
cfg := httpdns.DefaultConfig()
```

### 2.2 必填参数

- `AccountID`
- `AESKey`
- `ResolveHost`

推荐同时配置：

- `DispatchHost`
- `SignKey`（如果后端要求签名）

### 2.3 初始化示例

```go
cfg := httpdns.DefaultConfig()
cfg.AccountID = "your-account-id"
cfg.AESKey = "your-aes-key"
cfg.SecretKey = "your-sign-key" // 可选
cfg.DispatchHost = "r.pp.fgnlo.com"
cfg.ResolveHost = "r.dp.dgovl.com"
cfg.ResolveURL = "https://" + cfg.ResolveHost + "/v1/d"

client, err := httpdns.NewClient(cfg)
if err != nil {
    panic(err)
}
defer client.Close()
```

## 三、解析能力

### 3.1 同步解析

```go
ctx := context.Background()
res, err := client.Resolve(ctx, "www.baidu.com", httpdns.WithBothIP())
if err != nil {
    panic(err)
}
```

### 3.2 批量解析

```go
ctx := context.Background()
results, err := client.ResolveBatch(ctx, []string{"www.baidu.com", "www.aliyun.com"}, httpdns.WithIPv4Only())
if err != nil {
    panic(err)
}
_ = results
```

### 3.3 异步解析

```go
ctx := context.Background()
client.ResolveAsync(ctx, "www.baidu.com", func(res *httpdns.ResolveResult, err error) {
    if err != nil {
        return
    }
    _ = res
})
```

## 四、核心 API

- `DefaultConfig()`
- `NewClient(config)`
- `Resolve(ctx, domain, opts...)`
- `ResolveBatch(ctx, domains, opts...)`
- `ResolveAsync(ctx, domain, callback, opts...)`
- `Close()`

解析选项：

- `WithIPv4Only()`
- `WithIPv6Only()`
- `WithBothIP()`
- `WithTimeout(duration)`
- `WithClientIP(ip)`
