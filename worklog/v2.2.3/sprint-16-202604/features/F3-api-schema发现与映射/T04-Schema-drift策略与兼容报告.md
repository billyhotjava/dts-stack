# T04: Schema drift 策略与兼容报告

**优先级**: P0
**状态**: DRAFT
**依赖**: T02, T03

## 目标

处理 API 响应字段变化，避免外部系统字段漂移直接污染 ODS 或导致静默丢数。

## 范围

- 定义 drift 类型：新增字段、删除字段、类型变化、nullable 变化、嵌套结构变化。
- 定义策略：`notify`、`block`、`append_column`、`ignore_unknown`。
- 输出兼容报告和用户确认流程。
- 运行时发现 drift 时写入事件和任务状态。

## 完成标准

- [ ] 默认策略安全保守。
- [ ] 破坏性类型变化不会静默落库。
- [ ] 用户确认后的 schema version 可追踪。
- [ ] drift 事件能进入观测和审计。

