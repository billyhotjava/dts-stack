# Workbench Home Personalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Build a single `/workbench` homepage where each logged-in user can choose which predefined components appear and adjust their order with Chrome 95-compatible controls.

**Architecture:** `/workbench` becomes the only homepage container. Backend stores per-user component preferences and filters them by permissions. Frontend renders a registry of predefined components and provides a checkbox-based customization drawer with up/down ordering.

**Tech Stack:** Spring Boot/JHipster, Liquibase, React, Ant Design, Zustand/local cache as optional non-authoritative cache, Vite legacy build, Playwright smoke checks.

---

## File Structure

- Backend preference model/API: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/**`
- Backend migration: `source/dts-platform/src/main/resources/config/liquibase/**`
- Frontend service/types: `source/dts-platform-webapp/src/api/services/workbenchPreferencesService.ts`
- Frontend registry: `source/dts-platform-webapp/src/pages/workbench/workbenchComponentRegistry.tsx`
- Frontend container: `source/dts-platform-webapp/src/pages/workbench/index.tsx`
- Frontend customization UI: `source/dts-platform-webapp/src/pages/workbench/components/WorkbenchCustomizeDrawer.tsx`
- Route/menu contracts: `source/dts-platform-webapp/src/routes/sections/dashboard/**`, `source/dts-admin/src/main/resources/config/data/**`
- Worklog evidence: `worklog/v2.2.3/sprint-46-202606/it/`

## Execution Order

1. F1: lock route/menu contract first so duplicate homepages cannot regress.
2. F2: implement backend preference contract with TDD.
3. F3: add frontend registry and render selected components.
4. F4: add customization drawer with checkbox and up/down controls.
5. F5: migrate data-management page capability into registered components.
6. F6: run full verification and update evidence.

## TDD Rule

For each task:

- Write the failing source-contract/unit test first.
- Run the narrow test and confirm it fails for the expected reason.
- Implement the minimal code.
- Run the narrow test and then the relevant build/smoke command.
- Update worklog status only after proof exists.

## Required Verification Commands

```bash
cd source/dts-platform-webapp
node --test --experimental-strip-types <source-contract-tests>
pnpm build
```

```bash
cd source/dts-platform
npm run backend:unit:test
```

```bash
cd /opt/prod/s10/v2.2.3
git diff --check
```

Playwright smoke must cover:

- `/workbench`
- `/workbench?customize=1`
- `/workbench/data-management`
- `/services/consumption`
