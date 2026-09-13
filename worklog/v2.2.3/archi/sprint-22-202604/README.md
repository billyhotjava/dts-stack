# Sprint-22: Portal Session 安全架构升级（Admin Token 剥离 + 短 TTL + BFF/HttpOnly Cookie）

**时间**: 2026-04
**状态**: READY
**类型**: Architecture / Security（dts-platform-webapp + dts-platform + Keycloak realm）
**目标**: 把当前"前端持有 portal + admin 双套 token、access/refresh 全部明文写 localStorage"的会话模型，升级为"凭据由服务端持有、浏览器只见 SID cookie"的 BFF（Backend-for-Frontend）架构，从根本上消除 XSS 直取凭据的可能性，同时把"被盗 token 的可重放窗口"从小时级压缩到分钟级。

---

## 1. 背景

### 1.1 触发

针对 `dts-platform-webapp` 的 session 管理模块（`SessionManager`、`apiClient`、`LoginAuthGuard`、`portalSessionStorage`、`userStore`）做架构师 + 测试工程师双视角评审，发现 P0 级安全缺陷：前端把 portal 与 admin 两套 access/refresh token **全部以明文形式持久化在 localStorage**。任意 XSS = 全部凭据失陷 + 可重放刷新。配套发现一组 P1 级并发/一致性问题（leader 选举 split-brain、跨 tab token 同步走 localStorage、TEST_SESSION_ENABLED 旁路开关随生产构建发布等）。

完整研判明细见 `worklog/v2.2.3/sprint-22-202604/review/session-management-audit.md`（本 sprint 一并归档）。

### 1.2 现状画像

```
┌──────────────────────┐                              ┌──────────────────────────┐
│  浏览器 (SPA)        │                              │  dts-platform (Spring)   │
│                      │   POST /keycloak/auth/login  │  ┌────────────────────┐  │
│  zustand persist ──▶ │ ───────────────────────────▶ │  │ AuthController     │  │ ──┐
│  localStorage:       │                              │  │ (代理 Keycloak)    │  │   │
│   userToken {        │ ◀── access+refresh+admin ─── │  └────────────────────┘  │   │
│     accessToken      │       (双套 token 明文)      │                          │   │
│     refreshToken     │                              │  PortalSessionEntity     │   │
│     adminAccessToken │                              │   (DB 已经持有所有 token)│   │
│     adminRefreshTok. │                              │                          │   │
│   }                  │                              │                          │   │
│                      │ ──── Authorization: Bearer ─▶│  业务接口                │   ▼
│  Authorization Hdr ─▶│       (portal access only)   │                          │
│   每次业务请求注入   │                              │                          │  Keycloak
│                      │                              │                          │
│  /session/status     │ ── X-Portal-Access-Token ──▶ │  /session/status         │
│   30s 周期探活       │   (无 Authorization)         │                          │
└──────────────────────┘                              └──────────────────────────┘
```

关键事实：
- `userStore.ts:88-96` 把 `userToken`（含 portal + admin 四 token）通过 zustand persist 写入 `localStorage`。
- `apiClient.ts:280` 仅注入 `Bearer ${portalAccessToken}`；**admin token 在前端没有任何读用点**——纯遗留字段。
- `PortalSessionEntity.java:72-81` 已经在服务端持有 admin token 副本，BFF 模型的服务端 session 存储**已经具备**。
- `apiClient.ts:43-47` 的 `VITE_TEST_LONG_SESSION` 开关一旦在生产构建被误开，所有 session 失效响应（异地登录顶替、绝对过期、登出广播）会被前端静默忽略。

### 1.3 业务定位

DTS 平台是政企 / 数据治理类系统，CLAUDE.md、IAM 模块、密级矩阵、审计日志均明确：

- 多角色 + ABAC + 多密级
- 党政机关交付场景，对凭据托管有合规要求
- 审计可追溯、登出可强制、异地登录可顶替

这套定位与"token 明文留浏览器"的安全等级不匹配，需要一次架构层面的纠偏。

---

## 2. 核心原则

