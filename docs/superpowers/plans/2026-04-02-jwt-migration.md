# 回归纯 Keycloak JWT 认证 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 删除 Portal Session 层（DB session + opaque token + inactivity filter），回归 Keycloak JWT 标准认证。每次请求用 JWT 公钥本地验证，不查 DB。

**Architecture:** Login 时从 Keycloak 获取 JWT access token，存入 httpOnly cookie（复用现有 cookie 机制）。后端用 Keycloak 公钥验证 JWT，不再查 `portal_sessions` 表。Refresh 通过 Keycloak refresh token 完成。前端不变。

**Tech Stack:** Spring Boot 3.4.5, Spring Security OAuth2 Resource Server (JWT), Keycloak OIDC

---

## 改动范围

### 删除（3 个文件）
- `PortalOpaqueTokenIntrospector.java` — 用 Spring JWT decoder 替代
- `PortalSessionActivityService.java` — JWT 无滑动续期，靠 exp claim
- `PortalSessionInactivityFilter.java` — JWT 无 touch 机制

### 修改（4 个文件）
- `SecurityConfiguration.java` — `opaqueToken()` → `jwt()`
- `KeycloakAuthResource.java` — login 返回 Keycloak JWT（不创建 portal session），refresh 调 Keycloak
- `PortalSessionResource.java` — `/api/session/current` 从 JWT claims 读用户信息
- `ForwardAuthResource.java` — 从 JWT claims 提取 downstream headers

### 保持（1 个文件调整）
- `PortalSessionBearerTokenResolver.java` — 改为从 cookie 读 JWT（而非 opaque token）
- `PortalSessionCookieService.java` — 保留 cookie 机制，存 JWT 而非 `demo-<uuid>`

### 不变（所有前端）
- 前端不需要改动 — `/api/session/current` 返回相同的 response shape

---

## Task 1: SecurityConfiguration 切换到 JWT decoder

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/SecurityConfiguration.java`

- [ ] **Step 1: 替换 opaqueToken 为 jwt**

将：
```java
.oauth2ResourceServer(oauth2 ->
    oauth2
        .bearerTokenResolver(bearerTokenResolver)
        .opaqueToken(opaque -> opaque.introspector(opaqueTokenIntrospector))
)
```

改为：
```java
.oauth2ResourceServer(oauth2 ->
    oauth2
        .bearerTokenResolver(bearerTokenResolver)
        .jwt(Customizer.withDefaults())
)
```

Spring Boot 自动配置会从 `spring.security.oauth2.resourceserver.jwt.issuer-uri` 或 `jwk-set-uri` 获取 Keycloak 公钥。

- [ ] **Step 2: 删除 PortalOpaqueTokenIntrospector 的注入**

从构造函数参数和 filterChain 方法中删除 `PortalOpaqueTokenIntrospector`。

- [ ] **Step 3: 删除 PortalSessionInactivityFilter 的注入和注册**

删除 `sessionInactivityFilter` 参数和 `http.addFilterAfter(sessionInactivityFilter, ...)` 行。

- [ ] **Step 4: 确认 application.yml 有 Keycloak JWT 配置**

确保 `application.yml` 有：
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:https://sso.yuzhicloud.com/realms/S10}
```

- [ ] **Step 5: 编译验证**

---

## Task 2: 删除 Portal Session 鉴权组件

**Files:**
- Delete: `PortalOpaqueTokenIntrospector.java`
- Delete: `PortalSessionActivityService.java`
- Delete: `PortalSessionInactivityFilter.java`

- [ ] **Step 1: 删除三个文件**
- [ ] **Step 2: 修复所有编译错误**（其他文件对这三个类的引用）
- [ ] **Step 3: 编译验证**

---

## Task 3: KeycloakAuthResource — login 返回 Keycloak JWT

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/KeycloakAuthResource.java`

- [ ] **Step 1: login 端点修改**

当前 login 流程：
1. `adminAuthGateway.login()` → 获取 Keycloak tokens
2. `sessionRegistry.createSession()` → 创建 portal session（`demo-<uuid>` token）
3. 返回 portal session token 给前端

改为：
1. `adminAuthGateway.login()` → 获取 Keycloak JWT access token + refresh token
2. **跳过** `sessionRegistry.createSession()`
3. 将 Keycloak JWT access token 写入 `portal_session` cookie
4. 返回用户信息 + browserId（不返回 accessToken/refreshToken 到 response body）

- [ ] **Step 2: refresh 端点修改**

当前 refresh：调 `sessionRegistry.refreshSession()`
改为：调 Keycloak refresh token endpoint，获取新 JWT，写入 cookie

- [ ] **Step 3: logout 端点修改**

当前 logout：调 `sessionRegistry.invalidateByAccessToken()`
改为：调 Keycloak logout endpoint + 清除 cookie

- [ ] **Step 4: 编译验证**

---

## Task 4: PortalSessionResource — 从 JWT claims 读用户信息

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/PortalSessionResource.java`

- [ ] **Step 1: `/api/session/current` 从 SecurityContext 的 JWT 读 claims**

当前：从 `PortalSessionRegistry` 查 DB
改为：从 `SecurityContextHolder.getContext().getAuthentication()` 读 JWT claims

返回 shape 不变：
```json
{
  "authenticated": true,
  "username": "opadmin",
  "roles": ["ROLE_OP_ADMIN"],
  "permissions": [...],
  "expiresAt": "2026-04-02T15:00:00Z"
}
```

- [ ] **Step 2: 编译验证**

---

## Task 5: BearerTokenResolver — 从 cookie 读 JWT

**Files:**
- Modify: `PortalSessionBearerTokenResolver.java`

- [ ] **Step 1: resolve() 从 cookie 读 JWT（而非 opaque token）**

逻辑不变（cookie 优先 → header fallback），但 cookie 里存的是 JWT 而非 `demo-<uuid>`。
`CookieService.resolvePortalSessionToken()` 不需要改 — 它只是读 cookie value，不关心内容格式。

- [ ] **Step 2: 确认 bypass 列表完整**

确保所有 permitAll 端点都被 `shouldBypassAuthentication()` 跳过。

---

## Task 6: 验证端到端

- [ ] **Step 1: 编译 `mvn compile`**
- [ ] **Step 2: 跑后端单测 `mvn test`**
- [ ] **Step 3: 前端编译 `npx tsc --noEmit`**
- [ ] **Step 4: 手动测试：登录 → 工作台 → 菜单加载 → 大屏 → 下钻**
- [ ] **Step 5: 手动测试：多 tab → 30 分钟后不自动登出**
- [ ] **Step 6: 手动测试：退出 → 重新登录**
