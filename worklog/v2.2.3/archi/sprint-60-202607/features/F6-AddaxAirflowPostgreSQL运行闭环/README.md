# F6: Addax + Airflow + PostgreSQL 运行闭环

**优先级**: P0
**状态**: IN_PROGRESS（适配器、回调、状态机和租户隔离已完成，真实外部投递待验收）

## 目标

把建模产物真正接入现有 Addax、dbt、Airflow 和 PostgreSQL 运行体系，形成可查询的端到端证据。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 运行协议与状态机 | P0 | DONE | F3-T03 |
| T02 | Addax 批次上下文承接 | P0 | DONE | T01 |
| T03 | Airflow DAG 投递与 dbt 回调 | P0 | IN_PROGRESS | T01,T02 |
| T04 | PostgreSQL 目标表、目录与血缘回写 | P0 | IN_PROGRESS | T03 |

## 完成标准

- [x] 运行请求可关联 Addax batch、Airflow DAG、dbt selector 和 PostgreSQL 目标；真实外部服务回调待验收。
- [x] 失败、重试、取消和超时状态可观测（状态机及提交门禁已覆盖）。
- [ ] 运行证据回到模型台账和任务运维中心。
