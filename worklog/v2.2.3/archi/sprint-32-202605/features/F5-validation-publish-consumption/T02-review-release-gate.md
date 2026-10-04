# T02: 审核与 release gate

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

在发布前完成 platform 审核和 dbt release gate。

## 技术设计

- graph DBT_VALIDATED 后才能提交审核。
- 审核通过后调用 `/api/etl/dbt/release-gate/check`。
- release gate 返回 blockers/warnings/evidence。

## 影响范围

- `source/dts-platform` review/release gate
- `source/dts-metrics` publish service

## 验证

- [ ] 未审核模型不能 release submit。
- [ ] release gate blocker 阻断发布。

## 完成标准

- [ ] 审核和门禁证据可在发布页查看。
