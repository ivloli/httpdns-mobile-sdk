# iOS Demo 使用说明

## 前言

本文档说明客户下载 iOS Demo 压缩包后，如何在本地快速运行并验证 HTTPDNS 解析。

## 一、仓库目录说明

在本仓库中，目录关系如下：

- `httpdns-ios-demo`
- `ios-sdk`

说明：

- iOS Demo 工程通过 Local Swift Package 依赖本地 `../ios-sdk`。
- 若你移动 Demo 工程，请同步修改 `httpdns-ios-demo/MyDemoForHttpDns.xcodeproj/project.pbxproj` 里的 `relativePath`。

## 二、打开工程

1. 打开 Xcode。
2. 选择 `Open`。
3. 选择目录：`httpdns-mobile-sdk/httpdns-ios-demo`。
4. 等待 Swift Package Dependencies 解析完成。

## 三、运行 Demo

1. 选择 `MyDemoForHttpDns` Scheme。
2. 选择模拟器或真机。
3. 点击 Run。
4. 在页面中输入或修改 `Host / Hosts`、`Client IP`。
5. 依次点击 `Init`、`Resolve`（Sync/Async/NonBlocking）进行验证。

## 四、结果说明

页面会展示以下信息：

- 初始化结果
- 同步/异步/非阻塞解析结果
- Browser-like Access 访问结果
- 日志输出

示例输出字段：

- `host`
- `v4`
- `ipv6`
- `ttl`
- `expired`
- `extras`

## 五、常见问题

### 1) Swift Package 解析失败

- 检查 `httpdns-ios-demo` 与 `ios-sdk` 是否保持仓库内相对位置。
- 检查 `project.pbxproj` 中 `relativePath` 是否为 `../ios-sdk`。

### 2) 解析结果为空

- 检查账号和密钥是否正确。
- 检查待解析域名是否已在服务端配置。
- 检查设备网络是否可用。

### 3) 回调线程问题

- 异步回调可能不在主线程，UI 更新应切回主线程。

## 六、可选验证命令

在仓库根目录执行：

```bash
swift build --package-path ios-sdk --target ScloudHTTPDNS
```

作用：

- 验证 iOS SDK 源码可正常编译。
