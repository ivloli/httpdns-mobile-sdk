# Android Demo 使用说明

## 前言

本文档说明客户下载 Demo 压缩包后，如何在本地快速运行并验证 HTTPDNS 解析。

## 一、仓库目录说明

在本仓库中，目录关系如下：

- `httpdns-android-demo`
- `android-sdk`

说明：

- Demo 工程通过 `project(":httpdns-android-sdk")` 依赖本地 `../android-sdk` 源码。
- 若你移动 Demo 工程，请同步修改 `httpdns-android-demo/settings.gradle.kts` 里的 `projectDir`。

## 二、打开工程

1. 打开 Android Studio。
2. 选择 `Open`。
3. 选择目录：`httpdns-mobile-sdk/httpdns-android-demo`。
4. 等待 Gradle Sync 完成。

## 三、运行 Demo

1. 连接真机或启动模拟器。
2. 点击 Run，运行 `app`。
3. 在页面输入：
   - `Account ID`
   - `AES Key`
   - `Host`（例如 `www.baidu.com`）
4. 点击 `Resolve Async`。

## 四、结果说明

页面会展示：

- `host`
- `ipv4`
- `ipv6`
- `ttl`
- `expired`
- `extras`

若解析失败，会显示错误信息，可按“常见问题”排查。

## 五、常见问题

### 1) Gradle Sync 失败

- 检查 `httpdns-android-demo/settings.gradle.kts` 中 `project(":httpdns-android-sdk").projectDir` 是否指向 `../android-sdk`。
- 检查网络是否可访问依赖仓库（`google()`、`mavenCentral()`）。

### 2) 点击解析后无结果或报错

- 检查 `Account ID`、`AES Key` 是否正确。
- 检查待解析域名是否已在服务端配置。
- 检查设备网络是否可用。

### 3) 主线程调用同步接口问题

- Demo 默认使用异步接口。
- 若自行改成同步接口，建议放到子线程调用。

## 六、命令行验证（可选）

当前 Demo 默认通过 Android Studio 直接运行。

如需命令行验证，可在 `httpdns-android-demo` 目录执行：

```bash
./gradlew :app:assembleDebug
```

作用：

- 验证 Demo 工程可完整编译。
