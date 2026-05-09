---
name: dts-default-lake-constraint
description: Use when reviewing or changing DTS default data lake behavior, data source selection, ODS ingestion, dbt/modeling, metrics, data quality, governance, analytics, SQL execution, dataset pickers, or dts-admin/dts-platform data lake integration.
---

# DTS Default Data Lake Constraint

Use this skill whenever work touches data sources, datasets, modeling, metrics, data quality, governance, or analytics execution. The core invariant is: the default data lake is the only processing and analysis entry point; other registered data sources are external inputs that must be ingested into ODS before downstream analysis.

## Invariant

- `dts-admin` owns data lake configuration and exposes the current default lake to platform via `/api/platform/infra/data-lakes/default`.
- `dts-platform` may mirror or virtualize that default lake, but analysis flows must still resolve to the admin-managed default lake or its local mapped `sourceId`.
- External connectors are source systems to be governed and ingested. They are valid in connector management, metadata sync, source probing, and ingestion reader configuration.
- Downstream analysis starts after landing in ODS in the default lake, then moves through STG/DWD/DWS/ADS, dbt models, metrics, quality, and governance.
- Do not let metrics, data quality, governance execution, dbt/modeling, or analytics pick arbitrary external data sources as compute targets.

## Review Workflow

1. Locate the owning surface: admin data lake config, platform data source list, ingestion, catalog datasets, dbt/modeling, metrics, quality, governance, analytics, or SQL workbench.
2. If a UI selector or API accepts `dataSourceId`, `sourceId`, `datasetId`, `datasource`, or `connection`, classify whether it is source-ingestion only or downstream analysis.
3. For downstream analysis selectors, resolve the default lake first through existing platform APIs such as `/api/ingestion/default-destination` or server-side `DefaultDestinationSyncService`.
4. List datasets only from the default lake mapping, usually `listDatasets({ sourceId: defaultLake.dataSourceId, enabledOnly: true })`.
5. Add a server-side guard for write/execute paths. UI filtering is not enough.
6. Treat fallback heuristics as compatibility only. A fallback to first active JDBC/Postgres source is not permission to analyze an external source.

## Allowed

- Admin screens may create, edit, delete, and set a default data lake.
- Platform foundation screens may show external data sources for registration, connectivity, catalog sync, and governance metadata.
- Ingestion flows may choose external readers, but destination/writer configuration must come from the default data lake unless explicitly handling a repair/precheck path that still writes to default lake ODS.
- Catalog browse may display external assets as items requiring governance, as long as downstream compute actions do not execute against those external connections.

## Forbidden

- Do not populate data quality, metric, semantic, governance, dbt/modeling, or analytics compute selectors from unfiltered `listDatasets()` or `listDataSources()`.
- Do not create or update quality rules/tasks for a dataset whose `sourceId` is not the default lake mapped data source.
- Do not pass arbitrary external `datasourceId` into query execution for analysis paths.
- Do not silently fall back to the first active data source when the default lake is missing. Fail with an actionable default-lake configuration error.
- Do not label external data sources as "data lake" unless they are the configured default lake or its local mirror.

## Code Landmarks

- Admin default lake ownership: `source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/infra/InfraAdminService.java`
- Admin platform API: `source/dts-admin/src/main/java/com/yuzhi/dts/admin/web/rest/platform/PlatformInfraResource.java`
- Platform admin client: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/AdminInfraClient.java`
- Default destination status and local mapping: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/DefaultDestinationSyncService.java`
- Platform data source list merge: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/InfraManagementService.java`
- Ingestion default writer enforcement: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/IngestionTaskProxyResource.java`
- Default-lake dataset enforcement: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/DefaultLakeDatasetGuard.java`
- dbt/modeling source resolution: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java`
- dbt source picker enforcement: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ModelingSqlModelResource.java`
- Generated model source resolution: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelGenerationService.java`
- Quality rules/tasks: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/governance/QualityRuleService.java`, `QualityTaskService.java`
- Dataset selectors: `source/dts-platform-webapp/src/components/catalog/DatasetPicker.tsx`, governance quality pages, metric pages, and analytics data pages.
- Query execution risk points: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/query/HiveQueryGateway.java`, `JdbcQueryGateway.java`, `DataSourceAccessGuard.java`

## Audit Hotspots

Check these patterns before approving a change:

- `listDatasets({ page, size })` in downstream pages without `sourceId: defaultLake.dataSourceId`.
- `DatasetPicker` used in quality, metrics, governance, or analytics without a default-lake filter.
- Backend `validateDataset(...)` methods that only check existence and do not verify default-lake ownership.
- Services that rank data source candidates with `DataSourceScorer` and can select a non-default external source.
- Query gateways that execute by caller-supplied `datasourceId` outside ingestion/source-probe flows.

## Validation

- For frontend selectors, verify the request includes the default lake `sourceId` and external datasets cannot appear.
- For backend mutations or executions, add tests that a non-default dataset/source is rejected and a default-lake dataset is accepted.
- For ingestion, verify external reader plus default-lake destination still works.
- For dbt/modeling, verify generated models retain ODS/STG/DWD/DWS/ADS layering and resolve source IDs to the default lake or ODS datasets derived from it.
