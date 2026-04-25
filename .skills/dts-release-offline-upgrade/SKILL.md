---
name: dts-release-offline-upgrade
description: Use when preparing, validating, documenting, or troubleshooting DTS offline releases, customer-site upgrades, rollback plans, image builds, Kylin/Kunpeng/ARM compatibility, Maven prebuilds, dbt image options, image version files, and deployment handoff packages.
---

# DTS Release And Offline Upgrade

Use this skill for release packaging, offline deployment, and customer-site upgrade work.

## Workflow

1. Load `references/offline-upgrade.md`.
2. Determine whether the task is build preparation, package validation, upgrade execution, rollback, or post-upgrade verification.
3. Run preflight checks:

```bash
.skills/dts-release-offline-upgrade/scripts/release_preflight.sh
```

4. Keep image tags, compose files, env files, database migrations, dbt assets, Keycloak realm changes, and certificates in one release view.
5. Document customer-site operations separately from developer-only commands.

## Guardrails

- Never assume internet access at the customer site.
- Prefer reproducible build inputs and explicit image version files.
- Back up PostgreSQL, Keycloak state, MinIO/uploads, dbt project files, and customer configuration before upgrade.
- Validate rollback before changing persistent state.
- For ARM/Kunpeng/Kylin, verify base images and native dependencies early.
