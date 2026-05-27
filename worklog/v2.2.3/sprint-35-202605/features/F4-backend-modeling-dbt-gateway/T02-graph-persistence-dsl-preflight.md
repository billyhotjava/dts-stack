# T02: graph persistence 与 DSL preflight

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

实现 graph draft 持久化和 DSL 预检，使 `dts-metrics` 可以在调用 platform/dbt 前先发现结构性错误。

## 技术设计

- 持久化实体：`metric_graph_draft`、`metric_graph_node`、`metric_graph_edge`、`metric_graph_version`。
- preflight 检查节点类型、资产层级、Join 基数、fanout、指标依赖拓扑、字段引用、标准码、公式 DSL。
- 诊断字段统一为 `nodeId`、`edgeId`、`fieldId`、`metricCode`、`severity`、`code`、`message`。

## 影响范围

- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/domain/**`
- `source/dts-metrics/src/main/java/com/yuzhi/dts/metrics/service/**`
- `source/dts-metrics/src/test/**`

## 验证

- [ ] ODS/STG 节点 preflight ERROR。
- [ ] DWD 直接连接 publish ERROR。
- [ ] 复杂指标依赖循环 ERROR。

## 完成标准

- [ ] graph preflight 可以阻断明显错误，不依赖 dbt 才发现。
