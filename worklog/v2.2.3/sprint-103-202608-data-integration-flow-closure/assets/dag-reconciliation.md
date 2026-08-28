# Airflow DAG 只读对账

检查时间：2026-08-28（Asia/Shanghai）。来源为 Airflow metadata、`airflow dags list`、`ingestion_task` 和 `ingestion_execution` 只读查询。

## 结论

- Airflow 共 57 个 DAG，其中 `ingestion_*` 34 个；import error 为 0。
- 3 个 revision-owned DAG 与当前任务记录精确匹配；其中 task 11、12 已删除，task 13 ACTIVE。
- 31 个未匹配 DAG 全部未暂停：27 个与历史 execution 账本精确匹配；4 个没有 execution 账本且从未运行。
- 本 Sprint 对 31 个差异项的决定均为 `KEEP`。没有审批，不执行 pause、delete、rename、adopt 或文件清理。

## Revision-owned DAG

| DAG | task | task 状态 | Airflow 状态 | 最后运行 | 决定 |
|---|---:|---|---|---|---|
| `ingestion_revision_44_task_11_revision_44` | 11 | deleted | unpaused | 无 | KEEP |
| `ingestion_revision_45_task_12_revision_45` | 12 | deleted | unpaused | 无 | KEEP |
| `ingestion_revision_46_task_13_revision_46` | 13 | active | unpaused | 无 | OWNED |

已删除任务对应 DAG 也不在本 Sprint 自动退役；需要独立批准和依赖证明。

## 有 execution 账本的历史 DAG（27）

这些 DAG 名称中的 task/revision/execution 与 `ingestion_execution.airflow_dag_id` 精确一致，调用方证据为对应 execution 账本。它们属于旧的 per-execution 发布模式。

| task | DAG 中 execution | 数量 | 最后运行范围 | 末次结果分布 | 决定 |
|---:|---|---:|---|---|---|
| 3 | 1, 2, 3, 4, 5, 6, 8, 9, 19 | 9 | 2026-08-04 07:53～09:21 | failed 8 / success 1 | KEEP |
| 4 | 28, 29, 30, 31 | 4 | 2026-08-04 18:03～18:11 | failed 4 | KEEP |
| 5 | 32, 33, 34, 35, 37 | 5 | 2026-08-04 18:27～18:52 | failed 5 | KEEP |
| 6 | 39, 41, 42, 44, 53 | 5 | 2026-08-04 18:54～21:21 | failed 4 / success 1 | KEEP |
| 7 | 54 | 1 | 2026-08-05 10:36 | success 1 | KEEP |
| 8 | 55 | 1 | 2026-08-05 10:43 | success 1 | KEEP |
| 9 | 56 | 1 | 2026-08-05 10:47 | success 1 | KEEP |
| 10 | 75 | 1 | 2026-08-05 11:35 | success 1 | KEEP |

精确命名模式为 `ingestion_revision_{revision}_execution_{execution}_task_{task}`；上表中的每一个 execution 均可用同名 `airflow_dag_id` 在账本复查。

## 无 execution 账本的 DAG（4）

| DAG | Airflow 状态 | 最后运行 | 调用方证据 | 决定 |
|---|---|---|---|---|
| `ingestion_revision_2_execution_12380_task_7` | unpaused | 无 | 未发现 execution 账本 | KEEP |
| `ingestion_revision_2_execution_12381_task_7` | unpaused | 无 | 未发现 execution 账本 | KEEP |
| `ingestion_revision_2_execution_12382_task_7` | unpaused | 无 | 未发现 execution 账本 | KEEP |
| `ingestion_revision_2_execution_12383_task_7` | unpaused | 无 | 未发现 execution 账本 | KEEP |

这 4 项只能标记为“待独立审批调查”，不能据“未运行”直接判定可删除。

## 后续退役门槛

只有同时满足以下条件才允许把 KEEP 改为 `RETIRE_AFTER_APPROVAL`：

1. 精确 DAG 名称和文件路径已冻结；
2. ingestion task/revision/execution、外部调用方和调度依赖均证明不再引用；
3. 最近运行和暂停状态已复核；
4. 有业务 owner 与运维审批；
5. 先 dry-run，再单项可恢复操作；
6. 操作后验证 import error、DAG 数、任务调度与历史日志。

