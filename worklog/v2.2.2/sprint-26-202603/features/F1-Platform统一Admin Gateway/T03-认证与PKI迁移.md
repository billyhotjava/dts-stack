# T03: 认证与PKI迁移

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

将登录、刷新、登出、PKI challenge、PKI login 统一迁入 auth gateway，并补齐平台代理接口。

## 技术设计

- 建立 `auth gateway`
- 保持平台现有登录语义
- 前端 `adminService` 与 `pkiService` 改为只调平台 API

## 影响范围

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/**`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/KeycloakAuthResource.java`
- `source/dts-platform-webapp/src/api/services/adminService.ts`
- `source/dts-platform-webapp/src/api/services/pkiService.ts`

## 验证

- [ ] `cd source/dts-platform && ./mvnw -q -Dtest=KeycloakAuthResourceTest,AdminAuthGatewayTest test`

## 完成标准

- [ ] 前端不再直接以 `adminApiBaseUrl` 发起 auth / pki 请求
- [ ] 平台认证行为与现有页面兼容
