# API Gap Register

本登记表只记录“现有页面无法闭环”的 API 缺口。编码前必须先确认现有 `platformApi.ts`、后端 Resource、真实响应是否已能满足页面。

| 缺口 | 页面来源 | 现有候选接口 | 判断规则 | 状态 |
|------|----------|--------------|----------|------|
| 系统类型在数据源表单统一展示 | `/foundation/data-sources` | `/platform/dict/system-types` | 若接口已有且返回可用，仅做前端接线；若 404 才补只读接口 | TO_VERIFY |
| 资产详情批量标签/域展示 | `/catalog/assets`, `/catalog/datasets/:id` | `/catalog/datasets/openmetadata/batch`, `/catalog/assets-v2/*` | 若批量接口可用，列表页用 batch；若不可用，详情懒加载优先 | TO_VERIFY |
| 资产血缘边完整展示 | `/catalog/datasets/:id`, `/catalog/lineage/*` | `/catalog/assets-v2/{id}/lineage`, `/catalog/datasets/{id}/lineage` | 优先 assets-v2；仅当边缺失且后端确实不返回时补字段 | TO_VERIFY |
| Addax 手工同步后血缘刷新 | `/explore/etl/transform*`, `/catalog/lineage/import` | `/catalog/lineage/sync-addax` | 页面只负责触发/提示；后端同步失败不阻断主任务 | TO_VERIFY |
| 数据集质量和授权跨页面复用 | `/catalog/datasets/:id`, `/studio/sql-modeling`, `/governance/*` | `/catalog/datasets/*/quality`, `/governance/quality/*`, `/asset-grants` | 优先复用现有治理页面数据；缺少按 dataset 查询才补 | TO_VERIFY |
| 工作台下一步上下文 | `/workbench` | 现有页面 query / local preference / backend preference | 优先路由 query 和前端状态，不新增聚合 API | TO_VERIFY |

## 后端补充原则

- 只补页面验收需要的最小接口，不为“统一架构好看”新增泛化服务。
- 每个新增 API 必须写入本表，包含页面、字段、失败态、source-contract。
- 能通过已有 endpoint + 前端适配完成的，不补后端。
