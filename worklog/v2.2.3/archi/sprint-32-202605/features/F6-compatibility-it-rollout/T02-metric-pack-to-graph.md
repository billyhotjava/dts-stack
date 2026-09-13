# T02: metric-pack 到 graph draft 映射

**优先级**: P0
**状态**: READY
**依赖**: F3,F4

## 目标

让合作方 metric-pack 导入后生成可编辑 React Flow graph draft。

## 技术设计

- manifest/domains/objects/dimensions/metrics/models 映射为 nodes/edges。
- 导入前仍做 platform contract/permission resolver。
- 导入结果显示缺失资产、冲突指标、危险公式和不可发布项。

## 影响范围

- `source/dts-metrics` metric-pack import
- `source/dts-metrics-webapp` graph draft loader

## 验证

- [ ] 示例包可生成 graph draft。
- [ ] 缺术语/缺资产的包不能进入可发布状态。

## 完成标准

- [ ] metric-pack 与手工画布使用同一个 graph/DSL 模型。
