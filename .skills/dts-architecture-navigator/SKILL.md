---
name: dts-architecture-navigator
description: Use when working on DTS repository architecture, module ownership, feature placement, cross-module impact, or deciding where code belongs across dts-admin, dts-platform, dts-common, dts-analytics, dbt, webapps, compose, builds, and services.
---

# DTS Architecture Navigator

Use this skill before making changes that may cross module boundaries or when the user asks where a DTS capability should live.

## Workflow

1. Read `AGENTS.md` at the repository root and this skill.
2. Load `references/module-map.md` when you need module ownership or directory responsibilities.
3. Identify the primary ownership boundary before editing:
   - Identity, PKI, Keycloak, users, departments, audit administration, MDM sync: `source/dts-admin`.
   - Data sources, SQL modeling, dbt orchestration, data service APIs, API publishing, platform workbench: `source/dts-platform`.
   - Shared Java libraries used by multiple services: `source/dts-common`.
   - Analytics service and dashboard runtime: `source/dts-analytics` and `source/dts-analytics-webapp/modern`.
   - dbt project, models, macros, profiles, generated manifest: `services/dts-dbt`.
   - Packaging, image builds, compose topology, mounted service assets: root `builds/`, `services/`, and `docker-compose*.yml`.
4. Inspect the current implementation in the owning module before introducing a new pattern.
5. For cross-module work, explicitly list the contracts being changed: REST API, database table, dbt model, generated file, compose service, route, menu, permission, or env key.
6. Validate only the modules you touched unless the contract crosses modules.

## Placement Rules

- Prefer keeping domain logic in the owning service rather than moving it into `dts-common`.
- Use `dts-common` only for stable shared types, clients, utilities, or configuration that already has more than one consumer.
- Keep dbt model files and platform database records in sync when touching logical modeling.
- Keep web routing, menus, and permission checks aligned with backend authorization.
- Treat compose/build changes as product delivery changes, not local-only tweaks.

## Exit Checklist

- The owner module is clear.
- Any cross-module contract has a verification step.
- No unrelated module style or dependency churn was introduced.
- Operational effects are documented when env, compose, images, ports, certificates, or mounted data paths change.
