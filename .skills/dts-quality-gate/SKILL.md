---
name: dts-quality-gate
description: Use before or after DTS code changes to choose and run the correct validation checks for Java services, webapps, dbt models, Docker/build scripts, compose files, API e2e tests, or web e2e tests based on touched modules and risk.
---

# DTS Quality Gate

Use this skill whenever you need to verify a DTS change.

## Workflow

1. Load `references/test-matrix.md`.
2. Inspect changed files with `git status --short` and, when useful, `git diff --name-only`.
3. Map touched paths to the smallest meaningful validation set.
4. Run checks for directly touched modules first.
5. Broaden validation when a shared contract changes: API schema, database schema, permission model, dbt manifest, compose topology, image build, or shared Java code.
6. Report commands run and any checks skipped because dependencies, services, or credentials were unavailable.

## Helper

Use the helper to suggest checks:

```bash
.skills/dts-quality-gate/scripts/changed_module_checks.sh
```

Use `--run` only after reviewing the suggested commands:

```bash
.skills/dts-quality-gate/scripts/changed_module_checks.sh --run
```

## Minimum Standard

- Do not claim validation passed without command output.
- If tests cannot run, state the blocker and remaining risk.
- For UI changes, prefer a build plus screenshot or E2E check when a browser environment is available.
- For dbt changes, at least run static checks; run `dbt parse` when profiles and dependencies are ready.
