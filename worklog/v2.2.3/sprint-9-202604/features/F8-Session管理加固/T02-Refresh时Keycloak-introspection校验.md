# T02: Refresh 时增加 Keycloak token introspection 校验

**优先级**: P0
**状态**: READY
**依赖**: 无

## 问题

Platform 的 `PortalSessionRegistry` 生成的 access token 是 `demo-` 前缀 UUID，与 Keycloak 签发的 JWT 完全无关。
Keycloak 控制台撤销用户或禁用账号后，platform opaque token **仍然有效**（直到 DB session 超时）。
这是安全性缺口：用户应被撤销时无法及时生效。

## 技术设计

### 方案
在 `KeycloakAuthResource.refreshSession()` 中，增加对上游 Keycloak token 的 introspection 校验。

### 改动

| 文件 | 改动 |
|------|------|
| `KeycloakAuthResource.java` refresh 端点 (~L604) | 刷新前调用 Keycloak introspection 端点验证上游 token 有效性 |
| 新增 `KeycloakTokenIntrospector.java` | 封装 `POST /protocol/openid-connect/token/introspect` 调用 |
| `PortalSessionRegistry.java` | `refreshSession()` 增加 `upstreamValid` 参数，false 时撤销 session |
| `application.yml` | 新增 `dts.platform.keycloak.introspection-on-refresh: true`（可关闭） |

### 流程
```
前端 refresh → KeycloakAuthResource.refreshSession()
  → 1. 取出 session 关联的 Keycloak refresh_token
  → 2. 调 Keycloak /token/introspect 验证
  → 3a. 有效 → 正常续期 portal session
  → 3b. 无效 → 撤销 portal session + 返回 401
```

### 特殊处理
- **PKI 登录的 session 没有关联 Keycloak token**（`adminTokens = null`），此时跳过 introspection，仅做 DB session 续期
- 新增配置开关 `introspection-on-refresh`，默认 true，现场可关闭以降低 Keycloak 压力

## 验证

- [ ] 正常 refresh 仍然成功
- [ ] Keycloak 控制台禁用用户后，下一次 refresh 返回 401
- [ ] PKI 登录的 session refresh 不受影响（跳过 introspection）
- [ ] 配置 `introspection-on-refresh: false` 时回退到原行为

## 影响范围

- 仅改动 refresh 路径
- 不改动 login / pki-login / pki-session 端点
