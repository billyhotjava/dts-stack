# v2.2.1 Web E2E Migration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Migrate the `v2.5.0` Playwright web automation layer into `customer/2.2.1`, adapt it to the current repo layout, and land `worklog/v2.2.1/sprint-2/` with granular tasks.

**Architecture:** Restore the top-level `tests/` surface and `tests/web-e2e/` assets from the old branch, but trim suite wiring to web-only coverage. Keep the page object and mock-first structure from `v2.5.0`, adapt the analytics app path to `modern`, then verify suite discovery, builds, and core execution.

**Tech Stack:** Python runner, Playwright, TypeScript, Node.js, pnpm, React/Vite

---

### Task 1: Create the failing baseline and land the design/worklog scaffolding

**Files:**
- Create: `worklog/v2.2.1/sprint-2/README.md`
- Create: `worklog/v2.2.1/sprint-2/it/README.md`
- Create: `worklog/v2.2.1/sprint-2/tasks/WE-001-tests-root-bootstrap.md`
- Create: `worklog/v2.2.1/sprint-2/tasks/WE-002-playwright-runtime-bootstrap.md`
- Create: `worklog/v2.2.1/sprint-2/tasks/WE-003-platform-suite-migration.md`
- Create: `worklog/v2.2.1/sprint-2/tasks/WE-004-admin-suite-migration.md`
- Create: `worklog/v2.2.1/sprint-2/tasks/WE-005-analytics-suite-migration.md`
- Create: `worklog/v2.2.1/sprint-2/tasks/WE-006-suite-wiring-and-verification.md`

**Step 1: Write the failing baseline**

Run:

```bash
python3 tests/run_suite.py --suite web-e2e-core --dry-run
```

Expected:

- failure because `tests/run_suite.py` does not exist yet

**Step 2: Create the sprint-2 worklog shell**

Add the sprint README in `sprint-1` style and create granular task cards for the migration batches.

**Step 3: Re-run the failing baseline**

Run the same command again.
Expected:

- it still fails
- failure remains caused by missing migrated test assets, not worklog files

### Task 2: Restore the top-level web test runner layer

**Files:**
- Create: `tests/README.md`
- Create: `tests/.env.example`
- Create: `tests/run_suite.py`
- Create: `tests/run_gates.sh`
- Create: `tests/suites.json`
- Create: `tests/reports/README.md`
- Create: `tests/reports/.gitkeep`

**Step 1: Write the failing discovery check**

Run:

```bash
python3 tests/run_suite.py --suite web-e2e-core --dry-run
```

Expected:

- failure caused by missing suite registry or missing files under `tests/web-e2e`

**Step 2: Add minimal web-only runner assets**

Port the generic runner/reporting logic and trim `suites.json` / `run_gates.sh` to the four web suites only.

**Step 3: Verify the runner fails for the right next reason**

Run:

```bash
python3 tests/run_suite.py --suite web-e2e-core --dry-run
```

Expected:

- command selection works
- failure, if any, is now caused by missing `tests/web-e2e` files rather than missing runner assets

### Task 3: Restore the Playwright project and common support

**Files:**
- Create: `tests/web-e2e/README.md`
- Create: `tests/web-e2e/package.json`
- Create: `tests/web-e2e/pnpm-lock.yaml`
- Create: `tests/web-e2e/playwright.config.ts`
- Create: `tests/web-e2e/global.setup.ts`
- Create: `tests/web-e2e/tsconfig.json`
- Create: `tests/web-e2e/.gitignore`
- Create: `tests/web-e2e/reports/.gitkeep`
- Create: `tests/web-e2e/fixtures/`
- Create: `tests/web-e2e/support/`

**Step 1: Write the failing support-level check**

Run:

```bash
python3 tests/run_suite.py --suite web-e2e-core --dry-run
```

Expected:

- dry-run succeeds or exposes missing spec/page/support paths

**Step 2: Restore Playwright runtime files**

Port the common fixtures, storage state handling, mock auth server, and support helpers from `v2.5.0`.

**Step 3: Adapt current-repo specifics**

Change:

- analytics app startup path to `source/dts-analytics-webapp/modern`
- URL defaults and storage-state logic to current app paths

**Step 4: Verify suite discovery works**

Run:

```bash
python3 tests/run_suite.py --suite web-e2e-core --dry-run
python3 tests/run_suite.py --suite web-e2e-full --dry-run
python3 tests/run_suite.py --suite biz-e2e --dry-run
python3 tests/run_suite.py --suite web-e2e-quarantine --include-optional --dry-run
```