1. **凭据不出后端**：portal access/refresh、admin access/refresh、Keycloak refresh token 全部由 dts-platform 服务端持有，浏览器只见不可逆的 SID。
2. **Keycloak 仍是唯一 IdP**：本 sprint 不替换 SSO 服务器，只把 dts-platform 的"代理拦截角色"显式化为 BFF。
3. **渐进迁移、可回滚**：三阶段拆分，每一阶段独立可灰度、独立可下线，不允许"一次性切换"导致回退困难。
4. **业务接口零改动**：业务 axios 调用方（catalog/governance/iam 各模块）的代码不应感知到鉴权载体的变化（从 Authorization header 切到 cookie）。
5. **跨 tab 复杂度下沉**：cookie 天然跨 tab 共享，前端 SessionManager 中 leader 选举、tokenSync 广播、refresh loop 等代码可大幅简化或删除。
6. **测试先行**：每一阶段都要有行为级（vitest + jsdom + 真实 axios mock + Playwright e2e）测试覆盖，不再依赖 source-string 字面量 contract test。

---

## 3. Feature 列表

| ID | Feature | Task 数 | 优先级 | 阶段 |
|----|---------|--------:|--------|------|
| F1 | Admin Token 前端剥离 | 4 | P0 | 阶段一（本 sprint W1） |
| F2 | Portal Token 短 TTL + Refresh Rotation | 5 | P0 | 阶段二（本 sprint W2-W3） |
| F3 | Leader 选举切换至 navigator.locks | 3 | P1 | 阶段二并行 |
| F4 | 生产构建剔除 TEST_SESSION 旁路 + Dev Fallback host allowlist | 3 | P0 | 阶段一并行 |
| F5 | 移除生产 console 中的 Authorization / 响应体打印 | 2 | P0 | 阶段一并行 |
| F6 | BFF 层骨架（Spring Security OAuth2 Client + Session Cookie） | 6 | P0 | 阶段三（W4-W5） |
| F7 | 前端切换：axios `withCredentials` + 移除 token 持久化 + CSRF 注入 | 5 | P0 | 阶段三（W5-W6） |
| F8 | Keycloak Backchannel Logout 接入 | 3 | P1 | 阶段三 |
| F9 | SessionManager 简化（删除 leader/refresh/tokenSync） | 3 | P1 | 阶段三尾部 |
| F10 | 行为级测试与 e2e 闭环 | 5 | P0 | 全程 |

**合计 39 个 task。**

---

## 4. 三阶段路线图

### 4.1 阶段一（W1）：止血——前端凭据暴露面收敛

#### F1. Admin Token 前端剥离

##### 原理

`adminAccessToken / adminRefreshToken / adminAccessTokenExpiresAt / adminRefreshTokenExpiresAt` 在前端只写不读：从登录响应里被收下来，写到 localStorage（`userStore.ts:138-154, 257-265`），跨 tab 同步广播（`session-manager.tsx:213-217, 484-486`），但**没有任何业务请求把它当作 header 发出去**——`apiClient.ts:280` 注入的 `Authorization` 永远来自 portal accessToken，跟 admin 字段无关。

后端 `PortalSessionEntity.java:72-81` 与 `PortalSessionRegistry.java:303-307` 已经在服务端保留了 admin token 的副本，因此前端的这四个字段是早期"前端直调 admin API"那版的纯遗留字段。

##### 完善的原因

- **风险**：admin token 比 portal token 更敏感（admin 后台能力），却跟 portal token 在同一份 localStorage 里。任意 XSS 一次性偷走两套。
- **收益/成本比极高**：纯字段删除，**无任何功能损失**。
- **是 BFF 改造的前置准备**：admin token 前端不可见之后，第三阶段把 portal token 也搬回服务端时不需要再考虑 admin 维度的兼容。

##### 改动清单

后端（`dts-platform`）：
1. `/keycloak/auth/login`、`/keycloak/auth/platform/login`、`/keycloak/auth/refresh` 三个响应 DTO 中删除 `adminAccessToken / adminRefreshToken / adminAccessTokenExpiresAt / adminRefreshTokenExpiresAt`。
2. 如果业务方需要"admin 链路是否已建立"，下发不可重放的标记字段如 `adminLinked: boolean`（不带 token）。

