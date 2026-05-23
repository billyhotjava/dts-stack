# 2026-05-23 Loop 1 Evidence

## 范围

- 新增 dts-admin DB 审计动作目录、模块目录、未分类 miss 表。
- `AuditV2Service` 改为按 `sourceSystem + actionCode` 优先查 DB catalog。
- `/api/audit-events` 透传 platform `sourceSystem`。
- platform 主题域和报表动作改为稳定业务 actionCode。

## 验证命令

| 命令 | 结果 | 说明 |
|------|------|------|
| `./mvnw -q -pl dts-admin -Dtest=AuditV2ServiceTest test` | PASS | 覆盖 platform sourceSystem、DB catalog 命中、unknown action miss |
| `xmllint --noout source/dts-admin/src/main/resources/config/liquibase/changelog/20260523-01_audit_action_catalog.xml source/dts-admin/src/main/resources/config/liquibase/master.xml` | PASS | Liquibase XML 格式检查 |
| `./mvnw -q -pl dts-platform -Dtest=CatalogDomainResourceAuditTest,ReportsResourceWebMvcTest test` | PASS | 覆盖主题域动作码和报表查看审计动作 |
| `./mvnw -q -pl dts-platform -DskipTests compile` | PASS | platform 编译检查 |
| `npx gitnexus impact AuditV2Service --repo s10-stack` | MEDIUM | 18 个直接/间接使用点，无执行流程受影响 |
| `npx gitnexus impact AuditIngestResource --repo s10-stack` | LOW | 无上游调用者 |
| `npx gitnexus impact CatalogDomainResource --repo s10-stack` | LOW | 无上游调用者 |
| `npx gitnexus impact ReportsResource --repo s10-stack` | LOW | 无上游调用者 |
| `npx gitnexus impact SemanticModelingResource --repo s10-stack` | LOW | 无上游调用者 |
| `npx gitnexus detect-changes --repo s10-stack` | LOW | 当前工作区含用户此前未提交改动，整体 23 文件/125 symbols |

## Review 发现

- DONE: `AuditV2Service` 不再写死 `sourceSystem=admin`。
- DONE: platform 已注册动作优先使用 DB 目录，request override 不再覆盖正式分类。
- DONE: unknown platform action 写入 `platform.unclassified` 并记录 `audit_classification_miss`。
- DONE: 主题域 CRUD/move 不再使用 `CATALOG_ASSET_*`。
- DONE: 报表 visit/create/update/delete/share/revoke 使用 `REPORT_*` 动作。
- READY: 审计中心模块/分组选项仍待切到 DB catalog。
- READY: HTTP fallback 降噪边界仍待专项收紧。

## 现场 Smoke Checklist

- auditadmin 查询日志类型：platform 事件应显示“业务端审计”。
- 新建主题域应显示“主题域 / 新增主题域”，不应显示“数据资产”。
- 修改主题域应显示“主题域 / 修改主题域”。
- 打开大屏应显示“数据大屏 / 查看大屏”。
- 修改大屏应显示“数据大屏 / 修改大屏”。
- 未注册 actionCode 应显示“未分类业务操作”，并在 `audit_classification_miss` 有记录。
