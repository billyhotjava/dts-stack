# Excel Formula And Placeholder Cleaning Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make Excel-based project-management imports stable by evaluating formula cells before CSV export, forcing file-source ODS tables to land as text, and centralizing placeholder cleanup in dbt.

**Architecture:** Keep the existing `platform -> csv -> addax/airflow -> postgres -> dbt` pipeline, but tighten the boundary of each stage. `dts-platform` becomes responsible for formula evaluation when converting Excel to `data.csv`, `dts-ingestion` treats file uploads as raw text during Addax DDL generation, and `services/dts-dbt` owns semantic cleanup of placeholders like `"/"` and `#VALUE!`.

**Tech Stack:** Spring Boot, EasyExcel + Apache POI, JUnit 5, Addax job generation, dbt SQL/macros, Maven

---

### Task 1: Lock Platform Excel Formula Behavior In Tests

**Files:**
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/infra/ExcelImportResourceIT.java`
- Possibly create: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/infra/ExcelImportServiceTest.java`

**Steps:**
1. Add a failing test that uploads or stages an `.xlsx` containing a formula cell and parses it through `ExcelImportService`.
2. Assert the generated `data.csv` contains the formula result, not an empty string and not the formula expression itself.
3. Add a failing test case for a formula error cell and assert parsing degrades to empty value plus warning instead of hard failure.
4. Run the targeted platform test and verify it fails for the expected reason before implementation.

### Task 2: Implement Formula Evaluation In `ExcelImportService`

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/ExcelImportService.java`

**Steps:**
1. Extend the Excel parsing path so formula cells are evaluated explicitly before CSV serialization.
2. Preserve current merged-cell fill, date formatting, header normalization, and preview behavior.
3. Treat formula evaluation failures as empty values that can be reported through the existing error/warning path instead of aborting the whole parse when `skipErrors=true`.
4. Re-run the targeted platform test and make it pass.

### Task 3: Lock File-Source ODS Column Types In Tests

**Files:**
- Modify: `source/dts-ingestion/src/test/java/com/yuzhi/dts/ingestion/service/etl/AddaxJobServiceTest.java`

**Steps:**
1. Add a failing test that creates a file-source Addax job with `_fileColumns` containing `date`, `double`, and `boolean` sample types.
2. Assert generated `CREATE TABLE` DDL uses text/varchar columns for those file-source fields instead of strong typed `date/double precision/boolean`.
3. Keep the existing drop/create full-refresh expectation intact.
4. Run the targeted ingestion test and verify it fails before implementation.

### Task 4: Force File Uploads To Land As Text In Addax DDL

**Files:**
- Modify: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/AddaxJobService.java`
- Optionally modify: `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/FileUploadService.java`

**Steps:**
1. Change file-source DDL generation so uploaded Excel/CSV columns default to text-compatible PostgreSQL types regardless of inferred sample type.
2. Keep column naming, extra columns, and file-source full-refresh behavior unchanged.
3. Avoid broad side effects for non-file JDBC readers/writers.
4. Re-run the targeted ingestion test and make it pass.

### Task 5: Add dbt Placeholder Cleaning Primitives

**Files:**
- Create: `services/dts-dbt/macros/nullif_placeholder.sql`
- Possibly create: `services/dts-dbt/macros/parse_numeric_safe.sql`
- Modify: `services/dts-dbt/macros/parse_date_safe.sql`

**Steps:**
1. Add a shared placeholder macro that converts common sentinels such as `''`, `'/'`, `'-'`, `'--'`, `'N/A'`, and `'#VALUE!'` to `NULL`.
2. Update date parsing to route through placeholder cleanup before attempting format conversion.
3. Add numeric-safe parsing if needed so downstream models can cast cleaned text without blowing up on placeholders.
4. Keep the macros generic enough for reuse beyond project management.

### Task 6: Apply Placeholder Cleanup In Project-Management Models

**Files:**
- Modify: `services/dts-dbt/models/dwd/prjtest1/biz_dwd_project_node.sql`
- Modify: `services/dts-dbt/models/dwd/prjtest1/biz_dwd_project_node_enriched.sql`
- Inspect for follow-up: `services/dts-dbt/models/dws/prjtest1/*.sql`

**Steps:**
1. Replace direct `NULLIF(btrim(...), '')` usage on business text fields with the shared placeholder cleanup where appropriate.
2. Route date columns through the updated date macro and normalize raw numeric/week parsing so placeholders yield `NULL`.
3. Keep legitimate slash-bearing business text such as subsystem names untouched by limiting cleanup to exact placeholder values.
4. Run dbt-facing verification appropriate to the environment and capture any residual gaps.

### Task 7: Verify The End-To-End Contract

**Files:**
- No new code expected unless verification exposes regressions

**Steps:**
1. Run targeted platform tests for Excel parsing.
2. Run targeted ingestion tests for file-source Addax DDL generation.
3. Run relevant Maven module tests/build checks for touched Java modules.
4. Run dbt validation available in the workspace or, if not available, record the exact limitation and the static verification performed instead.
