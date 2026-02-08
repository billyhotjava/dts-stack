# Sample: CLI Import for External LLM Models

This sample demonstrates how to import externally generated dbt SQL models
into Platform logical modeling by command line.

Usage:
1. Edit `manifest/models.tsv` if needed.
2. Export required env vars:
   - `API_BASE`
   - `TOKEN`
   - `PLAN_ID`
   - `SOURCE_DATA_SOURCE_ID`
   - `ACTIVE_DEPT` (optional)
3. Run (recommended):
   `bin/dts-dbt-import --manifest docs/intergration/etl/sample-cli-model-import/manifest/models.tsv --api-base "$API_BASE" --token "$TOKEN" --plan-id "$PLAN_ID" --source-data-source-id "$SOURCE_DATA_SOURCE_ID"`

Legacy fallback:
- `bash scripts/import_models.sh`

Notes:
- This is API multipart import, not ZIP import.
- Re-running the same manifest may create duplicate model records.
