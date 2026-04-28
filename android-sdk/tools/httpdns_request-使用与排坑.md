# HTTPDNS Python Tool 使用与排坑说明

> 适用文件：`tools/httpdns_request.py`

## 1. 目的

这份文档用于记录当前项目里 HTTPDNS 调度接口和解析接口的正确访问方式，以及 `tools/httpdns_request.py` 的正确使用姿势。

重点是把今天已经验证成功的组合固定下来，避免后续再次踩坑。

## 2. 当前已验证成功的服务端访问方式

### 2.1 Dispatch（调度接口）

正确访问方式：

- Host：`r.pp.fgnlo.com`
- Path：`/dnps-apis/v1/httpdns/endpoints`
- Query 参数：
  - `account_id=<ACCOUNT_ID>`
  - `enc=<AES-GCM-HEX>`
- 加密算法：`AES-GCM`
- 当前已验证成功的明文格式：`proto`
- 当前已验证成功的 key 解析方式：`utf8`

当前成功口径：

- `dispatch = utf8 key + proto payload`

调度接口成功示例命令：

```bash
python3 "tools/httpdns_request.py" \
  --mode dispatch \
  --scheme https \
  --account-id 430992419037876224 \
  --aes-key 78eb9332f1fc18a45597dbf332858ff4 \
  --aes-key-mode utf8 \
  --bootstrap-host r.pp.fgnlo.com \
  --dispatch-payload-format proto
```

成功后的典型结果：

```json
{"list":[{"region":"os","ips":[],"domains":["r.dp.dgovl.com"]}]}
```

### 2.2 Resolve（解析接口）

正确访问方式：

- Host：`r.dp.dgovl.com`
- Path：`/v1/d`
- Query 参数：
  - `id=<ACCOUNT_ID>`
  - `enc=<AES-GCM-HEX>`
- 加密算法：`AES-GCM`
- 当前已验证成功的明文格式：`json`
- 当前已验证成功的 key 解析方式：`hex`

当前成功口径：

- `resolve = hex key + json payload`

解析接口成功示例命令：

```bash
python3 "tools/httpdns_request.py" \
  --mode resolve \
  --scheme https \
  --account-id 430992419037876224 \
  --aes-key 78eb9332f1fc18a45597dbf332858ff4 \
  --aes-key-mode hex \
  --resolve-host r.dp.dgovl.com \
  --dn www.baidu.com \
  --q 4,6 \
  --sdns-os ios
```

成功后的典型结果：

```json
{
  "answers": [
    {
      "dn": "www.baidu.com",
      "v4": {"ips": ["180.101.49.44", "180.101.51.73"], "ttl": 120},
      "v6": {"ips": ["240e:e9:6002:1fd:0:ff:b0e1:fe69", "240e:e9:6002:1ac:0:ff:b07e:36c5"], "ttl": 120},
      "ttl": 120
    }
  ],
  "cip": "172.31.54.81",
  "latency": 0
}
```

## 3. 为什么 Dispatch 和 Resolve 不能共用同一套 key 规则

今天联调结论是：

- Dispatch 对当前账号链路，`utf8` key 才能成功
- Resolve 对当前账号链路，`hex` key 才能成功

也就是：

- `dispatch=utf8`
- `resolve=hex`

这也是为什么 Android SDK 最后做成了“内部按接口分流解析 key”。

## 4. `tools/httpdns_request.py` 使用方式

### 4.1 基本命令格式

```bash
python3 "tools/httpdns_request.py" [参数...]
```

### 4.2 支持的模式

- `--mode dispatch`
- `--mode resolve`

### 4.3 常用参数与默认值

以下默认值来自当前代码：`tools/httpdns_request.py:26`

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `--mode` | `dispatch` | 模式：`dispatch` 或 `resolve` |
| `--scheme` | `https` | 请求协议：`https` 或 `http` |
| `--account-id` | 空 | 账号 ID，必填 |
| `--aes-key` | 空 | AES Key，必填 |
| `--aes-key-mode` | `auto` | key 解析模式：`auto` / `hex` / `utf8` |
| `--bootstrap-host` | `r.pp.fgnlo.com` | dispatch 使用的 host |
| `--bootstrap-ip` | 空 | dispatch 直连 IP，可选 |
| `--resolve-host` | 空 | resolve 使用的 host |
| `--resolve-ip` | 空 | resolve 直连 IP，可选 |
| `--region` | `global` | dispatch region |
| `--dispatch-payload-format` | `proto` | dispatch 明文格式：`proto` / `json` |
| `--exp-seconds` | `600` | 当前时间基础上的过期秒数 |
| `--dn` | `www.example.com` | resolve 目标域名 |
| `--q` | `4,6` | 解析类型 |
| `--cip` | 空 | 客户端 IP，可选 |
| `--sdns-os` | 空 | 扩展参数，可选 |
| `--timeout-seconds` | `10` | 超时秒数 |
| `--disable-proxy` | `true` | 是否禁用系统代理 |
| `--tls-verify` | `true` | 是否校验证书 |
| `--ip-connect-sni` | `true` | 使用 IP 直连时是否带 SNI |
| `--auto-fallback-http` | `false` | HTTPS 失败后是否自动退 HTTP |
| `--show-http-only` | `false` | 仅打印请求，不真正发请求 |

