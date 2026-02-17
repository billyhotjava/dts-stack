# P1-01 数据源统一与 SQL 模式

`status`: `in-progress`  
`priority`: `P1`  
`inspiration`: `Metabase(Card资产) + Superset(SQL Lab + Explore 双模式)`

## 目标

统一 `metric/dataset/sql/card/api` 数据源入口，降低绑定复杂度并增强灵活分析能力。

## 子任务

1. QuerySpec 扩展
- `sourceType` 标准化：`metric|dataset|sql|card|api`。

2. SQL 模式
- 设计器内提供 SQL 查询配置页（数据库选择 + SQL + 参数）。

3. 执行协议一致化
- 输出统一 `DataFrame` 结构，含字段元数据与数据类型。

4. 安全与限制
- SQL 超时、行数上限、危险语句拦截策略。

## 验收标准

- 同一图表可在 `card` 与 `sql` 两种来源间切换并正常渲染。
- SQL 参数可被全局变量引用。
- 执行错误均返回统一错误结构。

## 风险与回滚

- 风险：SQL 自由度带来安全风险。  
- 回滚：默认只开放 `SELECT` 与白名单数据源。

## 实现记录（2026-02-14）

- 前端数据源协议升级：`type/sourceType` 兼容，统一识别 `database -> sql`，并扩展 `sql/dataset/metric` 类型位。
- 设计器属性面板新增 SQL 模式配置：数据库、SQL、最大行数、超时、刷新周期、参数绑定（可绑定全局变量）。
- 执行链路统一：`useCardDataSource` 新增 `sql` 统一分支，调用 `/analytics/api/dataset`，支持 `parameters/query_timeout/constraints/queryContext`。
- 兼容性：保留 legacy `databaseConfig` 读取，并在 Spec v2 归一化时自动迁移到 `sqlConfig`。
- 后端安全加固：`QueryExecutionFacade` 对 native SQL 增加只读校验（仅 `SELECT/WITH`）、危险关键字拦截、多语句拦截、SQL 长度上限。
- 健康体检与预热链路兼容 `sql`：`ScreenResource` 与 `ScreenWarmupService` 支持 `sourceType/sqlConfig` 和 legacy `databaseConfig` 双路径。
- 测试覆盖：新增 `QueryExecutionFacadeTest`，验证 SQL 模板渲染、非只读语句拦截、多语句拦截。
- 运行时查询调度补强（2026-02-15）：
  - 新增前端查询调度器（并发上限、超时保护、可重试错误退避重试）；
  - `useCardDataSource` 统一接入调度器，覆盖 `card/sql/dataset/api`；
  - 增加请求序列保护，避免慢请求回写覆盖新请求结果（stale response 覆盖问题）。
- DataFrame 归一化补强（2026-02-16）：
  - `useCardDataSource.toCardData` 新增统一列模型归一化（兼容 `cols`、`results_metadata.columns`、对象行推导）；
  - 统一行模型归一化（兼容二维数组、对象数组、标量数组）；
  - 各类数据源输出继续收敛到同一 `CardData(rows, cols)` 协议，减少渲染分支差异。
- `metric` 数据源链路补齐（2026-02-16）：
  - 属性面板新增 `Metric 语义模式` 类型入口（`cardId + metricId/version + 参数绑定 + 刷新频率`）；
  - `useCardDataSource` 新增 `metric` 执行分支，统一调用 `queryCard + semantic`；
  - 补齐 `metric` 缓存键策略（含 `cardId/metricId/metricVersion/params/context`），避免不同口径混用缓存。
  - 修复运行时变量注入遗漏：`ComponentRenderer` 已将 `metricConfig.parameterBindings` 纳入参数解析链路，确保全局变量能正确传入语义查询。
