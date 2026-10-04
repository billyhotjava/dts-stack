# Foundation Temporary Features Retirement Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove the temporary `项目主体域接入` and `专题绑定中心` features end-to-end from `dts-platform` and `dts-platform-webapp`, and drop their database tables with a forward-only Liquibase migration.

**Architecture:** The retirement is a subtractive refactor. Frontend routes, pages, API clients, and entry points are deleted; backend resources, services, repositories, entities, and runtime hooks are removed; then a new Liquibase changelog drops the retired schema objects in dependency-safe order.

**Tech Stack:** Spring Boot, JPA, Liquibase, React, TypeScript, Vite, pnpm, Maven

---

### Task 1: Remove frontend routes and visible entry points

**Files:**
- Modify: `source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx`
- Modify: `source/dts-platform-webapp/src/pages/foundation/DataSourcesPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`

**Step 1: Write the failing test**

Add or update a focused UI/helper test that asserts the retired route entries or jump links are absent from the rendered action set.

**Step 2: Run test to verify it fails**

Run: `cd source/dts-platform-webapp && pnpm build`
Expected: existing code still references the retired routes/pages, so later tasks are still required.

**Step 3: Write minimal implementation**

- Remove lazy imports for `ProjectCockpitImportsPage` and `TopicBindingCenterPage`
- Remove the two static route objects
- Remove the two buttons from `DataSourcesPage`
- Remove all `router.push("/foundation/topic-bindings")` links and topic-binding status UI from `SqlModelingPage`

**Step 4: Run test to verify it passes**

Run: `cd source/dts-platform-webapp && pnpm build`
Expected: build passes without unresolved imports or retired route references.

**Step 5: Commit**

```bash
git add source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx source/dts-platform-webapp/src/pages/foundation/DataSourcesPage.tsx source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx
git commit -m "refactor: remove temporary feature entry points"
```

### Task 2: Remove frontend pages, API client, helpers, and tests

**Files:**
- Delete: `source/dts-platform-webapp/src/pages/foundation/ProjectCockpitImportsPage.tsx`
- Delete: `source/dts-platform-webapp/src/pages/foundation/TopicBindingCenterPage.tsx`
- Delete: `source/dts-platform-webapp/src/api/services/topicBindingService.ts`
- Delete: `source/dts-platform-webapp/src/pages/foundation/projectCockpitImportBinding.helpers.ts`
- Delete: `source/dts-platform-webapp/src/pages/foundation/projectCockpitImportBinding.helpers.test.ts`
- Delete: `source/dts-platform-webapp/src/pages/foundation/topicBindingCenter.helpers.ts`
- Delete: `source/dts-platform-webapp/src/pages/foundation/topicBindingCenter.helpers.test.ts`

**Step 1: Write the failing test**

Use an import/build failure as the guard: after Task 1, these files should become unreferenced and removable without changing behavior elsewhere.

**Step 2: Run test to verify it fails**

Run: `cd source/dts-platform-webapp && rg -n "topicBindingService|ProjectCockpitImportsPage|TopicBindingCenterPage" src`
Expected: references still exist before deletion cleanup is complete.

**Step 3: Write minimal implementation**

Delete the retired pages, helper modules, helper tests, and the topic binding API client.

**Step 4: Run test to verify it passes**

Run: `cd source/dts-platform-webapp && rg -n "topicBindingService|ProjectCockpitImportsPage|TopicBindingCenterPage" src`
Expected: no matches.

**Step 5: Commit**

```bash
git add source/dts-platform-webapp/src
git commit -m "refactor: remove temporary foundation frontend modules"
```

### Task 3: Remove topic binding backend API and runtime coupling

