# Sprint-22 IT / API Runtime 验收入口

## 验收目标

验证 API 数据接入从“配置草稿/mock DAG”推进为可执行运行时：凭据引用、HTTP 请求、分页、cursor checkpoint、ODS raw landing、重跑/回补和错误分类都可定位。

## 脚本

- `scripts/api-runtime-smoke.sh`：验证 API connector contract、auth provider、可选执行/回补/重试路径。
- `samples/api-source-config.json`：API 源配置样例，覆盖 bearer secretRef、分页、cursor、raw landing。

## 执行方式

只验证契约：

```bash
DTS_INGESTION_URL=http://127.0.0.1:18083 \
DTS_SERVICE_HEADER=dts-ingestion \
worklog/v2.2.3/sprint-22-202605/it/scripts/api-runtime-smoke.sh
```

验证执行、回补和重试：

```bash
DTS_INGESTION_URL=http://127.0.0.1:18083 \
DTS_SERVICE_HEADER=dts-ingestion \
DTS_API_TASK_ID=<api_ingestion_task_id> \
DTS_API_EXECUTION_ID=<failed_or_latest_execution_id> \
DTS_API_BACKFILL_COLUMN=updatedAt \
DTS_API_BACKFILL_START=2026-01-01T00:00:00Z \
DTS_API_BACKFILL_END=2026-01-02T00:00:00Z \
worklog/v2.2.3/sprint-22-202605/it/scripts/api-runtime-smoke.sh
```

## 证据目录

```text
it/evidence/<yyyymmdd>-<env>/
  01-contract.body.json
  02-auth-providers.body.json
  03-execute.body.json
  04-backfill.body.json
  05-retry.body.json
  ods-row-count.sql.txt
  checkpoint.sql.txt
  airflow-log.txt
  residual-risks.md
```

## SQL 验证

```sql
select count(*) from ods_api_mock_orders;
select task_id, resource_id, cursor_value, updated_at
from dts_api_ingestion_checkpoint
order by updated_at desc;
```

## 通过口径

- Contract API 返回 `contractVersion`、`connectorType=api_http`、auth providers 和 raw landing 字段。
- API DAG 不生成 Addax job，直接用 PythonOperator 运行 HTTP -> ODS raw landing。
- ODS 表包含 `_dts_raw_record`、`_dts_batch_id`、`_dts_execution_id`、`_dts_endpoint`、`_dts_cursor_value`。
- 失败时错误分类以 `API_RUNTIME_*` 前缀进入 Airflow 日志和 execution 错误摘要。
- cursor 仅在任务成功落库后推进 checkpoint。
