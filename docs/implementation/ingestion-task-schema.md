# Ingestion Task JSON (v1) Proposal

This document defines the recommended "ingestion task" JSON contract for `dts-ingestion`.
It hides Addax/OpenMetadata terminology while preserving Addax capabilities.

## Goals
- Stable business API (no Addax/OpenMetadata terms exposed).
- Express ETL configuration (source/target/sync/schedule/namespace/stream selection).
- Allow safe secrets injection and future governance/lineage hooks.

## Top-Level Structure
```json
{
  "name": "ERP-ODS Sync",
  "owner": "admin",
  "description": "ERP schema erp_demo to ODS",
  "source": {
    "type": "postgresqlreader",
    "definitionId": null,
    "existingSourceId": null,
    "config": {
      "username": "erp",
      "password": "****",
      "column": ["*"],
      "splitPk": "id",
      "connection": [
        {
          "jdbcUrl": ["jdbc:postgresql://10.0.0.10:5432/erpdb"],
          "table": ["erp_demo.order"]
        }
      ]
    }
  },
  "destination": {
    "usePlatformDefault": true,
    "type": "postgresqlwriter",
    "definitionId": null,
    "existingDestinationId": null,
    "config": {
      "username": "ods",
      "password": "****",
      "column": ["*"],
      "preSql": [],
      "postSql": [],
      "connection": [
        {
          "jdbcUrl": "jdbc:postgresql://10.0.0.20:5432/ods",
          "table": ["ods_order"]
        }
      ]
    }
  },
  "sync": {
    "mode": "incremental",
    "destinationMode": "append",
    "schedule": {
      "type": "cron",
      "cron": "0 * * * *"
    },
    "namespace": {
      "definition": "destination",
      "format": "ods_${source}"
    },
    "prefix": "ods_"
  },
  "streams": {
    "selection": "all",
    "include": [],
    "exclude": []
  },
  "schemaChanges": {
    "mode": "notify"
  },
  "jobConfig": null,
  "lineage": {
    "enabled": true,
    "domain": "财务域",
    "tags": ["erp", "ods"],
    "owner": "admin"
  },
  "airflow": {
    "enabled": true,
    "dagId": "ingest_erp_ods",
    "scheduleType": "cron",
    "cron": "0 * * * *"
  }
}
```

## Field Definitions

### 1) source
- `type`: Addax Reader 插件名称（也可通过 `config.readerType` / `config.reader` / `config.type` 提供）。
- `definitionId`: 预留字段，当前 Addax 流程不使用。
- `existingSourceId`: 预留字段，当前 Addax 流程不使用。
- `config`: Addax Reader `parameter` 内容（会写入作业 JSON）。

### 2) destination
- `usePlatformDefault`: if true, use platform configured destination as defaults.
- `type`: Addax Writer 插件名称（也可通过 `config.writerType` / `config.writer` / `config.type` 提供）。
- `definitionId`: 预留字段，当前 Addax 流程不使用。
- `existingDestinationId`: 预留字段，当前 Addax 流程不使用。
- `config`: Addax Writer `parameter` 内容（会写入作业 JSON）。

### 3) sync
高层同步配置（用于命名、前缀和元数据注册）。
- `mode`: `full_refresh` | `incremental`（预留字段，当前 Addax 不使用）。
- `destinationMode`: `append` | `overwrite` | `append_dedup`（预留字段）。
- `schedule.type`: `manual` | `cron` | `interval`。
- `schedule.cron`: cron expression when `type=cron`.
- `schedule.intervalMinutes`: interval when `type=interval`.
- `namespace.definition`: `source` | `destination`（用于元数据命名）。
- `namespace.format`: format string if namespace is customized.
- `prefix`: table prefix for destination.

### 4) streams
Controls stream selection and per-stream overrides.
- `selection`: `all` | `include` | `exclude`.
- `include`: list of stream names (when selection=include).
- `exclude`: list of stream names (when selection=exclude).
- Per-stream overrides can be added later as:
  - `streams.overrides[{stream, syncMode, destinationMode, cursorField, primaryKey}]`.

### 5) schemaChanges
预留字段，用于定义源端 Schema 变化策略。
- `mode`: `notify` | `ignore` | `propagate`.

### 6) jobConfig
可选的完整 Addax 作业 JSON。提供后将直接使用该配置并忽略 reader/writer 拼装逻辑。

### 7) lineage (OpenMetadata)
High-level lineage hints for OpenMetadata registration.
- `enabled`: whether to register lineage.
- `domain`: business domain name.
- `tags`: list of business tags.
- `owner`: business owner.

### 8) OpenMetadata ingestion (auto)
The ingestion service will optionally create/ensure a database service and an ingestion pipeline for the
destination database, then trigger ingestion when `runNow=true`.
Configuration is read from `dts.openmetadata.*` (service name/type, destination db/schema) and from the
destination config.

### 9) airflow
Airflow orchestration configuration.
- `enabled`: whether to create/attach DAG.
- `dagId`: stable DAG id.
- `scheduleType`: `cron` | `manual` | `interval`.
- `cron` / `intervalMinutes`.

## Mapping Notes
- `source.type` / `destination.type` are mapped to Addax Reader/Writer plugins.
- If `jobConfig` is provided, it is used as-is and overrides reader/writer assembly.
- `lineage` and `airflow` are optional and can be ignored in MVP.

## MVP Coverage
For MVP, the ingestion service should:
1) Build Addax job JSON (or use `jobConfig` as-is).
2) Trigger Addax job via Airflow (optional).
3) Return job path + metadata.
4) Optionally register OpenMetadata lineage when service names + FQNs can be resolved.
5) Optionally trigger Airflow DAG when configured.

## API Endpoint (MVP)
- `POST /api/ingestion/tasks` with the JSON payload above.
