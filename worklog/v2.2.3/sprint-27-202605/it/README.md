# Sprint-27 IT 验收

**状态**: PLANNED

## 验收范围

- ELT 可视化控制台 smoke。
- 指标可视化运营台 smoke。
- 审计一致性 smoke。
- Kafka 关闭状态下主流程 smoke。

## 证据目录

证据统一归档到：

`worklog/v2.2.3/sprint-27-202605/it/evidence/<date>-local/`

## 原则

- Kafka 不作为必需依赖。
- 端到端权限、脱敏、审批只验证审计字段预留，不验证最终策略。
- smoke 失败时必须能区分页面失败、API 失败、依赖服务失败。
