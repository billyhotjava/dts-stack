# dbt Layering Rules

## Paths

- Project root: `services/dts-dbt`
- Models: `services/dts-dbt/models`
- Macros: `services/dts-dbt/macros`
- Profiles: `services/dts-dbt/profiles`
- Generated target: `services/dts-dbt/target`

## Layer Semantics

- ODS: source-facing raw or lightly normalized input. Preserve source traceability.
- DWD: detail-wide layer. Clean types, normalize dates/numbers, preserve business keys.
- DWS: subject/service aggregate layer. Use stable grain and clear dimensions.
- ADS: application/dashboard/API-facing output. Optimize for consumption and published metrics.

## Naming

- Auto-generated models: `dwd_*`, `dws_*`, `ads_*`.
- External business models: `biz_dwd_*`, `biz_dws_*`, `biz_ads_*`.
- Source YAML files should stay close to related source/layer files.
- Model names must be valid dbt identifiers and should also satisfy platform import naming rules: `^[A-Za-z][A-Za-z0-9_]*$`.

## SQL Conventions

- Use repository macros for placeholder cleanup, numeric parsing, date parsing, schema naming, and relation truncation.
- Avoid hard-coded schemas unless the existing project pattern requires it.
- Use explicit casts for fields consumed by metrics or dashboards.
- For incremental or destructive operations, confirm the platform release/run semantics first.
- Document unresolved source gaps in model descriptions or issue notes instead of hiding them in SQL comments only.

## YAML And Metadata

Each production model should have, where practical:

- Description.
- Owner or responsible domain.
- Tags for layer and business area.
- Column names, descriptions, and tests for business keys and required fields.
- Accepted values tests for important status fields.

## Manifest And Lineage

- `target/manifest.json` and `target/catalog.json` are generated outputs.
- Platform lineage and file browser features depend on dbt parse/run artifacts.
- If code changes rely on manifest shape, test both a missing-target and present-target scenario.
