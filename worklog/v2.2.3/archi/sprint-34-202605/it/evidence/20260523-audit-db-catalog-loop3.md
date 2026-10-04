# 2026-05-23 Loop 3 Evidence

## Review 发现

- `audit_classification_miss` 每次 miss 都插入新行，现场高频未知动作会刷爆治理表。
- DB catalog 最小 seed 只覆盖本次现场问题，已有 common catalog 中的大量 platform 动作升级后会全部进入 `platform.unclassified`。
- admin 默认扫描包不包含 `com.yuzhi.dts.common`，直接注入 common `AuditActionCatalog` 需要显式 Bean。

## 修复

- `AuditActionCatalogService.recordMiss` 改为查找同类 miss 后累计 `occurrenceCount` 与 `lastSeenAt`。
- 新增 `AuditActionCatalogBootstrapService`，启动后把 common catalog 中 DB 缺失的 platform 动作导入 `audit_module_catalog` / `audit_action_catalog`。
- 新增 `AuditCommonCatalogConfiguration`，显式注册 `AuditActionCatalog`。

## 验证命令

| 命令 | 结果 | 说明 |
|------|------|------|
| `./mvnw -q -pl dts-admin -Dtest=AuditActionCatalogBootstrapServiceTest,AuditActionCatalogServiceTest,AuditV2ServiceTest test` | PASS | 覆盖缺失 seed、不覆盖 DB、miss 累计、V2 解析 |
| `./mvnw -q -pl dts-admin -DskipTests compile` | PASS | admin 编译检查 |
| `./mvnw -q -pl dts-platform -Dtest=CatalogDomainResourceAuditTest,ReportsResourceWebMvcTest,AuditLoggingFilterTest test` | PASS | 回归 platform 主题域、报表和 fallback 降噪 |
