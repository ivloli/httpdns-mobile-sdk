# Android SDK API 概览

## 前言

本文档列出当前版本 Android SDK 的公开 API。

## 入口类

### `HttpDns`

- `init(accountId: String, config: InitConfig)`
- `getService(accountId: String): HttpDnsService`
- `getService(accountId: String, config: InitConfig): HttpDnsService`
- `getService(context: Context, accountId: String): HttpDnsService`（Deprecated）

## 核心服务接口

### `HttpDnsService`

#### 预解析

- `setPreResolveHosts(hostList: List<String>, requestIpType: RequestIpType = RequestIpType.auto)`
- `setPreResolveHosts(hostList: List<String>)`

#### 同步解析

- `getHttpDnsResultForHostSync(host: String, requestIpType: RequestIpType): HTTPDNSResult`
- `getHttpDnsResultForHostSync(hostList: List<String>, requestIpType: RequestIpType): List<HTTPDNSResult>`
- `getHttpDnsResultForHostSync(host: String): HTTPDNSResult`
- `getHttpDnsResultForHostSync(hostList: List<String>): List<HTTPDNSResult>`

#### 异步解析

- `getHttpDnsResultForHostAsync(host: String, requestIpType: RequestIpType, callback: HttpDnsCallback)`
- `getHttpDnsResultForHostAsync(hostList: List<String>, requestIpType: RequestIpType, callback: HttpDnsBatchCallback)`
- `getHttpDnsResultForHostAsync(host: String, callback: HttpDnsCallback)`
- `getHttpDnsResultForHostAsync(hostList: List<String>, callback: HttpDnsBatchCallback)`

#### 同步非阻塞解析

- `getHttpDnsResultForHostSyncNonBlocking(host: String, requestIpType: RequestIpType): HTTPDNSResult`
- `getHttpDnsResultForHostSyncNonBlocking(hostList: List<String>, requestIpType: RequestIpType): List<HTTPDNSResult>`
- `getHttpDnsResultForHostSyncNonBlocking(host: String): HTTPDNSResult`
- `getHttpDnsResultForHostSyncNonBlocking(hostList: List<String>): List<HTTPDNSResult>`

#### 缓存清理

- `cleanHostCache(hosts: List<String>?)`

#### 兼容保留接口（Deprecated）

- `getHttpDnsResultForHostAsync(host: String, requestIpType: RequestIpType): HTTPDNSResult`
- `getHttpDnsResultForHostAsync(hostList: List<String>, requestIpType: RequestIpType): List<HTTPDNSResult>`
- `getHttpDnsResultForHostAsync(host: String): HTTPDNSResult`
- `getHttpDnsResultForHostAsync(hostList: List<String>): List<HTTPDNSResult>`

## 配置类

### `InitConfig.Builder`

- `setContext(context: Context)`
- `setAesSecretKey(aesSecretKey: String)`
- `setRegion(region: Region)`
- `setEnableCacheIp(enableCacheIp: Boolean, expiredThresholdMillis: Long)`
- `setEnableExpiredIp(enableExpiredIp: Boolean)`
- `setTimeoutMillis(timeoutMillis: Int)`
- `setEnableHttps(enableHttps: Boolean)`
- `setUseCronet(useCronet: Boolean)`
- `setResolveHostOverride(resolveHostOverride: String?)`
- `setResolveEndpoint(resolveHost: String, connectIp: String? = null)`
- `setResolvePortOverride(resolvePort: Int?)`
- `setClientIp(clientIp: String?)`
- `clearResolveEndpointOverride()`
- `setCacheTtlChanger(cacheTtlChanger: CacheTtlChanger?)`
- `setNotUseHttpDnsFilter(notUseHttpDnsFilter: NotUseHttpDnsFilter?)`
- `setLogger(logger: ILogger?)`
- `build(): InitConfig`
