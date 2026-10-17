# 云端识别反代网关

把 DeepSeek 密钥关进服务端，让 APK 里**不含任何第三方密钥**。

```
手机 App ──X-ST-Token: st:xxxx──▶ 本网关 ──Authorization: Bearer <真 key>──▶ api.deepseek.com
                                      │
                                      └─ 白名单模型 / 限流 / 日配额 / 令牌吊销
```

## 为什么这么做

直连方案里，`sk-...` 一旦进了 APK 或用户手机，就等于交出去了：

| 泄露渠道 | 直连 | 走网关 |
|---|---|---|
| 反编译 APK | 如果硬编码 → **key 直接暴露** | 只有网关 URL |
| root / 备份导出 SharedPreferences | 能拿到明文 key | 只能拿到设备令牌 |
| 用户截图/贴日志 | 可能带出 key | 只能带出 `st:` 令牌 |
| 令牌被滥用 | 无法限制 | 配额 + 速率 + 一键吊销 |
| 换 key | 要发版 | 改 `.env` 重启 |

关键点：**令牌不是密钥**。攻击者拿到 `st:xxxx` 只能刷这个令牌的配额（默认 50 次/天、6 次/分钟），
拿不到 DeepSeek 密钥，也无法换成别的模型。

## 部署

```bash
cd gateway
cp .env.example .env
# 填 DEEPSEEK_API_KEY 和 ISSUE_SECRET
node server.js
```

只依赖 Node 18+ 内置模块，**不需要 npm install**（少装包就少一份供应链风险）。

### 生成令牌

```bash
curl -X POST http://localhost:8787/v1/admin/issue \
  -H "X-Issue-Secret: <你的 ISSUE_SECRET>"
# {"token":"st:9f2a...e1"}
```

这个令牌只显示一次，库里存的是 `sha256(token)`，磁盘上看不到明文。
把 `st:9f2a...e1` 连同网关地址填进 App 的「云端识别凭据」页即可。

### 吊销 / 换令牌

```bash
curl -X POST "http://localhost:8787/v1/admin/revoke?id=<令牌 id>" \
  -H "X-Issue-Secret: <你的 ISSUE_SECRET>"
```

App 里点「生成新的设备令牌」会本地造一个新 `st:` 令牌；旧令牌需要在网关上吊销
（本地生成是为了零配置能跑通，正式环境应改成「先找网关签发、再存本地」）。

### 看用量

```bash
curl -H "X-Issue-Secret: <你的 ISSUE_SECRET>" http://localhost:8787/v1/admin/stats
# {"tokens":3,"active":2,"todayCalls":17}
```

## 端点

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| `POST` | `/v1/ai/analyze` | `X-ST-Token` | 识别，请求体同 DeepSeek `/chat/completions` |
| `GET` | `/v1/ai/health` | `X-ST-Token` | 健康检查 + 剩余配额，不消耗 token |
| `POST` | `/v1/admin/issue` | `X-Issue-Secret` | 签发令牌 |
| `POST` | `/v1/admin/revoke?id=` | `X-Issue-Secret` | 吊销令牌 |
| `GET` | `/v1/admin/stats` | `X-Issue-Secret` | 用量统计 |

## 自测

```bash
node test/forward.test.js
```

不需要真实 API key：脚本会拉起一个假上游（`test/fake-upstream.js`），
把网关的 `UPSTREAM_OVERRIDE` 指向它，然后断言 12 项行为。
全部通过时最后一行输出 `ALL PASS`，退出码 0。

实测输出（本机 Node v24.20.0）：

```
PASS  issue returns 201
PASS  token has st: prefix
PASS  analyze returns 200
PASS  response body passed through
PASS  upstream received exactly one call
PASS  model forced to whitelist  got deepseek-flash
PASS  client Authorization dropped  got Bearer sk-REAL-SECRET-IN-GATEWAY
PASS  extra field dropped
PASS  stream forced false
PASS  response_format pinned
PASS  quota decremented after success  left=49/50
PASS  stats counts the token
ALL PASS
```

其中最关键的两条：客户端传 `model: "deepseek-v4-pro"` 被改写成 `deepseek-flash`；
客户端塞的 `Authorization` 头被丢弃，上游只看到网关自己的密钥。


## 网关做了什么保护

1. **模型白名单**：客户端传 `model` 不在 `ALLOWED_MODELS` 里就被强制改成第一个，
   防止有人拿你的 key 去跑贵模型。
2. **字段白名单**：只转发 `model / messages / temperature / max_tokens / response_format`，
   `stream` 强制 `false`，客户端塞的任何额外字段一律丢弃。
3. **丢弃客户端 Authorization**：上游请求头由网关自己重建，防止被当成跳板。
4. **限流**：每分钟 + 每天双闸，超限返回 `429` 带具体原因。
5. **请求体上限**：默认 8 MB，超过直接 `413`，防止有人拿它传大文件。
6. **CORS 拒绝浏览器**：只放行 `Origin: null`，正常浏览器页面无法直接调用。
7. **令牌只存哈希**：`data/tokens.json` 泄露也无法直接伪造请求。

## 还需要自己补的（生产环境）

- **HTTPS**：本网关是裸 HTTP。前面必须挂 Nginx / Caddy / 云负载均衡做 TLS，
  Android 9+ 默认禁止明文 HTTP，只有调试才放宽。
- **反向代理限流**：nginx 层再加一层按 IP 的限流，挡掉扫描。
- **IP 白名单 / 国内出网**：DeepSeek 需要稳定出网，建议部署在香港或国内云。
- **日志脱敏**：本网关只打状态码和错误消息，不打请求体（里面有照片 base64）。
- **令牌与管理端分离**：正式环境把 `/v1/admin/*` 绑到内网或换成独立的运维端口。

## App 侧对应

| App 设置 | 值 |
|---|---|
| 设备令牌 | `st:` 开头的字符串 |
| 网关地址 | `https://你的域名`（可带子路径，自动去尾斜杠） |
| 模型 | `deepseek-flash`（必须在服务端白名单内） |

App 的判断逻辑很简单：令牌以 `st:` 开头就走网关，否则视为直连原厂 key（并在 UI 上红字警告）。
