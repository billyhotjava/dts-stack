# F1：规划与建模概览真实化

**优先级**：P0  
**状态**：CODE_COMPLETE（最终 Review、构建、部署与 E2E 待 F5）

## 目标

规划人员维护真实计划、域、过程、层级和策略；建模人员在概览看到由真实 owner 聚合的数量、状态与待办，不再看到项目/财务示例。

## 契约

| UI | API | Owner |
|---|---|---|
| `PlanningPage` | `warehousePlanApi`、CatalogDomain、planning adapter | WarehousePlan/CatalogDomain/既有过程层级 |
| `PlanningPage`（数仓分层） | `/api/modeling/warehouse-layers`（`warehouseLayerApi`） | `WarehouseLayerApplicationService` + `modeling_warehouse_layer` |
| `OverviewPage` | 并发 list plans/model specs/standards/indicators；必要时只新增无表的只读聚合 | 既有事实投影 |

## 四态与交互

- 进入即加载；无记录显示真实空态和创建入口。
- 写操作具备 busy、校验、权限、错误重试、成功刷新。
- 无统一 owner 的主题域等目录不得用本地数组冒充；只读投影或明确现场定义。

| Task | 状态 |
|---|---|
| T01 接入规划真实目录和可维护动作 | CODE_COMPLETE（含数仓分层切片 Tasks 1–7） |
| T02 接入建模概览真实投影 | CODE_COMPLETE |
| T03 完成七态与聚焦契约测试 | CODE_COMPLETE |

## DoR

- [x] API owner 已映射
- [x] UI 控件已登记
- [x] 无平行表/审计方案
- [x] 验收可由聚焦测试与 IT-84-01 证明

## 数仓分层切片（2026-08-05）

- 系统分层：代码字典（`Sprint64GovernanceContract`），只读，删除返回 `WAREHOUSE_LAYER_BUILTIN_PROTECTED`。
- 自定义分层：`modeling_warehouse_layer`，全局共享，可新增/逻辑删除；编码删除后不可复用。
- 模型选择：ModelSpec 当前头与 revision snapshot 保存 `warehouseLayerCode`；canonical 执行语义仍由 `layer` 承担；旧调用方缺省回退 canonical layer。
- 删除保护：非 `ARCHIVED` ModelSpec 引用返回 `WAREHOUSE_LAYER_IN_USE`（409 + referenceCount）。
- 审计：`MODELING_WAREHOUSE_LAYER_CREATE/DELETE` 严格写入公共审计。
- 真实交付状态：代码/契约测试完成；认证 E2E 未执行前保持 `PENDING/BLOCKED_E2E_INPUT`。

## 当前编码证据（2026-08-03）

- 当前实现以 `worklog/prototype/dm` 的页面结构为 UI 基线；旧 `PlanningWorkspace`、`HomeWorkspace` 与共享“新建建设计划”模块已物理删除。
- `PlanningPage` 只在“建模空间”提供 WarehousePlan 维护；其他规划页消费各自 canonical owner，owner 缺失时展示明确空态，不回退演示数据。
- `OverviewPage` 聚合 WarehousePlan、ModelSpec、标准和指标真实投影；请求失败、无数据、无权限均有独立状态。
- 代码和契约测试已完成；当前 bundle 尚未完成最终 Review、构建、部署及真实浏览器 E2E，因此不标记为部署或交付完成。
