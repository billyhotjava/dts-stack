# Excel Import Issue Log Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make every Excel import issue record emit a single structured line into `dts-platform` `app.log`, covering `ERROR`, `WARN`, and `PARSE_WARNING`.

**Architecture:** Keep the existing `infra_project_cockpit_issue` persistence path as the source of truth, and add a side-effect logger in `ExcelImportService` at the same points where issue entities are created. Use a stable log prefix and `key=value` fields so onsite users can `grep`/`tail` `logs/dts-platform/app.log` without extra tooling.

**Tech Stack:** Spring Boot, SLF4J/Logback, JUnit 5, Mockito, existing platform integration tests.

---

### Task 1: Add a failing unit test for structured issue logs

**Files:**
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/infra/ExcelImportServiceTest.java`

**Step 1: Write the failing test**

Add a test that:
- prepares a parsed CSV with one `ERROR` row and one `WARN` row
- prepares `error.csv` with one parse warning row
- attaches a Logback `ListAppender` to `ExcelImportService`
- runs `loadProjectCockpitBatch(...)`
- asserts that logs contain three `[excel-import-issue]` entries with `severity=ERROR`, `severity=WARN`, and `issueCode=PARSE_WARNING`

**Step 2: Run test to verify it fails**

Run: `mvn -f source/dts-platform/pom.xml -Dtest=ExcelImportServiceTest#loadProjectCockpitBatchShouldEmitStructuredIssueLogs test`

Expected: FAIL because structured log lines are not emitted yet.

**Step 3: Write minimal implementation**

Modify `ExcelImportService` to emit structured single-line logs when:
- validation issues are created during `persistProjectCockpitRows(...)`
- parse issues are created during `persistProjectCockpitIssues(...)`

**Step 4: Run test to verify it passes**

Run: `mvn -f source/dts-platform/pom.xml -Dtest=ExcelImportServiceTest#loadProjectCockpitBatchShouldEmitStructuredIssueLogs test`

Expected: PASS

### Task 2: Keep log format stable and readable onsite

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/ExcelImportService.java`

**Step 1: Add a single logging helper**

Add a helper that formats:
- `batch`
- `fileId`
- `row`
- `severity`
- `issueCode`
- `message`
- `projectNo`
- `subsystem`
- `nodeTask`
- `planDate`
- `completionStatus`
- `riskLevel`
- `rawLine` for `PARSE_WARNING` only, truncated

**Step 2: Reuse helper in both validation and parse-warning paths**

Do not duplicate logging format in multiple branches.

**Step 3: Keep logs grep-friendly**

Prefix every line with `[excel-import-issue]`.

### Task 3: Extend integration coverage

**Files:**
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/infra/projectcockpit/ProjectCockpitImportServiceIT.java`
- Modify: `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/infra/ExcelImportResourceIT.java`

**Step 1: Verify new behavior does not break existing import flow**

Keep the existing batch/issue assertions green.

**Step 2: Run integration tests**

Run: `mvn -f source/dts-platform/pom.xml -Dtest=ProjectCockpitImportServiceIT,ExcelImportResourceIT test`

Expected: PASS

### Task 4: Verify frontend remains unaffected

**Files:**
- No code changes expected

**Step 1: Run frontend production build**

Run: `pnpm -C source/dts-platform-webapp build`

Expected: PASS

### Task 5: Verification

**Step 1: Run backend unit test**

Run: `mvn -f source/dts-platform/pom.xml -Dtest=ExcelImportServiceTest#loadProjectCockpitBatchShouldEmitStructuredIssueLogs test`

**Step 2: Run backend integration tests**

Run: `mvn -f source/dts-platform/pom.xml -Dtest=ProjectCockpitImportServiceIT,ExcelImportResourceIT test`

**Step 3: Run frontend build**

Run: `pnpm -C source/dts-platform-webapp build`

**Step 4: Report onsite grep usage**

Document:
- `grep '\[excel-import-issue\]' logs/dts-platform/app.log`
- `grep 'severity=ERROR' logs/dts-platform/app.log`
