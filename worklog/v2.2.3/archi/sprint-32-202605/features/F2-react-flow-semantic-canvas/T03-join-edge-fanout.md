# T03: Join 边与 fanout 风险

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

让用户在画布上连接业务对象和资产，并显式配置 Join 条件、基数和 fanout 风险。

## 技术设计

- React Flow edge 类型表达 `joins_to`。
- Join 配置包括 source key、target key、join type、relationship、cardinality、approvalRequired、fanoutWarning。
- fanout 风险由平台 hint + 本地图规则共同判断。

## 影响范围

- `source/dts-metrics-webapp/src/features/metric-flow/edges/**`
- `source/dts-metrics` graph preflight

## 验证

- [ ] 无 key 的 join 被阻断。
- [ ] many-to-many 默认给出 fanout 阻断或审批提示。

## 完成标准

- [ ] Join 边能参与后续 DSL 和 dbt artifact 生成。
