# Project Cockpit System Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a DTS-standard project cockpit system with shared filters, five themed views, upload-to-ODS data ingestion, dbt-based semantic models, and reusable project-management visual components.

**Architecture:** Keep the existing `analytics modern` application as the runtime shell, add a dedicated `/analytics/project-cockpit` route, and back it with curated `dts-analytics` aggregation APIs over dbt semantic tables. Replace TSV-as-runtime-data with the DTS standard chain `upload Excel/CSV -> ODS batch/row/issues -> dbt DWD/DWS/ADS -> project cockpit APIs`, while retaining the current `project-cockpit/*.tsv` files only as local fixtures and regression samples.

**Tech Stack:** dbt, Spring Boot/JHipster, React + Vite + TypeScript, Ant Design, ECharts/runtime chart helpers, pnpm, Maven

---

### Task 1: Formalize Sprint-9 DTS Data Contract

**Files:**
- Modify: `worklog/v2.2.1/sprint-9/README.md`
- Modify: `worklog/v2.2.1/sprint-9/project-cockpit-system-design.md`
- Modify: `worklog/v2.2.1/sprint-9/project-cockpit-system-plan.md`
- Modify: `worklog/v2.2.1/sprint-9/tasks/*.md`

**Steps:**
1. Update sprint documentation from “demo TSV cockpit” to “DTS standard project-domain chain”.
2. Explicitly document that TSV fixtures are not a formal runtime source.
3. Capture batch, ODS, issue, ADS, and data-support requirements in the sprint plan.
4. Keep the implementation backlog aligned with the revised design.

**Expected:**
- Sprint-9 documentation reflects the agreed formal delivery path.

### Task 2: Generate Reproducible 2000-Row Excel Test Batch

**Files:**
- Create: `worklog/v2.2.1/sprint-9/it/generate_project_cockpit_test_data.py`
- Create: `worklog/v2.2.1/sprint-9/it/project-cockpit-test-batch-2000.xlsx`
- Modify: `worklog/v2.2.1/sprint-9/it/README.md`
- Modify: `worklog/v2.2.1/sprint-9/it/demo-checklist.md`

**Steps:**
1. Write a reproducible generator script for the project cockpit demo batch.
2. Generate 2000 rows covering 5 major projects and 30 subprojects.
3. Enforce distribution targets: 50% delayed, 30% normal, 20% early.
4. Mix in controlled dirty-data samples for parsing and cleaning verification.
5. Document how to regenerate the file from repo state.

**Run:**
- `python3 worklog/v2.2.1/sprint-9/it/generate_project_cockpit_test_data.py`

**Expected:**
- The Excel file is regenerated deterministically and matches the requested distributions.

