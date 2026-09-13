# F2: 指标可视化运营台

**优先级**: P0
**状态**: IN_PROGRESS
**目标**: 面向指标 owner、治理人员和管理人员，补齐指标定义、运行、质量、订阅、消费的运营视图。

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | IN_PROGRESS | 梳理指标定义、模板、运行、订阅、质量和消费 API 的现状 |
| T02 | DONE | 新增指标运营概览 API：总量、上线数、异常数、订阅数、最近运行 |
| T03 | IN_PROGRESS | 新增指标运营页面：健康度、异常趋势、热门指标、未维护指标 |
| T04 | PLANNED | 指标详情补充运行时间线、质量结果、血缘影响和消费入口 |
| T05 | IN_PROGRESS | 增加 smoke，验证概览、列表、详情、异常下钻 |

## 当前落地

- 新增 `/metrics/operations` 指标运营台入口。
- 运营台已展示指标概览、语义指标、可消费模型、校验失败、指标链路、异常分布、发布闭环和消费链路。
- 运营台操作区已接入 `/ops/events`，可从指标链路跳转到统一事件观测。
- 后端新增 `/api/platform/sprint27/metric-operations` 聚合 API，统一返回 `sources`、指标概览、趋势、主题域、对象、指标、模型和最近运行。
- 前端运营台已改为消费 Sprint-27 聚合 API，并展示数据源状态，空数据可区分 `READY`、`EMPTY`、`ERROR`。
- 后端新增 `/api/semantic/workbench` 和 `/api/semantic/menu-diagnostics`，从指标工作台、主题域映射、业务对象 JOIN、指标可视化配置、DWS/ADS 数据集、审核发布和血缘、模型运行监控七个菜单维度诊断功能完整性。
- 语义建模概览页已接入工作台诊断卡片，菜单入口能展示 `READY`、`PARTIAL`、`EMPTY` 状态和下一步动作，避免 UI 重构后只剩静态入口。
- Sprint-27 smoke 已覆盖 `/metrics/operations` 页面和 `/api/platform/sprint27/metric-operations` API。

## 验收标准

- 指标中心能区分“定义完成”“已发布”“有运行结果”“被消费”。
- 语义建模首页能按七个菜单维度展示后端能力、主 API 和数据完整性状态。
- 指标异常可以追溯到质量失败、dbt 失败或上游采集失败。
- 页面归属继续保持在 `pages/metrics/**`，不回流到 governance 巨页。
- 不依赖 Kafka，指标运行事件只作为后续增量刷新通道。

## 非目标

- 不新增复杂指标市场功能。
- 不接入自然语言问答。
- 不强制定义指标审批流。
