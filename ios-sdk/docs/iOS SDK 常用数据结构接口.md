# iOS SDK 常用数据结构接口

## 前言

本文档介绍 iOS SDK 常用数据结构与回调类型。

## HttpdnsRequest

主要字段：

- `host`
- `resolveTimeoutInSecond`
- `queryIpType`
- `sdnsParams`
- `cacheKey`
- `accountId`

说明：

- 当前链路生效字段：`host`、`resolveTimeoutInSecond`、`queryIpType`。

## HttpdnsResult

字段：

- `host`
- `ips`
- `ipv6s`
- `ttl`
- `expired`
- `extras`

方法：

- `hasIpv4Address()`
- `hasIpv6Address()`
- `firstIpv4Address()`
- `firstIpv6Address()`

## HttpdnsQueryIPType

- `auto`
- `ipv4`
- `ipv6`
- `both`

## AlicloudHttpDNS_IPType

- `v4`
- `v6`
- `v64`

## HttpdnsLogger

```swift
protocol HttpdnsLogger: AnyObject {
    func log(_ message: String)
}
```

## HttpdnsPublicConstant

- `HTTPDNS_IOS_SDK_VERSION`
- `ALICLOUD_HTTPDNS_DEFAULT_REGION_KEY`
- `ALICLOUD_HTTPDNS_HONGKONG_REGION_KEY`
- `ALICLOUD_HTTPDNS_SINGAPORE_REGION_KEY`
- `ALICLOUD_HTTPDNS_GERMANY_REGION_KEY`
- `ALICLOUD_HTTPDNS_AMERICA_REGION_KEY`
