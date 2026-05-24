# Sprint-34 IT Evidence

**状态**: LOOP-6-PASS（代码与 focused 验证完成；analytics 容器待重建后补现场 smoke）

## 自动化验证

| 模块 | 命令 | 状态 | 证据 |
|------|------|------|------|
| dts-admin | `./mvnw -q -pl dts-admin -Dtest=AuditV2ServiceTest test` | PASS | `it/evidence/20260523-audit-db-catalog-loop1.md` |
| dts-admin | `xmllint --noout ...20260523-01_audit_action_catalog.xml ...master.xml` | PASS | `it/evidence/20260523-audit-db-catalog-loop1.md` |
| dts-platform | `./mvnw -q -pl dts-platform -Dtest=CatalogDomainResourceAuditTest,ReportsResourceWebMvcTest test` | PASS | `it/evidence/20260523-audit-db-catalog-loop1.md` |
| dts-platform | `./mvnw -q -pl dts-platform -DskipTests compile` | PASS | `it/evidence/20260523-audit-db-catalog-loop1.md` |
| dts-admin | `./mvnw -q -pl dts-admin -Dtest=AuditV2ServiceTest test` | PASS | `it/evidence/20260523-audit-db-catalog-loop2.md` |
| dts-admin | `./mvnw -q -pl dts-admin -DskipTests compile` | PASS | `it/evidence/20260523-audit-db-catalog-loop2.md` |
| dts-platform | `./mvnw -q -pl dts-platform -Dtest=CatalogDomainResourceAuditTest,ReportsResourceWebMvcTest,AuditLoggingFilterTest test` | PASS | `it/evidence/20260523-audit-db-catalog-loop2.md` |
| dts-platform | `./mvnw -q -pl dts-platform -DskipTests compile` | PASS | `it/evidence/20260523-audit-db-catalog-loop2.md` |
| dts-admin | `./mvnw -q -pl dts-admin -Dtest=AuditActionCatalogBootstrapServiceTest,AuditActionCatalogServiceTest,AuditV2ServiceTest test` | PASS | `it/evidence/20260523-audit-db-catalog-loop3.md` |
| dts-admin | `./mvnw -q -pl dts-admin -DskipTests compile` | PASS | `it/evidence/20260523-audit-db-catalog-loop3.md` |
| dts-platform | `./mvnw -q -pl dts-platform -Dtest=CatalogDomainResourceAuditTest,ReportsResourceWebMvcTest,AuditLoggingFilterTest test` | PASS | `it/evidence/20260523-audit-db-catalog-loop3.md` |
| dts-analytics | `./mvnw -q -pl dts-analytics -Dtest=AnalyticsAuditLoggingFilterTest,AnalyticsAuditForwarderServiceTest test` | PASS | `it/evidence/20260523-audit-db-catalog-loop4-analytics.md` |
| dts-admin | `./mvnw -q -pl dts-admin -Dtest=AuditV2ServiceTest,AuditIngestResourceTest,AuditEntryViewMapperTest test` | PASS | `it/evidence/20260523-audit-db-catalog-loop4-analytics.md` |
| dts-admin | `./mvnw -q -pl dts-admin -DskipTests compile` | PASS | `it/evidence/20260523-audit-db-catalog-loop4-analytics.md` |
| dts-analytics | `./mvnw -q -pl dts-analytics -DskipTests compile` | PASS | `it/evidence/20260523-audit-db-catalog-loop4-analytics.md` |
| dts-admin | `xmllint --noout ...20260523-01_audit_action_catalog.xml ...master.xml` | PASS | `it/evidence/20260523-audit-db-catalog-loop4-analytics.md` |
| dts-platform | `./mvnw -q -pl dts-platform -Dtest=AuditLoggingFilterTest test` | PASS | `it/evidence/20260523-audit-db-catalog-loop5-display-fallback.md` |
| dts-admin | `./mvnw -q -pl dts-admin -Dtest=AuditEntryViewMapperTest,AuditV2ServiceTest test` | PASS | `it/evidence/20260523-audit-db-catalog-loop5-display-fallback.md` |
| dts-admin-webapp | `pnpm exec vitest run src/admin/views/audit-center.source-contract.test.ts` | PASS | `it/evidence/20260523-audit-db-catalog-loop5-display-fallback.md` |
| dts-admin-webapp | `pnpm build` | PASS | `it/evidence/20260523-audit-db-catalog-loop5-display-fallback.md` |
| repo | `git diff --check` | PASS | `it/evidence/20260523-audit-db-catalog-loop5-display-fallback.md` |
| dts-analytics | `./mvnw -q -pl dts-analytics -Dtest=AnalyticsAuditLoggingFilterTest,ScreenAuditServiceTest,SemanticAuditServiceTest,AnalyticsAuditForwarderServiceTest,AdminAuditHttpHeadersFactoryTest,ScreenPermissionServiceTest test` | PASS | `it/evidence/20260523-audit-db-catalog-loop6-analytics-screen-ingest.md` |
| dts-admin | `./mvnw -q -pl dts-admin -Dtest=AuditV2ServiceTest,AuditIngestResourceTest,AuditEntryViewMapperTest,AuditActionCatalogServiceTest test` | PASS | `it/evidence/20260523-audit-db-catalog-loop6-analytics-screen-ingest.md` |
| dts-analytics | `./mvnw -q -pl dts-analytics -DskipTests compile` | PASS | `it/evidence/20260523-audit-db-catalog-loop6-analytics-screen-ingest.md` |
| dts-analytics | `./mvnw -q -pl dts-analytics test` | FAIL | `it/evidence/20260523-audit-db-catalog-loop6-analytics-screen-ingest.md` |
| repo | `git diff --check` | PASS | `it/evidence/20260523-audit-db-catalog-loop6-analytics-screen-ingest.md` |

