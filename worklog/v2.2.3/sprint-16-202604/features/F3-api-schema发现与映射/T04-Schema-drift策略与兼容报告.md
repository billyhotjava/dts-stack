# T04: Schema drift 策略与兼容报告

**优先级**: P0
**状态**: DRAFT
**依赖**: T02, T03

## 目标

处理 API 响应字段变化，避免外部系统字段漂移静默影响 stg / DWD / DWS。ODS 保存原始 record，因此新增未知字段不会丢失；drift 主要作用于 schema snapshot、stg mapping 和下游发布门禁。

## 范围

- 定义 drift 类型：新增字段、删除字段、类型变化、nullable 变化、嵌套结构变化。
- 定义策略：`notify`、`block_stg`、`append_to_stg`、`ignore_unknown_in_stg`。
- 输出兼容报告和用户确认流程。
- 运行时发现 drift 时写入事件和任务状态。

## 完成标准

- [ ] 默认策略安全保守。
- [ ] 破坏性类型变化不会静默进入 stg 和下游模型。
- [ ] 用户确认后的 schema version 可追踪。
- [ ] drift 事件能进入观测和审计。
