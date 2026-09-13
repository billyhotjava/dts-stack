# 2026-05-23 Loop 2 Evidence

## 范围

- 审计中心模块、分组、分类选项切换为优先读取 DB audit catalog。
- `AuditEntryResource` 保留旧映射回退，只在 DB catalog 为空时使用。
- `AuditLoggingFilter` 收紧支撑 GET 降噪边界，避免 forward-auth、菜单、统计、下拉选项等支撑请求进入人工审计。

## 验证命令

| 命令 | 结果 | 说明 |
|------|------|------|
| `./mvnw -q -pl dts-admin -Dtest=AuditV2ServiceTest test` | PASS | 回归 DB catalog 分类、sourceSystem、miss 记录 |
| `./mvnw -q -pl dts-admin -DskipTests compile` | PASS | admin 编译检查 |
| `./mvnw -q -pl dts-platform -Dtest=CatalogDomainResourceAuditTest,ReportsResourceWebMvcTest,AuditLoggingFilterTest test` | PASS | 回归主题域、报表动作和 fallback 降噪 |
| `./mvnw -q -pl dts-platform -DskipTests compile` | PASS | platform 编译检查 |

## Review 结论

- DONE: 审计中心选项现在与运行时 DB catalog 同源，减少新增 platform 模块时维护两套来源的问题。
- DONE: HTTP fallback 继续保留人工动作兜底，但跳过支撑查询、菜单、统计、下拉、auth probe。
- DONE: 保留旧 `OperationMappingEngine` 作为空目录兼容回退，避免迁移初期目录为空导致页面选项不可用。
- RISK: 现场 UI 文案和真实浏览器 smoke 仍需部署后补证，尤其是历史数据与新 catalog 混合时的筛选体验。
