# iOS SDK 域名解析接口

## 前言

本文档介绍 `HttpDnsService` 中当前已实现的域名解析接口。

## 设置预解析域名

### `setPreResolveHosts`

接口定义：

```swift
setPreResolveHosts(_ hosts: [String])
setPreResolveHosts(_ hosts: [String], byIPType: HttpdnsQueryIPType)
setPreResolveHosts(_ hosts: [String], queryIPType: AlicloudHttpDNS_IPType)
```

## 同步解析域名

### `getHttpDnsResultForHostSync`

接口定义：

```swift
getHttpDnsResultForHostSync(_ host: String) -> HttpdnsResult
getHttpDnsResultForHostSync(_ host: String, byIPType: HttpdnsQueryIPType) -> HttpdnsResult
getHttpDnsResultForHostSync(_ host: String, queryIPType: AlicloudHttpDNS_IPType) -> HttpdnsResult
```

请求对象版本：

```swift
getHttpDnsResultForHostSync(_ request: HttpdnsRequest) -> HttpdnsResult
getHttpDnsResultForRequestSync(_ request: HttpdnsRequest) -> HttpdnsResult
```

Swift-only 批量版本：

```swift
getHttpDnsResultForHostSync(_ hostList: [String]) -> [HttpdnsResult]
getHttpDnsResultForHostSync(_ hostList: [String], byIPType: HttpdnsQueryIPType) -> [HttpdnsResult]
```

## 异步解析域名

### `getHttpDnsResultForHostAsync`

接口定义：

```swift
getHttpDnsResultForHostAsync(_ host: String, completion: @escaping (HttpdnsResult) -> Void)
getHttpDnsResultForHostAsync(_ host: String, byIPType: HttpdnsQueryIPType, completion: @escaping (HttpdnsResult) -> Void)
getHttpDnsResultForHostAsync(_ host: String, queryIPType: AlicloudHttpDNS_IPType, completion: @escaping (HttpdnsResult) -> Void)
```

Swift-only 批量版本：

```swift
getHttpDnsResultForHostAsync(_ hostList: [String], completion: @escaping ([HttpdnsResult]) -> Void)
getHttpDnsResultForHostAsync(_ hostList: [String], byIPType: HttpdnsQueryIPType, completion: @escaping ([HttpdnsResult]) -> Void)
```

## 同步非阻塞解析域名

### `getHttpDnsResultForHostSyncNonBlocking`

接口定义：

```swift
getHttpDnsResultForHostSyncNonBlocking(_ host: String) -> HttpdnsResult
getHttpDnsResultForHostSyncNonBlocking(_ host: String, byIPType: HttpdnsQueryIPType) -> HttpdnsResult
getHttpDnsResultForHostSyncNonBlocking(_ host: String, queryIPType: AlicloudHttpDNS_IPType) -> HttpdnsResult
```

Swift-only 批量版本：

```swift
getHttpDnsResultForHostSyncNonBlocking(_ hostList: [String]) -> [HttpdnsResult]
getHttpDnsResultForHostSyncNonBlocking(_ hostList: [String], byIPType: HttpdnsQueryIPType) -> [HttpdnsResult]
```

## 清理缓存

### `cleanHostCache`

```swift
cleanHostCache(_ hosts: [String]?)
```
