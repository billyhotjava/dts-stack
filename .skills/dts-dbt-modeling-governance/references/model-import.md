# External SQL Model Import

Use this reference when importing SQL generated outside the production environment.

## Preferred Path

Use the platform API import path when the model must appear in logical modeling:

- Endpoint: `POST /api/modeling/sql-models/import`
- Required fields: `planId`, `name`, `layer`, `sourceDataSourceId`, `sql`
- Common optional fields: `alias`, `schemaName`, `materialized`, `tags`, `description`, `enabled`, `status`, `ownerDept`, `csv`
- Auth headers: `Authorization: Bearer <token>` and optional `X-Active-Dept`

## File-Only Path

Writing only to `services/dts-dbt/models` may be acceptable for temporary troubleshooting, but the model will not automatically appear in the logical modeling list.

## Isolation Rules

- External model prefixes: `biz_dwd_`, `biz_dws_`, `biz_ads_`.
- Auto-generated model prefixes: `dwd_`, `dws_`, `ads_`.
- Prefer custom external paths such as `models/custom/...`.
- Import is create-oriented; repeated imports may create duplicate platform records.

## Pre-Import Checklist

- Model names are unique inside the target project space.
- The selected layer matches the model prefix.
- `sourceDataSourceId` is a valid UUID.
- SQL has no environment-only schemas, temp paths, or personal test filters.
- Any sample CSV columns match SQL output columns.

## Source Document

The repository source of truth is `docs/intergration/etl/external-llm-cli-import-spec.md`.