前端（`dts-platform-webapp`）：
1. `types/entity.ts:33-36` — 删除四字段。
2. `store/userStore.ts:138-154, 257-265, 287-292` — 删除读写。
3. `api/apiClient.ts:56-59, 171-199` — `PortalRefreshResult` 与 refresh 处理删除。
4. `components/auth/session-manager.tsx:213-217, 484-486` — `tokenSync` 事件不再带 admin 字段。

##### 验证

- 登录后在 DevTools `Application → Local Storage → dts.platform.userStore` 确认 `userToken` 不含 admin 任何字段。
- 全量 grep `adminAccessToken|adminRefreshToken` 在 `dts-platform-webapp/src` 应返回 0 结果。
- 既有 e2e 用例（catalog/iam/governance）回归通过。

---

#### F4. TEST_SESSION_ENABLED 旁路下线 + Dev Fallback Host Allowlist

##### 原理

`apiClient.ts:43-47` 的 `TEST_SESSION_ENABLED` 由 `VITE_TEST_LONG_SESSION` / `VITE_TEST_SESSION` 环境变量在 build 期被 Vite 内联：

```ts
shouldForceLogout && !TEST_SESSION_ENABLED && !isLoginRequest
```

这意味着只要某次生产 build 误带了这两个 env，所有 `x-session-conflict / x-session-expired` 响应头会被前端无视，异地登录顶替能力被静默禁用。

`userStore.ts:328-378` 的 `handleDevFallback` 在 `import.meta.env.DEV && VITE_DEV_LOGIN_FALLBACK=true` 时，前端凭 `username` 自签 `dev-access-*` 假 token 并赋予 `ROLE_OP_ADMIN`。DEV 构建一旦被部署到 staging / 内网测试环境就能"无密码登录"。

##### 完善的原因

- 配置错误就能导致全局安全失效是不可接受的。
- 阶段三 BFF 改造完成后，`TEST_SESSION_ENABLED` 这种"前端旁路"在物理上不可能存在（因为前端没 token），但阶段一/二期间必须先把这个口子封住。

##### 改动清单

1. Vite 构建增加 `mode === 'production'` 判断，强制把 `VITE_TEST_LONG_SESSION` / `VITE_TEST_SESSION` 打成 `false`，不读 env。或直接 strip 整段相关代码（通过 `define` + dead code elimination）。
2. `handleDevFallback` 增加运行时 host allowlist：仅在 `location.hostname` 命中 `localhost / 127.0.0.1 / *.local` 时生效。中长期改为后端 mock。
3. 增加构建时检查脚本：production build 完成后 grep 输出 bundle 不应含字符串 `VITE_TEST_LONG_SESSION` 或 `dev-access-`。

---

#### F5. 生产 Console 凭据/敏感数据打印移除

##### 原理

`apiClient.ts:320` `console.log("API Request:", config.method?.toUpperCase(), config.baseURL, config.url, config)` 会把整个 axios `config` 对象（包含 `headers.Authorization`）落入浏览器 console。`apiClient.ts:331` 同款打印响应体。

浏览器扩展、F12 共享屏幕、远程协助调试、内部录屏培训等场景都会把 token 与 PII 暴露出去。属 OWASP A09 日志与监控。

##### 完善的原因

- 一行改动消除明文 token 落 console。
- 阶段三 BFF 后 token 不在前端，但响应体的 PII 仍可能出现，所以这条治理跟阶段三独立。

##### 改动清单

1. 抽 `apiClient.ts` 内部 logger，开发模式下保留打印但屏蔽 `Authorization`、`X-Portal-Access-Token`、响应 `data` 字段；生产模式下完全静默。
2. ESLint 规则 `no-console`（除 `console.error / warn`）在 `src/api/**` 强制开启。
3. PR 模板加一项 "新增 console.log 是否包含敏感字段" checkbox。

---

### 4.2 阶段二（W2-W3）：缩寿命 + 修并发——可重放窗口压到分钟级

#### F2. Portal Token 短 TTL + Refresh Rotation

##### 原理

当前 `SESSION_TIMEOUT_MINUTES` 默认 30 分钟（`session-manager.tsx:22-26`），前端 refresh 节奏按"半个 portal session"兜底，约每 14 分钟一次。Access token 实际寿命取决于 Keycloak realm 配置。

被盗 token 的"可重放窗口" = `access TTL + refresh TTL`。如果配置是默认 5 分钟 access + 30 分钟 refresh，窗口最长 35 分钟；若 refresh 是几小时甚至天级，窗口就太长。