Expected:

- all four commands exit successfully
- case IDs and Playwright commands are printed

### Task 4: Migrate core specs, page objects, and mocks for all three apps

**Files:**
- Create: `tests/web-e2e/pages/`
- Create: `tests/web-e2e/specs/core/`
- Create: `tests/web-e2e/specs/contracts/`
- Create: `tests/web-e2e/specs/biz/`

**Step 1: Write the failing Playwright list command**

Run:

```bash
pnpm --dir tests/web-e2e exec playwright test --list
```

Expected:

- fails because specs/pages/support are incomplete or dependencies are not installed yet

**Step 2: Port core specs and supporting page objects**

Bring over:

- auth
- platform AI
- admin AI pack
- analytics AI query
- selector contract

**Step 3: Port business specs and app-specific mocks**

Bring over the five `biz` specs and their support/mocks.

**Step 4: Re-run the list command**

Run:

```bash
pnpm --dir tests/web-e2e exec playwright test --list
```

Expected:

- all migrated specs are discovered

### Task 5: Finalize suite composition and reports

**Files:**
- Modify: `tests/suites.json`
- Modify: `tests/run_gates.sh`
- Modify: `tests/README.md`
- Modify: `tests/web-e2e/README.md`
- Modify: `tests/.env.example`

**Step 1: Write the failing semantic expectation**

Run:

```bash
python3 tests/run_suite.py --suite web-e2e-full --dry-run
```

Expected:

- output does not yet reflect the desired `core + biz` semantics, or docs are incomplete

**Step 2: Fix suite semantics**

Ensure:

- `web-e2e-core` = 5 core cases
- `biz-e2e` = 5 business cases
- `web-e2e-full` = core + biz
- `web-e2e-quarantine` = optional quarantined case(s)

**Step 3: Verify dry-run outputs**

Run:

```bash
python3 tests/run_suite.py --suite web-e2e-core --dry-run
python3 tests/run_suite.py --suite web-e2e-full --dry-run
python3 tests/run_suite.py --suite biz-e2e --dry-run
```

Expected:

- suite composition matches the design

### Task 6: Install dependencies and verify frontend builds

**Files:**
- No new files required

**Step 1: Write the failing build baseline**

Run:

```bash
pnpm -C source/dts-platform-webapp build
pnpm -C source/dts-admin-webapp build
pnpm -C source/dts-analytics-webapp/modern build
```

Expected:

- record any pre-existing failures before blaming the migrated suite

**Step 2: Install Playwright project dependencies**

Run:

```bash
pnpm install --frozen-lockfile --dir tests/web-e2e
```

**Step 3: Re-run app builds**

Run the three build commands again.
Expected:

- all three builds still pass

### Task 7: Verify the migrated browser suite

**Files:**
- No new files required unless fixes are needed

**Step 1: Run the core suite**

Run:

```bash
python3 tests/run_suite.py --suite web-e2e-core --fail-fast
```

Expected:

- suite runs and produces reports/artifacts

**Step 2: Run the business suite**

Run:

```bash
python3 tests/run_suite.py --suite biz-e2e --fail-fast
```

Expected:

- suite runs and produces reports/artifacts

**Step 3: Run the full suite**

Run:

```bash
python3 tests/run_suite.py --suite web-e2e-full --fail-fast
```

Expected:

- full suite runs and produces reports/artifacts

### Task 8: Finalize documentation and capture residual gaps

**Files:**
- Modify: `worklog/v2.2.1/sprint-2/README.md`
- Modify: `worklog/v2.2.1/sprint-2/it/README.md`
- Modify: `worklog/v2.2.1/sprint-2/tasks/*.md`

**Step 1: Update task statuses with evidence**

Record what was migrated, what commands passed, and any known quarantine/fidelity gaps.

**Step 2: Record next-step follow-ups**

Document later work for:

- CI integration
- reduced mocking
- flaky case management
- broader report gating

**Step 3: Final verification**

Run:

```bash
python3 tests/run_suite.py --suite web-e2e-core --dry-run
python3 tests/run_suite.py --suite web-e2e-full --dry-run
python3 tests/run_suite.py --suite biz-e2e --dry-run
python3 tests/run_suite.py --suite web-e2e-quarantine --include-optional --dry-run
```

Expected:

- all suite registrations still work after documentation updates
