# Screen Designer Productization Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Close the main productization gaps in the analytics screen designer by implementing a real marketplace path, productized template/industry-pack flows, plugin install closure, and baseline test coverage.

**Architecture:** Keep the existing screen designer kernel, template asset center, and industry-pack model intact. Add the missing backend marketplace resource, replace prompt-style flows with structured UI, and make installed plugins/templates visible through one consistent asset pipeline.

**Tech Stack:** Spring Boot/JHipster, React + Vite + Ant Design, Maven, pnpm, Playwright

---

### Task 1: Marketplace Backend Minimum Viable Flow

**Files:**
- Create: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/MarketplaceResource.java`
- Create: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/MarketplaceService.java`
- Create/Modify: marketplace-related domain/repository/storage support under `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/`
- Test: `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/MarketplaceResourceIT.java`

**Steps:**
1. Add a failing integration test proving `/api/marketplace/components` and `/api/marketplace/templates` must no longer return 404.
2. Implement a minimal service that can list component/template assets and expose install actions.
3. Keep phase-1 storage local and instance-scoped; do not build a remote community sync pipeline.
4. Return enough fields for the current market page: id, name, description, author, version, category, tags, thumbnail, downloads, installed.
5. Verify with `./mvnw test -Dtest=MarketplaceResourceIT`.

### Task 2: Marketplace Frontend Closure

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/ScreenMarketplacePage.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/api/analyticsApi.ts`
- Test: `source/dts-analytics-webapp/modern/src/pages/screens/ScreenMarketplacePage.test.tsx`

**Steps:**
1. Add a failing component test for loading, empty, install-success, and install-failure states.
2. Replace the “敬请期待” placeholder posture with real empty/loading/error handling.
3. Add install progress, explicit error messages, and a detail view or drawer for market items.
4. Keep existing API names so the page contract stays stable.
5. Verify with `pnpm -C source/dts-analytics-webapp/modern test -- ScreenMarketplacePage`.

### Task 3: Template Gallery Productization

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/components/TemplateGallery.tsx`
- Create: modal/form helpers under `source/dts-analytics-webapp/modern/src/pages/screens/components/`
- Test: `source/dts-analytics-webapp/modern/src/pages/screens/components/TemplateGallery.test.tsx`

**Steps:**
1. Add a failing test for import/export/listing/restore flows that should not rely on raw `window.prompt` or `alert`.
2. Replace industry-pack import/export, runtime probe, ops health, template restore, and listing actions with structured modal flows.
3. Preserve all existing backend contracts while improving UX state handling.
4. Standardize success/error feedback with app-level notifications instead of blocking dialogs.
5. Verify with targeted frontend tests and `pnpm -C source/dts-analytics-webapp/modern build`.

### Task 4: Screen Header Productization

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/components/ScreenHeader.tsx`
- Create/Modify: reusable dialog components under `source/dts-analytics-webapp/modern/src/pages/screens/components/`
- Test: `source/dts-analytics-webapp/modern/src/pages/screens/components/ScreenHeader.test.tsx`

**Steps:**
1. Add a failing test around save-as-template, publish, rollback, share, and export actions.
2. Replace prompt-style flows with typed forms and clearer confirmation/result surfaces.
3. Keep the existing backend action order and permission checks intact.
4. Make action completion states visible and testable.
5. Verify with frontend tests and full `pnpm -C source/dts-analytics-webapp/modern build`.

### Task 5: Plugin Install and Visibility Closure

**Files:**
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenPluginResource.java`
- Modify/Create: plugin install/registry services under `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/plugins/manifestLoader.ts`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/componentLibrary.ts`
- Test: backend plugin IT + targeted frontend tests

**Steps:**
1. Add a failing test showing installed marketplace plugins must appear in `/api/screen-plugins`.
2. Move `ScreenPluginResource` beyond demo-only behavior by sourcing installed assets plus local fallbacks.
3. Ensure frontend component library can distinguish built-in vs installed plugin assets.
4. Keep demo plugin support as fallback, not the primary source.
5. Verify with backend tests, frontend tests, and a manual build.

### Task 6: Backend Screen Asset Integration Tests

**Files:**
- Create: `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ScreenTemplateResourceIT.java`
- Create: `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ScreenIndustryPackResourceIT.java`
- Create: `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/ScreenPluginResourceIT.java`
- Create: `source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/MarketplaceResourceIT.java`

**Steps:**
1. Cover list/detail/install/restore/listing/import/export/runtime-probe happy paths and permission boundaries.
2. Focus on the contracts used by the modern screen frontend.
3. Make 404/403 regressions explicit in tests.
4. Verify with targeted Maven IT runs before broader analytics test execution.
5. Keep fixtures minimal and isolated.

### Task 7: Frontend Screen Module Test Coverage

**Files:**
- Create: tests under `source/dts-analytics-webapp/modern/src/pages/screens/`
- Modify: shared helpers only if they need extraction for testability

**Steps:**
1. Expand test coverage beyond the current 3 utility tests.
2. Prioritize `TemplateGallery`, `ScreenMarketplacePage`, and header action helpers.
3. Extract pure helper functions where needed instead of snapshot-testing entire pages.
4. Verify with `pnpm -C source/dts-analytics-webapp/modern test`.

### Task 8: Playwright and Runbook Closure

**Files:**
- Modify/Create: analytics-related specs under `tests/web-e2e/`
- Modify: `tests/suites.json` if new cases need registration
- Modify: `worklog/v2.2.1/sprint-8/it/README.md`

**Steps:**
1. Add or update smoke flows for screen list, template selection, template asset creation, and analytics page entry.
2. Keep the first iteration mock-friendly if full backend state is expensive to orchestrate.
3. Record exact commands, env, and artifact expectations in sprint documentation.
4. Verify with targeted Playwright runs plus suite dry-run.

