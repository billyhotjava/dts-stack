# T04: Graph draft 保存与加载

**优先级**: P0
**状态**: READY
**依赖**: T03

## 目标

保存 React Flow graph draft，使指标语义图可恢复、可 diff、可回滚。

## 技术设计

- 保存 nodes、edges、viewport、selected output、validation snapshot。
- graph draft 版本化，支持 latest 和历史版本。
- 保存时只记录 platform contract reference，不复制资产事实。

## 影响范围

- `source/dts-metrics` graph draft API/entity
- `source/dts-metrics-webapp` flow state adapter

## 验证

- [ ] 保存后刷新页面可恢复画布。
- [ ] platform 资产变更后 graph reload 能显示 contract drift。

## 完成标准

- [ ] graph draft 是后续验证、发布和回滚的统一输入。
