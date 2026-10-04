---
name: dts-devops-runbook
description: Use when starting, stopping, diagnosing, or repairing DTS local/dev/deploy environments, Docker Compose services, certificates, domains, ports, logs, Maven/container build failures, dbt runtime issues, Keycloak, PostgreSQL, Traefik, or service mount problems.
---

# DTS DevOps Runbook

Use this skill for operational tasks in the DTS workspace.

## Workflow

1. Load `references/container-troubleshooting.md`.
2. Identify the mode: local dev, deploy mode, image build, database/dbt runtime, auth/SSO, proxy/TLS, or offline customer environment.
3. Prefer read-only diagnostics before changing state.
4. Use repository scripts where possible:
   - `./init.sh single 'Strong@2025!' dts.local`
   - `./dev-up.sh --mode local`
   - `./dev-stop.sh --mode local`
   - `docker compose -f docker-compose-app.yml up -d`
5. When a command fails due to sandbox, permissions, or network, request escalation instead of inventing an alternate write path.
6. Record any operationally relevant change: env key, port, domain, cert, image tag, mount path, or persistent data directory.

## Diagnostics

Start with:

```bash
.skills/dts-devops-runbook/scripts/dts_healthcheck.sh
```

This script is intentionally read-only except for normal command output.

## Guardrails

- Do not delete persistent data directories unless the user explicitly asks.
- Treat `services/dts-pg/data`, Keycloak realm data, MinIO data, dbt project files, and uploaded platform assets as customer state.
- If a container is unhealthy, inspect logs and config before rebuilding.
- If domains change, rerun initialization and verify certificates/trusted proxies.
