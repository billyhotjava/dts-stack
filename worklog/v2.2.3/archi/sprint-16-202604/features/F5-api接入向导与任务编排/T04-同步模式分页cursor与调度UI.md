# T04: 同步模式、分页、cursor 与调度 UI

**优先级**: P1
**状态**: DRAFT
**依赖**: F1/T03, F4/T03, F4/T04

## 目标

让用户以产品化方式配置 API 的 full_refresh、incremental、分页、cursor 和调度策略。

## 范围

- 根据 capability 展示可选同步模式。
- 配置 pagination 类型、page size、终止条件。
- 配置 cursor 字段、请求参数注入位置、lookback window。
- 配置 cron/manual 和失败重试策略。

## 完成标准

- [ ] 不支持的同步模式不可选。
- [ ] cursor 配置有 preview 校验。
- [ ] 分页配置能被后端 dry-run 验证。
- [ ] 调度配置与现有 Airflow 任务入口兼容。

