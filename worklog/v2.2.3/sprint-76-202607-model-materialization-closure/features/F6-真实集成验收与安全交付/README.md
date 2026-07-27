# F6：真实集成验收与安全交付

**优先级**：P0
**状态**：DRAFT
**依赖**：F1～F5、F7

## 目标

用真实 PostgreSQL、Airflow 2.9.3/LocalExecutor、Docker dbt、Spring Security 和 Chrome95 证明普通/高级两种实现共用唯一 Python 物化 runtime 并安全发布；验证 Airflow 是唯一调度真值、手工/CRON 正确落账、plan scope 完整、tmpfs profile lease 无泄漏、Candidate duty role 与 M05 action policy 双门禁，再完成迁移、回滚、运维和 Go/No-Go。

## 契约定义

- `it/README.md` IT-01～IT-20；
- `assets/nfr-budget.md` 全部适用 fitness functions；
- clean Liquibase upgrade/rollback；
- release plan 和 runbook；
- GitNexus impact/detect_changes 与代码 review。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 建立后端契约数据库安全与NFR测试矩阵 | P0 | DRAFT | F1～F4、F7 |
| T02 | 完成真实dbtAirflowPostgreSQL与Chrome95闭环 | P0 | DRAFT | T01、F5、F7 |
| T03 | 完成发布回滚运维证据与最终GoNoGo | P0 | DRAFT | T02 |

## Definition of Ready

- [x] IT 场景、数据和证据位置已命名。
- [x] DoD 覆盖架构/UI/竖切片。
- [x] 发布、回滚、运维产物 owner 已命名。
- [ ] F1～F5、F7 完成且证据无 placeholder。
- [ ] PG-03 外部依赖 Sprint-36/F3 实际 DONE，不以 READY 状态替代。

## 完成标准

- [ ] IT-01～IT-20 全 PASS。
- [ ] NFR fitness functions 全绿。
- [ ] 四目标关系真实存在且与 current revision 对应。
- [ ] 发布后至少完成一次真实 OPERATIONAL_RUN 并更新目标数据/运行健康。
- [ ] Airflow actual schedule/nextRun、DagRun/TaskInstance 与平台 pipeline run 全部对账。
- [ ] tracked/shared credential 清零且 tmpfs lease、pairwise Airflow auth、secret rotation/cleanup 演练通过。
- [ ] 两类 DAG 只 import 一个 task factory，legacy per-tag DAG 已停止触发。
- [ ] 三类职责账号与 Sprint-36/F3 双门禁真实通过。
- [ ] rollback/runbook/security/release evidence 齐全。
- [ ] 最终 Go/No-Go 明确且无占位证据。