### Task 3: Add Project-Domain Batch / ODS / Issue Structures

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/ExcelImportService.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/projectcockpit/`
- Create: `source/dts-platform/src/main/resources/config/liquibase/changelog/*project_cockpit*.xml`
- Test: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/infra/projectcockpit/`

**Steps:**
1. Add batch metadata, raw row, and issue persistence for project cockpit uploads.
2. Reuse existing Excel prepare/parse flow instead of building a parallel uploader.
3. Preserve row-level issues without blocking the whole batch on business-data quality.
4. Expose enough metadata for later data-support and refresh-status views.

**Run:**
- `cd source/dts-platform && ./mvnw -Dtest=*ProjectCockpit* test`

**Expected:**
- A parsed upload can persist batch, rows, and issues into the platform-side project domain tables.

### Task 4: Wire Project Upload Parsing To ODS Refresh Flow

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/infra/ExcelImportResource.java`
- Modify: `source/dts-platform-webapp/src/api/services/dataSourcesService.ts`
- Create: `source/dts-platform-webapp/src/pages/...` 
- Modify: `services/dts-dbt/models/...`

**Steps:**
1. Add a project-specific parse/load action that takes prepared Excel data into the project-domain raw tables.
2. Make the flow write batch counts and issue counts.
3. Prepare the resulting ODS structures for dbt execution.
4. Keep the user-facing API in DTS terminology instead of exposing dbt internals.

**Run:**
- `cd source/dts-platform && ./mvnw -Dtest=*ExcelImport* test`

**Expected:**
- The platform can accept a project cockpit Excel batch and persist it into the formal project domain path.

### Task 5: Expand dbt For Formal Project-Domain Consumption

**Files:**
- Modify: `services/dts-dbt/dbt_project.yml`
- Create: `services/dts-dbt/models/dwd/model/pm_ods_project_progress_row.sql`
- Modify: `services/dts-dbt/models/dwd/model/biz_dwd_project_node.sql`
- Modify: `services/dts-dbt/models/dwd/model/biz_dwd_project_node_enriched.sql`
- Modify: `services/dts-dbt/models/dws/model/biz_dws_week_subproject_summary.sql`
- Modify: `services/dts-dbt/models/ads/model/biz_ads_major_project_overview.sql`
- Modify: `services/dts-dbt/models/ads/model/biz_ads_major_project_tree_snapshot.sql`
- Modify: `services/dts-dbt/models/ads/model/biz_ads_delay_reason_trend.sql`
- Modify: `services/dts-dbt/models/project_cockpit_schema.yml`

**Steps:**
1. Add formal ODS-based sources for the project cockpit pipeline.
2. Carry batch metadata and issue counts through DWD/DWS/ADS where needed.
3. Keep semantic mappings stable on `major -> subproject -> node`.
4. Add tests for coverage, hierarchy validity, and delay-reason normalization.

**Run:**
- `docker exec dts-dbt sh -lc 'cd /opt/dbt && dbt run --profiles-dir /root/.dbt --select project-management'`
- `docker exec dts-dbt sh -lc 'cd /opt/dbt && dbt test --profiles-dir /root/.dbt --select project-management'`

**Expected:**
- Formal project-domain models build from ODS instead of relying on TSV fixtures.

### Task 6: Replace TSV Runtime Data With Warehouse Queries In dts-analytics

**Files:**
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ProjectCockpitService.java`
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ProjectCockpitResource.java`
- Create: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/projectcockpit/`
- Modify: `source/dts-analytics/src/main/resources/config/application.yml`
- Test: `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ProjectCockpitResourceIT.java`

**Steps:**
1. Move the service from classpath TSV loading to warehouse-backed queries.
2. Keep TSV files only as fixtures and never as formal runtime fallback.
3. Return “暂无正式数据” when no batch has been modeled.
4. Expose batch/data-support metadata alongside business metrics.

**Run:**
- `cd source/dts-analytics && mvn -Dtest=ProjectCockpitResourceIT test`

**Expected:**
- Project cockpit APIs read formal warehouse data and behave correctly for empty-state batches.

### Task 7: Upgrade Data Support View Into A Real Support Center

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/project-cockpit/views/DataSupportView.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/pages/project-cockpit/components/DataSupportCard.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/api/analyticsApi.ts`
- Test: `source/dts-analytics-webapp/modern/src/pages/project-cockpit/views/DataSupportView.test.ts`

**Steps:**
1. Surface batch id, upload time, refresh time, valid row counts, and issue counts.
2. Show mapping coverage, unknown categories, and pending follow-up items.
3. Add a proper indicator glossary table tied to formal definitions.
4. Make the support view the primary troubleshooting entry for information-office users.

**Run:**
- `cd source/dts-analytics-webapp/modern && node --import tsx --test src/pages/project-cockpit/views/DataSupportView.test.ts`

**Expected:**
- Data-support view shows formal batch and quality context instead of static demo text.

### Task 8: Execution View And Gantt Enhancements

**Files:**
- Create: `source/dts-analytics-webapp/modern/src/pages/project-cockpit/views/ExecutionView.tsx`
- Create: `source/dts-analytics-webapp/modern/src/pages/project-cockpit/components/ExecutionKpiPanel.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/components/ComponentRenderer.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/hooks/cardDataMapper.ts`
- Test: `source/dts-analytics-webapp/modern/src/pages/screens/hooks/cardDataMapper.test.ts`

**Steps:**
1. Build the execution theme around gantt, milestone progress, due/overdue lists, and org workload.
2. Harden gantt mapping for project-management fields.
3. Keep gantt enhancements reusable for later screen-designer usage.
4. Add targeted data-mapper tests for project execution rows.

**Run:**
- `cd source/dts-analytics-webapp/modern && node --import tsx --test src/pages/screens/hooks/cardDataMapper.test.ts`

**Expected:**
- Gantt mapping tests pass and execution view builds cleanly.

### Task 9: Risk Attribution View

**Files:**
- Create: `source/dts-analytics-webapp/modern/src/pages/project-cockpit/views/RiskAttributionView.tsx`
- Create: `source/dts-analytics-webapp/modern/src/pages/project-cockpit/components/DelayReasonMatrix.tsx`
- Test: `source/dts-analytics-webapp/modern/src/pages/project-cockpit/views/RiskAttributionView.test.ts`

**Steps:**
1. Implement risk distribution, delay trends, delay-reason structure, and key delayed project lists.
2. Add a dedicated delay-reason matrix component.
3. Keep the view compatible with both leader and section-chief use.

**Run:**
- `cd source/dts-analytics-webapp/modern && node --import tsx --test src/pages/project-cockpit/views/RiskAttributionView.test.ts`

**Expected:**
- Risk view tests pass and the matrix renders stable mock data.

### Task 10: Major Project Tree View

**Files:**
- Create: `source/dts-analytics-webapp/modern/src/pages/project-cockpit/views/MajorProjectTreeView.tsx`
- Create: `source/dts-analytics-webapp/modern/src/pages/project-cockpit/components/ProjectTreeProgressBoard.tsx`
- Create: `source/dts-analytics-webapp/modern/src/pages/project-cockpit/components/ProjectTreeDetailPanel.tsx`
- Test: `source/dts-analytics-webapp/modern/src/pages/project-cockpit/views/MajorProjectTreeView.test.ts`

**Steps:**
1. Implement the `major -> subproject -> node` tree progress board as the main view.
2. Show progress, risk, delay, and child rollups at each level.
3. Add a detail panel for the currently selected tree node.
4. Ensure the tree can anchor the rest of the system context.

**Run:**
- `cd source/dts-analytics-webapp/modern && node --import tsx --test src/pages/project-cockpit/views/MajorProjectTreeView.test.ts`

**Expected:**
- Tree view tests pass and hierarchy selection behaves correctly.

### Task 11: Data Support View

**Files:**
- Create: `source/dts-analytics-webapp/modern/src/pages/project-cockpit/views/DataSupportView.tsx`
- Create: `source/dts-analytics-webapp/modern/src/pages/project-cockpit/components/DataSupportCard.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/api/analyticsApi.ts`

**Steps:**
1. Implement update time, coverage, indicator glossary, and missing-data reminders.
2. Surface the “customer to supplement” checklist in a stable support view.
3. Keep the visual language consistent with the other four themes.

**Run:**
- `cd source/dts-analytics-webapp/modern && pnpm build`

**Expected:**
- Support view compiles and does not regress route loading.

### Task 12: Component Package Hardening

**Files:**
- Create: `source/dts-analytics-webapp/modern/src/pages/project-cockpit/components/index.ts`
- Modify: `source/dts-analytics-webapp/modern/src/pages/project-cockpit/`
- Test: `source/dts-analytics-webapp/modern/src/pages/project-cockpit/components/*.test.ts`

**Steps:**
1. Normalize shared card, panel, empty-state, and chart-wrapper primitives for the cockpit.
2. Keep naming and contracts stable so the components can later be promoted into broader screen usage.
3. Remove view-local duplication before closing the sprint.

**Run:**
- `cd source/dts-analytics-webapp/modern && pnpm build`

**Expected:**
- Shared component extraction does not change user-visible behavior.

### Task 13: Verification, Runbook, And Demo Dataset Closure

**Files:**
- Create: `worklog/v2.2.1/sprint-9/it/README.md`
- Create: `worklog/v2.2.1/sprint-9/it/demo-checklist.md`
- Modify: `worklog/v2.2.1/sprint-9/README.md`

**Steps:**
1. Document dbt seed/run/test commands for the project cockpit dataset.
2. Document backend/frontend verification commands.
3. Add a demo checklist for leadership walkthrough, section-chief walkthrough, and info-office support walkthrough.
4. Record the customer missing-data list that must be collected after the demo.

**Run:**
- `cd services/dts-dbt && dbt seed && dbt run --select project-management`
- `cd source/dts-analytics && mvn -Dtest=ProjectCockpitResourceIT test`
- `cd source/dts-analytics-webapp/modern && pnpm build`

**Expected:**
- The runbook is sufficient to reproduce the demo environment from repo state.
