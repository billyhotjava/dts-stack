# F4：dbt 执行网关与调度

**优先级**：P0
**状态**：PLANNED

## 目标

以 `DbtExecutionGateway` 统一 ReleaseCandidate/Materialization 到 Airflow/dbt 的提交、查询、取消和回执对账，彻底解除建模对旧 `/etl/dbt/run`、HTTP resource、Docker 的依赖。

## 契约定义

| 类型 | 契约 | 要点 |
|---|---|---|
| 提交 | `submit(DbtExecutionRequest)` | candidate/version/attempt、target、artifact checksum、leaseId、idempotencyKey |
| 查询 | `query(ExecutionHandle)` | QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED/UNKNOWN |
| 取消 | `cancel(handle, expectedState)` | CAS，已终态幂等 |
| Airflow | deterministic `dagRunId` | candidate/version/attempt/mode 确定；服务鉴权 |
| dbt 回执 | artifact + relation evidence | 必须匹配 candidate/version/attempt/checksum |

## UI/UX 规格

不新增执行页面。Sprint-80 建模页的构建/发布/物化动作最终只看到 canonical candidate/materialization 状态；上游超时显示“待对账”，不得显示成功或自动重复创建运行。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 定义并实现 DbtExecutionGateway | P0 | PLANNED | F1、F2/T03 契约 |
| T02 | 重接 ReleaseCandidate/Materialization 调用 | P0 | PLANNED | T01、F2/T03 |
| T03 | 收敛 Airflow/dbt adapter 与回执对账 | P0 | PLANNED | T01～T02 |

## Definition of Ready

- [x] request/handle/status、超时、幂等和凭据边界已冻结。
- [ ] 旧 `/etl/dbt/run` 与 Airflow/dbt caller impact 完成。
- [ ] 当前运行中任务/旧 run 表画像完成。

## 完成标准

- [ ] 建模执行只通过 gateway，旧 route 调用为 0。
- [ ] 重放、超时、UNKNOWN、取消、旧 attempt 回执均有测试。
- [ ] 请求/日志/DB/outbox 不包含 profile、token 或数据源 secret。