**Refresh Token Rotation**：每次刷新返回新 refresh token，旧 refresh 立即作废。配合 reuse detection（旧 refresh 再次出现 = 被偷 → 把整条 token 家族强制下线）。

##### 完善的原因

- 阶段三 BFF 落地前，先把"被盗 token 能用多久"这个时间维度压到最小。
- Rotation 是端到端 token 安全的标配，Keycloak 19+ 原生支持，realm 开关即可。
- 是阶段三的预演：把 refresh 频次拉高后，leader 选举的并发问题会更尖锐，倒逼 F3 同步落地。

##### 改动清单

后端 / Keycloak realm：
1. `Access Token Lifespan` 调到 ≤ 5 分钟。
2. `SSO Session Idle` / `Client Session Idle` 与业务 idle 阈值对齐（建议 30 分钟，与 `SESSION_TIMEOUT_MINUTES` 一致）。
3. 打开 `Revoke Refresh Token` + `Refresh Token Max Reuse = 0`（rotation + reuse detection）。
4. `/keycloak/auth/refresh` 响应明确返回 `expiresIn` 与 `refreshExpiresIn`（秒），不再让前端靠经验值。

前端：
1. `session-manager.tsx:76-86` `nextRefreshDelayMs`：`MIN_DELAY` 由 60s 调到 30s，`SKEW` 由 60s 调到 30s。常量参数化提到模块顶部。
2. `session-manager.tsx:72-74` `nextRefreshRetryDelayMs`：上限 30s 在 5 分钟 access TTL 下偏长，下调到 ≤ 15s。
3. 增加"refresh 失败 + 旧 refresh 已被服务端吊销" → `reason=ROTATION_REUSE` 分支，触发友好提示后重登。
4. 长睡眠场景（合电脑 → 唤醒）：`visibilitychange` visible 时立即触发一次 refresh 而不是等下次定时器。

---

#### F3. Leader 选举切换至 navigator.locks

##### 原理

当前 `session-manager.tsx:265-280` + `sessionLeadership.helpers.ts:36-45` 用 localStorage lease + `LEADER_CONFIRM_MS=250` 软互斥，在两个 tab 同时读到 `lease=null` 时会出现 split-brain：A、B 先后 setItem 后 readBack，A 读到自己刚写、B 读到自己刚写（B 后写覆盖 A），但 storage event 抵达前 A 的 in-memory 状态仍以为自己是 leader，并发触发 refresh。

`navigator.locks.request("portal-refresh-leader", { mode: "exclusive" }, async () => { ... })` 是浏览器原生互斥，跨 tab 强一致，无 split-brain。

##### 完善的原因

- F2 的 rotation 上线后，两个 tab 拿同一份旧 refresh token 同时刷会被服务端判 reuse → 全家族作废 → 用户被踢。这种"突然被踢"是 rotation 上线最常见的事故，必须先把 leader 互斥做硬。
- 阶段三 BFF 落地后该模块整体删除，但本阶段必须先稳住。

##### 改动清单

1. 新增 `components/auth/sessionLeadership.lock.ts`，封装 `navigator.locks.request` 返回一个 `withLeadership(callback)` 高阶函数。
2. `session-manager.tsx` 的 refresh loop 改为 `withLeadership(async () => { await refreshPortalSessionIfPossible(); broadcastTokenSync(...); })`，移除 lease 续租 timer。
3. 浏览器兼容回退：`navigator.locks` 在 Chrome 69+ / Firefox 96+ / Safari 15.4+ 可用；不可用时降级到原 lease 实现（保留 `sessionLeadership.helpers.ts` 一段时间）。
4. 测试：vitest + 模拟两个并发 tab（两个 jsdom worker）验证只发出一次 refresh。

---

### 4.3 阶段三（W4-W6）：架构升级——BFF + HttpOnly Cookie

#### 3.1 为什么是"对的选择"

**行业共识层面**

- IETF OAuth WG 草案 `draft-ietf-oauth-browser-based-apps`（2023+）将 BFF（Token-Mediating Backend）列为浏览器应用的**首选**架构，把"SPA 直持 token"列为"acceptable but discouraged"。
- Keycloak 官方文档：高安全场景的 SPA 推荐 "confidential client + BFF"；公共客户端（Auth Code + PKCE，token 留浏览器）只列为低敏感备选。
- Curity / Auth0 / Okta 的 reference architecture 在高合规客户里清一色走 BFF（也叫 Token Handler Pattern）。

