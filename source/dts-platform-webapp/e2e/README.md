# SQL IDE E2E Smoke Suite

Sprint-11 accumulated 10 production bugs that code review and unit tests all missed because nobody drove the real UI before approval. This suite is the missing quality gate: 5 smoke tests against a running dev environment, each mapped to a specific bug class.

## Running locally

```bash
cd source/dts-platform-webapp

# Default: hits https://bi.yuzhicloud.com
pnpm e2e

# Override target (dev server)
E2E_BASE_URL=http://localhost:3001 pnpm e2e

# Custom credentials
E2E_USERNAME=opadmin E2E_PASSWORD=opadmin123 pnpm e2e

# Headed mode for debugging
pnpm e2e:headed

# Interactive UI mode
pnpm e2e:ui
```

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `E2E_BASE_URL` | `http://localhost:3001` | Base URL of the platform webapp |
| `E2E_USERNAME` | `opadmin` | Login username |
| `E2E_PASSWORD` | `opadmin123` | Login password |

Credentials are never hardcoded in source files. The auth setup calls `/api/keycloak/auth/login` and writes a Playwright storageState to `e2e/.auth/user.json` (gitignored).

## Test inventory

### `sqlide-smoke.spec.ts`
**Catches Sprint-11 bugs #5, #6**
- #5 — SqlIdePage had absolute positioning that caused the SQL IDE to render off-screen under the breadcrumb
- #6 — menu.component hardcoded a route that bypassed the `VITE_ENABLE_SQL_IDE_V2` flag, so the activity bar / mode switcher were never mounted

Verifies: breadcrumb "即席查询" visible, all four activity bar icons present, mode switcher (radiogroup) visible, Monaco editor initialises, bottom placeholder text present.

### `sqlide-tab-sync.spec.ts`
**Catches Sprint-11 bugs #7, #10**
- #7 — After the first debounced sync, the tab's `updatedAt` in the store was not updated, so the second PUT carried a stale timestamp and the server returned 409 Conflict
- #10 — `activeTabId` remap on restore chose the wrong tab ID, leaving the UI permanently stuck on "正在恢复 Tab……"

Verifies: no "正在恢复 Tab" spinner after 4 s, no 409 responses on `/api/sql/v2/tabs` after a second edit.

### `sqlide-schema.spec.ts`
**Catches Sprint-11 bugs #4, #3 (partial)**
- #4 — The catalog API returned a hardcoded Trino/Hive/Postgres stub list instead of reading from `infra_data_source`
- #3 — QueryGateway contract passed wrong datasource ID so the real PG source was never selectable

Verifies: schema panel shows "数据仓库" or "biadmin" (the one real datasource), does NOT show "Trino · default" or "Apache Hive".

### `sqlide-execute.spec.ts`
**Catches Sprint-11 bugs #1, #2, #3, #8**
- #1 — `JdbcSqlExecutor` was not wired into the execution path; the Trino stub was still invoked and returned an empty/error result
- #2 — The chunk-persist path persisted a null result set when execution succeeded, so the FE got no rows
- #3 — QueryGateway sent the wrong datasource ID in the execution request
- #8 — FE polling stopped before the terminal state arrived, leaving the status badge permanently on "运行中"

Verifies: `SELECT ... FROM information_schema.tables LIMIT 5` succeeds ("成功" badge visible), "❌ 执行失败" not visible.

### `sqlide-chart-tab.spec.ts`
**Catches Sprint-11 bug #9**
- #9 — `ResultChart.tsx` called a React hook conditionally (inside an `if`), which caused "Rendered more hooks than previous render" when switching to the Chart tab after a successful query

Verifies: clicking "图表" tab after execution produces no `pageerror` events containing "Rendered more hooks".

## Adding a new test

1. Create `e2e/your-test.spec.ts`.
2. Import `{ expect, test } from "@playwright/test"` — the global `storageState` from `playwright.config.ts` handles auth automatically.
3. Use `page.goto("/#/your/route")` — the hash router prefix `#` is required.
4. Run `pnpm e2e` to verify locally before committing.
5. Document which bug / regression the test catches in the file header comment.

## What is NOT covered

- CI wiring (deferred)
- Cross-browser matrix (Chromium only)
- Test data seeding
- Tests beyond the 5 Sprint-11 smoke cases
