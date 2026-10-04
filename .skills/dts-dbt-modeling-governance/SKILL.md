---
name: dts-dbt-modeling-governance
description: Use when creating, reviewing, importing, validating, or publishing DTS dbt models, SQL modeling records, ODS/DWD/DWS/ADS layers, metrics SQL, dbt macros, lineage, manifest synchronization, or external LLM model imports.
---

# DTS dbt Modeling Governance

Use this skill for DTS data modeling work. The goal is to keep dbt files, platform model records, lineage, and published metrics consistent.

## Workflow

1. Load `references/dbt-layering.md`.
2. If the task involves offline or external SQL import, load `references/model-import.md`.
3. Inspect current files under `services/dts-dbt` and related platform code before editing.
4. Confirm whether the change is file-only dbt work or must also update `modeling_sql_model` through platform APIs/code.
5. Keep naming, schema YAML, tags, and materialization consistent with the target layer.
6. Validate with `scripts/dbt_guard.sh`; add `--parse` only when the local dbt environment is ready.

## Hard Rules

- External LLM/business models use `biz_dwd_`, `biz_dws_`, or `biz_ads_` prefixes.
- Auto-generated models keep `dwd_`, `dws_`, or `ads_` prefixes.
- External custom models live under `models/custom/...` when possible.
- Do not silently change generated `target/` artifacts by hand.
- When a model should appear in the logical modeling UI, use the platform import path instead of only writing a `.sql` file.
- Treat metrics SQL as a contract: define parameter names, grain, dimensions, null handling, and source-layer assumptions.

## Validation

Run the smallest meaningful check:

```bash
.skills/dts-dbt-modeling-governance/scripts/dbt_guard.sh
```

When dbt is installed and profiles are usable:

```bash
.skills/dts-dbt-modeling-governance/scripts/dbt_guard.sh --parse
```

For containerized validation, prefer the repository-approved dbt container workflow when available.
