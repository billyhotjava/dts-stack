# DTS v2.2.1 Worklog

## 文件索引
- 审查结论：`worklog/v2.2.1/review-findings.md`
- 未完成任务总清单：`worklog/v2.2.1/platform-analytics-v2.2.1-task-list.md`
- P0 回归清单：`worklog/v2.2.1/p0-regression-checklist.md`
- P0 回归矩阵：`worklog/v2.2.1/p0-regression-matrix.md`
- RV-003 证据索引：`worklog/v2.2.1/rv-003/README.md`
- BI 商用化分解（P0/P1/P2）：`worklog/v2.2.1/BI/screen-designer-commercialization-p0-p2-breakdown.md`

## 说明
- 本目录用于承接 v2.2.0 的剩余工作与 review 新增修复项。
- 默认按 `P0 -> P1 -> P2 -> RV` 顺序推进。

## Progress (2026-02-13)
- Completed: `V221-P1-001` first implementation (admin user search no longer triggers full snapshot refresh on empty keyword hits; user list role aggregation switched to batch query to avoid per-row N+1).
- Added tests: `AdminUserServiceListSnapshotsTest` (keyword-empty fast path + first-page empty full refresh fallback).
- Completed: `V221-P1-002` first implementation (Airflow failure sync now captures failed task/log excerpt and writes classified failure context to execution/audit; retry audit now carries previous failure category/advice).
- Completed: `V221-P1-002` second implementation (execution records persist `failure_category`/`failure_advice`; `/api/ingestion/tasks/{id}/executions` supports `status` + `failureCategory` filtering).
- Added tests: `AirflowExecutionSyncServiceTest` (failed/success sync paths).
- Verified: `mvn -f source/dts-admin/pom.xml -DskipTests compile` passed.
- Verified: `mvn -f source/dts-admin/pom.xml -Dtest=AdminUserServiceListSnapshotsTest test` passed.
- Verified: `mvn -f source/dts-ingestion/pom.xml -DskipTests compile` passed.
- Verified: `mvn -f source/dts-ingestion/pom.xml -Dtest=AirflowExecutionSyncServiceTest,AirflowAdapterTest,AddaxJobServiceTest test` passed.
- Completed: `V221-RV-001` first implementation (`QueryDataset` dept visibility no longer depends on exact `owner_dept` match).
- Completed: `V221-RV-002` first implementation (`QueryDataset`/`BI Link` global manage scope aligned to `CATALOG_MAINTAINERS`).
- Completed: `V221-P0-002` hardening (`generate-from-ods` now catches per-mapping runtime errors and continues processing remaining mappings).
- Completed: `V221-P0-002` regression tests (`ModelingSqlModelServiceTest`: source fallback to dataset source + per-mapping skip/continue behavior).
- Completed: `V221-P0-001` regression tests (`IngestionTaskFullRefreshExecutionTest`: non-file full refresh calls target provisioner + file full refresh skips provisioner).
- Completed: execution hardening in `IngestionTaskService` (invalid Addax container path now returns readable error; success audit meta is null-safe and no longer risks `Map.of` NPE).
- Verified: `mvn -f source/dts-ingestion/pom.xml -Dtest=IngestionTaskFullRefreshExecutionTest,AirflowAdapterTest,AirflowExecutionSyncServiceTest,AddaxJobServiceTest test` passed.
- In progress: `V221-P0-003` first-pass (`AirflowAdapter` adds second-stage wait/retry after DAG 404, and task-log 404 noise downgraded to debug).
- Added tests: `AirflowAdapterTest` (3 cases, includes DAG 404 recovery path).
- Added tests: `AddaxJobServiceTest` full-refresh semantics (file path uses DROP+CREATE, RDBMS path keeps TRUNCATE when preSql is absent).
- Verified: `mvn -f source/dts-platform/pom.xml -DskipTests compile` passed.
- Verified: `mvn -f source/dts-platform/pom.xml -Dtest=QueryDatasetServiceTest test` passed.
- Verified: `mvn -f source/dts-ingestion/pom.xml -Dtest=AirflowAdapterTest test` passed.
- Verified: `mvn -f source/dts-ingestion/pom.xml -DskipTests compile` passed.
- Completed: `V221-P1-003` first implementation (report center classification visibility and maintainer-vs-employee permission guard regression coverage).
- Added tests: `BiReportLinkServiceTest` (classification filter + role matching), `ReportsResourceWebMvcTest` (employee read-only, maintainer create allowed).
- Verified: `mvn -f source/dts-platform/pom.xml -Dtest=ReportsResourceWebMvcTest,BiReportLinkServiceTest,QueryDatasetServiceTest test` passed.
- Verified: `mvn -f source/dts-ingestion/pom.xml -Dtest=AirflowExecutionSyncServiceTest,AirflowAdapterTest,AddaxJobServiceTest,IngestionTaskFullRefreshExecutionTest,IngestionTaskExecutionFilterTest,IngestionExecutionMapperTest test` passed.
- Completed: `V221-P2-001` done (SQL modeling supports project-level ZIP import; backend `import-project` endpoint now supports `skip/overwrite/fail`, manifest/folder auto-detect, `dryRun` precheck, and package fingerprint).
- Completed: `V221-P2-002` first-pass (project ZIP import now supports `manifest/indicators.tsv`, converting indicator definitions into ADS SQL models for offline delivery compatibility).
- Added tests: `ModelingSqlProjectImportServiceTest` (manifest create path + conflict skip path + dry-run no-write path + indicators manifest path).
- Verified: `mvn -f source/dts-platform/pom.xml -Dtest=ModelingSqlProjectImportServiceTest,ModelingSqlModelServiceTest,QueryDatasetServiceTest test` passed.
- Verified: `pnpm -C source/dts-platform-webapp build` passed.
- Completed: `V221-P2-003` first implementation (`QueryWorkbenchPage` added dataset-management tab; new `QueryDatasetManager` supports list/filter, version create/publish/archive, BI-link dependency view, and SQL preview).
- Verified: `pnpm -C source/dts-platform-webapp build` passed (includes `QueryDatasetManager` and `QueryWorkbenchPage` tab integration).
- Completed: `V221-RV-003` first-pass evidence landing (`rv-003` added 24h stability, Addax/Airbyte semantic compare, and isolation/lineage regression templates).
- Pending: fill现场实测数据并形成最终审计结论（x86/ARM + legacy/normal/dev）。
