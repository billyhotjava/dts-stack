# Sprint-34 Loop 4 Evidence: dts-analytics 审计重构

**日期**: 2026-05-23
**范围**: dts-analytics fallback/forwarder + dts-admin ingest/catalog/view mapping
**状态**: PASS

## 变更摘要

- dts-analytics fallback 审计从中文 action 推导为稳定 actionCode，并由 forwarder 写入 `buttonCode`/`operationCode`。
- dts-admin ingest 在缺少 `buttonCode` 时从 `operationCode`/`action` 补齐，避免 analytics 事件退成 `PLATFORM_GENERIC_EVENT`。
- dts-admin DB catalog 增加 analytics 模块与首批人工操作目录。
- `AuditV2Service` 将 analytics 未注册动作落入 `analytics.unclassified` 并记录 miss。
- 审计中心将 analytics 显示为 `BI分析` / `分析端审计`。

## 自动化验证

| 命令 | 结果 |
|------|------|
| `./mvnw -q -pl dts-analytics -Dtest=AnalyticsAuditLoggingFilterTest,AnalyticsAuditForwarderServiceTest test` | PASS |
| `./mvnw -q -pl dts-admin -Dtest=AuditV2ServiceTest,AuditIngestResourceTest,AuditEntryViewMapperTest test` | PASS |
| `xmllint --noout source/dts-admin/src/main/resources/config/liquibase/changelog/20260523-01_audit_action_catalog.xml source/dts-admin/src/main/resources/config/liquibase/master.xml` | PASS |
| `./mvnw -q -pl dts-admin -DskipTests compile` | PASS |
| `./mvnw -q -pl dts-analytics -DskipTests compile` | PASS |

## 注意事项

- 本地测试前清理了 root-owned 的 `dts-admin/target/generated-*/*annotations` 生成目录；这是构建产物权限问题，不属于业务代码变更。
- 现场部署后仍需 smoke：大屏查看/修改/导出/授权、语义查询/VDS 操作应显示为分析端审计，未知 analytics actionCode 应进入未分类治理。
