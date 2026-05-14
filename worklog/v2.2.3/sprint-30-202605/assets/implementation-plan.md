# CSV Training Snapshot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the first formal metro small-model integration using CSV training snapshot packages instead of demo contracts or Parquet.

**Architecture:** DTS remains the source of truth for ingestion, governance, dbt modeling, quality checks, and lineage. DTS exports a bounded snapshot package with JSON contract files plus `data.csv`; metro-stack validates that package and runs training against the governed CSV snapshot. Parquet is intentionally out of scope for this sprint.

**Tech Stack:** Spring Boot 3 / Java 21, PostgreSQL, dbt-postgres, React + Vite + Ant Design, FastAPI/Python in metro-stack, CSV snapshot files.

---

## File Structure

### DTS / dbt

- Modify `worklog/v2.2.3/thales/v1/dbt_model/**`: make the package authoritative for CSV snapshot fields and contract models.
- Modify `source/dts-platform/src/main/java/com/yuzhi/dts/platform/**`: add export API and service.
- Modify `source/dts-platform/src/main/resources/config/application*.yml`: add snapshot root config.
- Modify `source/dts-platform-webapp/**`: add export entry only after backend API is stable.

### metro-stack

- Modify `/opt/prod/metro-app/sources/metro-stack/backend/src/metro_pack_service/contract_validation.py`: CSV-first contract validation.
- Modify `/opt/prod/metro-app/sources/metro-stack/backend/src/metro_pack_service/batch_runner.py`: source type `training_snapshot`.
- Modify `/opt/prod/metro-app/sources/metro-stack/backend/src/metro_pack_service/main.py`: `/api/batches/from-training-snapshot`.
- Modify `/opt/prod/metro-app/sources/metro-stack/frontend/src/api.ts` and `PackDashboard.tsx`: real snapshot import and display.

## Task Order

### Task 1: Contract Tests First

- [x] Add/adjust metro-stack tests so `data_format=csv` passes and missing `data.csv` blocks.
- [x] Run:

```bash
cd /opt/prod/metro-app/sources/metro-stack
PYTHONPATH=backend/src .venv/bin/python -m unittest discover -s backend/tests -p 'test_contract_*.py'
```

- [x] Expected before implementation: failing tests for CSV support.

### Task 2: metro-stack CSV Contract Validation

- [x] Update `contract_validation.py` to accept `csv`, require `data_uri`/`csv_uri`, and compare schema features with `data.csv` headers.
- [x] Keep `demo_training_contract()` available behind explicit demo endpoint only.
- [x] Re-run metro-stack contract tests.

### Task 3: thales dbt Package

- [x] Update `worklog/v2.2.3/thales/v1/dbt_model` contract metadata to emit CSV snapshot semantics.
- [x] Rebuild `thales-metro-dbt-model.zip`.
- [x] Run `dbt parse` and `unzip -t`.

### Task 4: DTS Export API Tests

- [ ] Add Spring Boot tests for request validation and output path safety.
- [ ] Test names should cover:
  - successful CSV export request;
  - unsupported `format=parquet`;
  - model name missing;
  - output path traversal rejection.

### Task 5: DTS Export Service

- [ ] Add `TrainingSnapshotExportService`.
- [ ] Stream `data.csv` from query results.
- [ ] Write `manifest.json`, `schema.json`, `quality_report.json`, and `lineage.json`.
- [ ] Add config for snapshot root and max rows.

### Task 6: metro-stack Training Snapshot Batch

- [ ] Add `/api/batches/from-training-snapshot`.
- [ ] Persist `source_type=training_snapshot`, `snapshot_dir`, `snapshot_id`, and `data_csv`.
- [ ] Make recompute use snapshot `data.csv` rather than raw upload CSV.

### Task 7: Frontend Real Snapshot Flow

- [ ] DTS UI: expose snapshot export action and output directory.
- [ ] metro-stack UI: add snapshot directory input, validate action, and create training task action.
- [ ] Remove default demo data from `数据与特征`; leave demo as explicit fallback.

### Task 8: End-to-End IT

- [ ] Use one operator CSV sample.
- [ ] Run DTS upload/precheck, dbt parse/run, snapshot export, metro validate, metro train.
- [ ] Save evidence under `worklog/v2.2.3/sprint-30-202605/it/evidence/`.

## Verification Gates

- DTS Java focused tests pass for touched services.
- metro-stack Python contract tests pass.
- metro-stack frontend `pnpm build` passes.
- DTS webapp module build passes if UI is touched.
- `worklog/v2.2.3/sprint-30-202605/it/README.md` contains real evidence before marking DONE.

## Self-Review

- No Parquet implementation is included.
- CSV is treated as governed training snapshot, not raw file relay.
- Demo endpoints remain explicit and cannot be confused with production flow.
