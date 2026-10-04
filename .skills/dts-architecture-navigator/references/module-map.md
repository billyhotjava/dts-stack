# DTS Module Map

## Root

- `AGENTS.md`: repository operating rules.
- `docker-compose-app.yml`: self-contained x86 deployment stack.
- `docker-compose.dev.yml`: self-contained local development stack with source mounts.
- `docker-compose.legacy.yml`: self-contained ARM/Kylin legacy deployment stack.
- `builds/`: Dockerfiles and product image build scripts.
- `services/`: runtime assets, certificates, dbt workspace, drivers, Airflow DAGs, OpenMetadata ingestion, Keycloak realm.
- `docs/`: architecture, implementation, integration, release, and plan documents.
- `tests/`: API and web end-to-end tests.

## Java Services

- `source/dts-admin`
  - User, organization, role, permission, Keycloak integration.
  - PKI and USBKey integration.
  - Admin audit, IP whitelist, MDM sync, security administration.
  - Typical validation: `npm run backend:unit:test`.
- `source/dts-platform`
  - Data source management, SQL modeling, dbt workflow, API publishing, platform workbench.
  - Data-service side access control and audit hooks.
  - Typical validation: `npm run backend:unit:test`.
- `source/dts-common`
  - Shared Java code that is intentionally stable across services.
  - Avoid placing service-specific domain workflows here.
- `source/dts-ingestion`
  - Ingestion-related Java service code.
- `source/dts-analytics`
  - Analytics backend service.

## Webapps

- `source/dts-admin-webapp`: admin console.
- `source/dts-platform-webapp`: platform console, modeling UI, workbench, SQL IDE.
- `source/dts-analytics-webapp/modern`: modern analytics frontend.

## Data And Runtime Assets

- `services/dts-dbt`
  - dbt project, `models/`, `macros/`, `profiles/`, and `target/`.
  - Generated artifacts such as `manifest.json`, `catalog.json`, and `run_results.json`.
- `services/dts-airflow`
  - DAG JSON and generated DAG Python files.
- `services/dts-openmetadata/ingestion`
  - OpenMetadata ingestion configs and runners.
- `services/dts-platform/drivers`
  - JDBC drivers used by the platform service.
- `services/dts-admin/vendor`
  - Vendor jars for security and legacy integration.

## Architecture Documents Worth Checking

- `docs/implementation/dts-architecture-2025-11-16.md`
- `docs/implementation/audit-architecture.md`
- `docs/implementation/governance/dg.md`
- `docs/implementation/governance/metrics.md`
- `docs/intergration/etl/external-llm-cli-import-spec.md`
- `docs/intergration/pki/pki-crypto-modes.md`
- `docs/release/v2.2.2/offline-upgrade-guide-kylin-kunpeng.md`
