# F8: Session 管理加固（Keycloak 对接完善）

**优先级**: P0
**状态**: READY

## 架构约束

- **不改动 PKI 验签逻辑**（admin 的 PkiChallengeService / PkiVerificationService 不动）
- **admin 和 platform 是两个独立平台**，用户群隔离，不做跨应用 SSO
- admin 超时 10min 是合规基线，不修改
- 默认 session 超时都是 10 分钟（客户合规要求）

## 根因分析

Platform 当前的认证链路存在架构缺陷：
```
前端 → Platform → AdminAuthGateway → Admin → Keycloak → Admin → Platform → 前端
                                                                    ↓
                                                    生成 "demo-UUID" opaque token
                                                    吞掉 Keycloak JWT 的 exp/session_state
```

**认证和数据获取混在一起**，全部走 admin 代理。Keycloak JWT 被 admin + platform 两层包装后，session 信息（exp、session_state、refresh lifecycle）全部丢失。前端拿到 `demo-UUID` 无法判断 token 过期，只能用 120s `loginTs` 硬编码宽限期来猜，120s 后误踢用户。

目标架构：
```
密码登录: 前端 → Platform → Keycloak (直接) → JWT + expiresIn
                Platform → Admin → 菜单/权限数据（认证和数据分离）

PKI 登录:  前端 → Platform → Admin (验签) → username
                Platform → Keycloak (token-exchange, platform client) → JWT + expiresIn

Refresh:   前端 → Platform → Keycloak (直接) → 新 JWT + expiresIn（不经过 admin）
```

## Feature 结构

### F8a: Platform 直接对接 Keycloak（架构层修复）

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T08 | Platform 配置自己的 Keycloak OIDC client | P0 | READY | - |
| T09 | 新增 KeycloakDirectAuthService（password/refresh/token-exchange/logout） | P0 | READY | T08 |
| T10 | Login 端点改为直接调 Keycloak + 调 admin 获取业务数据 | P0 | READY | T09 |
| T11 | Refresh 端点改为直接调 Keycloak refresh_token grant | P0 | READY | T09 |
| T12 | PKI Token Exchange 改用 platform client | P0 | READY | T09 |
| T13 | Logout 直接调 Keycloak revoke/logout | P1 | READY | T09 |
| T14 | 前端 session 管理适配（expiresIn / JWT exp） | P0 | READY | T10, T11 |
| T15 | 移除 AdminAuthGateway 认证代理方法，保留 PKI 验签 + 数据获取 | P1 | READY | T10-T13 |
| T16 | 集成测试 + PKI 回归 | P0 | READY | T14, T15 |

详细设计见 `F8a-Platform直接对接Keycloak.md`。

### F8b: Session 防护层加固（在架构修复基础上）

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | Admin token refresh 失败时降级处理 | P0 | READY | - |
| T02 | Refresh 时 Keycloak introspection 校验 | P1 | READY | F8a |
| T03 | Login/refresh response 透传 expiresIn | P0 | READY | - |
| T04 | Session 刷新乐观锁防竞态 | P1 | READY | - |
| T05 | CORS 配置统一收敛到 Traefik 层 | P2 | READY | - |
| T06 | Cookie SameSite/Secure 显式配置 | P2 | READY | T05 |
| T07 | Token 存储迁移 HttpOnly cookie | P1 | READY | T06 |

注：F8a 完成后，T02 的 introspection 可能不再需要（Platform 直接拿 Keycloak JWT，Keycloak 的 refresh 自带验证）。T03 会被 T10/T14 覆盖。实际需要做的是 T01、T04-T07。

## 实施顺序

```
第一阶段（F8a 架构修复，P0）:
  T08 → T09 → T10 + T11 + T12 并行 → T13 → T14 → T15 → T16

第二阶段（F8b 防护层，P1-P2）:
  T01 → T04 → T05 → T06 → T07
```

先做 F8a 把架构拉正，再做 F8b 在正确架构上加固。

## 完成标准

- [ ] Platform 使用自己的 Keycloak client，认证链路不经过 admin
- [ ] 前端能通过 expiresIn 或 JWT exp 正确判断 token 过期
- [ ] Keycloak session 状态变化能实时传导到 platform 前端
- [ ] admin 不可用时 platform 仍能登录（降级：无菜单数据）
- [ ] PKI 登录功能和行为不变（验签仍走 admin）
- [ ] admin 和 platform 保持各自独立的 session 策略
- [ ] 超时 10min 合规基线不变