**Files:**
- Delete: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/TopicBindingResource.java`
- Delete: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/topic/`
- Delete: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/topic/`
- Delete: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/topic/`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`
- Delete/Modify tests under:
  - `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/TopicBindingResourceIT.java`
  - `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/topic/`
  - `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/EtlResourceTest.java`

**Step 1: Write the failing test**

Add or adjust `EtlResourceTest` expectations so the class compiles and passes without `TopicBindingRuntimeService`.

**Step 2: Run test to verify it fails**

Run: `cd source/dts-platform && ./mvnw -q -Dtest=EtlResourceTest test`
Expected: fail while `EtlResource` still depends on removed topic-binding runtime behavior.

**Step 3: Write minimal implementation**

- Remove `TopicBindingResource`
- Remove the entire topic service/domain/repository package set
- Remove topic-binding runtime compilation and vars merge from `EtlResource`
- Update tests to align with the simplified ETL flow

**Step 4: Run test to verify it passes**

Run: `cd source/dts-platform && ./mvnw -q -Dtest=EtlResourceTest test`
Expected: pass.

**Step 5: Commit**

```bash
git add source/dts-platform/src/main/java source/dts-platform/src/test/java
git commit -m "refactor: retire topic binding backend flow"
```

### Task 4: Remove project cockpit import backend persistence flow

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/infra/ExcelImportResource.java`
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/infra/ExcelImportService.java`
- Delete: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/infra/InfraProjectCockpitBatch.java`
- Delete: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/infra/InfraProjectCockpitRow.java`
- Delete: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/infra/InfraProjectCockpitIssue.java`
- Delete: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/infra/InfraProjectCockpitBatchRepository.java`
- Delete: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/infra/InfraProjectCockpitRowRepository.java`
- Delete: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/infra/InfraProjectCockpitIssueRepository.java`
- Delete/Modify tests under:
  - `source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/infra/ExcelImportResourceIT.java`
  - `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/infra/ExcelImportServiceTest.java`
  - `source/dts-platform/src/test/java/com/yuzhi/dts/platform/service/infra/projectcockpit/`

**Step 1: Write the failing test**

Reduce backend coverage to the remaining generic excel-import behavior and delete tests that only assert project-cockpit-specific endpoints and persistence.

**Step 2: Run test to verify it fails**

Run: `cd source/dts-platform && ./mvnw -q -Dtest=ExcelImportResourceIT,ExcelImportServiceTest test`
Expected: fail until project-cockpit endpoints and dependencies are removed consistently.

**Step 3: Write minimal implementation**

- Remove `/project-cockpit/load` and `/project-cockpit/issues` endpoints
- Remove project-cockpit batch/row/issue persistence logic from `ExcelImportService`
- Delete dedicated entities, repositories, and tests
- Keep generic parse/error-preview behavior if still used elsewhere

**Step 4: Run test to verify it passes**

Run: `cd source/dts-platform && ./mvnw -q -Dtest=ExcelImportResourceIT,ExcelImportServiceTest test`
Expected: pass with only supported generic excel-import coverage remaining.

**Step 5: Commit**

```bash
git add source/dts-platform/src/main/java source/dts-platform/src/test/java
git commit -m "refactor: remove project cockpit import backend flow"
```

### Task 5: Drop retired database objects with a forward-only migration

**Files:**
- Modify: `source/dts-platform/src/main/resources/config/liquibase/master.xml`
- Create: `source/dts-platform/src/main/resources/config/liquibase/changelog/20260328_01_drop_temporary_foundation_features.xml`

**Step 1: Write the failing test**

Use compile/startup migration registration as the guard: the new changelog must be included and syntactically valid.

**Step 2: Run test to verify it fails**

Run: `cd source/dts-platform && ./mvnw -q -DskipTests compile`
Expected: after adding the include but before a valid changelog, compile or startup validation would fail.

**Step 3: Write minimal implementation**

- Add a new changelog include to `master.xml`
- Create a changelog that drops:
  - `topic_binding`
  - `topic_template_entity`
  - `topic_template`
  - `infra_project_cockpit_issue`
  - `infra_project_cockpit_row`
  - `infra_project_cockpit_batch`
- Use `tableExists` preconditions and safe drop ordering

**Step 4: Run test to verify it passes**

Run: `cd source/dts-platform && ./mvnw -q -DskipTests compile`
Expected: pass.

**Step 5: Commit**

```bash
git add source/dts-platform/src/main/resources/config/liquibase
git commit -m "refactor: drop retired temporary foundation tables"
```

### Task 6: Run final verification and residual reference sweep

**Files:**
- Modify as needed based on verification output

**Step 1: Write the failing test**

Use residual reference scans to ensure retired feature names no longer appear in active code.

**Step 2: Run test to verify it fails**

Run: `cd /opt/prod/s10/s10-stack && rg -n "project-cockpit-imports|topic-bindings|TopicBinding|ProjectCockpitImports|TopicBindingCenter" source/dts-platform source/dts-platform-webapp`
Expected: any remaining matches must be either retired-history changelogs or intentionally preserved references; active code matches indicate incomplete removal.

**Step 3: Write minimal implementation**

Remove or update any remaining active-code references uncovered by the scan.

**Step 4: Run test to verify it passes**

Run:

```bash
cd source/dts-platform && ./mvnw -q -DskipTests compile
cd source/dts-platform && ./mvnw -q -Dtest=EtlResourceTest,ExcelImportResourceIT,ExcelImportServiceTest test
cd source/dts-platform-webapp && pnpm build
cd /opt/prod/s10/s10-stack && rg -n "project-cockpit-imports|topic-bindings|TopicBinding|ProjectCockpitImports|TopicBindingCenter" source/dts-platform source/dts-platform-webapp
```

Expected:

- backend compile passes
- focused backend tests pass after retirement updates
- frontend build passes
- residual references only remain in historical changelogs or deliberate migration docs

**Step 5: Commit**

```bash
git add source/dts-platform source/dts-platform-webapp
git commit -m "refactor: retire temporary foundation features"
```
