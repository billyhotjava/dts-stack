# F1：规划与建模概览真实化

**优先级**：P0  
**状态**：CODE_COMPLETE（部署/E2E 待 F5/T02）

## 目标

规划人员维护真实计划、域、过程、层级和策略；建模人员在概览看到由真实 owner 聚合的数量、状态与待办，不再看到项目/财务示例。

## 契约

| UI | API | Owner |
|---|---|---|
| `PlanningWorkspace` | `warehousePlanApi`、CatalogDomain、planning adapter | WarehousePlan/CatalogDomain/既有过程层级 |
| `HomeWorkspace` | 并发 list plans/model specs/standards/indicators；必要时只新增无表的只读聚合 | 既有事实投影 |

## 四态与交互

- 进入即加载；无记录显示真实空态和创建入口。
- 写操作具备 busy、校验、权限、错误重试、成功刷新。
- 无统一 owner 的应用层/主题域/空间/参数不得用本地数组冒充；只读投影或明确现场定义。

| Task | 状态 |
|---|---|
| T01 接入规划真实目录和可维护动作 | DONE |
| T02 接入建模概览真实投影 | CODE_COMPLETE |
| T03 完成七态与聚焦契约测试 | DONE |

## DoR

- [x] API owner 已映射
- [x] UI 控件已登记
- [x] 无平行表/审计方案
- [x] 验收可由聚焦测试与 IT-84-01 证明

## 编码证据（2026-08-02）

- `PlanningWorkspace` 已删除八类内置业务记录，接入 WarehousePlan CRUD/CAS、CatalogDomain、业务过程、系统分层、DataMart 和计划策略；无统一 owner 的主题域等对象显示诚实空态。
- `HomeWorkspace` 已并发读取 WarehousePlan、ModelSpec、标准与指标，并用阶段投影生成交付状态和阻塞待办；API 失败不回退示例。
- RED：新增 source-contract 时因 `planningHomeAdapter.ts` 尚不存在失败（ENOENT）。
- GREEN：`pnpm exec vitest run src/pages/data-modeling/planningHomeIntegration.source-contract.test.ts src/pages/data-modeling/adapters/planningHomeAdapter.test.ts`，2 files / 6 tests 通过。
- 静态质量：五个 F1 文件的 `pnpm exec biome check` 通过。
- Sprint-84 前端冻结快照聚焦测试和 Chrome 95 生产构建已通过；真实部署/E2E 仍按集中验证约束统一在 F5/T02 执行。
- `DimensionalModelingWorkspace` 已消费 `/data-modeling/dimensions/workbench?create=1` 并打开真实创建态；该跨组件依赖已关闭。
- 规划与指标可重入读取增加 request epoch，迟到响应不会覆盖新上下文；规划参数加载期间禁止提交旧版本。
- 代码/契约已完成；真实租户权限、审计与浏览器交互仍待 F5/T02，故不标记为交付完成。
