# F2：候选驱动物化编排与运行真值

**优先级**：P0
**状态**：DRAFT
**依赖**：F1

## 目标

把 ReleaseCandidate 的 START_BUILD 状态迁移接到 durable pipeline run 和现有 Airflow/dbt build，并提供只委托 canonical command 的单模型 Build Intent。普通/高级页面共用一个 `schedule=None` 的 RELEASE_BUILD executor DAG；凭据和 target 由服务端/Airflow secret 控制面解析，使构建在超时、重试、并发和服务重启后仍可恢复且不可被请求篡改。

## 契约定义

| 类型 | 契约 | 关键字段 |
|---|---|---|
| API | 既有 `POST .../{candidateId}/lock` | If-Match、Idempotency-Key、reason |
| facade | 预定 `POST .../model-specs/{id}/build-intents` | headers=If-Match/Idempotency-Key；body=plan/environment；返回 canonical candidate/run |
| candidate | ReleaseCandidate/Entry 兼容扩展 | origin、execution target snapshot、activeClaimKey |
| 数据 | 扩展 `modeling_pipeline_run` | candidate_id、implementation revision/checksum、environment、artifact/scoped bundle checksum |
| dispatch | `MaterializationDispatchService.dispatch(candidateId)` | QUEUED → SUBMITTED/RUNNING |
| 外部 | `DbtExecutionGateway.submitReleaseBuild` | durable run → RELEASE_BUILD executor DAG |
| reconcile | `MaterializationRunReconciler` | Airflow/manifest/run_results → entry run states |
| 凭据 | 既有数据源 secrets + `DbtRuntimeProfileLeaseService` | executionTargetKey → host tmpfs-backed task-scoped profile lease；Airflow 只持有 leaseId |

## UI/UX 规格

模型详情和高级建模页显示“构建”，计划交付工作台显示“开始构建”；三处动作最终都委托同一 candidate START_BUILD。提交后立即显示 durable QUEUED/RUNNING 状态。HTTP 超时、页面刷新或服务重启不得丢失运行，用户通过刷新/自动轮询看到同一 run。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 将START_BUILD与pipeline run原子绑定 | P0 | DRAFT | F1/T03 |
| T04 | 收敛dbt运行凭据与单目标能力边界 | P0 | DRAFT | T01 |
| T02 | 复用现有dbt/Airflow通道完成可靠调度与回收 | P0 | DRAFT | T01、T04 |
| T03 | 实现幂等重放漂移阻断与失败重试 | P0 | DRAFT | T02 |

## Definition of Ready

- [x] 快捷 facade 不拥有状态，只委托既有 candidate command。
- [x] 数据 owner、外部调用边界和状态机已固定。
- [x] 多模型 candidate 语义已固定。
- [x] P0 只有一个真实安全 target，其他 target fail-closed。
- [ ] F0 GO、F1 contracts GREEN。

## 完成标准

- [ ] candidate BUILDING 与 QUEUED rows 同事务。
- [ ] 单模型 Build Intent 只精确复用 SINGLE_MODEL candidate，批量冲突严格阻断。
- [ ] active claim、服务端派生和单执行目标由数据库/契约测试证明。
- [ ] tracked plaintext profile 已清除；secret 不进入 DAG/conf/XCom/API/DB/env/log/evidence，canonical task 不再依赖共享 profiles 目录。
- [ ] Airflow 内部调用使用 pairwise service token，缺失/伪造/路径越界 fail-closed。
- [ ] 外部 submit 可恢复且不会因请求重放重复触发。
- [ ] 一个 candidate 共享一个 Airflow run，各 entry 独立追踪。
- [ ] candidate 失败、pipeline UNKNOWN 对账、retry 和 stale 全部可解释，UNKNOWN 不被当作 candidate 状态。
