# Modeling Release Warning Downgrade Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove quality/release `BLOCKED` gating from SQL modeling release submission, keep only true execution-precondition failures hard-stopped, and fix imported dbt package compatibility issues that currently cause false template warnings and source conflicts.

**Architecture:** Keep the existing two-step submit flow in the frontend, but change backend semantics so quality gate and release gate findings are always surfaced as warnings that can be confirmed and bypassed. Fix the quality gate's schema-yml lookup so imported packages that centralize tests in root-level schema files are recognized, and remove stale legacy source files during package import when a package brings its own managed source file.

**Tech Stack:** Spring Boot service layer, JUnit 5, Mockito, React/TypeScript client, dbt workspace file management.

---

### Task 1: Lock Down Release Submission Downgrade Behavior

**Files:**
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtReleaseSubmissionServiceTest.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtReleaseSubmissionService.java`

**Step 1: Write the failing test**

Add tests that prove:
- `qualityGate.blocking=true` returns `WARNING` instead of `BLOCKED` when `confirmWarnings=false`
- `releaseGate.blocking=true` returns `WARNING` instead of `BLOCKED` when `confirmWarnings=false`
- the same findings allow `SUBMITTED` when `confirmWarnings=true`

**Step 2: Run test to verify it fails**

Run: `./mvnw -pl source/dts-platform -Dtest=DbtReleaseSubmissionServiceTest test`

Expected: FAIL because current code returns `BLOCKED`.

**Step 3: Write minimal implementation**

Update `DbtReleaseSubmissionService.submit(...)` so:
- Airflow disabled / DAG unresolved / DAG missing remain hard stop
- quality gate blockers are merged into warnings
- release gate blockers are merged into warnings
- `confirmWarnings=true` bypasses those warnings and triggers DAG submission

**Step 4: Run test to verify it passes**

Run: `./mvnw -pl source/dts-platform -Dtest=DbtReleaseSubmissionServiceTest test`

Expected: PASS

### Task 2: Fix Root-Level Schema YML Detection

**Files:**
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/etl/DbtQualityGateServiceTest.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtQualityGateService.java`

**Step 1: Write the failing test**

Add a test that creates:
- project dir with nested SQL path like `models/dwd/project/biz_dwd_quality_issue.sql`
- root-level schema file `models/pm_schema.yml`
- repository model whose `modelPath` points to the nested SQL path

Assert `evaluate("model:biz_dwd_quality_issue")` does not report missing test template for that model.

**Step 2: Run test to verify it fails**

Run: `./mvnw -pl source/dts-platform -Dtest=DbtQualityGateServiceTest test`

Expected: FAIL because current lookup only checks current dir and one parent.

**Step 3: Write minimal implementation**

Update schema lookup to walk upward from the SQL directory until the dbt `models/` root (or project root fallback) and scan each level for yml files containing the model definition.

**Step 4: Run test to verify it passes**

Run: `./mvnw -pl source/dts-platform -Dtest=DbtQualityGateServiceTest test`

Expected: PASS

### Task 3: Remove Legacy `pm_ods_sources.yml` During Package Import

**Files:**
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelServiceTest.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelFileService.java`

**Step 1: Write the failing test**

Add a batch-import test that:
- seeds workspace with `models/pm_ods_sources.yml`
- imports an archive that contains `models/pm_sources.yml`
- asserts the stale `pm_ods_sources.yml` is deleted after import

**Step 2: Run test to verify it fails**

Run: `./mvnw -pl source/dts-platform -Dtest=ModelingSqlModelServiceTest test`

Expected: FAIL because current import copies new files but keeps the old conflicting source file when `cleanOldFiles=false`.

**Step 3: Write minimal implementation**

Teach `ModelFileService.copyWorkspaceCompanionFiles(...)` to remove `models/pm_ods_sources.yml` when the import payload includes `models/pm_sources.yml`.

**Step 4: Run test to verify it passes**

Run: `./mvnw -pl source/dts-platform -Dtest=ModelingSqlModelServiceTest test`

Expected: PASS

### Task 4: Verify the Combined Change

**Files:**
- No new files

**Step 1: Run focused backend suite**

Run: `./mvnw -pl source/dts-platform -Dtest=DbtReleaseSubmissionServiceTest,DbtQualityGateServiceTest,ModelingSqlModelServiceTest test`

Expected: PASS

**Step 2: Review remaining operational risk**

Check that the runtime still may fail for unrelated dbt/package issues, but the specific false template warning and duplicate `pm_ods_sources.yml` conflict are addressed by code, not manual cleanup.