## 5. 参数解释与推荐使用方式

### 5.1 `--aes-key-mode`

可选值：

- `utf8`：把 key 当 UTF-8 原文字节
- `hex`：把 key 当十六进制字符串解码
- `auto`：优先 UTF-8，失败再尝试 hex

当前推荐：

- dispatch：显式传 `--aes-key-mode utf8`
- resolve：显式传 `--aes-key-mode hex`

不要依赖 `auto` 做最终联调结论。

### 5.2 `--dispatch-payload-format`

可选值：

- `proto`
- `json`

当前推荐：

- dispatch 使用 `proto`

### 5.3 `--bootstrap-host` 与 `--resolve-host`

当前正确值：

- dispatch：`r.pp.fgnlo.com`
- resolve：`r.dp.dgovl.com`

不要混用：

- 不要拿 `resolve` 域名去测 dispatch
- 不要拿 `bootstrap` 域名去测 resolve

### 5.4 `--bootstrap-ip` / `--resolve-ip`

用途：

- 用于“逻辑 host + 直连 IP”的联调方式

说明：

- 工具会在发请求时保留 `Host` 头
- HTTPS 场景下可配合 `--ip-connect-sni` 控制是否带 SNI

### 5.5 `--tls-verify` 与 `--ip-connect-sni`

当前默认：

- `--tls-verify true`
- `--ip-connect-sni true`

什么时候调整：

- 如果证书主机名不匹配，可临时尝试 `--tls-verify false`
- 如果需要复现“IP 直连但不带 SNI”的特殊联调脚本，可尝试 `--ip-connect-sni false`

## 6. 当前推荐命令模板

### 6.1 推荐的 Dispatch 命令模板

```bash
python3 "tools/httpdns_request.py" \
  --mode dispatch \
  --scheme https \
  --account-id <ACCOUNT_ID> \
  --aes-key <AES_KEY> \
  --aes-key-mode utf8 \
  --bootstrap-host r.pp.fgnlo.com \
  --dispatch-payload-format proto
```

### 6.2 推荐的 Resolve 命令模板

```bash
python3 "tools/httpdns_request.py" \
  --mode resolve \
  --scheme https \
  --account-id <ACCOUNT_ID> \
  --aes-key <AES_KEY> \
  --aes-key-mode hex \
  --resolve-host r.dp.dgovl.com \
  --dn <HOST> \
  --q 4,6
```

## 7. 今天踩过的坑

### 7.1 Dispatch 用错了 payload 格式

错误表现：

- HTTP `400`
- 返回 `{"code":"MissingArgument"}`

原因：

- 当前 dispatch 不是 JSON 明文，而是 proto 明文

正确做法：

- `--dispatch-payload-format proto`

### 7.2 Dispatch 用错了 key 模式

错误表现：

- dispatch 返回 `MissingArgument`

原因：

- 当前 dispatch 对这组账号链路要求 `utf8` key 解析

正确做法：

- `--aes-key-mode utf8`

### 7.3 Resolve 用错了 key 模式

错误表现：

- HTTP `400`
- 返回 `{"code":400,"msg":"decrypt failed"}`

原因：

- 当前 resolve 对这组账号链路要求 `hex` key 解析

正确做法：

- `--aes-key-mode hex`

### 7.4 用错了 bootstrap 域名

错误表现：

- TLS/证书异常
- 或行为与预期不一致

正确做法：

- dispatch 统一用 `r.pp.fgnlo.com`

### 7.5 误以为 preResolve 是同步填充缓存

说明：

- `preResolve` 只是异步调度后台任务
- 不保证下一行读取时缓存一定已经写好

## 8. 快速排查建议

建议按这个顺序排查：

1. 先单测 dispatch 是否通
2. dispatch 通后，再单测 resolve 是否通
3. 明确 key 模式：dispatch 看 `utf8`，resolve 看 `hex`
4. 明确 host：dispatch 用 `r.pp...`，resolve 用 `r.dp...`
5. 明确 payload：dispatch 用 `proto`，resolve 用 `json`

## 9. 一句话结论

当前这套账号链路的正确访问口径是：

- `dispatch = r.pp.fgnlo.com + utf8 key + proto payload`
- `resolve = r.dp.dgovl.com + hex key + json payload`
