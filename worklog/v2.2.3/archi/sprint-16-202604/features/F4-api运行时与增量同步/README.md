# F4: API 运行时与增量同步

**优先级**: P0
**状态**: DRAFT
**依赖**: F1, F2, F3

## 目标

实现 API 数据接入的正式运行链路，包括 execution plan、请求执行、分页、重试、限流、落 ODS、checkpoint 和失败分类。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | ExecutionPlan 落地与兼容适配 | P0 | DRAFT |
| T02 | API Runner 选型与适配实现 | P0 | DRAFT |
| T03 | 分页、重试、限流与熔断 | P0 | DRAFT |
| T04 | 增量 cursor 与 checkpoint | P0 | DRAFT |
| T05 | 错误分类、指标与运行事件 | P0 | DRAFT |

## 完成标准

- [ ] API full_refresh 可稳定落 ODS。
- [ ] API incremental cursor 可恢复、可重跑、可观测。
- [ ] 外部 API 异常不会泄露密钥或造成无限重试。
- [ ] 运行结果能继续触发 dbt、目录、血缘和质量链路。

