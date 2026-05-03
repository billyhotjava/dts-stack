# F1: ELT 可视化控制台

**优先级**: P0
**状态**: IN_PROGRESS
**目标**: 建立面向数据工程与运维人员的 ELT 链路控制台，把 ingestion、dbt、质量、血缘、发布状态放到同一条可解释链路里。

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | IN_PROGRESS | 梳理 ELT 链路数据源：IngestionTask、IngestionExecution、schema snapshot、dbt run、quality run、lineage job |
| T02 | DONE | 新增 ELT 链路总览 API，按任务/资产返回阶段状态和最近执行摘要 |
| T03 | IN_PROGRESS | 新增 ELT 控制台页面，展示采集、建模、质量、血缘、发布的时间线 |
| T04 | PLANNED | 支持失败下钻：失败分类、错误摘要、重试状态、影响资产 |
| T05 | IN_PROGRESS | 增加 smoke，验证页面可打开、阶段数据可展示、失败任务可下钻 |

## 当前落地

- 新增 `/explore/etl` 与 `/explore/etl/console` 控制台入口。
- 控制台已展示执行概览、链路态势、运行诊断、治理闭环和资产链路。
- 控制台操作区已接入 `/ops/events`，可从 ELT 链路跳转到统一事件观测。
- 后端新增 `/api/platform/sprint27/elt-console` 聚合 API，统一返回 `sources`、`observability`、`governance`、`stages`、`chainItems`。
- 前端控制台已改为消费 Sprint-27 聚合 API，并展示数据源状态，空数据可区分 `READY`、`EMPTY`、`ERROR`。
- Sprint-27 smoke 已覆盖 `/explore/etl` 页面和 `/api/platform/sprint27/elt-console` API。

## 验收标准

- 用户能从一个入口看到指定资产或任务的 ELT 全链路状态。
- 采集失败、dbt 失败、质量失败能区分展示，不混成单一“失败”。
- 可跳转到既有 ingestion/dbt/质量/血缘详情，不重复造详情页。
- 不依赖 Kafka，后续事件化只作为状态刷新优化。

## 非目标

- 不重写 Airflow/Addax/dbt 执行逻辑。
- 不新增实时流式采集引擎。
- 不在本 feature 固化审批阻断规则。
