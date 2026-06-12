# API 对接鉴权决策记录

> Sprint-38 配套。场景：我方（DTS 平台）调用应用系统 API 拉取数据入湖。
> 鉴权方案由对方（应用系统）设计实现，我方只在对方给出的选项中选择并对接。

## 对方给出的三个选项与我方评估

| 选项 | 安全性 | 我方成本 | 评估 |
|------|--------|----------|------|
| **JWT token** | 较高：长期凭据仅换 token 时上线，日常请求为短时 token，泄漏自动过期 | 近零：F2-T03 token 策略覆盖（配 tokenUrl + 登录报文格式 + 过期重取） | **首选** |
| **API Key** | 一般：静态密钥每请求上线，内网 HTTP 抓包即得 | 零（apikey 已 GA） | 次选，须配 IP 白名单 + 轮换兜底 |
| HTTP Basic | 同 API Key 级，但跑的是"密码"语义、base64 易误解为加密 | 零（basic 已 GA） | 不选：同级安全没理由选更糟的语义 |

## 决策

**已确认（2026-06-12）：采用 JWT token。** 对接实现 = F2-T03 `JwtLoginAuthStrategy`（登录换 token → 内存缓存 → 过期/401 重登）。

## 待对方确认的对接信息（JWT 已选定，逐项回填）

| # | 项 | 说明 | 对方答复 |
|---|----|------|----------|
| 1 | 登录端点 URL | 例 `POST /api/auth/login` | ☐ |
| 2 | 登录报文格式 | 字段名（username/password 还是 appId/appSecret）、Content-Type | ☐ |
| 3 | token 字段路径 | 响应中 token 位置，例 `data.token` | ☐ |
| 4 | 过期信息 | expiresIn 字段名；若无，确认 JWT payload 含标准 `exp` claim | ☐ |
| 5 | token 放置方式 | `Authorization: Bearer {token}` 还是自定义 header | ☐ |
| 6 | 账号发放 | 给我方的专用账号 + 密码线下交接方式、轮换周期 | ☐ |
| 7 | 增量字段 | `updated_at` 格式（ISO8601 / epoch 毫秒，定死一种） | ☐ |
| 8 | 分页 | 参数名、pageSize 上限、末页判定（total 或 hasNext，勿靠"不足一页"） | ☐ |
| 9 | 限流 | QPS 上限；超限是否 429 + Retry-After | ☐ |
| 10 | 错误码 | 401 鉴权 / 4xx 参数 / 5xx 服务端；禁止全 200 + body 塞错误 | ☐ |
| 11 | 测试环境 | 测试地址 + 测试账号 + 1 个样例资源 | ☐ |

## 对 Sprint-38 的影响

零变更。apikey/basic 已 GA；JWT 归入 F2-T03 token 获取策略（与 OAuth2 client credentials 同形态，支持自定义登录端点）。`customSignature`/`mtls` 维持 PREVIEW。
