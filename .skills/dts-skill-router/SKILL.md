---
name: dts-skill-router
description: Use when starting DTS repository work, when unsure which DTS skill applies, or when routing a DTS request across architecture, dbt modeling, security, DevOps, release, quality, frontend, and observability skills without loading all detailed references.
---

# DTS Skill Router

Use this skill first when the request is about DTS but the correct specialist skill is not obvious.

## Routing Workflow

1. Classify the task by primary intent, not by every file mentioned.
2. Select one primary specialist skill.
3. Add one secondary skill only when it changes how the work should be done.
4. Read only the selected skills and their conditional references.
5. Finish with `dts-quality-gate` when code, config, dbt, or scripts changed.

## Skill Selection

- Repository ownership, module placement, cross-module impact:
  - Primary: `dts-architecture-navigator`
- dbt, ODS/DWD/DWS/ADS, metrics SQL, model import, lineage, manifest:
  - Primary: `dts-dbt-modeling-governance`
  - Secondary: `dts-architecture-navigator` when platform records or APIs are involved.
- PKI, USBKey, Keycloak, users, roles, departments, audit, IP whitelist, secrets:
  - Primary: `dts-security-compliance`
  - Secondary: `dts-architecture-navigator` for ownership decisions.
- Containers, local startup, logs, compose, runtime environment, cert/domain issues:
  - Primary: `dts-devops-runbook`
- Offline package, customer upgrade, rollback, image versions, Kylin/Kunpeng/ARM:
  - Primary: `dts-release-offline-upgrade`
  - Secondary: `dts-devops-runbook` for live diagnostics.
- Frontend pages, menus, routes, permissions UI, SQL IDE, dashboards:
  - Primary: `dts-frontend-product-consistency`
  - Secondary: `dts-security-compliance` when auth visibility or permissions are touched.
- Production stability, incidents, diagnostics, SLOs, health checks, monitoring:
  - Primary: `dts-observability-sre`
  - Secondary: `dts-devops-runbook` for container-level triage.
- Verification after any change:
  - Primary: `dts-quality-gate`

## Common Combinations

- Logical modeling failure: `dts-architecture-navigator` + `dts-dbt-modeling-governance` + `dts-quality-gate`
- Keycloak `dept_code` issue: `dts-security-compliance` + `dts-devops-runbook` + `dts-quality-gate`
- Customer offline upgrade failure: `dts-release-offline-upgrade` + `dts-devops-runbook` + `dts-observability-sre`
- SQL IDE UI change: `dts-frontend-product-consistency` + `dts-quality-gate`
- API publish permission bug: `dts-architecture-navigator` + `dts-security-compliance` + `dts-quality-gate`

## Avoid

- Do not load all DTS skills by default.
- Do not treat this router as a replacement for the specialist skill.
- Do not skip repository inspection; skills guide the workflow, they do not replace reading code.
