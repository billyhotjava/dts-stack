# T01: Airflow瘦触发DAG

**优先级**: P0
**状态**: DONE
**依赖**: F2-T01

## 目标

`buildApiDagSource` 由 385 行内嵌 Python 业务逻辑改为「瘦触发器」：DAG 仅调用 dts-ingestion 内部执行端点并轮询完成状态，业务逻辑全部移入 Java 执行器。

## 技术设计

- 新 DAG 模板（仍由 `AirflowDagService` 生成，但只保留触发/轮询胶水）：PythonOperator 调 `POST {ingestion}/internal/api-ingestion/executions`（带 taskId/backfill 窗口/batch_id），随后轮询 `GET .../executions/{id}` 直至终态；dts-ingestion 失败 → DAG task 失败（保留 Airflow 告警/重试语义）。
- 内部端点鉴权：复用 `ServiceDependencyAuthenticationFilter`（X-DTS-Service 服务间认证，对齐 dbt sync 端点做法）。
- DAG 仍保留 `max_active_runs=1`、schedule 来自 `task.syncSchedule`、`execution_timeout` 改由 ApiProperties 注入（不再写死 15min）。
- ingestion 服务地址在 DAG 中经 env 注入（默认值进 ApiProperties，不写死在模板字面量）。

## 影响范围

- `service/etl/AirflowDagService.java`（buildApiDagSource 重写为瘦模板）
- 新增 `web/rest/InternalApiIngestionResource.java`（内部触发/查询端点）
- `security/ServiceDependencyAuthenticationFilter`（端点纳入）

## 验证

- [x] DAG 模板单测断言不再包含 `SOURCE_CONFIG_JSON`/`psycopg2`/raw DDL/checkpoint 业务逻辑，仅包含内部 POST/GET 调用与轮询。
- [x] 内部 POST/GET 资源层单测覆盖 `batchId`、backfill 窗口解析、稳定 DB execution id 查询。
- [x] 服务层单测覆盖 `executeInternalApi` 复用 API connector/executor，保留 Airflow 传入 batch/backfill，且不触发 Airflow 二次调度。
- [x] 新 DAG 触发→执行→终态回传全链路（mock API）
- [x] dts-ingestion 重启时进行中执行的恢复语义明确（标 FAILED 可重试）
- [x] gitnexus_impact: `buildApiDagSource` 上游链为 LOW；`IngestionTaskService.execute#3` 为 HIGH，采用兼容重载，公共 execute/backfill 参数与默认 batch 生成保持不变。

## 进展记录

### 2026-06-12

- `AirflowDagService.buildApiDagSource` 已从内嵌 API runtime 改为薄 DAG：读取 `DTS_INGESTION_INTERNAL_BASE_URL`、`DTS_SERVICE_NAME`、`DTS_SERVICE_TOKEN`，POST `/internal/api-ingestion/executions`，优先使用响应 `id` 轮询 GET `/internal/api-ingestion/executions/{id}`。
- 新增 `InternalApiIngestionResource`：POST 提交内部 API 执行；GET 返回 status、rowsRead/rowsWritten、error/failure、backfill 字段。
- `IngestionTaskService.executeInternalApi` 支持内部调用传入 `batchId`、`BACKFILL_RANGE` 窗口，并限制只允许 API source task；实际执行仍走 `SourceConnectorRegistry` → `ApiHttpSourceConnector` → `ApiIngestionExecutor`。
- `AirflowExecutionSyncService.recoverStaleExecutions` 覆盖服务重启恢复：超过 stale 阈值的内部 API running/preparing 执行会标记为 `failed`，保留 retryable 状态；Airflow disabled/API 内部执行不会错误查询 Airflow。
- live IT 已补齐 Airflow 真实触发链路：`RUN_LIVE=1 bash worklog/v2.2.3/sprint-38-202606/it/scripts/api-end-to-end.sh` 创建任务 `90`，Airflow run `s38-api-e2e-airflow-90-1781249278` task state `success`，第三次由 DAG 创建的 execution `136` 写入 1 行，raw 行数推进到 4，checkpoint 推进到 `2026-06-12T00:04:00Z`。证据: `it/evidence/api-end-to-end-20260612.txt`。
- 运行态根因修复：Airflow 容器存在 `HTTP_PROXY/HTTPS_PROXY` 且 `NO_PROXY` 为空时，Python `urllib` 会把内部 `dts-ingestion` 回调带到代理路径；瘦 DAG 模板改为 `_DTS_INTERNAL_HTTP_OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}))`，内部 ingestion API 与 OpenLineage 回调均禁用代理。
- 定向验证：
  - `./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-ingestion -Dtest=IngestionTaskServiceTest test`
  - `./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-ingestion -Dtest=IngestionTaskServiceTest#executeInternalApi_shouldUseRequestBatchAndBackfillWindow test`
  - `./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-ingestion -Dtest=InternalApiIngestionResourceTest,AirflowDagServiceTest#shouldGenerateThinApiDagThatCallsInternalIngestionEndpoint test`
  - `(cd source && ./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-ingestion -Dtest=AirflowExecutionSyncServiceTest test)`
  - `./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -pl dts-ingestion -Dtest=AirflowDagServiceTest#shouldGenerateThinApiDagThatCallsInternalIngestionEndpoint,InternalApiIngestionResourceTest,IngestionTaskServiceTest#executeInternalApi_shouldUseRequestBatchAndBackfillWindow+executeInternalApi_shouldEnsureThinDagForAirflowEnabledApiTask+rebuildApiDags_shouldForceRebuildOnlyActiveApiTasks test`
  - `./builds/dts-build.sh --image dts-ingestion --no-save`
  - `docker compose -p v223 -f docker-compose-app.yml up -d --no-deps --force-recreate dts-ingestion`
  - `RUN_LIVE=1 bash worklog/v2.2.3/sprint-38-202606/it/scripts/api-end-to-end.sh`

## 完成标准

- [x] DAG 文件不再包含业务逻辑/凭据语义，仅触发与轮询
- [x] Airflow 真实触发瘦 DAG 后由 dts-ingestion 执行 API 入湖并回填终态
