# T04: 审计、审批和 review event

**优先级**: P0
**状态**: READY
**依赖**: T01-T03

## 目标

统一记录 graph 保存、验证、提交、审批、发布、撤销和回滚动作，让审计中心可追踪指标模型全生命周期。

## 技术设计

- graph 保存：记录 actor、graphId、version、assetKeys。
- validation：记录 trace id、状态、失败数量、dbt invocation id。
- review：记录提交人、审批人、意见、状态变更。
- publish：记录 platform publish reference、BI Dataset reference、lineage registration id。
- rollback：记录 fromVersion、toVersion、影响 consumer lock。

## 影响范围

- `source/dts-platform` audit events API / catalog
- `source/dts-metrics` audit client
- `source/dts-admin` 审计目录展示后续接入

## 验证

- [ ] 每个关键动作写 audit event。
- [ ] 失败事件也进入审计。
- [ ] 审计 payload 不包含敏感凭据。

## 完成标准

- [ ] 指标模型生命周期能被审计中心复盘。
