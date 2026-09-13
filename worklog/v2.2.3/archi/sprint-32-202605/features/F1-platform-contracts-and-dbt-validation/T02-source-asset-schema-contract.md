# T02: source asset/schema/field 查询契约

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

为 React Flow 节点池提供可建模 source asset、schema、字段、分类分级、owner、生命周期和 join hint。

## 技术设计

- platform 提供只读查询 API，返回 dataset/table/column/schema/classification/owner/lifecycle。
- 返回字段必须包含稳定 asset identity，不能暴露内部不稳定实现细节。
- 支持按主题域、资产类型、关键词、权限可见性筛选。

## 影响范围

- `source/dts-platform` asset/catalog/internal API
- `source/dts-metrics` platform contract client
- `source/dts-metrics-webapp` React Flow node palette

## 验证

- [ ] 无权限资产不会进入节点池。
- [ ] 字段缺失、资产下线、生命周期禁用都有结构化错误。

## 完成标准

- [ ] React Flow 可从该契约加载 source asset 和 field tree。
