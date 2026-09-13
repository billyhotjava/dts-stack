# T07: Token 存储迁移 HttpOnly cookie（BFF 模式）

**优先级**: P1
**状态**: READY
**依赖**: T06

## 问题

两个 webapp 都把 accessToken/refreshToken 存在 localStorage。
任何 XSS 漏洞（第三方依赖、UGC 内容注入）都能直接 `localStorage.getItem` 窃取 token。

## 技术设计

### 方案：后端 Set-Cookie + 前端去 token 管理

#### 后端改动

| 文件 | 改动 |
|------|------|
| `KeycloakAuthResource.java` login/pki-session/refresh | Response 增加 `Set-Cookie: dts_access=<token>; HttpOnly; Secure; SameSite=Lax; Path=/api` |
| `SecurityConfiguration.java` | 新增 CookieBearerTokenResolver：从 cookie 中提取 token 用于认证 |
| admin 侧同上 | 对称改动 |

#### 前端改动

| 文件 | 改动 |
|------|------|
| `apiClient.ts` 请求拦截器 | 移除手动附加 `Authorization` header（cookie 自动携带） |
| `userStore.ts` | 移除 accessToken/refreshToken 持久化；仅保留 user profile |
| `session-manager.tsx` | 心跳改为调 `/api/keycloak/auth/session-check`（新端点），不再本地读 token |
| `login-auth-guard.tsx` | 改为调后端 `/api/account` 判断是否已登录，不再本地检查 token |

#### 新增后端端点

| 端点 | 功能 |
|------|------|
| `GET /api/keycloak/auth/session-check` | 返回 session 状态 + 剩余 TTL（前端心跳用） |

### 兼容过渡
- 同时支持 cookie 和 Authorization header 两种方式
- 前端检测到 cookie 模式可用后，停止在 header 中发 token
- 老版本前端不受影响（仍可用 header 方式）

## 验证

- [ ] 登录后 token 不出现在 localStorage / JS 可访问的存储中
- [ ] API 请求通过 cookie 自动携带认证信息
- [ ] XSS 注入脚本无法读取 token
- [ ] PKI 登录后 cookie 同样正确设置
- [ ] 跨标签页 session 同步仍然正常（基于 cookie 天然共享）

## 影响范围

- 前后端联动改动，工作量较大
- 不改动 PKI 认证链路
- 向后兼容：header 方式保留
