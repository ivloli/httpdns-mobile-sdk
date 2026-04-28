# iOS SDK接入流程

## 前言

本文档介绍 Scloud HTTPDNS iOS SDK 的接入和基础使用流程。

说明：

- 本文档只描述当前代码已实现能力。
- 所有接口以 `httpdns-ios-sdk` 仓库代码为准。

## 准备工作

- 获取可用的 `accountID`。
- 获取可用的 `aesSecretKey`。
- 确保待解析域名已在服务端配置。

## 第一步：将 SDK 添加到应用

当前仓库是 Swift Package，产品名为 `ScloudHTTPDNS`。

在业务代码中引入：

```swift
import ScloudHTTPDNS
```

## 第二步：初始化 SDK

```swift
let service = HttpDnsService.initWithAccountID(
    "<Your-AccountId>",
    aesSecretKey: "<Your-AES-Key>"
)
```

获取实例：

```swift
let shared = HttpDnsService.sharedInstance()
```

## 第三步：配置 SDK

```swift
service.setRegion("global")
service.setHTTPSRequestEnabled(true)
service.setReuseExpiredIPEnabled(true)
service.setNetworkingTimeoutInterval(2.0)
service.setClientIp("1.2.3.4")
```

## 第四步：开始解析

### 1 预解析

```swift
service.setPreResolveHosts(["www.aliyun.com", "www.taobao.com"], byIPType: .both)
```

### 2 异步解析

```swift
service.getHttpDnsResultForHostAsync("www.aliyun.com", byIPType: .both) { result in
    let ipv4 = result.ips
    let ipv6 = result.ipv6s
}
```

### 3 同步解析

```swift
let result = service.getHttpDnsResultForHostSync("www.aliyun.com", byIPType: .both)
```

### 4 同步非阻塞解析

```swift
let result = service.getHttpDnsResultForHostSyncNonBlocking("www.aliyun.com", byIPType: .both)
```

## 第五步：使用解析结果

```swift
let result = service.getHttpDnsResultForHostSync("www.aliyun.com", byIPType: .both)
let host = result.host
let ips = result.ips
let ipv6s = result.ipv6s
let ttl = result.ttl
let expired = result.expired
let extras = result.extras
```

## 注意事项

1. 建议传入纯域名，不要带 `http://`、路径或端口。
2. 业务侧必须有降级逻辑（失败时回退 LocalDNS/原始域名）。
3. 异步回调不保证在主线程。
4. `setPersistentCacheIPEnabled(false)/setCachedIPEnabled(false)` 可关闭缓存读写；`cacheExpiredThresholdMillis` 当前版本未参与过期计算。