## 现场 Smoke Checklist

- [ ] auditadmin 登录 dts-admin 后，可看到普通用户 platform 业务端审计。
- [ ] 新建主题域显示为“数据目录/主题域/新增主题域”，不是“数据资产”。
- [ ] 修改主题域显示为“修改主题域”。
- [ ] 查看报表显示为“数据可视化/查看报表”。
- [ ] 修改报表显示为“数据可视化/修改报表”。
- [ ] 支撑查询、下拉框、菜单、字典、轮询不刷屏。
- [ ] 未注册 actionCode 进入未分类治理记录。
- [ ] dts-analytics 大屏查看/修改/导出/授权显示为“BI分析/分析端审计”。
- [ ] dts-analytics 语义查询、VDS 新增/修改/删除/提升显示为“自助BI与语义层”。

## Loop 2 Review

- 已完成审计中心模块/分组/分类选项 DB catalog 化。
- 已完成 HTTP fallback 支撑查询降噪，降低 auditadmin 页面刷屏风险。
- 现场浏览器 smoke 未在本地执行，需要部署后确认 UI 展示文案。

## Loop 3 Review

- 已修复同类未分类 miss 重复插入问题，改为累计 occurrenceCount 和 lastSeenAt。
- 已补启动导入服务：common audit catalog 只作为缺失 seed 写入 DB，不覆盖已有 DB catalog 配置。
- 已显式注册 admin 侧 `AuditActionCatalog` Bean，避免 common 包不在默认扫描路径导致启动失败。

## Loop 4 Review

- 已完成 dts-analytics fallback actionCode 生成和 forwarder `buttonCode`/`operationCode` 转发。
- 已补 analytics DB catalog seed，并让未知 analytics actionCode 落入 `analytics.unclassified`。
- 已修复审计中心 analytics sourceSystem 展示，不再显示为管理端审计。

## Loop 5 Review

- 已修复审计中心“模块名称”列取错来源系统字段的问题，模块显示回到 DB catalog/module 快照。
- 已修复 raw actionCode 摘要覆盖中文动作名的问题，新入库和历史展示都会优先使用已治理 operationName。
- 已补大屏字体/图片 GET 支撑资源降噪，查看大屏不再生成“查看screen-fonts”类人工审计。

## Loop 6 Review

- 已修复 dts-analytics 大屏管理复数路径 `/api/screens` 未命中 `SCREEN_*` 目录的问题。
- 已修复 analytics 中央审计 actor 优先使用邮箱的问题，改为优先上报平台登录名，避免 admin ingest 因现场用户 email 为空而跳过。
- 已停止把权限 local fallback 这类系统迁移保护事件转发为中央人工审计，保留本地 WARN 线索。
