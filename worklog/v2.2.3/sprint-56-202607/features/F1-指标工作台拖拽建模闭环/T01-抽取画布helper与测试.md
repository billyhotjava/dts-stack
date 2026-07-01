# T01: 抽取画布 helper 与测试

**优先级**: P0  
**状态**: DONE  
**依赖**: 无

## 目标

把指标画布的节点布局、边生成、连线解析、拖拽 payload 和指标更新 payload 抽成可测试 helper。

## 技术设计

- 新增 `metricCanvas.helpers.ts`。
- 覆盖 `buildMetricCanvasNodes`、`buildMetricCanvasEdges`、`resolveMetricBinding`、`parseMetricDragPayload`、`findMetricDropTargetObject`。
- 新增 `buildSemanticMetricUpdatePayload`，适配后端全量 PUT 语义。

## 影响范围

- `source/dts-platform-webapp/src/pages/modeling/metric-workbench/metricCanvas.helpers.ts`
- `source/dts-platform-webapp/src/pages/modeling/metric-workbench/metricCanvas.helpers.test.ts`

## 验证

- [x] `pnpm exec vitest run src/pages/modeling/metric-workbench/metricCanvas.helpers.test.ts`

## 完成标准

- [x] helper 单测 6/6 通过。
