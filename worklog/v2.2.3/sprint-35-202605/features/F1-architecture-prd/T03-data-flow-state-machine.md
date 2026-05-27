# T03: 数据流与状态机设计

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

把源数据库到指标消费的完整链路和 `dts-metrics` 状态机串起来，避免画布、DSL、dbt 和发布状态各自为政。

## 技术设计

数据流：

```text
源数据库 / 文件 / API
  -> Connector Center
  -> Addax/Airflow ODS
  -> dbt STG
  -> dbt DWD
  -> dbt DWS
  -> dbt ADS
  -> platform Catalog / OpenLineage / governance
  -> dts-metrics visual assets / graph draft / DSL
  -> platform/dbt validation
  -> BI Dataset / 大屏 / API 消费
```

状态机：

```text
ASSET_SELECTED -> GRAPH_DRAFTED -> GRAPH_PREFLIGHTED
  -> CONTRACT_VALIDATED -> DBT_VALIDATED
  -> REVIEW_SUBMITTED -> APPROVED -> PUBLISHED -> CONSUMED
```

失败态必须区分 layer、grain、contract、dbt、review、publish。

## 影响范围

- `worklog/v2.2.3/sprint-35-202605/assets/dts-metrics-elt-layer-prd.md`
- `source/dts-metrics` 后续 graph/model status
- `source/dts-metrics-webapp` 后续 status badge 和 action toolbar

## 验证

- [ ] 每个状态都有进入条件和失败态。
- [ ] DWD 高级建模路径不会跳过 DWS 候选验证。
- [ ] ADS 消费路径不会反向成为新指标口径唯一事实源。

## 完成标准

- [ ] API、前端和后端 task 都引用同一状态机。
