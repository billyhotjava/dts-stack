# T06: Cookie SameSite/Secure 显式配置

**优先级**: P2
**状态**: READY
**依赖**: T05

## 问题

admin 端 `application.yml:228-230` 启用了 HttpOnly，但 platform 端没有。
两端都没有显式配置 `SameSite` 属性。

浏览器默认策略 `SameSite=Lax` 会阻止跨站 POST 请求携带 cookie。
在跨域 SSO 场景（admin ↔ platform ↔ Keycloak 三方交互）下，可能导致 cookie 丢失。

## 技术设计

### 改动

| 文件 | 改动 |
|------|------|
| `dts-platform/application.yml` | 新增 `server.servlet.session.cookie.http-only: true` |
| `dts-platform/application.yml` | 新增 `server.servlet.session.cookie.same-site: Lax`（或 `None` + `secure: true` 如果跨域） |
| `dts-admin/application.yml` | 确认 `http-only: true`，补齐 `same-site` 配置 |

### 决策点
- 如果 admin 和 platform 同域（同源或子域），用 `SameSite=Lax`
- 如果跨域部署，必须用 `SameSite=None` + `Secure=true`（要求 HTTPS）

## 验证

- [ ] Response Set-Cookie header 包含 SameSite 和 Secure 属性
- [ ] 跨域 SSO 场景下 cookie 正常传递
- [ ] PKI 登录后的 cookie 同样携带安全属性
