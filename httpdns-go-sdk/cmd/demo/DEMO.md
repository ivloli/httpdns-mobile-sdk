# Demo 使用说明

本目录是独立 Go 模块（`cmd/demo/go.mod`），用于演示 SDK 接入方式。

## 1. 准备配置

默认读取当前目录 `config.local.yaml`，也可通过 `APP_CONFIG_PATH` 指定。

```yaml
service:
  host: "0.0.0.0"
  port: 38088

httpdns:
  account_id: "<YOUR_ACCOUNT_ID>"
  aes_key: "<YOUR_AES_KEY>"
  sign_key: "<YOUR_SIGN_KEY>"
  sign_algorithm: "hmac-sha1"
  sign_param_name: "sign"
  dispatch_host: "r.pp.fgnlo.com"
  resolve_host: "r.dp.dgovl.com"
  default_sdns_os: "ios"
```

## 2. 启动

在 demo 目录启动：

```bash
cd /Users/hechuan/Git_repos/GAI/httpdns-go-sdk/cmd/demo
APP_CONFIG_PATH=config.local.yaml go run .
```

启动 demo server（简化接口路径）：

```bash
cd /Users/hechuan/Git_repos/GAI/httpdns-go-sdk/cmd/demo
APP_CONFIG_PATH=config.local.yaml go run ./server
```

## 3. 输出内容

Demo 会依次执行：

1. `Resolve` 单域名解析
2. `ResolveBatch` 批量解析
3. `ResolveAsync` 异步解析

终端输出会包含域名、TTL、IPv4/IPv6 结果。

## 4. Demo Server 接口

启动 `./server` 后可用以下接口：

- `GET /health`
- `GET /resolve?host=www.baidu.com&cip=111.55.146.208&resolve_type=A+AAAAA`

示例：

```bash
curl -i "http://127.0.0.1:38088/health"
curl -i "http://127.0.0.1:38088/resolve?host=www.baidu.com&cip=111.55.146.208&resolve_type=A%2BAAAAA"
```
