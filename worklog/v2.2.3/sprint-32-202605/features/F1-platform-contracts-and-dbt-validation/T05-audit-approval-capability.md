# T05: 审计、审批和 capability 回传

**优先级**: P1
**状态**: READY
**依赖**: T04

## 目标

让 metrics graph 的保存、验证、提交、发布、撤销和回滚进入 platform 审计、审批和 capability 体系。

## 技术设计

- platform capability 返回 metrics、dbt validation、BI Dataset、lineage 的可用状态。
- 每次验证和发布写 audit event。
- 发布前必须产生 review record。
- outbox/event 记录指标模型状态变化。

## 影响范围

- `source/dts-platform` audit/capability/review/outbox
- `source/dts-metrics` publish workflow

## 验证

- [ ] 保存草稿不触发发布审计。
- [ ] 验证、审核、发布、撤销都有对应审计。
- [ ] capability 关闭时前端显示明确降级态。

## 完成标准

- [ ] 运维能从 platform 追踪完整指标模型生命周期。
