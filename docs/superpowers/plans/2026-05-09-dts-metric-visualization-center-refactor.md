# DTS Metric Visualization Center Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the DTS Platform semantic metric center around a clear DWD -> metrics -> DWS/ADS -> publish workflow, with frontend cleanup first and backend hardening second.

**Architecture:** Move repeated semantic modeling logic into tested frontend helpers, wire the existing step pages to those helpers, then remove or isolate obsolete workspace code. Backend changes are limited to contract gaps discovered by the frontend pass.

**Tech Stack:** React 18, TypeScript, Ant Design, Vitest, Spring Boot, Java, dbt integration.

---

### Task 1: Shared Semantic Modeling Helpers

**Files:**
- Create: `source/dts-platform-webapp/src/pages/metrics/semantic/semanticModeling.helpers.ts`
- Create: `source/dts-platform-webapp/src/pages/metrics/semantic/semanticModeling.helpers.test.ts`
- Modify: `source/dts-platform-webapp/src/pages/metrics/semantic/semanticModelingShared.ts`

- [x] Write tests for DWD detection, dataset normalization, safe code generation, numeric field detection, and default formula JSON.
- [x] Run the helper test and verify it fails before implementation.
- [x] Implement the helper functions.
- [x] Re-run the helper test and verify it passes.

### Task 2: Wire Pages To Shared Helpers

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/metrics/semantic/SemanticSubjectsPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/metrics/semantic/SemanticObjectsPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/metrics/semantic/SemanticMetricDesignerPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/metrics/semantic/SemanticDatasetsPage.tsx`

- [x] Replace duplicated dataset normalization and DWD checks with helper imports.
- [x] Replace duplicated code generation and numeric detection in metric designer.
- [x] Generate formula JSON in the skill-compatible shape.
- [x] Add a compact formula preview or risk cue in the metric designer without changing the backend contract.
- [x] Add a visual formula template generator for aggregation, conditional, and ratio metrics.
- [x] Run the helper test again.

### Task 3: Retire Obsolete Workspace Page If Safe

**Files:**
- Delete if unused: `source/dts-platform-webapp/src/pages/metrics/semantic/SemanticWorkspacePage.tsx`

- [x] Search references to `SemanticWorkspacePage`.
- [x] If no references exist, delete the unused page.
- [x] If references exist, leave it in place and add a note in the final report instead of deleting it.

### Task 4: Frontend Verification

**Files:**
- No production files expected unless verification exposes compile issues.

- [x] Run the targeted Vitest helper test.
- [x] Run `pnpm build` from `source/dts-platform-webapp`.
- [x] Capture any build failures and fix only failures caused by this refactor.

### Task 5: Backend Follow-Up Scope

**Files:**
- Inspect first: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/SemanticModelingService.java`
- Inspect first: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/SemanticModelingResource.java`

- [x] Run GitNexus impact before editing Java symbols.
- [x] Add backend formula validation or generation fixes only if the frontend pass exposes a concrete contract gap.
- [x] Add targeted Java tests for any backend change.

### Completion Audit

- [x] `dts-metric-visualization-development` skill concepts are visible in the frontend workflow.
- [x] Frontend refactor is implemented before backend changes.
- [x] Shared helper tests pass.
- [x] `source/dts-platform-webapp` build passes or unrelated blockers are documented.
- [x] Unrelated dirty worktree changes are not reverted or folded into this refactor.
