DBT quality ingestion

This ingestion job pushes dbt test results into the metadata service so the platform can display quality status.

Prerequisites
- Put your dbt project under services/dts-dbt (dbt_project.yml, models, etc.).
- Ensure profiles.yml exists under services/dts-dbt/profiles.

Run dbt test (inside the dbt container)
- docker compose run --rm dts-dbt dbt deps --profiles-dir /root/.dbt
- docker compose run --rm dts-dbt dbt test --profiles-dir /root/.dbt
- docker compose run --rm dts-dbt dbt docs generate --profiles-dir /root/.dbt

Ingest results
- docker compose run --rm dts-openmetadata-ingestion

Postgres metadata ingest (optional, manual)
- docker compose run --rm dts-openmetadata-ingestion /bin/sh -lc /opt/openmetadata/ingestion/run-postgres-ingestion.sh

If OpenMetadata auth is enabled, set DTS_PLATFORM_OPENMETADATA_AUTH_TOKEN in .env first.
If you want to force no-auth ingest in dev, set OPENMETADATA_ALLOW_NO_AUTH=true for the run.

Artifacts expected under services/dts-dbt/target:
- manifest.json
- run_results.json
- catalog.json
