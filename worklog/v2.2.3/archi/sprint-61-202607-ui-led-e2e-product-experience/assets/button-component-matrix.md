# 按钮与组件矩阵

| Control | Type | Owner Component | User Intent | Handler/Route/API | Required States | Test |
|---------|------|-----------------|-------------|-------------------|-----------------|------|
| 继续端到端旅程 | primary button | `DataManagementWorkbenchPage` | 从当前阶段继续，不回菜单找入口 | route with `journey=e2e-data-product` | default/loading/disabled/error | source-contract + Playwright route smoke |
| 返回旅程工作台 | text/icon button | shared journey header | 从子页面回到工作台并保留上下文 | `/workbench?journey=e2e-data-product` | default/disabled | source-contract |
| 生成数仓规划草稿 | primary button | `DataSourcesPage` or workbench planning card | 从数据源生成 ODS/DWD/DWS/ADS 规划 | route/API TBD with `sourceId` | default/loading/empty/error/success | source-contract |
| 导入标准包并继续 | primary button | `StandardPackagePage` | 标准包应用后进入数据元落标 | `/governance/standards/elements?bindingDraft=1` | default/loading/error/success | existing + extended source-contract |
| 生成字段落标草稿 | primary button | `ElementsPage` | 把数据元输出给建模 | `POST /modeling/standard-binding-drafts` | default/loading/empty/permission/error/fallback/success | source-contract + service test |
| 创建模型草稿 | primary button | `SqlModelingPage` standard draft card | 由标准字段生成 DWD 模型候选 | open model drawer | default/disabled/success | source-contract |
| 应用到当前模型 | secondary button | `SqlModelingPage` standard draft card | 把标准字段绑定到当前模型 | `PUT /modeling/sql-models/{id}/standard-bindings` | default/loading/disabled/error/success | source-contract |
| 绑定指标口径 | primary button | `MetricWorkbenchPage` context panel | 从模型字段进入指标设计 | route/API with `modelId` | default/empty/error | source-contract |
| 发布门禁检查 | primary button | `SqlModelingPage` release panel | 发布前检查标准/质量/权限 | existing gate APIs | default/loading/blocking/passed/error | source-contract |
| 查看运行证据 | secondary button | `SqlModelingPage` / workbench evidence card | 跳到调度实例和日志 | `/ops/instances?journey=e2e-data-product` | default/empty | source-contract |
| 创建数据 API | primary button | `ApiServicesPage` context banner | 从模型/指标创建 API | route/API with `modelId`/`metricId` | default/loading/permission/error/success | source-contract |
| 生成客户验收包 | primary button | workbench evidence card | 汇总模型、服务、质量、审计证据 | UI aggregation first, API TBD | default/loading/partial/error/success | source-contract + Playwright |

## 状态要求

- `empty`: 必须说明缺少什么数据，以及下一步去哪里补。
- `disabled`: 必须有 tooltip 或附近说明，不能让用户猜。
- `error`: 必须可重试，并保留当前上下文。
- `success`: 必须给下一步按钮，不只弹 toast。
- `permission denied`: 按钮可见但不可操作时，要解释需要什么角色。
