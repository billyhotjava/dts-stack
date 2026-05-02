# F5: analytics 出站规范化

**优先级**: P0
**状态**: READY
**目标**: 把 `dts-analytics` 的两条出站链路(→ platform、→ admin)整理成命名直观的 outbound 配置,旧 env 保留 fallback。

**依赖**: F3

## 背景

dts-analytics 同时调 platform 与 admin:
- → platform: `PlatformInfraClient`、`PlatformPermissionClient`、`ProjectCockpitTopicBindingGateway`、`UserResource.headers.set("X-DTS-Service",...)`
- → admin: `AdminAuditHttpHeadersFactory`(审计上报)

当前各 client 各自 `@Value` 注入或读 `DtsAdminProperties`,语义参差。F5 统一成两个 outbound bean。

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | READY | 新增 `AnalyticsOutboundPlatformProperties`(`dts.analytics.outbound.platform`) |
| T02 | READY | 新增 `AnalyticsOutboundAdminProperties`(`dts.analytics.outbound.admin`) |
| T03 | READY | 把 `PlatformInfraClient`、`PlatformPermissionClient`、`ProjectCockpitTopicBindingGateway` 改注入新 platform bean |
| T04 | READY | 把 `AdminAuditHttpHeadersFactory` 改注入新 admin bean |
| T05 | READY | `UserResource` 行内 header.set 改为统一从 admin bean 取 service-name |
| T06 | READY | application.yml 加新 prefix,token 三级 fallback(`DTS_ANALYTICS_TO_PLATFORM` → 旧 → `DTS_ADMIN_SERVICE_TOKEN`) |
| T07 | READY | 单测覆盖三个 client 的 token 注入与 fallback |

## 影响范围

- 新增: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/config/AnalyticsOutboundPlatformProperties.java`
- 新增: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/config/AnalyticsOutboundAdminProperties.java`
- 修改: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/PlatformInfraClient.java`
- 修改: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/PlatformPermissionClient.java`
- 修改: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/projectcockpit/ProjectCockpitTopicBindingGateway.java`
- 修改: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/audit/AdminAuditHttpHeadersFactory.java`
- 修改: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/UserResource.java:216`
- 修改: `source/dts-analytics/src/main/resources/config/application.yml`(`dts.analytics.outbound.*` 段)
- 旧 `DtsAdminProperties` / `DtsAnalyticsProperties` 字段标 `@Deprecated`(若存在重叠)

## 验证

- [ ] analytics 启动后两个新 bean 装配正常
- [ ] 调用 platform 三个 client 都能正常带新 token
- [ ] 审计上报到 admin 的 header 来自 admin bean
- [ ] 三级 env fallback 测试通过

## 完成标准

- [ ] 4 个 client + 1 个 resource 全部走新 bean
- [ ] application.yml 双前缀同时存在
- [ ] 测试覆盖 token 注入路径