**项目现状层面**

- `dts-platform`（Spring Boot）已经自己 wrap 了 Keycloak。前端调的不是 KC，是 `/keycloak/auth/login` `/keycloak/auth/refresh` `/keycloak/auth/logout`（`api/services/userService.ts:21-23`）—— 这些是 dts-platform 里的 Spring controller，本来就是代理。
- 服务端已经有完整 session 表 `PortalSessionEntity`，里面已经持有所有 token（`PortalSessionEntity.java:72-81`）。
- `/session/status`（`platformApi.ts:18`）已经是"问 BFF 当前会话状态"的接口语义。
- `PortalSessionRegistry.java:303-307` 已经在管 admin token 的生命周期。

**结论**：BFF 的所有基础设施都有，剩下的工作是"把响应里的 token 换成 Set-Cookie"+"把前端 axios 注入 Authorization 改为 withCredentials"——核心改动两点。比"全新引入 BFF"少做了 70% 的工作。

#### 3.2 目标架构

```
┌────────────┐  ①POST /bff/login          ┌─────────────┐  ②token endpoint           ┌──────────┐
│  浏览器    │ ──────────────────────────▶│   BFF       │────────────────────────▶  │ Keycloak │
│ (SPA)      │                            │ (dts-platform│                            │          │
│            │ ◀── Set-Cookie: SID=…  ─── │  + Spring   │ ◀── access+refresh token ── │          │
│            │     HttpOnly Secure        │  Security)  │                            └──────────┘
│            │     SameSite=Strict        │             │
│            │                            │ Portal      │
│            │  ③业务请求自动带 cookie    │ Session     │
│            │ ──────────────────────────▶│ Entity      │ ── 取 portal token ─▶ 业务路由
│            │ ◀────业务数据──────────────│ (DB/Redis)  │
└────────────┘                            └─────────────┘
```

关键变化：
- **浏览器只持有不透明 SID cookie**（HttpOnly + Secure + SameSite=Strict）。token 不在浏览器。
- **BFF 持有 token**：`PortalSessionEntity` 已经具备，扩展为 SID → session 映射即可。
- **登录、登出、refresh 全在 BFF 内部完成**，浏览器不可见。
- **跨 tab 同步问题消失**：cookie 天然跨 tab 共享，leader 选举、tokenSync、refresh loop 整块前端复杂度可删（F9）。

#### 3.3 与 Keycloak 集成的关键决策

##### 决策 A：登录流程 — ROPC vs Auth Code + PKCE

**现状**：`/keycloak/auth/login` 收 `{username, password}`，本质是 Keycloak 的 Resource Owner Password Credentials (ROPC / Direct Access Grant) flow。

**问题**：
- OAuth 2.1 已正式废弃 ROPC。
- Keycloak 文档明确写 "only for legacy"。
- 用户密码经过应用层转发，破坏 KC SSO 单点登录语义（用户不会真的被 KC 登录，KC 那边的 `KEYCLOAK_SESSION` cookie 不存在 → 跨产品 SSO 失效）。
- PKI 证书登录（`pkiService.ts`）按理应该作为 KC 的 custom authenticator 在 KC 内部实现，而不是绕过 KC。

**方案**：
- **首选**：BFF 跳转用户到 KC 登录页（Auth Code + PKCE），KC 完成认证后回跳 `/bff/callback?code=xxx`，BFF 用 code 换 token 并落 PortalSessionEntity，发 SID cookie。PKI 登录改造为 KC custom authenticator。
- **折中**：保留"自有登录页（不跳 KC 域名）"，BFF 在内部仍走 ROPC，但 token 不出 BFF。安全性比纯 Auth Code 略弱（ROPC 固有问题），但比当前"前端见 token"好得多。**本 sprint 默认走折中方案**，把"切到 Auth Code + PKCE"留给后续 sprint。

##### 决策 B：Session 存储

**现状**：`PortalSessionEntity` 是 PostgreSQL JPA 实体，DB 做 session store。

