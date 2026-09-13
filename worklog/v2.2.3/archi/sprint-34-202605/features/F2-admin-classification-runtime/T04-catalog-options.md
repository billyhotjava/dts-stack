# T04: 字典/分组查询迁移到 DB catalog

**优先级**: P1
**状态**: DONE
**依赖**: T02

## 目标

审计中心模块和分组选项来自 DB action catalog，而不是旧 `audit_operation_mapping` 展示规则。

## 技术设计

- `/api/audit-entries/modules` 优先读取 DB 模块目录。
- `/api/audit-entries/groups` 和 `/categories` 读取 DB action catalog 的模块/资源分组。
- 旧 `audit_operation_mapping` 只保留给历史 URL 规则和兼容，不作为新运行时权威。

## 影响范围

- `AuditEntryResource.java`
- `AuditEntryQueryService.java`
- `OperationMappingEngine.java`

## 验证

- [x] 模块下拉包含业务端模块。
- [x] 新增 DB catalog 模块无需改前端硬编码翻译。

## 完成标准

- [x] 审计中心筛选项与运行时分类目录一致。

## 实现记录

- `AuditEntryQueryService` 优先从 `audit_module_catalog` 和 `audit_action_catalog` 生成模块、分组和分类选项。
- `AuditEntryResource` 的 `/groups`、`/categories` 在 DB catalog 有数据时使用目录结果，仅在目录为空时回退旧 `OperationMappingEngine` 兼容历史环境。
