# Android SDK 常用数据结构接口

## 前言

本文档介绍 SDK 常用数据结构与回调接口。

## HTTPDNSResult

`HTTPDNSResult` 为域名解析结果对象。

### `getHost`

接口定义：

```kotlin
getHost(): String
```

### `getIps`

接口定义：

```kotlin
getIps(): Array<String>
```

### `getIpv6s`

接口定义：

```kotlin
getIpv6s(): Array<String>
```

### `getExtras`

接口定义：

```kotlin
getExtras(): Map<String, String>
```

### `getTtl`

接口定义：

```kotlin
getTtl(): Int
```

### `isExpired`

接口定义：

```kotlin
isExpired(): Boolean
```

## HttpDnsCallback

### `onHttpDnsCompleted`

接口定义：

```kotlin
fun onHttpDnsCompleted(result: HTTPDNSResult)
```

## HttpDnsBatchCallback

### `onHttpDnsBatchCompleted`

接口定义：

```kotlin
fun onHttpDnsBatchCompleted(results: List<HTTPDNSResult>)
```

## CacheTtlChanger

### `changeCacheTtl`

接口定义：

```kotlin
fun changeCacheTtl(host: String, type: RequestIpType, ttl: Int): Int
```

## NotUseHttpDnsFilter

### `notUseHttpDns`

接口定义：

```kotlin
fun notUseHttpDns(host: String): Boolean
```

## ILogger

### `log`

接口定义：

```kotlin
fun log(msg: String)
```

## RequestIpType

枚举值：

- `v4`
- `v6`
- `both`
- `auto`

兼容常量：

- `V4`
- `V6`
- `BOTH`
- `AUTO`

## Region

枚举值：

- `DEFAULT`
- `CN`
- `OS`
- `GLOBAL`
