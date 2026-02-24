# P2-02 公共码表企业级导入与校验

`status`: `done`
`priority`: `P2`

## 目标

提升码表导入质量：格式校验、重复检测、差异预览、回滚支持。

## 范围

`ReferenceCodeService`、`GovernanceReferenceCodeResource`、`ReferenceCodesPage.tsx`。

## 子任务

1. 批量导入改为结构化模板（CSV/Excel）并提供预检结果。
2. 增加重复码值、跨系统映射冲突校验。
3. 提供导入回滚和审计追踪。

## 验收标准

- 导入前可预览新增/更新/冲突条目。
- 冲突可阻断或按策略合并。
- 导入操作可追踪、可回滚。

## 完成说明

- 新增结构化导入能力（预检/执行/回滚）：
  - `POST /api/governance/reference-codes/{id}/items/import/preview`
  - `POST /api/governance/reference-codes/{id}/items/import/apply`
  - `POST /api/governance/reference-codes/{id}/items/import/{runId}/rollback`
- 新增导入批次审计实体：`gov_reference_import_run`（Liquibase + Entity + Repository）。
- 前端码表页已改为结构化导入主流程，并展示冲突/错误明细与回滚入口。

## 风险与回滚

- 风险：历史脏数据导致导入失败率高。
- 回滚：提供“宽松模式”但默认严格。
