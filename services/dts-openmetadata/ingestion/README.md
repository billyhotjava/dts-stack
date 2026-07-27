DBT quality ingestion

This ingestion job pushes dbt test results into the metadata service so the platform can display quality status.

Prerequisites
- Put your dbt project under services/dts-dbt (dbt_project.yml, models, etc.).
- Canonical model materialization obtains a one-run profile from the platform's
  tmpfs lease and does not use the shared profiles directory.
- For manual local dbt/OpenMetadata commands only, copy
  `services/dts-dbt/profiles/profiles.example.yml` to the ignored
  `services/dts-dbt/profiles/profiles.yml` and inject the documented
  `DTS_DBT_DEV_*` environment variables. Never package or commit that local
  file.

Run dbt test (inside the dbt container)
- docker compose run --rm dts-dbt dbt deps --profiles-dir /root/.dbt
- docker compose run --rm dts-dbt dbt test --profiles-dir /root/.dbt
- docker compose run --rm dts-dbt dbt docs generate --profiles-dir /root/.dbt

Ingest results
- docker compose run --rm dts-openmetadata-ingestion

Postgres metadata ingest (optional, manual)
- docker compose run --rm --entrypoint /bin/sh dts-openmetadata-ingestion -c /opt/openmetadata/ingestion/run-postgres-ingestion.sh

Postgres metadata ingest scope
- PostgreSQL ingestion is only allowed for warehouse/analytics-owned databases.
- Do not ingest platform business/internal databases such as dts_platform, dts_admin, dts_common, dts_analytics, keycloak, OpenMetadata, Airflow, or Ranger databases.
- Set DTS_OPENMETADATA_INGEST_DATABASE to the warehouse database to scan. The default generated value is biadmin.
- DTS_OPENMETADATA_FORBIDDEN_DATABASES is a comma-separated deny list. If DTS_OPENMETADATA_INGEST_DATABASE matches it, the script exits with code 2 before authentication or ingestion starts.
- dbt artifacts are checked with the same deny list. If manifest.json or catalog.json references a forbidden database, dbt ingestion exits with code 2 before sending metadata to OpenMetadata.

If OpenMetadata auth is enabled, set DTS_PLATFORM_OPENMETADATA_AUTH_TOKEN in .env first.
If the deployment is explicitly no-auth, set DTS_OPENMETADATA_ALLOW_NO_AUTH=true in .env or OPENMETADATA_ALLOW_NO_AUTH=true for a one-off run. OpenMetadata ingestion 1.11.x still expects `authProvider: openmetadata`, so no-auth mode writes an empty JWT and only works when the OpenMetadata server accepts unauthenticated requests. A missing token without no-auth now fails fast instead of silently skipping ingestion.

Artifacts expected under services/dts-dbt/target:
- manifest.json
- run_results.json
- catalog.json
