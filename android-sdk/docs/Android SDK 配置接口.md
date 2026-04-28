# Android SDK 配置接口

## 前言

本文档介绍 `InitConfig.Builder` 的配置接口。

## 必填配置

### `setContext`

接口定义：

```kotlin
setContext(context: Context)
```

说明：

- 必填。
- 建议使用 `applicationContext`。

### `setAesSecretKey`

接口定义：

```kotlin
setAesSecretKey(aesSecretKey: String)
```

说明：

- 必填。
- 支持 UTF-8 长度 `16/24/32` 或 hex 长度 `32/48/64`。

## 网络与区域配置

### `setRegion`

接口定义：

```kotlin
setRegion(region: Region)
```

支持值：

- `Region.DEFAULT` -> `global`
- `Region.CN` -> `cn`
- `Region.OS` -> `os`
- `Region.GLOBAL` -> `global`

### `setEnableHttps`

```kotlin
setEnableHttps(enableHttps: Boolean)
```

### `setUseCronet`

```kotlin
setUseCronet(useCronet: Boolean)
```

### `setTimeoutMillis`

```kotlin
setTimeoutMillis(timeoutMillis: Int)
```

说明：

- 范围会被限制在 `100..5000`。

## 缓存与解析行为配置

### `setEnableExpiredIp`

```kotlin
setEnableExpiredIp(enableExpiredIp: Boolean)
```

说明：

- 控制是否允许返回过期缓存结果。

### `setEnableCacheIp`

```kotlin
setEnableCacheIp(enableCacheIp: Boolean, expiredThresholdMillis: Long)
```

说明（重要）：

- `enableCacheIp = false` 时，SDK 不读取缓存也不写入缓存。
- `expiredThresholdMillis` 当前版本仅作为配置参数保留，未参与缓存过期计算。

### `setCacheTtlChanger`

```kotlin
setCacheTtlChanger(cacheTtlChanger: CacheTtlChanger?)
```

说明：

- 可自定义解析结果 TTL。

### `setNotUseHttpDnsFilter`

```kotlin
setNotUseHttpDnsFilter(notUseHttpDnsFilter: NotUseHttpDnsFilter?)
```

说明：

- 可对指定 host 跳过 HTTPDNS。

## 解析入口 override

### `setResolveHostOverride`

```kotlin
setResolveHostOverride(resolveHostOverride: String?)
```

### `setResolveEndpoint`

```kotlin
setResolveEndpoint(resolveHost: String, connectIp: String? = null)
```

### `setResolvePortOverride`

```kotlin
setResolvePortOverride(resolvePort: Int?)
```

### `clearResolveEndpointOverride`

```kotlin
clearResolveEndpointOverride()
```

说明：

- 当设置 `resolveConnectIpOverride` 时，必须同时有 `resolveHostOverride`。
- 端口仅允许 `1..65535`。

## 其他配置

### `setClientIp`

```kotlin
setClientIp(clientIp: String?)
```

说明：

- 会透传到 resolve 请求 `cip`。

### `setLogger`

```kotlin
setLogger(logger: ILogger?)
```

### `build`

```kotlin
build(): InitConfig
```
