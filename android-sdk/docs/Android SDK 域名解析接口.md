# Android SDK 域名解析接口

## 前言

本文档介绍 `HttpDnsService` 中当前已实现的域名解析接口。

## 设置预解析域名

### `setPreResolveHosts`

接口定义：

```kotlin
setPreResolveHosts(hostList: List<String>, requestIpType: RequestIpType = RequestIpType.auto)
setPreResolveHosts(hostList: List<String>)
```

代码示例：

```kotlin
service.setPreResolveHosts(listOf("www.aliyun.com"))
service.setPreResolveHosts(listOf("www.aliyun.com", "www.taobao.com"), RequestIpType.both)
```

说明：

- host 会做 `trim + lowercase` 归一化。
- 有有效缓存的 host 会跳过预解析。

## 同步解析域名

### `getHttpDnsResultForHostSync`

接口定义：

```kotlin
getHttpDnsResultForHostSync(host: String, requestIpType: RequestIpType): HTTPDNSResult
getHttpDnsResultForHostSync(hostList: List<String>, requestIpType: RequestIpType): List<HTTPDNSResult>
getHttpDnsResultForHostSync(host: String): HTTPDNSResult
getHttpDnsResultForHostSync(hostList: List<String>): List<HTTPDNSResult>
```

说明：

- 若在主线程调用，同步接口会自动降级为非阻塞逻辑。
- 缓存命中直接返回。
- 缓存过期且允许复用时，返回过期结果并后台刷新。

## 异步解析域名

### `getHttpDnsResultForHostAsync`

接口定义：

```kotlin
getHttpDnsResultForHostAsync(host: String, requestIpType: RequestIpType, callback: HttpDnsCallback)
getHttpDnsResultForHostAsync(hostList: List<String>, requestIpType: RequestIpType, callback: HttpDnsBatchCallback)
getHttpDnsResultForHostAsync(host: String, callback: HttpDnsCallback)
getHttpDnsResultForHostAsync(hostList: List<String>, callback: HttpDnsBatchCallback)
```

代码示例：

```kotlin
service.getHttpDnsResultForHostAsync("www.aliyun.com", RequestIpType.both) { result ->
    // handle result
}
```

说明：

- 回调在工作线程执行，不保证主线程。

## 同步非阻塞解析域名

### `getHttpDnsResultForHostSyncNonBlocking`

接口定义：

```kotlin
getHttpDnsResultForHostSyncNonBlocking(host: String, requestIpType: RequestIpType): HTTPDNSResult
getHttpDnsResultForHostSyncNonBlocking(hostList: List<String>, requestIpType: RequestIpType): List<HTTPDNSResult>
getHttpDnsResultForHostSyncNonBlocking(host: String): HTTPDNSResult
getHttpDnsResultForHostSyncNonBlocking(hostList: List<String>): List<HTTPDNSResult>
```

说明：

- 只查缓存并立即返回。
- 无缓存或缓存不可用时会后台刷新，并返回空结果。

## 清理缓存

### `cleanHostCache`

接口定义：

```kotlin
cleanHostCache(hosts: List<String>?)
```

说明：

- `hosts == null` 或空列表：清空全部缓存。
- 非空列表：清理指定 host 缓存。

## 兼容保留接口

以下接口已标记 `Deprecated`，建议迁移到回调版本：

- `getHttpDnsResultForHostAsync(host: String, requestIpType: RequestIpType): HTTPDNSResult`
- `getHttpDnsResultForHostAsync(hostList: List<String>, requestIpType: RequestIpType): List<HTTPDNSResult>`
- `getHttpDnsResultForHostAsync(host: String): HTTPDNSResult`
- `getHttpDnsResultForHostAsync(hostList: List<String>): List<HTTPDNSResult>`
