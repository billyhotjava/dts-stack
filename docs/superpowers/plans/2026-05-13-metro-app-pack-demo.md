# Metro App-Pack Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an end-to-end demo path where DTS governs metro signal ODS/dbt assets and launches the metro App-Pack LSTM workflow.

**Architecture:** DTS owns data landing, governance, dbt modeling, and menu navigation. metro-stack consumes DTS-exported training windows and exports expert feedback as governance tables that can be re-imported into DTS.

**Tech Stack:** PostgreSQL ODS tables, dbt models, JHipster menu seed JSON, FastAPI metro-pack backend, Python LSTM dataset builder.

---

### Task 1: DTS Metro ODS And dbt Assets

**Files:**
- Create: `worklog/v2.2.3/metro-app-pack/demo/ods_create_tables.sql`
- Create: `services/dts-dbt/models/metro_sources.yml`
- Create: `services/dts-dbt/models/stg/metro/stg_metro__signal_feature_window.sql`
- Create: `services/dts-dbt/models/stg/metro/stg_metro__expert_event_label.sql`
- Create: `services/dts-dbt/models/stg/metro/stg_metro__expert_rule.sql`
- Create: `services/dts-dbt/models/dwd/metro/metro_dwd_lstm_training_window.sql`
- Create: `services/dts-dbt/models/dwd/metro/metro_dwd_expert_governance_signal.sql`
- Create: `services/dts-dbt/models/ads/metro/metro_ads_app_pack_demo_summary.sql`
- Create: `services/dts-dbt/models/metro_schema.yml`

- [ ] Add demo ODS DDL for signal feature windows, raw telemetry, expert labels, expert rules, and threshold policies.
- [ ] Register the ODS tables as dbt sources under `metro_ods`.
- [ ] Add STG models that normalize DTS technical fields and expert governance data.
- [ ] Add DWD/ADS models for LSTM training windows, expert governance features, and demo summary output.
- [ ] Run `dbt parse` from `services/dts-dbt`.

### Task 2: DTS Admin App-Pack Menu

**Files:**
- Modify: `source/dts-admin/src/main/resources/config/data/portal-menu-seed.json`
- Modify: `source/dts-admin/src/main/resources/config/data/role-menu-defaults.json`

- [ ] Add a top-level `行业业务开发(App-Pack)` portal section.
- [ ] Add a `地铁应用` child menu with `externalLink` pointing to `/expert/metro/dashboard`.
- [ ] Grant the leaf route to `OP_ADMIN` by default.
- [ ] Validate both JSON files with `jq empty`.

### Task 3: metro-stack DTS Data Entry And Governance Export

**Files:**
- Modify: `/opt/prod/metro-app/sources/metro-stack/backend/src/metro_mlops/datasets/build_lstm_ae_dataset.py`
- Modify: `/opt/prod/metro-app/sources/metro-stack/backend/src/metro_pack_service/batch_runner.py`
- Modify: `/opt/prod/metro-app/sources/metro-stack/backend/src/metro_pack_service/feedback.py`
- Modify: `/opt/prod/metro-app/sources/metro-stack/backend/src/metro_pack_service/main.py`

- [ ] Add DTS training-window CSV auto-detection to the dataset builder.
- [ ] Expand `feature_payload` JSON and expert columns into numeric LSTM features.
- [ ] Add batch creation from a DTS export directory and skip the raw Excel profiling step for DTS-sourced batches.
- [ ] Export expert labels, rule hints, and threshold overrides into CSV files matching the DTS ODS table contracts.
- [ ] Run Python compile checks on changed metro-stack backend files.
