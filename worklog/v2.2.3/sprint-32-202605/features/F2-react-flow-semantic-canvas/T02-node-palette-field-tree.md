# T02: 节点池和字段树

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

从 platform 资产契约加载可建模资产和字段，形成画布节点池和字段树。

## 技术设计

- 节点池包含 source asset、business object、dimension、metric、model、publish。
- 字段树展示 schema、字段类型、治理标准、可用权限和分类分级。
- 拖拽字段到画布生成 dimension/metric 节点。

## 影响范围

- `source/dts-metrics-webapp/src/features/metric-flow/palette/**`
- `source/dts-metrics/src/main/java/**PlatformContractClient*`

## 验证

- [ ] 无权限资产不显示或显示为不可拖拽。
- [ ] 字段搜索、筛选、拖拽生成节点可用。

## 完成标准

- [ ] 节点池不使用静态 mock 作为主要数据源。
