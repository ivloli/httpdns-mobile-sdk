# Android SDK接入流程

## 前言

本文档介绍 Scloud HTTPDNS Android SDK 的接入和基础使用流程。

说明：

- 本文档只描述当前代码已实现能力。
- 所有接口以 `httpdns-android-sdk` 仓库代码为准。

## 准备工作

- 获取可用的 `accountId`。
- 获取可用的 `aesSecretKey`。
- 确保待解析域名已在服务端配置。

## 第一步：将 SDK 添加到应用

### 1 Maven/Module 依赖方式

当前仓库是 Android Library 模块，可作为模块依赖接入。

在业务工程 `settings.gradle.kts` 增加：

```kotlin
include(":httpdns-android-sdk")
project(":httpdns-android-sdk").projectDir = file("../httpdns-android-sdk")
```

在业务工程 `app/build.gradle.kts` 增加：

```kotlin
dependencies {
    implementation(project(":httpdns-android-sdk"))
}
```

### 2 网络权限

在业务 App `AndroidManifest.xml` 添加：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## 第二步：初始化 SDK

建议在 `Application` 启动阶段初始化。

```kotlin
val config = InitConfig.Builder()
    .setContext(applicationContext)
    .setAesSecretKey("<Your-AES-Key>")
    .setRegion(Region.DEFAULT)
    .setEnableHttps(true)
    .setUseCronet(true)
    .setEnableExpiredIp(true)
    .setTimeoutMillis(2000)
    .build()

HttpDns.init("<Your-AccountId>", config)
```

获取服务实例：

```kotlin
val service = HttpDns.getService("<Your-AccountId>")
```

## 第三步：开始解析

### 1 预解析

```kotlin
service.setPreResolveHosts(listOf("www.aliyun.com", "www.taobao.com"), RequestIpType.both)
```

### 2 异步解析

```kotlin
service.getHttpDnsResultForHostAsync("www.aliyun.com", RequestIpType.both) { result ->
    val ipv4 = result.getIps()
    val ipv6 = result.getIpv6s()
}
```

### 3 同步解析

```kotlin
val result = service.getHttpDnsResultForHostSync("www.aliyun.com", RequestIpType.both)
```

### 4 同步非阻塞解析

```kotlin
val result = service.getHttpDnsResultForHostSyncNonBlocking("www.aliyun.com", RequestIpType.both)
```

## 第四步：使用解析结果

```kotlin
val result = service.getHttpDnsResultForHostSync("www.aliyun.com", RequestIpType.both)
val host = result.getHost()
val ips = result.getIps()
val ipv6s = result.getIpv6s()
val ttl = result.getTtl()
val expired = result.isExpired()
val extras = result.getExtras()
```

## 注意事项

1. 建议传入纯域名，不要带 `http://`、路径或端口。
2. 业务侧必须有降级逻辑（失败时回退 LocalDNS/原始域名）。
3. 同步接口不建议在主线程使用；当前实现在主线程会降级为非阻塞逻辑。
4. `setEnableCacheIp(false, ...)` 可关闭缓存读写；`expiredThresholdMillis` 当前版本未参与过期计算。
