# T05: 图结构校验与节点诊断

**优先级**: P0
**状态**: READY
**依赖**: T04

## 目标

在提交 platform/dbt 检测前，先完成本地图结构和 DSL 前置校验。

## 技术设计

- 校验必填节点、孤立节点、非法边、循环依赖、缺字段、缺时间粒度、fanout 风险。
- 诊断对象必须包含 nodeId/edgeId/severity/message/remediation。
- 前端在节点上显示 warning/error 状态。

## 影响范围

- `source/dts-metrics` graph preflight service
- `source/dts-metrics-webapp` validation panel

## 验证

- [ ] 缺少 source asset 的指标阻断验证。
- [ ] 循环指标依赖阻断验证。

## 完成标准

- [ ] 只有 GRAPH_VALIDATED 的草稿才能进入 platform contract precheck。
