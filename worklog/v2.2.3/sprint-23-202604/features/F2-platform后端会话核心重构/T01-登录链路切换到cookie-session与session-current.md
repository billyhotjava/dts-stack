# T01: 登录链路切换到 cookie session 与 `session/current`

**优先级**: P0
**状态**: READY
**依赖**: F1/T01

## 目标
让登录态建立与查询完全依赖服务端 cookie session，而不是返回给前端的 portal access/refresh token。

## 技术设计
- 改造 `KeycloakAuthResource` 登录成功后的响应，改为设置 `portal_session` 和 `browser_id` cookie
- 新增或改造 `GET /api/session/current`，返回当前登录态、显示名、到期时间、reason code 能力
- 保留 Keycloak 作为身份认证入口，但登录完成后立即落到 platform session
- 统一 cookie 的 domain、path、secure、sameSite 策略，并兼容 `/bi` 路由
- 为未登录和已失效状态返回可区分的响应体，便于前端状态机消费

## 影响范围
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/KeycloakAuthResource.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/SecurityConfiguration.java`
- `source/dts-platform/src/main/resources/config/application*.yml`
- `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/KeycloakAuthResourceTest.java`

## 验证
- [ ] 登录后浏览器仅收到 cookie，不再收到 portal access/refresh token
- [ ] `GET /api/session/current` 能正确区分 authenticated 和 unauthenticated
- [ ] 大屏和 platform 页面共享同一浏览器 session

## 完成标准
- [ ] 前端可只依赖 `session/current` 建立初始化状态
- [ ] 登录链路不再要求浏览器读取 portal session token
