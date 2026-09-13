# T02: semantic/metric 历史映射报告

**优先级**: P0
**状态**: DONE
**依赖**: F1-F4

## 目标

盘点 platform 内 `semantic_*` 历史数据如何映射到后续 dts-metrics 的 `metric_*` 资产模型。

## 技术设计

- 输出 subject、object、dimension、metric、model、artifact 的映射策略。
- 明确哪些保留在 platform 兼容代理，哪些迁移到 dts-metrics。
- 不在 Sprint-31A 执行生产迁移。

## 影响范围

- SemanticModelingService
- dts-metrics migration plan
- Sprint-32 assets

## 验证

- [x] 每类 semantic 表都有迁移去向。
- [x] 平台权限和资产引用不丢失。

## 完成标准

- [x] Sprint-32 能按报告实现迁移。

## 证据

- `worklog/v2.2.3/sprint-31a-202605/assets/semantic-metric-history-mapping.md`