**含义**：
- 持久会话（进程重启不掉线）—— 优点。
- 每个请求一次 DB 查询 —— 在 N 个微服务实例 + 高 QPS 下要看索引和缓存。`PortalSessionRegistry` 应已做缓存层，本 sprint F6 task 之一是核对。
- 可选优化：Spring Session Data Redis 做热缓存，PG 做持久化兜底。中等规模可先不动。

**SID 设计**：
- 32 字节高熵随机串（不是 UUID v4）。
- 每次登录新发，每次 refresh 不变。
- `Set-Cookie: SID=...; Path=/; HttpOnly; Secure; SameSite=Strict`。

##### 决策 C：Logout 协调

Keycloak 支持两种登出：
- **Front-channel logout**：用户在 KC 主动登出，KC 通过隐藏 iframe 把所有 RP 一并登出。BFF 暴露 `/bff/keycloak-logout-callback` 接收。
- **Back-channel logout**（KC 19+ 推荐）：KC 直接 POST 到 BFF，告知"用户 X 已登出"。配置在 KC client → "Backchannel Logout URL"。

**现有"异地登录顶替（CONCURRENT）"逻辑应迁移到 KC backchannel logout 上**，KC 那边已经实现"踢老 session"语义，BFF 不用在 PortalSessionRegistry 里再做一遍。F8 task 专门覆盖这块。

##### 决策 D：多产品 SSO 协调

DTS 平台有两个 webapp：`dts-platform-webapp` 与 `dts-admin`（admin token 来源暗示了 admin-console）。BFF 模式下：
- 两个 webapp 同父域时（如 `platform.x.gov.cn` / `admin.x.gov.cn`），cookie 设 `Domain=.x.gov.cn` 可共享 SID —— **不推荐**，platform XSS 会影响 admin。
- **推荐做法**：两个 BFF 各发各的 cookie；KC 的 `KEYCLOAK_SESSION` 单点登录提供 SSO —— 用户首次登 platform 后，去 admin 时 BFF 跳 KC，KC 看到自己的 cookie 直接静默回跳，无感 SSO，但两个 BFF 的 SID 隔离。

这恰好是 KC 设计的"正确用法"，跟 BFF 模式天然契合。

##### 决策 E：CSRF 防护（cookie 模式必须补）

HttpOnly cookie 不防 CSRF —— 浏览器自动带上 cookie 是双刃剑。要做：
- 全部状态修改请求（POST/PUT/DELETE）要求 `X-CSRF-Token` header，服务端 double-submit 校验。
- `SameSite=Strict` 已能挡住绝大多数跨站；嵌入式（iframe）兼容需求才退到 `SameSite=Lax`。
- Spring Security 内置 `CsrfFilter`，配 `CookieCsrfTokenRepository.withHttpOnlyFalse()`（CSRF token 必须 JS 可读，与 SID 不冲突，两个 cookie 各司其职）。

#### 3.4 Feature 拆解

##### F6. BFF 层骨架

后端任务（dts-platform）：
1. 新增 `BffAuthController`：`/bff/login`（POST）、`/bff/logout`（POST）、`/bff/session`（GET）、`/bff/callback`（GET，给 Auth Code 折中方案预留）。
2. SID 发行模块：32 字节 secure random，`Set-Cookie: SID=...; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=...`。
3. `SidAuthenticationFilter`（Spring Security 自定义 filter）：从 cookie 读 SID → 查 PortalSessionEntity → 注入 `SecurityContext`。原 `Bearer` 拦截器在迁移期保留双轨。
4. CSRF：启用 Spring `CsrfFilter` + `CookieCsrfTokenRepository`，仅对状态修改方法生效。
5. CORS：`allowCredentials=true`，`Access-Control-Allow-Origin` 显式列出（不能是 `*`）。
6. Spring Session Data Redis 接入（可选，本 sprint 评估即可）。

##### F7. 前端切换

1. `apiClient.ts`：
   - `axios.create({ baseURL, withCredentials: true })`。
   - 请求拦截器：移除 `Authorization` 注入；增加 `X-CSRF-Token` 注入（从 `XSRF-TOKEN` cookie 读，axios 默认支持）。
   - 401 处理：不再尝试 silent refresh（refresh 由 BFF 内部完成），直接跳 `/login`。
