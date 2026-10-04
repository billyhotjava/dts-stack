# Sprint-34 Loop 6 Evidence: Analytics 大屏审计入库补强

## Scope

- 修复 dts-analytics 大屏管理实际接口 `/api/screens` 未归一到 `analytics.screen` 的问题。
- 修复 analytics 中央审计上报 actor 优先使用邮箱，导致 dts-admin 无法匹配现场登录用户的问题。
- 停止把权限本地 fallback 这类系统迁移保护事件转发为中央人工审计。

## Root Cause

- 大屏管理前端调用 `/bi/api/screens`，经网关转发后 analytics 实际处理 `/api/screens`；`AnalyticsAuditLoggingFilter` 只识别单数 `screen`，复数路径退化为 `ANALYTICS_SCREENS_*` 和 `analytics.screens`。
- 现场 dts-admin 容器日志显示 analytics 审计上报被跳过：`Skipped audit ingest with non-user actor: source=analytics candidates=[xiezm@platform.local]`；数据库中 `admin_keycloak_user` 有 `username=xiezm`，但 email 为空。
- `AnalyticsAuditLoggingFilter`、`ScreenAuditService`、`SemanticAuditService` 均优先使用 `email` 作为 actor，上报到 dts-admin 后无法被解析为登录用户。
- `ScreenPermissionService` 的 local fallback 是内部权限迁移保护，不属于人工操作痕迹，却以 `actor=null` 转发到中央审计，造成 `unknown` 跳过日志。

## Verification

| Command | Result | Note |
|---------|--------|------|
| `./mvnw -q -pl dts-analytics -Dtest=AnalyticsAuditLoggingFilterTest,ScreenAuditServiceTest,SemanticAuditServiceTest,AnalyticsAuditForwarderServiceTest,AdminAuditHttpHeadersFactoryTest,ScreenPermissionServiceTest test` | PASS | 覆盖复数大屏路由、平台用户名 actor、fallback 非人工审计降噪 |
| `./mvnw -q -pl dts-admin -Dtest=AuditV2ServiceTest,AuditIngestResourceTest,AuditEntryViewMapperTest,AuditActionCatalogServiceTest test` | PASS | 覆盖 admin ingest/catalog/view 映射 |
| `./mvnw -q -pl dts-analytics -DskipTests compile` | PASS | analytics 编译通过 |
| `./mvnw -q -pl dts-analytics test` | FAIL | 失败为既有 `MbqlToSqlServiceTest` 与 `QueryPermissionServiceTest` 断言漂移，和本轮审计改动无关 |
| `git diff --check` | PASS | 无空白错误 |

## Runtime Evidence

- `docker compose -f docker-compose-app.yml logs --tail=300 dts-admin` 中存在 `source=analytics candidates=[xiezm@platform.local]` 与 `candidates=[unknown]` 跳过记录。
- `docker compose -f docker-compose-app.yml exec -T dts-pg ... admin_keycloak_user ...` 确认现场用户 `xiezm`、`test2` 的 email 为空，因此 analytics 必须上报平台登录名。
- `docker compose -f docker-compose-app.yml exec -T dts-pg ... audit_entry where source_system='analytics'` 当前为 0 行，符合被 admin ingest 跳过的现场表现。

## Notes

- 当前运行中的 `dts-analytics` 容器创建于本轮源码修改之前；现场验证需要重建/重启 analytics 服务后再做 smoke。
- local fallback 仍保留 analytics 本地 WARN 日志，方便排查权限迁移问题，但不再进入中央人工审计。
