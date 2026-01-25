# OpenMetadata adapter improvements (2026-01-21)

## Goal
Make OpenMetadata metadata collection more reliable in the ingestion flow, while keeping the business API stable.

## What changed
- Added OpenMetadata ingestion support in `dts-ingestion`:
  - Create/ensure database service in OpenMetadata based on the destination config.
  - Create/ensure an ingestion pipeline for that service.
  - Trigger ingestion when `runNow=true`.
- Lineage and ingestion are now both returned by the ingestion task response.

## Key files
- OpenMetadata config + adapter:
  - `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/config/OpenMetadataProperties.java`
  - `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/openmetadata/OpenMetadataClient.java`
  - `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/openmetadata/OpenMetadataAdapter.java`
- Ingestion task integration:
  - `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/web/rest/IngestionTaskResource.java`
- Config wiring:
  - `source/dts-ingestion/src/main/resources/application.yml`
  - `docker-compose-app.yml`, `docker-compose.dev.yml`, `docker-compose.legacy.yml`

## Behavior summary
- If OpenMetadata is enabled and destination config is complete, the adapter will:
  1) Look up (or create) a database service.
  2) Look up (or create) an ingestion pipeline.
  3) Trigger pipeline when `runNow=true`.
- If required fields are missing, it returns `skipped` without failing the task.

## New env vars
These are optional but recommended for stable ingestion:
- `DTS_OPENMETADATA_DESTINATION_SERVICE_NAME`
- `DTS_OPENMETADATA_DESTINATION_SERVICE_TYPE` (default: `Postgres`)
- `DTS_OPENMETADATA_INGESTION_ENABLED` (default: `true`)
- `DTS_OPENMETADATA_INGESTION_PREFIX` (default: `dts_ingest`)
- `DTS_OPENMETADATA_INGESTION_SCHEDULE` (default: `0 * * * *`)

## Notes / assumptions
- The destination connection uses the destination (writer) config. If host/port/database/username are missing, ingestion is skipped.
- The OpenMetadata service must already be reachable, and the auth token (if required) must be configured.

## Next validation steps
1) Create an ingestion task with `runNow=true`.
2) Verify the response includes `openmetadataIngestion.status=triggered`.
3) In OpenMetadata, confirm the database service and ingestion pipeline are present.
4) Wait for ingestion to finish and check metadata in the asset portal.
