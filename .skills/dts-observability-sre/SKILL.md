---
name: dts-observability-sre
description: Use when designing, debugging, or hardening DTS production stability, health checks, logs, metrics, tracing, diagnostics bundles, SLOs, Airflow/dbt/OpenMetadata/PostgreSQL/Keycloak/MinIO monitoring, incident response, or customer-site support workflows.
---

# DTS Observability And SRE

Use this skill for production stability and incident-oriented work.

## Workflow

1. Load `references/sre-runbook.md`.
2. Define the symptom in operational terms: unavailable service, failed login, failed dbt run, delayed sync, data mismatch, slow query, broken dashboard, or deployment regression.
3. Collect diagnostics before changing state:

```bash
.skills/dts-observability-sre/scripts/collect_diagnostics.sh
```

4. Separate signal categories: service health, logs, database, dbt artifacts, Airflow/OpenMetadata, auth, proxy/TLS, storage, and resource pressure.
5. Prefer permanent observability improvements over one-off log scraping when the same issue can recur.

## Stability Principles

- Every async workflow should expose status, error, retry, and last-updated time.
- Every customer-facing failure path should have a log correlation point.
- Health checks should verify dependencies at the right depth without making the service fragile.
- Dashboards should track product workflows, not only infrastructure metrics.
- Diagnostic bundles must redact secrets.
