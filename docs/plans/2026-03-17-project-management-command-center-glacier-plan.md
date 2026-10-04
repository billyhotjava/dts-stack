# Project Management Command Center Glacier Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Rework the `project-management-command-center` screen template into a glacier-white research-institute style without changing layout, bindings, or carousel behavior.

**Architecture:** Keep the existing three-page Java-backed demo template intact and only replace visual tokens and component config defaults in the template definition. Validate the redesign through targeted template assertions plus normal frontend type/build checks.

**Tech Stack:** React + Vite, TypeScript, node:test, pnpm

---

### Task 1: Lock The Visual Contract In Tests

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.test.ts`

**Steps:**
1. Extend the existing template registration test with failing assertions for the default `glacier` theme and representative light-style component values.
2. Assert at least one overview panel, one filter component, one KPI card, one chart, and one table reflect the new glacier palette.
3. Keep assertions focused on stable visual tokens instead of every hardcoded component value.
4. Verify the test fails before implementation.

### Task 2: Repaint The Template With Glacier Tokens

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/projectManagementCommandCenterTemplate.ts`

**Steps:**
1. Replace the current dark constants with glacier-style canvas, card, border, text, and accent tokens.
2. Switch the template default theme from `legacy-dark` to `glacier`.
3. Update helper builders for panels, filters, KPI cards, charts, and tables so all three pages inherit the new visual language.
4. Do not change data source URLs, response paths, variable keys, or component/page layout geometry.

### Task 3: Verify The Frontend Contract

**Files:**
- No code changes expected unless verification exposes regressions

**Steps:**
1. Run the targeted node test for `projectManagementCommandCenterTemplate.test.ts`.
2. Run `pnpm -C source/dts-analytics-webapp/modern typecheck`.
3. Run `pnpm -C source/dts-analytics-webapp/modern build`.
4. If verification reveals unrelated pre-existing failures, separate them from this change and document them clearly.