2. `userStore.ts`：移除 `userToken` 字段；`userInfo` 仍保留但不再 persist 任何 token。zustand persist 改为只 persist `userInfo` 中的非敏感子集。
3. `pages/sys/login/login-form.tsx`：调用改为 POST `/bff/login`，响应不含 token，后端 Set-Cookie。
4. `LoginAuthGuard`：用 `/bff/session` 替代 `/session/status`；不再读本地 token 判过期。
5. PKI 登录链路同步切换。

##### F8. Backchannel Logout

1. KC 控制台为 `dts-platform` client 配 Backchannel Logout URL = `https://<bff>/bff/keycloak-logout-callback`。
2. 后端实现 `/bff/keycloak-logout-callback`：校验 KC 签名（JWS）→ 找到对应 PortalSessionEntity → 标记登出 → 删除 SID 映射。
3. 前端通过下次 `/bff/session` 探活感知到登出，自动跳登录页。
4. CONCURRENT 顶替：KC realm 配 `Single Session Per User=true`（如已开则去掉前端 sessionTakeover 逻辑，由 KC backchannel logout 自然驱动）。

##### F9. SessionManager 简化

阶段三完成后 `components/auth/session-manager.tsx` 中：
- `decodeJwtExp / nextRefreshDelayMs / nextRefreshRetryDelayMs` 全删（refresh 由 BFF）。
- Leader 选举（`claimLeadership`、`isLeaderRef`）全删。
- Cross-tab token sync（`broadcastTokenSync`、`STORAGE_KEYS.TOKEN_SYNC` 处理分支）全删。
- Refresh loop（uses `refreshPortalSessionIfPossible`）全删。
- 保留：闲置软锁（前端 idle 提醒）、`/bff/session` 心跳探活、跨 tab 登出广播（用 BroadcastChannel 替代 localStorage）。

预期 LoC 从 ~540 降到 ~150。

##### F10. 测试与 e2e

1. **删除** `session-management.source-contract.test.ts`（源码字符串扫描，反价值）。
2. 新增 `session-manager.spec.ts`：vitest + jsdom + fake timers + axios mock，覆盖：
   - 登录成功后 `/bff/session` 探活返回 authenticated。
   - 探活返回 401 → 跳登录页。
   - 探活返回 503 → 不跳登录页（保留本地 session）。
   - 登出广播 → 跨 tab 跳转。
3. `bff-auth.spec.ts`（后端 SpringBootTest）：
   - 登录成功设 SID cookie。
   - 错误密码不设 cookie。
   - SID 过期返回 401 + 清 cookie。
   - CSRF 缺失返回 403。
   - Backchannel logout 标记 session 失效。
4. e2e（Playwright）：
   - 异地登录顶替：tab A 登录 → tab B 登录 → tab A 在 ≤30s 内被踢出。
   - 后端服务重启：tab A 不被错误踢出。
   - 多 tab 同时活动：refresh 仅由 BFF 内部完成，浏览器无感。

---

## 5. 实施时间线

| 周 | 阶段 | Feature | 关键交付 |
|----|------|---------|----------|
| W1 | 一 | F1, F4, F5 | admin token 字段在前后端全部删除；prod build 不含 TEST_SESSION 旁路；console 不再打印 token |
| W2 | 二 | F2, F3 (前半) | KC realm 调短 TTL + 开 rotation；前端 refresh 节奏匹配；leader 切到 navigator.locks |
| W3 | 二 | F3 (后半) | rotation + leader 联调，e2e 验证多 tab 不会触发 reuse detection |
| W4 | 三 | F6 | BFF 骨架完成，`/bff/login`/`/bff/session` 与 SID cookie 联通，与现有 token 通道双轨 |
| W5 | 三 | F7 | 前端 `withCredentials` + CSRF 完整；feature flag `VITE_BFF_AUTH=true` 灰度 |
| W6 | 三 | F8, F9, F10 | Backchannel logout 接入；SessionManager 简化；测试闭环；旧 token 通道关闭 |

---

## 6. 风险与回滚

### 6.1 阶段一回滚

- F1：恢复后端响应字段 + 前端字段（不会，因为新代码不读，回滚无意义）。
- F4：恢复环境变量读取——但应该是 never。
- F5：恢复 console.log，开发体验回退；**回滚需谨慎**。

