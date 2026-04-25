# DTS Test Matrix

## Java Services

`source/dts-admin`

- Primary: `npm run backend:unit:test`
- Compile fallback when tests are too heavy: `./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -DskipTests compile`

`source/dts-platform`

- Primary: `npm run backend:unit:test`
- Compile fallback: `./mvnw -q -Dmaven.repo.local=/tmp/codex-m2 -DskipTests compile`

`source/dts-common`

- Primary: `npm run backend:unit:test`
- Broaden to dependent services if shared APIs change.

`source/dts-analytics`, `source/dts-ingestion`

- Use module Maven tests or compile based on local `pom.xml` and existing scripts.

## Webapps

`source/dts-admin-webapp`

- `pnpm build`
- Use route-level browser checks for UI behavior.

`source/dts-platform-webapp`

- `pnpm build`
- Use Playwright or browser screenshots for modeling, SQL IDE, workbench, and permission-sensitive UI changes.

`source/dts-analytics-webapp/modern`

- `pnpm typecheck`
- `pnpm build`

## dbt

`services/dts-dbt`

- Static: `.skills/dts-dbt-modeling-governance/scripts/dbt_guard.sh`
- Parse: `.skills/dts-dbt-modeling-governance/scripts/dbt_guard.sh --parse`
- Container parse/run may be preferred in customer-like environments.

## Compose, Build, And Runtime

Root compose files, `builds/`, `services/`

- `docker compose -f docker-compose.yml -f docker-compose-app.yml config`
- Targeted `docker build` for changed image definitions.
- `.skills/dts-devops-runbook/scripts/dts_healthcheck.sh` for environment sanity.

## End-To-End

`tests/api-e2e-java`

- Use when backend contracts, auth, imports, or platform APIs change.

`tests/web-e2e`

- Use when routes, menus, workflows, or cross-webapp behavior changes.
