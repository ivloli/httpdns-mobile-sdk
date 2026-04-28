# iOS SDK API 概览

## 入口类

### `HttpDnsService`

- `init(accountID:aesSecretKey:logger:)`
- `initWithAccountID(_:aesSecretKey:)`
- `initWithAccountID(_:aesSecretKey:logger:)`
- `getInstanceByAccountId(_:)`
- `sharedInstance()`

## 配置接口

- `setRegion(_:)`
- `setReuseExpiredIPEnabled(_:)`
- `setExpiredIPEnabled(_:)`
- `setHTTPSRequestEnabled(_:)`
- `setNetworkingTimeoutInterval(_:)`
- `setLogger(_:)`
- `setPersistentCacheIPEnabled(_:)`
- `setCachedIPEnabled(_:)`
- `setClientIp(_:)`
- `setResolveEndpoint(host:connectIp:port:)`
- `clearResolveEndpointOverride()`

## 解析接口

### 预解析

- `setPreResolveHosts(_:)`
- `setPreResolveHosts(_:byIPType:)`
- `setPreResolveHosts(_:queryIPType:)`

### 同步解析

- `getHttpDnsResultForHostSync(_ host: String)`
- `getHttpDnsResultForHostSync(_ host: String, byIPType: HttpdnsQueryIPType)`
- `getHttpDnsResultForHostSync(_ host: String, queryIPType: AlicloudHttpDNS_IPType)`
- `getHttpDnsResultForHostSync(_ request: HttpdnsRequest)`
- `getHttpDnsResultForRequestSync(_ request: HttpdnsRequest)`

Swift-only（`@nonobjc`）批量重载：

- `getHttpDnsResultForHostSync(_ hostList: [String])`
- `getHttpDnsResultForHostSync(_ hostList: [String], byIPType: HttpdnsQueryIPType)`

### 异步解析

- `getHttpDnsResultForHostAsync(_ host: String, completion:)`
- `getHttpDnsResultForHostAsync(_ host: String, byIPType: HttpdnsQueryIPType, completion:)`
- `getHttpDnsResultForHostAsync(_ host: String, queryIPType: AlicloudHttpDNS_IPType, completion:)`

Swift-only（`@nonobjc`）批量重载：

- `getHttpDnsResultForHostAsync(_ hostList: [String], completion:)`
- `getHttpDnsResultForHostAsync(_ hostList: [String], byIPType: HttpdnsQueryIPType, completion:)`

### 同步非阻塞解析

- `getHttpDnsResultForHostSyncNonBlocking(_ host: String)`
- `getHttpDnsResultForHostSyncNonBlocking(_ host: String, byIPType: HttpdnsQueryIPType)`
- `getHttpDnsResultForHostSyncNonBlocking(_ host: String, queryIPType: AlicloudHttpDNS_IPType)`

Swift-only（`@nonobjc`）批量重载：

- `getHttpDnsResultForHostSyncNonBlocking(_ hostList: [String])`
- `getHttpDnsResultForHostSyncNonBlocking(_ hostList: [String], byIPType: HttpdnsQueryIPType)`

### 缓存清理

- `cleanHostCache(_ hosts: [String]?)`
