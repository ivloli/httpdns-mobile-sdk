# iOS SDK 配置接口

## 前言

本文档介绍 `HttpDnsService` 当前实现的配置接口。

## 初始化与实例获取

### `initWithAccountID`

```swift
let service = HttpDnsService.initWithAccountID("<account>", aesSecretKey: "<aes>")
```

### `getInstanceByAccountId`

```swift
let instance = HttpDnsService.getInstanceByAccountId("<account>")
```

### `sharedInstance`

```swift
let shared = HttpDnsService.sharedInstance()
```

## 区域与网络配置

### `setRegion`

```swift
service.setRegion("cn")
```

当前识别值：

- `cn`
- `os` / `oversea` / `overseas`
- `global`

### `setHTTPSRequestEnabled`

```swift
service.setHTTPSRequestEnabled(true)
```

### `setNetworkingTimeoutInterval`

```swift
service.setNetworkingTimeoutInterval(2.0)
```

- 单位秒。
- 内部会限制到 `100..5000ms`。

## 缓存与行为配置

### `setReuseExpiredIPEnabled`

```swift
service.setReuseExpiredIPEnabled(true)
```

### `setExpiredIPEnabled`

```swift
service.setExpiredIPEnabled(true)
```

- `setReuseExpiredIPEnabled` 别名。

### `setPersistentCacheIPEnabled`

```swift
service.setPersistentCacheIPEnabled(true)
```

### `setCachedIPEnabled`

```swift
service.setCachedIPEnabled(true)
```

- `setPersistentCacheIPEnabled` 别名。

说明（重要）：

- `enableCacheIp = false` 时，SDK 不读取缓存也不写入缓存。
- `cacheExpiredThresholdMillis` 当前版本仅作为配置参数保留，未参与缓存过期计算。

## 其他配置

### `setClientIp`

```swift
service.setClientIp("1.2.3.4")
```

- 会透传到 resolve 请求 `cip`。

### `setLogger`

```swift
service.setLogger(logger)
```

### `setResolveEndpoint`

```swift
service.setResolveEndpoint(host: "r.dp.dgovl.com", connectIp: nil, port: 0)
```

### `clearResolveEndpointOverride`

```swift
service.clearResolveEndpointOverride()
```
