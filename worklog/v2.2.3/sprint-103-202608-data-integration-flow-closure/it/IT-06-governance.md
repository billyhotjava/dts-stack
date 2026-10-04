# IT-06：迁移、兼容、DAG 与发布治理

检查日期：2026-08-28。判定：`PASS_WITH_ENV_NOTE`。

## 数据库与兼容证据

- 发布前重复 `batch_id` 组与重复 retry parent 组均为 0。
- `20260822-01-ingestion-quality-workflow-link`、`20260828-00-ingestion-target-dataset-identity`、`20260828-01-ingestion-execution-command-idempotency` 均为 `EXECUTED`。
- task、revision、execution 三个 `target_dataset_id` 均为 nullable UUID；历史 88 条 execution 未做猜测性资产回填。
- batch-id 与 retry-parent 两个条件唯一索引均存在；legacy 目标身份继续按 `qualityPolicyRef=dataset:<uuid>` 双读。

## DAG 与发布证据

- Airflow 57 个 DAG、接入 34 个、import error 0；3 个 revision-owned，31 个差异全部默认 `KEEP`，未做运行态变更。
- 新镜像摘要：ingestion `40cae5c9e830…`、platform `5ad73eb0d5e6…`、webapp `033dcc1937f9…`。
- 旧镜像保存在 `rollback-sprint103-20260828-030521`；三服务按 ingestion → platform → webapp 顺序切换并通过健康/Nginx 门禁。

## 环境说明

已证明 expand schema 与旧镜像可兼容，未实际把运行环境切回旧镜像，以免制造第二次业务中断。平台既有 `machineActor is not trusted` 定时任务错误仍存在，但健康和本次路由正常；独立跟踪。
