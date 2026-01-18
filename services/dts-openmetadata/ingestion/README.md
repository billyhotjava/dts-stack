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

Artifacts expected under services/dts-dbt/target:
- manifest.json
- run_results.json
- catalog.json
