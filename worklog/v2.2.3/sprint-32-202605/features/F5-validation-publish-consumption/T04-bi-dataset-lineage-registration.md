# T04: BI Dataset 与血缘注册

**优先级**: P0
**状态**: READY
**依赖**: T03

## 目标

发布成功后通过 platform 注册 BI Dataset 和 source -> DWS/ADS -> BI Dataset 血缘。

## 技术设计

- platform 提供 BI Dataset register internal API。
- platform 提供 lineage register internal API。
- 注册结果写回 metrics publish record。

## 影响范围

- `source/dts-platform` BI/lineage API
- `source/dts-analytics` consumption contract
- `source/dts-metrics-webapp` publish result UI

## 验证

- [ ] 发布模型能在 BI 侧被选择。
- [ ] 血缘页面能看到 source -> model -> BI Dataset。

## 完成标准

- [ ] 消费端引用的是发布版本。
