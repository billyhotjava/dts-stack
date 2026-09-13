# T04: Studio 建模上下文桥接

**状态**: DONE  
**优先级**: P0  
**涉及路由**: `/studio/projects`, `/studio/sql-modeling`, `/modeling/dbt-files`, `/explore/workbench`

## 目标

把项目、SQL 建模、dbt 文件和探索工作台连接起来，用户从数据源或主题进入建模时不会丢失上下文。

## 按钮清单

| 按钮 | 类型 | 目标行为 |
|------|------|----------|
| 从数据源建模 | 主按钮 | 带数据源/表进入 SQL 建模 |
| 导入 dbt 文件 | 次按钮 | 进入 dbt 文件管理并保留项目 |
| 预览模型 | 次按钮 | 展示模型输出样例 |
| 提交治理 | 主按钮 | 进入治理/发布门禁 |
| 回到项目 | 次按钮 | 返回项目详情 |

## 组件清单

- `StudioContextProvider`: 项目、主题、数据源上下文。
- `ModelingEntryCard`: SQL 建模、dbt 文件、探索工作台入口。
- `ModelPreviewDrawer`: 输出字段、样例、质量提示。
- `GovernanceSubmitPanel`: 提交治理前的检查项。
- `ProjectBreadcrumb`: 项目 > 模型 > 发布。

## 数据约束

- 建模上下文必须能从 query 或后端对象恢复。
- 提交治理必须关联真实模型或资产，不允许只改前端状态。

## 验收

- [x] 从数据源进入 SQL 建模时带入源表信息。
- [x] 从项目进入 dbt 文件能返回项目。
- [x] 提交治理后能进入治理或资产页面。
- [x] 上下文缺失时展示选择项目/数据源的空态。
