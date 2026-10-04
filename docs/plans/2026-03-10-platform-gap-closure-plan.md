# Platform Gap Closure Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the main placeholder flows in `dts-platform` and `dts-platform-webapp` with real platform capabilities on `customer/2.2.1`.

**Architecture:** Keep `dts-platform` as the backend aggregation and orchestration layer, and `dts-platform-webapp` as the primary user-facing shell. Replace hard-coded demo payloads and frontend placeholders with typed backend services, then wire the frontend pages to those services with stable selectors for Playwright.

**Tech Stack:** Spring Boot/JHipster, React + Vite + Ant Design, Maven, pnpm, Playwright

---

### Task 1: Visualization Realization

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/VisualizationResource.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/visualization/VisualizationSummaryService.java`
- Modify: `source/dts-platform-webapp/src/pages/visualization/AnalyticsPage.tsx`
- Modify: `source/dts-platform-webapp/src/api/platformApi.ts`
- Test: `source/dts-platform/src/test/...` and `tests/web-e2e/specs/...`

**Steps:**
1. Write failing backend tests for dashboard summary aggregation and classification filtering.
2. Move hard-coded payload assembly out of `VisualizationResource` into a service that reads real repositories and link records.
3. Replace the redirect-only analytics page with a real platform entry page that renders backend summaries and links to embedded reports.
4. Add stable `data-testid` hooks and extend Playwright smoke coverage.
5. Verify with `./mvnw test`, `pnpm build`, and the targeted `python3 tests/run_suite.py` web suites.

### Task 2: IAM Classification Sync Wiring

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/iam/ClassificationService.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/IamClassificationSyncResource.java`
- Modify: `source/dts-platform-webapp/src/api/services/iamService.ts`
- Create: `source/dts-platform-webapp/src/pages/security/UserClassificationPage.tsx`
- Test: `source/dts-platform/src/test/...`

**Steps:**
1. Add failing backend tests for user search, sync status, sync execution, and failure retry.
2. Expose REST endpoints for `searchUsers`, `refreshUser`, `datasets`, `syncStatus`, `executeSync`, and `retryFailure`.
3. Replace the frontend placeholder service with real API calls.
4. Add or wire a UI page for classification sync operations and failure visibility.
5. Verify backend tests, frontend build, and one Playwright flow that exercises the sync console shell.

### Task 3: Task Scheduling Console

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/SchedulerResource.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/scheduler/SchedulerConsoleService.java`
- Modify: `source/dts-platform-webapp/src/pages/foundation/TaskSchedulingPage.tsx`
- Create: `source/dts-platform-webapp/src/pages/foundation/components/SchedulerOverview*.tsx`
- Test: backend scheduler tests and targeted web smoke

**Steps:**
1. Define the minimum scheduler console contract: overview, recent runs, dag states, pause/resume/retry actions.
2. Implement a backend aggregation service over existing ops/etl/airflow data.
3. Replace the three-card navigation page with a real scheduler console.
4. Keep direct links to detailed ops/etl pages for drill-down.
5. Verify with backend tests, `pnpm build`, and Playwright smoke on the scheduler page.

### Task 4: API Service Try Invoke

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/services/ApiCatalogService.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/services/ApiInvokeProxyService.java`
- Modify: `source/dts-platform-webapp/src/pages/services/ApiServicesPage.tsx`
- Modify: `source/dts-platform-webapp/src/api/services/apiServicesService.ts`
- Test: backend service tests + frontend smoke

**Steps:**
1. Write a failing test showing `tryInvoke` should proxy a real request instead of generating sample rows.
2. Introduce a proxy service that executes a configured downstream request with safe timeout and policy-hit tracing.
3. Expand the frontend modal to collect params/headers and render response metadata.
4. Preserve masking and policy explanations in the response.
5. Verify with backend tests, frontend build, and Playwright API-services smoke.

### Task 5: Workbench Real Metrics and IA Cleanup

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/workbench/WorkbenchService.java`
- Modify: `source/dts-platform-webapp/src/pages/workbench/index.tsx`
- Modify: `source/dts-platform-webapp/src/pages/visualization/ReportsManagePage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/governance/QualityReportPage.tsx`
- Test: backend workbench tests + web smoke

**Steps:**
1. Add backend support for history/trend series and richer role-aware summary cards.
2. Remove simulated trend generation from the frontend and bind charts to real series.
3. Clean up alias/wrapper pages so page names match product boundaries.
4. Add stable selectors for the updated workbench and report management flows.
5. Verify with `./mvnw test`, `pnpm build`, and Playwright workbench/report smoke suites.
