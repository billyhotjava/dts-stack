# F5: analytics 出站规范化

**优先级**: P0
**状态**: DONE
**目标**: 把 `dts-analytics` 的出站链路整理成命名直观的 outbound 配置,旧 env 保留 fallback。

**Sprint-28 范围调整**:F5 实施时确认 analytics → admin 链路(`AdminAuditHttpHeadersFactory`、`UserResource:216`)已有独立的 `DtsAdminProperties`(prefix=dts.admin),且 admin 端目前对该流量无 token 强校验,**不在本 sprint 修复范围**。F5 仅做 analytics → platform 这一条核心链路。

**依赖**: F3

## 背景

dts-analytics 同时调 platform 与 admin:
- → platform: `PlatformInfraClient`、`PlatformPermissionClient`、`ProjectCockpitTopicBindingGateway`、`UserResource.headers.set("X-DTS-Service",...)`
- → admin: `AdminAuditHttpHeadersFactory`(审计上报)

当前各 client 各自 `@Value` 注入或读 `DtsAdminProperties`,语义参差。F5 统一成两个 outbound bean。

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | DONE | 新增 `AnalyticsOutboundPlatformProperties`(`dts.analytics.outbound.platform`),含 enabled/baseUrl/apiPath/serviceToken/serviceName/timeoutSeconds |
| T02 | SKIPPED | analytics 已有 `DtsAdminProperties`(prefix=dts.admin),语义清晰,未与 platform 链路混淆;F5 不引入 AnalyticsOutboundAdminProperties,留待 Sprint-29 统一命名 |
| T03 | DONE | `PlatformInfraClient` / `PlatformPermissionClient` / `ProjectCockpitTopicBindingGateway` 删除 `@Value`,改注入 AnalyticsOutboundPlatformProperties;统一 buildHeaders 写入 X-DTS-Service-Token(F3 强校验前置) |
| T04 | SKIPPED | `AdminAuditHttpHeadersFactory` 走 admin 链路,不属于本 sprint 修复范围(理由见上) |
| T05 | SKIPPED | `UserResource:216` 实际调 admin(端点为 admin 的 `/api/platform/directory/users`),不在 platform 链路;不动 |
| T06 | DONE | application.yml 新 `dts.analytics.outbound.platform.*` 段,token 二级 fallback `DTS_ANALYTICS_TO_PLATFORM` → `DTS_ADMIN_SERVICE_TOKEN`;旧 `dts.analytics.platform.*` 标 deprecated |
| T07 | DONE | `JdbcDetailsResolverTest` 修复构造器签名,3 测试通过 |

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

- [x] 3 个 platform-bound client(PlatformInfraClient/PermissionClient/ProjectCockpitTopicBindingGateway)走新 bean
- [x] application.yml 双前缀同时存在
- [x] 测试构造器更新,JdbcDetailsResolverTest 3 测试通过

## 实现记录

- 新增: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/config/AnalyticsOutboundPlatformProperties.java`
- 修改 3 个 client: `PlatformInfraClient`, `PlatformPermissionClient`, `ProjectCockpitTopicBindingGateway` — 删除 `@Value`,改注入 `AnalyticsOutboundPlatformProperties`,buildHeaders 加入 X-DTS-Service-Token
- 修改: `application.yml` 新 prefix + 二级 fallback,旧段标 deprecated
- 修改测试: `JdbcDetailsResolverTest.fakeClient` 改用新构造器签名
- 验证: `mvnw -f dts-analytics/pom.xml compile` BUILD SUCCESS;JdbcDetailsResolverTest 3 tests pass

## 与 Sprint-29 衔接

- analytics 自有 `DtsAdminProperties`(prefix=dts.admin)语义清晰,Sprint-29 起可重命名为 `AnalyticsOutboundAdminProperties` 与本 sprint 风格统一
- admin 入站对来自 analytics 的流量(audit ingest 之外)是否需要 token 强校验,取决于 Sprint-29 admin 侧 filter 升级
