# T01: 关系模型 helper 与预检规则

**优先级**: P0  
**状态**: READY  
**依赖**: 无

## 目标

在 `metricCanvas.helpers.ts` 中补齐语义编排关系模型，让画布能从现有 metrics 数据中解析 `OBJECT_METRIC` 和 `METRIC_DERIVES` 两类边，并能对派生关系做基础预检。

## 技术设计

- 扩展边类型：
  - `OBJECT_METRIC`: 来源业务对象，目标指标，来自 metric `objectId`。
  - `METRIC_DERIVES`: 来源指标，目标指标，来自目标 metric `formulaJson.dependsOnMetricIds`。
- 新增/调整 helper：
  - 解析合法 `formulaJson` 对象。
  - 从 `dependsOnMetricIds` 构造派生边。
  - 合并或移除目标指标的 `dependsOnMetricIds`。
  - 检测环依赖、孤立指标、ACTIVE 依赖 DRAFT、公式 JSON 不合法、业务对象缺失。
- 保持 `buildSemanticMetricUpdatePayload` 的全量 PUT 字段保护。

## 影响范围

- `source/dts-platform-webapp/src/pages/modeling/metric-workbench/metricCanvas.helpers.ts`
- `source/dts-platform-webapp/src/pages/modeling/metric-workbench/metricCanvas.helpers.test.ts`

## 验证

- [ ] helper 单测覆盖 `OBJECT_METRIC` 与 `METRIC_DERIVES` 边构造。
- [ ] helper 单测覆盖环依赖检测。
- [ ] helper 单测覆盖合法/非法 `formulaJson` 合并保护。
- [ ] helper 单测覆盖删除派生关系。

## 完成标准

- [ ] 关系解析不破坏 Sprint-56 已有业务对象绑定边。
- [ ] 非法 `formulaJson` 不会被静默覆盖。
- [ ] 预检问题能返回可定位的 nodeId 或 edgeId。
