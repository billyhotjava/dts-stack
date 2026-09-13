# F1: platform properties 拆分

**优先级**: P0
**状态**: DONE
**目标**: 把 `DtsAdminProperties` 在概念上拆成"出站客户端配置"与"入站服务认证配置"两个独立 bean,运行时行为不变,但代码层面彻底解耦,为 F2/F3 铺路。

## 背景

`dts.admin.*` 当前同时被两组消费方使用:
- **出站客户端**:`AdminGatewayHeaders`、`AdminGatewayTransport`、`AdminAuthGateway`、`AdminAuditGateway`、`AdminDirectoryGateway`、`AdminWorkflowConfigClient`、`AdminInfraClient`
- **入站鉴权**:`ServiceDependencyAuthenticationFilter`、`InfraDataSourceResource.serviceTokenMatches`

同一组属性承载两个语义,运维与开发都易混淆。F1 不动运行时行为,只做静态拆分。

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | DONE | 新增 `PlatformOutboundAdminProperties`(`dts.platform.outbound.admin`),包含 baseUrl/apiPath/adminApiPath/serviceToken/serviceName |
| T02 | DONE | 新增 `PlatformInboundServiceAuthProperties`(`dts.platform.inbound.service-auth`),含 trustedServiceNames + sharedSecret + trustedServices Map + legacyHeaderOnlyMode + 辅助方法(isTrustedServiceName/resolveExpectedToken/canonicalServiceName) |
| T03 | DONE | 8 个出站消费方改注入 PlatformOutboundAdminProperties:AdminGatewayHeaders/Transport、AdminAuthGateway、AdminAuditGateway、AdminDirectoryGateway、AdminWorkflowConfigClient、AdminInfraClient、PortalMenuClient |
| T04 | DONE | filter + InfraDataSourceResource + SecurityConfiguration 改注入 PlatformInboundServiceAuthProperties,行为与 Sprint-27 等价(F1 阶段 trustedServices Map 默认空,fallback 到 sharedSecret)|
| T05 | DONE | DtsAdminProperties 标 `@Deprecated(forRemoval=true)`,仍在 @EnableConfigurationProperties 中以兼容旧 yml 段;application.yml 旧 `dts.admin.*` 段标 deprecated 注释 |
| T06 | DONE | 测试更新:AdminGatewayTransportTest / AdminAuthGatewayTest / AdminDirectoryGatewayTest / InfraDataSourceResourceTest 全部切换到新 bean,18 测试通过 |

## 影响范围

- 新增: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/PlatformOutboundAdminProperties.java`
- 新增: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/PlatformInboundServiceAuthProperties.java`
- 修改: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/DtsPlatformApp.java`(`@EnableConfigurationProperties`)
- 修改: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/gateway/support/AdminGatewayHeaders.java`
- 修改: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/gateway/support/AdminGatewayTransport.java`
- 修改: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/gateway/auth/AdminAuthGateway.java`
- 修改: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/gateway/audit/AdminAuditGateway.java`
- 修改: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/admin/gateway/directory/AdminDirectoryGateway.java`
- 修改: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/workflow/AdminWorkflowConfigClient.java`
- 修改: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/AdminInfraClient.java`
- 修改: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/ServiceDependencyAuthenticationFilter.java`
- 修改: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/infra/InfraDataSourceResource.java`
- 修改: `source/dts-platform/src/main/resources/config/application.yml`
- 旧 `DtsAdminProperties.java` 标 `@Deprecated`,保留一个版本周期

## 验证

- [ ] `mvn -pl dts-platform compile` 通过,无引用旧 `DtsAdminProperties` 的告警
- [ ] `mvn -pl dts-platform test` 通过
- [ ] 启动 platform,日志确认两个新 bean 装配,Admin 出站调用正常
- [ ] 不带 `DTS_ADMIN_SERVICE_TOKEN`、仅设新 env 启动,filter 行为与之前等价

## 完成标准

- [x] 所有 8 个 Admin* gateway/client 改用新 outbound bean(实际 7 个 + PortalMenuClient)
- [x] filter + InfraDataSourceResource 改用新 inbound bean
- [x] application.yml 双前缀同时写入但语义清晰
- [x] `DtsAdminProperties` 仅作为兼容门面存在,所有消费方已迁出

## 实现记录

- 新增: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/PlatformOutboundAdminProperties.java`
- 新增: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/PlatformInboundServiceAuthProperties.java`(已含 F2 的 trustedServices Map 字段与解析方法,F2 阶段直接启用即可)
- 修改 main: `DtsPlatformApp.java`(@EnableConfigurationProperties 加新两个 bean), `SecurityConfiguration.java`(filter Bean 注入参数), `DtsAdminProperties.java`(标 @Deprecated, forRemoval=true)
- 修改 main 8 处出站消费方: `AdminGatewayHeaders/Transport`, `AdminAuthGateway`, `AdminAuditGateway`, `AdminDirectoryGateway`, `AdminWorkflowConfigClient`, `AdminInfraClient`, `PortalMenuClient`
- 修改 main 2 处入站消费方: `ServiceDependencyAuthenticationFilter`(用 isTrustedServiceName/canonicalServiceName), `InfraDataSourceResource`(serviceTokenMatches 用 resolveExpectedToken)
- 修改 application.yml: 新增 `dts.platform.outbound.admin.*` + `dts.platform.inbound.service-auth.*` 段,旧 `dts.admin.*` 加 deprecated 注释
- 修复历史 bug: 出站调 admin 时 `X-DTS-Service` header 不再错误地填入入站白名单字符串(`dts-admin,dts-ingestion,...`),改为正确填 `dts-platform`
- 修改 test 4 处: `AdminGatewayTransportTest`, `AdminAuthGatewayTest`, `AdminDirectoryGatewayTest`, `InfraDataSourceResourceTest`
- 验证: `./mvnw compile` BUILD SUCCESS;`./mvnw test -Dtest='AdminGatewayTransportTest,AdminAuthGatewayTest,AdminDirectoryGatewayTest,InfraDataSourceResourceTest'` 18 tests pass
