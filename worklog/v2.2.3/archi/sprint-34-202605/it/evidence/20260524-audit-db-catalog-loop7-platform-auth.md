# 2026-05-24 Loop 7 Evidence: Platform Auth Audit Classification

## Root Cause

- 现场 DB 中业务端登录/登出记录已落库为 `source_system=platform`，但 `button_code`/`operation_code` 是旧字符串 `AUTH LOGIN` / `AUTH LOGOUT`。
- `dts-platform` 的 `AuditForwarderService` 只在 metadata 已带 `actionCode`/`buttonCode` 时转发稳定编码；`KeycloakAuthResource` 仍发送旧 auth action，导致 dts-admin DB catalog 无法命中。
- dts-admin 侧没有 platform auth 目录项，未知 platform 动作按设计进入 `platform.unclassified`，因此 UI 显示“未分类业务操作”。

## Fix

- `dts-platform` 转发层兼容旧 auth action：
  - `AUTH LOGIN` -> `ADMIN_AUTH_PLATFORM_LOGIN`
  - `AUTH LOGOUT` -> `ADMIN_AUTH_PLATFORM_LOGOUT`
- dts-admin 新增独立 Liquibase changeSet `20260524-01-audit-action-catalog-platform-auth-seed`：
  - seed `platform.auth` / `业务端认证`
  - seed 业务端登录、业务端登出 action catalog
  - 窄范围纠偏历史错误记录：仅更新 `source_system=platform`、`module_key=platform.unclassified`、旧 auth action 的登录/登出记录，保留原始 summary。

## Red / Green

- RED: `./mvnw -q -pl dts-platform -Dtest=AuditForwarderServiceTest test`
  - 失败点：转发体缺少 `buttonCode=ADMIN_AUTH_PLATFORM_LOGIN` / `ADMIN_AUTH_PLATFORM_LOGOUT`。
- GREEN: `./mvnw -q -pl dts-platform -Dtest=AuditForwarderServiceTest,AuditServiceTest test`
  - PASS。
- GREEN: `./mvnw -q -pl dts-admin -Dtest=AuditActionCatalogLiquibaseSeedTest,AuditIngestResourceTest,AuditV2ServiceTest test`
  - PASS。
- GREEN: `xmllint --noout source/dts-admin/src/main/resources/config/liquibase/changelog/20260523-01_audit_action_catalog.xml source/dts-admin/src/main/resources/config/liquibase/master.xml`
  - PASS。
- GREEN: current Postgres transaction dry-run of the new seed/backfill SQL.
  - PASS: `INSERT 0 1`, `INSERT 0 2`, `UPDATE 8`, `UPDATE 2`, `ROLLBACK`。

## Notes

- 初次跑 Maven 时被 root-owned `target/generated-sources` / `target/generated-test-sources` 阻塞，已仅修正生成目录 owner 为 `billy:billy` 后重跑。
- 该修复不扩大人工审计范围，只给已经存在的业务端登录/登出人工操作补稳定目录编码。
