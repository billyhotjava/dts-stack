# T01: Platform 关联 admin token 的生命周期管理

**优先级**: P0
**状态**: READY
**依赖**: 无

## 背景

admin 和 platform 两端默认超时都是 **10 分钟**（客户合规要求）。
platform 当前被手动调到 30min，属于临时调整。两端是独立平台，用户群隔离。

## 问题

Platform 通过 `AdminAuthGateway` 代理 admin 的 PKI 认证接口。密码登录流程中，platform 后端也会调 admin 获取 Keycloak token。
这些场景下 platform 的 `portal_sessions` 里会关联存储一份 admin token（`KeycloakAuthResource.java:618` refresh 时联动）。

当 platform session 超时被临时调大（如 30min）而 admin 保持 10min 时，关联的 admin token 可能已过期。
如果此时触发需要 admin token 的操作（如 PKI 登录代理），admin 端返回 401。

即使两端都是 10min，在极端场景下（两端刷新时间点不同步）仍可能出现 admin token 先过期的边界条件。

## 技术设计

### 方案
platform refresh 端点在刷新 portal session 时，如果存在关联的 admin token，同步调 admin 的 refresh 接口续期。
如果 admin refresh 失败（admin session 已被撤销），仅清除 platform 侧关联的 admin token，**不影响** platform session 本身。

### 改动

| 文件 | 改动 |
|------|------|
| `KeycloakAuthResource.java` refresh 端点 (~L604-696) | admin token 续期失败时降级处理：清除关联 token + 日志告警，不中断 portal session |
| `AdminAuthGateway.java` | 新增 `refreshAdminToken(refreshToken)` 方法，调 admin 的 `/keycloak/auth/refresh` |

### 不做的事
- **不修改任何一端的超时配置**（10min 是合规基线）
- **不改变两个平台各自独立的 session 管理**

## 验证

- [ ] platform refresh 正常续期（无关联 admin token 时）
- [ ] platform refresh 正常续期（有关联 admin token 且 admin session 有效时，同步续期）
- [ ] platform refresh 降级续期（有关联 admin token 但 admin session 已过期时，清除关联 + portal session 继续有效）
- [ ] admin 独立超时不受影响
- [ ] 两端都保持 10min 超时时功能正常

## 影响范围

- 仅改动 platform refresh 路径中 admin token 的处理逻辑
- 不改动任何认证逻辑
- 不改动任何一端的超时配置
