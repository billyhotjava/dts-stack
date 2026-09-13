# T03: 资产生命周期状态契约

**优先级**: P0
**状态**: DONE
**依赖**: T02

## 目标

把资产从“存在即能用”改为有明确生命周期的治理对象。

## 技术设计

- 支持 `DISCOVERED`、`PENDING_GOVERNANCE`、`ACTIVE`、`DEPRECATED`、`ARCHIVED`。
- 缺 owner、classification、warehouseLayer、sourceSystem 的自动资产进入 `PENDING_GOVERNANCE`。
- 发布门禁和消费层只默认消费 `ACTIVE` 或显式允许的状态。

## 影响范围

- Catalog 数据模型
- Catalog 查询接口
- 资产门户筛选
- dts-metrics 资产引用校验

## 验证

- [x] 自动创建资产不会默认为 `ACTIVE`。
- [x] 列表和详情能复用统一治理状态策略。
- [ ] 最终统一测试阶段运行 `CatalogAssetGovernancePolicyTest`。

## 完成标准

- [x] 生命周期状态契约已落地，消费和发布阻断将在 F2/F4/F6 中接入。
