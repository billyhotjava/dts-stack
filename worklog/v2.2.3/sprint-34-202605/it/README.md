# Sprint-34 IT Evidence

**状态**: LOOP-3-PASS（代码与 focused 验证完成；现场 smoke 待部署补证）

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

## 现场 Smoke Checklist

- [ ] auditadmin 登录 dts-admin 后，可看到普通用户 platform 业务端审计。
- [ ] 新建主题域显示为“数据目录/主题域/新增主题域”，不是“数据资产”。
- [ ] 修改主题域显示为“修改主题域”。
- [ ] 查看报表显示为“数据可视化/查看报表”。
- [ ] 修改报表显示为“数据可视化/修改报表”。
- [ ] 支撑查询、下拉框、菜单、字典、轮询不刷屏。
- [ ] 未注册 actionCode 进入未分类治理记录。

## Loop 2 Review

- 已完成审计中心模块/分组/分类选项 DB catalog 化。
- 已完成 HTTP fallback 支撑查询降噪，降低 auditadmin 页面刷屏风险。
- 现场浏览器 smoke 未在本地执行，需要部署后确认 UI 展示文案。

## Loop 3 Review

- 已修复同类未分类 miss 重复插入问题，改为累计 occurrenceCount 和 lastSeenAt。
- 已补启动导入服务：common audit catalog 只作为缺失 seed 写入 DB，不覆盖已有 DB catalog 配置。
- 已显式注册 admin 侧 `AuditActionCatalog` Bean，避免 common 包不在默认扫描路径导致启动失败。