### 6.2 阶段二回滚

- F2 风险：rotation 上线后用户被错误踢出（leader split-brain 没修干净）→ 回滚 KC realm 配置（关 rotation），保留短 TTL 即可。
- F3 风险：navigator.locks 在某些企业内置浏览器（IE Edge legacy？）不可用 → fallback 路径必须长期保留。

### 6.3 阶段三回滚

- 双轨期内（W4-W5），BFF cookie 通道与原 Bearer 通道并存，前端 feature flag 控制。
- 任何阶段三问题 → flag off → 回到原 Bearer 通道，30 秒级回滚。
- 旧通道在阶段三末（W6 末）才下线，下线前需保证 BFF 通道生产稳定运行 ≥ 1 周。

### 6.4 不适用场景

- 移动 App / OpenAPI 客户端：cookie 不适用。如果 sprint 期间出现移动端需求，BFF 同时保留 token 通道（API 端点路径区分 `/bff/*` cookie vs `/api/*` token）。
- iframe 嵌入第三方场景：`SameSite=Strict` 会失败，需评估是否退到 `Lax` 并强化 CSRF。
- 跨子域名访问：cookie `Domain` 设置策略需提前与运维约定。

---

## 7. 验收标准

阶段一（W1 末）：
- [ ] 全量 grep `adminAccessToken|adminRefreshToken` 在 webapp 源码 0 命中。
- [ ] 生产 build 产物 grep `VITE_TEST_LONG_SESSION` / `dev-access-` 0 命中。
- [ ] 浏览器 console 在生产模式下不再出现 `Authorization` 字段。
- [ ] `userStore` localStorage 持久化字段对照 schema 严格收敛。

阶段二（W3 末）：
- [ ] KC realm `Access Token Lifespan ≤ 5min`、`Revoke Refresh Token=true`、`Refresh Token Max Reuse=0`。
- [ ] 多 tab 并发 refresh e2e 测试通过，无 token 家族 reuse 误报。
- [ ] 长睡眠唤醒场景（`visibilitychange` visible）触发即时 refresh 验证通过。

阶段三（W6 末）：
- [ ] 浏览器 DevTools 任意时刻看不到 access/refresh token；只见 `SID` cookie 且 HttpOnly=true。
- [ ] `LocalStorage` 中无任何 token 字段。
- [ ] CSRF 缺失的状态修改请求返回 403。
- [ ] KC 主动登出能触发所有 BFF session 失效（backchannel logout 链路通）。
- [ ] `SessionManager` LoC 减少 ≥ 60%。
- [ ] 新增的行为级测试覆盖率 ≥ 80%（针对 session 模块）。

---

## 8. 范围边界

### 8.1 本 sprint 做

- Admin token 前端剥离。
- Portal token 短 TTL + Rotation。
- Leader 选举切 navigator.locks。
- TEST_SESSION 旁路 / Dev fallback 加固。
- 生产 console 凭据移除。
- BFF 骨架（含 SID cookie + CSRF + Spring Security 集成）。
- 前端 axios 切 cookie 模式。
- Keycloak Backchannel Logout 接入。
- SessionManager 简化。
- 行为级 + e2e 测试闭环。

### 8.2 本 sprint 不做（留待后续）

- ROPC → Auth Code + PKCE 迁移（决策 A 折中方案下推迟）。
- PKI 登录改造为 KC custom authenticator（同上）。
- Session store 从 PG 切到 Redis（性能评估后再决策）。
- 移动 App / OpenAPI 客户端的 token 通道方案。
- 多 BFF 跨子域 SSO 协调细则（先满足单 BFF 场景）。
- KC 端 `KEYCLOAK_SESSION` cookie 与 dts-platform SID 的协同优化。

---

## 9. 关联资料

- 评审原始研判：`worklog/v2.2.3/sprint-22-202604/review/session-management-audit.md`（待沉淀）
- 引用 IETF 文档：`draft-ietf-oauth-browser-based-apps`
- Keycloak 官方文档：Securing Applications and Services Guide / Backchannel Logout
- 内部参照：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/session/PortalSessionRegistry.java`
- 内部参照：`source/dts-platform-webapp/src/components/auth/session-manager.tsx`
