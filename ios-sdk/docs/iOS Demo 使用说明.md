# iOS Demo 使用说明

## 前言

本文档说明客户下载 iOS Demo 压缩包后，如何在本地快速运行并验证 HTTPDNS 解析。

## 一、下载与解压

下载压缩包：

- `httpdns-ios-demo-with-sdk.zip`

解压后目录：

- `httpdns-ios-sdk`

说明：

- iOS Demo 位于仓库内 `Sources/ScloudHTTPDNSDemo`。
- Demo 通过 Swift Package 直接依赖同仓库内 SDK 源码。

## 二、运行前准备

请准备以下参数：

- `ACCOUNT_ID`
- `AES_KEY`

可选参数：

- `TEST_HOST`（单域名）
- `TEST_HOSTS`（多域名，支持逗号分隔）
- `CLIENT_IP`（用于透传 `cip`）

## 三、运行 Demo

在终端执行：

```bash
cd httpdns-ios-sdk
ACCOUNT_ID=<你的账号ID> AES_KEY=<你的AES密钥> swift run ScloudHTTPDNSDemo
```

## 四、结果说明

终端会输出以下信息：

- 初始化日志
- 预解析调用结果
- 同步/异步/非阻塞解析结果
- 每次解析的 `host/ips/ipv6/ttl/expired/extras`

示例输出字段：

- `host`
- `ips`
- `ipv6`
- `ttl`
- `expired`
- `extras`

## 五、常见问题

### 1) 运行时报 `set ACCOUNT_ID and AES_KEY first`

- 未设置 `ACCOUNT_ID` 或 `AES_KEY`。
- 请按示例命令传入环境变量。

### 2) 解析结果为空

- 检查账号和密钥是否正确。
- 检查待解析域名是否已在服务端配置。
- 检查设备网络是否可用。

### 3) 回调线程问题

- 异步回调在 SDK worker queue 执行，不保证主线程。

## 六、可选验证命令

在 `httpdns-ios-sdk` 目录执行：

```bash
swift build --target ScloudHTTPDNS
```

作用：

- 验证 SDK 源码可正常编译。
