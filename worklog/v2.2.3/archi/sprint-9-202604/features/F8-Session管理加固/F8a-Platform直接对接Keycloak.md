# F8a: Platform 直接对接 Keycloak（认证与数据分离）

**优先级**: P0
**状态**: READY
**依赖**: 无（独立于 T01-T07，是架构层修复）
**约束**: 不改动 PKI 认证流程的安全逻辑（签名/验签），但 PKI 的 Token Exchange 改为 platform 自己做

## 背景与问题

当前 Platform 登录链路：
```
前端 → Platform → AdminAuthGateway → Admin → Keycloak → Admin → Platform → 前端
                                                                    ↓
                                                    生成 "demo-UUID" opaque token
                                                    吞掉 Keycloak JWT 的 exp/session_state
```

问题：
1. **认证和数据获取混在一起**：Platform 通过 admin 的 `/keycloak/auth/platform/login` 同时拿到 Keycloak JWT 和用户数据，admin 是认证链路的中间人
2. **Keycloak session 信息丢失**：Keycloak JWT 被 admin + platform 两层包装后，exp/session_state/refresh lifecycle 全部被吞掉
3. **级联可用性依赖**：admin 不可用 → platform 也无法登录
4. **opaque token 与 Keycloak session 脱节**：Keycloak 撤销用户后 platform session 仍有效

## 目标架构

```
密码登录：
  前端 → Platform → Keycloak (grant_type=password) → Keycloak JWT
  前端 → Platform → Admin (带 JWT) → 菜单/权限/角色数据

PKI 登录：
  前端 → Platform → Admin (PKI challenge/verify) → 验证通过返回 username
  前端 → Platform → Keycloak (token-exchange, 用 platform 自己的 client) → Keycloak JWT
  前端 → Platform → Admin (带 JWT) → 菜单/权限/角色数据

Token 给前端：
  方案A: 直接给 Keycloak JWT（前端可解析 exp）
  方案B: 仍用 opaque token，但 response 携带 expiresIn（透传 Keycloak expires_in）

Refresh：
  前端 → Platform → Keycloak (grant_type=refresh_token) → 新 JWT + expires_in
  不再经过 admin
```

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T08 | Platform 配置自己的 Keycloak OIDC client | P0 | READY | - |
| T09 | Platform 新增 KeycloakDirectAuthService（密码/refresh/logout） | P0 | READY | T08 |
| T10 | Platform login 端点改为直接调 Keycloak，登录后调 admin 获取业务数据 | P0 | READY | T09 |
| T11 | Platform refresh 端点改为直接调 Keycloak refresh_token grant | P0 | READY | T09 |
| T12 | Platform PKI 登录的 Token Exchange 改为用 platform client | P0 | READY | T09 |
| T13 | Platform logout 端点直接调 Keycloak revoke/logout | P1 | READY | T09 |
| T14 | 前端 session 管理适配（expiresIn / JWT exp） | P0 | READY | T10, T11 |
| T15 | 移除 AdminAuthGateway 中的认证代理方法 | P1 | READY | T10, T11, T12, T13 |
| T16 | 集成测试 + PKI 回归 | P0 | READY | T14, T15 |

## Task 详细设计

### T08: Platform 配置自己的 Keycloak OIDC client

在 Keycloak S10 realm 中，Platform 目前复用 admin 的 client。需要创建独立的 client。

**Keycloak 配置**（可通过 realm export/import 或手动创建）：
- Client ID: `dts-platform`（环境变量 `OAUTH2_PLATFORM_CLIENT_ID`）
- Client Secret: 生成（环境变量 `OAUTH2_PLATFORM_CLIENT_SECRET`）
- Access Type: confidential
- Direct Access Grants Enabled: true（支持 password grant）
- Token Exchange: enabled（支持 PKI Token Exchange）
- Scope: openid, profile, email, offline_access

**application.yml 新增**：
```yaml
dts:
  platform:
    keycloak:
      issuer-uri: ${KEYCLOAK_ISSUER_URI:http://dts-keycloak:8080/realms/S10}
      client-id: ${OAUTH2_PLATFORM_CLIENT_ID:dts-platform}
      client-secret: ${OAUTH2_PLATFORM_CLIENT_SECRET:}
      scope: ${OAUTH2_PLATFORM_SCOPE:openid profile email offline_access}
```

**docker-compose 新增环境变量**：
```yaml
OAUTH2_PLATFORM_CLIENT_ID: dts-platform
OAUTH2_PLATFORM_CLIENT_SECRET: <generated>
```

### T09: Platform 新增 KeycloakDirectAuthService

新建 `com.yuzhi.dts.platform.service.keycloak.KeycloakDirectAuthService`，参考 admin 的 `KeycloakAuthService` 实现：

- `obtainToken(username, password)` → Keycloak `grant_type=password`
- `refreshTokens(refreshToken)` → Keycloak `grant_type=refresh_token`
- `tokenExchange(requestedSubject)` → Keycloak Token Exchange
- `revokeToken(token)` → Keycloak revoke endpoint
- `logout(refreshToken)` → Keycloak logout endpoint

