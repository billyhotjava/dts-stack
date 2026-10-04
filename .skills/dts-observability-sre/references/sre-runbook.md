# DTS SRE Runbook

## Core Signals

- Service availability and restart count.
- API error rate and latency.
- Login/SSO failure rate.
- dbt parse/run duration and failure count.
- Airflow DAG generation/import/execution failures.
- OpenMetadata ingestion failures.
- PostgreSQL connection pool, slow queries, and disk usage.
- MinIO/upload storage availability.
- Audit queue depth and forward retry count.
- Data source connection failures.

## Workflow-Level Health

Track these product workflows:

- User login and department claim propagation.
- Personnel/department master-data sync.
- Data source creation and connection test.
- ODS import and generated model sync.
- SQL model import, run, release, and rollback.
- API publish and consumer invocation.
- Dashboard load and key metric freshness.
- Audit search and export.

## Incident Triage

1. Capture time window, actor, affected module, request id or job id, and customer-visible symptom.
2. Check service status and recent restarts.
3. Inspect the owning service logs.
4. Check dependent systems: PostgreSQL, Keycloak, dbt, Airflow, OpenMetadata, MinIO, proxy/TLS.
5. Reproduce with the smallest safe request.
6. Preserve evidence before restarting or migrating.

## Diagnostic Redaction

Redact:

- Passwords, tokens, private keys, certificates, cookies, Authorization headers.
- Customer personal data unless required for the incident and approved.
- Database connection strings with credentials.

## SLO Candidates

- Login success latency.
- Platform API p95 latency.
- dbt model run success rate.
- Master-data sync freshness.
- Dashboard freshness and load time.
- Audit event persistence delay.
