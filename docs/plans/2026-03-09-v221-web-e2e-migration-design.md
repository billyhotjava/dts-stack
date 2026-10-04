# v2.2.1 Web E2E Migration Design

**Date:** 2026-03-09
**Branch:** `customer/2.2.1`
**Scope:** `tests/`, `tests/web-e2e/`, `worklog/v2.2.1/sprint-2/`

## Context

The current `customer/2.2.1` worktree does not contain the `v2.5.0` Playwright automation layer.
The goal is to migrate the web automation capability into the current product line without also reviving
the old release, Helm, GitOps, and non-web contract suites that no longer fit the repository layout.

The migration target is intentionally close to the `v2.5.0` web coverage model:

- three apps: `platform`, `admin`, `analytics`
- four browser suite groups:
  - `web-e2e-core`
  - `web-e2e-full`
  - `web-e2e-quarantine`
  - `biz-e2e`
- page object + fixture + support/mock layering
- top-level `tests/` runner and suite registry

The current branch has one important structural difference from `v2.5.0`:

- analytics lives at `source/dts-analytics-webapp/modern`, not at the webapp root

## Goals

- restore a top-level `tests/` structure focused on Playwright web automation
- migrate the `v2.5.0` Playwright assets with minimal unnecessary redesign
- adapt the suite wiring to the current `customer/2.2.1` repository layout
- preserve three-app Playwright coverage across `platform`, `admin`, and `analytics`
- create `worklog/v2.2.1/sprint-2/` using the `sprint-1/README.md` style with more granular task cards
- make the migrated suite discoverable, runnable, and report-producing before any later realism upgrades

## Non-Goals

- no migration of old release gate, Helm, GitOps, offline bundle, or audit contract tests
- no attempt to backport `v2.5.0` non-web product capabilities into `customer/2.2.1`
- no CI workflow integration in this sprint
- no attempt to convert the suite from mock-first into full black-box backend validation in this sprint

## Recommended Approach

Migrate only the Playwright-focused assets and the minimal top-level runner they depend on.

Why this approach:

- it keeps the highest-value browser automation from `v2.5.0`
- it avoids reviving broken non-web suites that target paths missing from `customer/2.2.1`
- it keeps naming, suite IDs, and spec organization close enough for later parity work
- it gives the branch an immediately usable Playwright harness instead of a half-restored gate system

## Design Decisions

### 1. Restore only the minimal top-level `tests/` layer

The migrated top-level test surface should include:

- `tests/README.md`
- `tests/.env.example`
- `tests/run_suite.py`
- `tests/run_gates.sh`
- `tests/suites.json`
- `tests/reports/README.md`
- `tests/web-e2e/`

The generic runner is worth keeping because it gives:

- stable suite naming
- dry-run discovery
- one command entrypoint
- structured reports

The runner will be trimmed to Web E2E use instead of bringing back unrelated `v2.5.0` suites.

### 2. Keep the `v2.5.0` Playwright folder structure

The migration should preserve:

- `fixtures/`
- `pages/`
- `specs/core/`
- `specs/biz/`
- `specs/contracts/`
- `support/`

This keeps the suite maintainable and makes future parity work straightforward.

### 3. Adapt only the parts that are repository-specific

The files that must be adapted to `customer/2.2.1` are:

- `tests/web-e2e/playwright.config.ts`
- `tests/web-e2e/support/storage-state.ts`
- `tests/web-e2e/support/mock-auth-server.mjs`
- `tests/.env.example`
- `tests/suites.json`
- `tests/run_gates.sh`

Primary adaptation:

- analytics dev server must launch from `source/dts-analytics-webapp/modern`

### 4. Improve suite semantics while keeping `v2.5.0` coverage intent

`v2.5.0` used both `web-e2e-core` and `web-e2e-full`, but they were effectively duplicates.
This migration keeps the same suite names but makes their meaning useful:

- `web-e2e-core`: five core specs
- `biz-e2e`: five business journey specs
- `web-e2e-full`: `core + biz`
- `web-e2e-quarantine`: optional quarantined spec(s)

This keeps coverage close to `v2.5.0` while fixing a known suite-design flaw.

### 5. Use a mock-first execution model in sprint 2

The sprint target is automation capability, not backend fidelity.

The migrated suite will use:

- real browser
- real frontend dev server
- local auth/storage bootstrap
- route mocks for unstable or missing backend flows

This is the right tradeoff for `customer/2.2.1` because it establishes the test harness now and leaves
backend realism improvements to later work.

## Migration Scope

### Core specs

- `auth-gateway.spec.ts`
- `platform-ai-assistant.spec.ts`
- `analytics-ai-query.spec.ts`
- `admin-ai-pack.spec.ts`
- `testid-contract.spec.ts`

### Business specs

- `erp-ingest-visible.spec.ts`
- `ai-modeling-flow.spec.ts`
- `analytics-publish-flow.spec.ts`
- `auth-rbac-hitl.spec.ts`
- `governance-remediation.spec.ts`

### Quarantine

- `seed-smoke.spec.ts`

## Worklog Layout

The worklog must use the numeric sprint sequence under `worklog/v2.2.1/`.
The existing numeric maximum is `sprint-1`, so the migration worklog will land at:

- `worklog/v2.2.1/sprint-2/`

Planned structure:

- `worklog/v2.2.1/sprint-2/README.md`
- `worklog/v2.2.1/sprint-2/tasks/`
- `worklog/v2.2.1/sprint-2/it/README.md`

The README will follow the existing `sprint-1/README.md` style:

- goals
- scope
- task list
- non-goals
- integration tests
- status tracking

Task cards will be more granular than the original `v2.5.0` breakdown.

## Verification Strategy

Verification is staged from cheap to expensive:

1. suite discovery and dry-run
2. three frontend builds
3. Playwright core execution
4. business suite execution
5. artifact/report validation

Primary verification commands:

- `python3 tests/run_suite.py --suite web-e2e-core --dry-run`
- `python3 tests/run_suite.py --suite web-e2e-full --dry-run`
- `python3 tests/run_suite.py --suite biz-e2e --dry-run`
- `python3 tests/run_suite.py --suite web-e2e-quarantine --include-optional --dry-run`
- `pnpm -C source/dts-platform-webapp build`
- `pnpm -C source/dts-admin-webapp build`
- `pnpm -C source/dts-analytics-webapp/modern build`

## Sprint Completion Criteria

- top-level Playwright test structure restored
- all four web suites discoverable
- three frontend builds pass
- `web-e2e-core` runs end-to-end in the migrated harness
- `biz-e2e` reaches a runnable state with artifact generation
- sprint worklog README and task files are fully landed
- known follow-up items for CI integration and backend realism are documented
