# Ingestion Task JSON (v1) Proposal

This document defines the recommended "ingestion task" JSON contract for the new `dts-ingestion-service`.
It hides Airbyte/OpenMetadata terminology while preserving Airbyte capabilities.

## Goals
- Stable business API (no Airbyte/OpenMetadata terms exposed).
- Express EL configuration (source/target/sync/schedule/namespace/stream selection).
- Allow safe secrets injection and future governance/lineage hooks.

## Top-Level Structure
```json
{
  "name": "ERP-ODS Sync",
  "owner": "admin",
  "description": "ERP schema erp_demo to ODS",
  "source": {
    "type": "postgres",
    "definitionId": "airbyte-source-def-id",
    "existingSourceId": null,
    "config": {
      "host": "10.0.0.10",
      "port": 5432,
      "database": "erpdb",
      "username": "erp",
      "password": "****",
      "schemas": ["erp_demo"],
      "ssl": false
    }
  },
  "destination": {
    "usePlatformDefault": true,
    "definitionId": null,
    "existingDestinationId": null,
    "config": {}
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
    "prefix": "ods_",
    "schemaChanges": {
      "mode": "notify"
    }
  },
  "streams": {
    "selection": "all",
    "include": [],
    "exclude": []
  },
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
- `type`: business-visible source type key (mapped to Airbyte source definition).
- `definitionId`: optional explicit Airbyte source definition id (if already known).
- `existingSourceId`: if set, reuse existing Airbyte source.
- `config`: source connector configuration (user-friendly fields).

### 2) destination
- `usePlatformDefault`: if true, use platform configured destination.
- `definitionId`: optional explicit destination definition id.
- `existingDestinationId`: reuse existing destination if set.
- `config`: destination configuration overrides (if not using default).

### 3) sync
Maps to Airbyte connection properties.
- `mode`: `full_refresh` | `incremental`.
- `destinationMode`: `append` | `overwrite` | `append_dedup` (Airbyte destination sync mode).
- `schedule.type`: `manual` | `cron` | `interval`.
- `schedule.cron`: cron expression when `type=cron`.
- `schedule.intervalMinutes`: interval when `type=interval`.
- `namespace.definition`: `source` | `destination` (Airbyte namespace definition).
- `namespace.format`: format string if namespace is customized.
- `prefix`: table prefix for destination.
- `schemaChanges.mode`: `notify` | `ignore` | `propagate` (mapped to Airbyte schema change handling).

### 4) streams
Controls stream selection and per-stream overrides.
- `selection`: `all` | `include` | `exclude`.
- `include`: list of stream names (when selection=include).
- `exclude`: list of stream names (when selection=exclude).
- Per-stream overrides can be added later as:
  - `streams.overrides[{stream, syncMode, destinationMode, cursorField, primaryKey}]`.

### 5) lineage (OpenMetadata)
High-level lineage hints for OpenMetadata registration.
- `enabled`: whether to register lineage.
- `domain`: business domain name.
- `tags`: list of business tags.
- `owner`: business owner.

### 6) OpenMetadata ingestion (auto)
The ingestion service will optionally create/ensure a database service and an ingestion pipeline for the
destination database, then trigger ingestion when `runNow=true`.
Configuration is read from `dts.openmetadata.*` (service name/type, destination db/schema) and from the
Airbyte destination config.

### 7) airflow
Airflow orchestration configuration.
- `enabled`: whether to create/attach DAG.
- `dagId`: stable DAG id.
- `scheduleType`: `cron` | `manual` | `interval`.
- `cron` / `intervalMinutes`.

## Mapping Notes
- `source.type` is mapped by the ingestion service to Airbyte source definitions.
- `sync.mode` + `destinationMode` are translated to Airbyte connection `syncCatalog`.
- `schemaChanges.mode` maps to Airbyte's schema change handling (notify/propagate).
- `lineage` and `airflow` are optional and can be ignored in MVP.

## MVP Coverage
For MVP, the ingestion service should:
1) Create/reuse Airbyte source + destination.
2) Build Airbyte connection (catalog + schedule).
3) Trigger sync and return job id.
4) Optionally register OpenMetadata lineage when service names + FQNs can be resolved.
5) Optionally trigger Airflow DAG when configured.

## API Endpoint (MVP)
- `POST /api/ingestion/tasks` with the JSON payload above.
