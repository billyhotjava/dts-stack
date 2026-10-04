# F5: 验证、发布、血缘与消费闭环

**优先级**: P0
**状态**: READY

## 目标

完成从 graph validation 到 platform/dbt validation、审核、发布、BI Dataset 注册、血缘注册和消费可见的闭环。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 验证状态机 | P0 | READY | F4 |
| T02 | 审核与 release gate | P0 | READY | T01 |
| T03 | dbt release submit | P0 | READY | T02 |
| T04 | BI Dataset 与血缘注册 | P0 | READY | T03 |
| T05 | 运行监控和消费关系 | P1 | READY | T04 |

## 完成标准

- [ ] 未通过 platform/dbt validation 的模型不能发布。
- [ ] 发布动作全部由 platform 记录审计和发布证据。
- [ ] BI/大屏/API 消费引用发布版本，不引用草稿。
