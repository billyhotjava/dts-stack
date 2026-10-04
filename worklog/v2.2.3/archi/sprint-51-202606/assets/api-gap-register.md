# API Gap Register

本登记表只记录“现有页面无法闭环”的 API 缺口。编码前必须先确认现有 `platformApi.ts`、后端 Resource、真实响应是否已能满足页面。

| 缺口 | 页面来源 | 现有候选接口 | 判断规则 | 状态 |
|------|----------|--------------|----------|------|
| 系统类型在数据源表单统一展示 | `/foundation/data-sources` | `/platform/dict/system-types` | 若接口已有且返回可用，仅做前端接线；若 404 才补只读接口 | DONE-FE (F1-T01: dictionaryService + TYPE_OPTIONS 兜底) |
| 资产详情批量标签/域展示 | `/catalog/assets`, `/catalog/datasets/:id` | `/catalog/datasets/openmetadata/batch`, `/catalog/assets-v2/*` | 若批量接口可用，列表页用 batch；若不可用，详情懒加载优先 | DONE-FE (F2-T01: `__tags` 从 assets-v2 detail 解析，profile json 解析摘要) |
| 资产血缘边完整展示 | `/catalog/datasets/:id`, `/catalog/lineage/*` | `/catalog/assets-v2/{id}/lineage`, `/catalog/datasets/{id}/lineage` | 优先 assets-v2；仅当边缺失且后端确实不返回时补字段 | DONE-FE (F2-T02: lineage tab 带 `?datasetId=` 上下文跳转；source-contract 通过) |
| Addax 手工同步后血缘刷新 | `/explore/etl/transform*`, `/catalog/lineage/import` | `/catalog/lineage/sync-addax` | 页面只负责触发/提示；后端同步失败不阻断主任务 | DONE-FE (F2-T03: TransformDetailPage 执行成功后显示「同步 Addax 血缘」→ /catalog/lineage/import) |
| 数据集质量和授权跨页面复用 | `/catalog/datasets/:id`, `/studio/sql-modeling`, `/governance/*` | `/catalog/datasets/*/quality`, `/governance/quality/*`, `/asset-grants` | 优先复用现有治理页面数据；缺少按 dataset 查询才补 | DONE-FE (F3-T01: 概览 Alert 对 BLOCKED/WARNING 状态显示质量/授权快捷动作；F3-T02: SqlModelingPage 已有 standardGateResult + testResult) |
| 工作台下一步上下文 | `/workbench` | 现有页面 query / local preference / backend preference | 优先路由 query 和前端状态，不新增聚合 API | DONE-FE (F4-T01: 工作台 header+详情 Space 增加血缘/字典横切入口；约束：不引入 sql 字面量) |

## 后端补充原则

- 只补页面验收需要的最小接口，不为“统一架构好看”新增泛化服务。
- 每个新增 API 必须写入本表，包含页面、字段、失败态、source-contract。
- 能通过已有 endpoint + 前端适配完成的，不补后端。