返回 `TokenResponse(accessToken, refreshToken, expiresIn, refreshExpiresIn, sessionState)`。

### T10: Platform login 端点重构

改造 `KeycloakAuthResource.login()`：

```
改前: adminAuthGateway.login(username, password) → 拿到一切
改后:
  1. keycloakDirectAuth.obtainToken(username, password) → Keycloak JWT + expiresIn
  2. 从 JWT 解析 roles/claims
  3. adminDataGateway.getUserMenusAndPermissions(username, jwt) → 菜单/权限数据
  4. sessionRegistry.createSession(username, roles, perms, adminTokens=null, keycloakExpiresIn)
  5. response 返回 accessToken + expiresIn
```

AdminAuthGateway 中的 `login()` 方法不再被调用，改为调新的 `AdminDataGateway.getUserData()` 仅获取业务数据。

### T11: Platform refresh 端点重构

改造 `KeycloakAuthResource.refresh()`：

```
改前: sessionRegistry.refreshSession(refreshToken, adminTokenProvider) → adminAuthGateway.refresh()
改后:
  1. sessionRegistry 查到 session，取出 keycloak refreshToken
  2. keycloakDirectAuth.refreshTokens(keycloakRefreshToken) → 新 JWT + expiresIn
  3. sessionRegistry.refreshSession(...) 更新 session
  4. response 返回新 accessToken + expiresIn
```

不再通过 admin 代理 refresh。

### T12: Platform PKI Token Exchange 改用 platform client

改造 `KeycloakAuthResource.createPkiSession()`：

```
改前: adminTokens = null（PKI 登录不关联 admin token）
改后:
  1. admin 完成 PKI 验签 → 返回 username
  2. keycloakDirectAuth.tokenExchange(username) → 用 platform client 做 Token Exchange → Keycloak JWT
  3. sessionRegistry.createSession(username, roles, ..., keycloakExpiresIn)
  4. response 返回 accessToken + expiresIn
```

PKI 验签逻辑不变（仍由 admin 的 PkiVerificationService 完成），只是 Token Exchange 从 admin client 改为 platform client。

### T13: Platform logout 直接调 Keycloak

改造 `KeycloakAuthResource.logout()`：
- 新增 `keycloakDirectAuth.logout(keycloakRefreshToken)` 直接撤销 Keycloak session
- 不再依赖 `adminAuthGateway.logout()`

### T14: 前端 session 管理适配

改造前端，利用后端返回的 `expiresIn`：

| 文件 | 改动 |
|------|------|
| `userStore.ts` | 新增 `tokenExpiresAt: number` state，login/refresh 成功后基于 `expiresIn` 计算并写入 |
| `login-auth-guard.tsx` | `isTokenExpired()` 改为：先尝试 JWT exp，再用 `tokenExpiresAt`，移除 `isRecentLogin()` 和 `loginTs` |
| `session-manager.tsx` | 心跳刷新延迟基于 `expiresIn` 计算，不再用 `SESSION_TIMEOUT_MS / 2` 猜测 |
| `apiClient.ts` | 移除 `loginTs` 相关的 grace window 逻辑 |

### T15: 移除 AdminAuthGateway 认证代理方法

从 `AdminAuthGateway.java` 中移除：
- `login(username, password)` — 不再代理认证
- `refresh(refreshToken)` — 不再代理刷新
- `logout(refreshToken)` — 不再代理登出

保留：
- `getPkiChallenge()` — PKI challenge 仍由 admin 的 PkiChallengeService 提供
- `pkiLogin(payload)` — PKI 验签仍由 admin 完成（但返回的只是 username，不再返回 token）

可能需要重命名为 `AdminPkiGateway` 或 `AdminDataGateway`，明确职责。

### T16: 集成测试 + PKI 回归

- 密码登录 → 验证前端拿到 expiresIn，能正确判断过期
- 密码登录 → admin 不可用时仍能登录（只是没有菜单数据，可降级）
- PKI 登录 → 验证 challenge/验签/Token Exchange 全链路
- Refresh → 验证直接跟 Keycloak 刷新，不经过 admin
- Keycloak 控制台禁用用户 → 下次 refresh 返回 401
- 并发登录检测/takeover 仍正常
- 超时后被踢 → 重新登录正常

## 完成标准

- [ ] Platform 使用自己的 Keycloak client，不再复用 admin client
- [ ] 密码登录直接调 Keycloak，不再经过 admin 代理
- [ ] PKI 登录：验签仍走 admin，Token Exchange 改用 platform client
- [ ] Refresh 直接调 Keycloak，不再经过 admin
- [ ] 前端拿到 expiresIn，能正确判断 token 过期
- [ ] admin 不可用时 platform 仍能登录（降级：无菜单数据）
- [ ] Keycloak session 状态变化能传导到 platform 前端
- [ ] PKI 登录功能和行为不变
