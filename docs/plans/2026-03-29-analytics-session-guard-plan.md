# Analytics Session Guard Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a unified analytics route guard so non-public pages redirect to platform login when no platform session token is available, instead of falling into page-level 401 error states.

**Architecture:** Introduce a small route/session helper layer that classifies public analytics paths and builds the platform login redirect URL. Wrap non-public analytics routes with a guard component so fullscreen editor routes and AppLayout routes share the same session requirement while `/public/*` routes remain anonymously accessible.

**Tech Stack:** React Router 7, React 19, TypeScript, node:test, tsx

---

### Task 1: Add failing tests for route/session helper behavior

**Files:**
- Create: `source/dts-analytics-webapp/modern/src/routes/sessionGuard.test.ts`
- Create: `source/dts-analytics-webapp/modern/src/routes/sessionGuard.ts`

**Step 1: Write the failing test**

Cover these behaviors:
- `/public/card/:uuid`, `/public/dashboard/:uuid`, `/public/screen/:uuid` do not require a platform session
- `/`, `/screens`, `/screens/:id/edit`, `/screens/:id/preview`, `/screens/:id/export` do require a platform session
- Missing access token produces `/#/auth/login?redirect=...` with the current analytics path

**Step 2: Run test to verify it fails**

Run: `node --import tsx --test src/routes/sessionGuard.test.ts`

Expected: FAIL because helper does not exist yet.

### Task 2: Implement the minimal session guard helper

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/routes/sessionGuard.ts`
- Reference: `source/dts-analytics-webapp/modern/src/api/platformSession.ts`

**Step 1: Write minimal implementation**

Add pure helpers:
- `isPublicAnalyticsPath(pathname)`
- `requiresPlatformSession(pathname)`
- `buildPlatformLoginRedirect(pathname, search)`
- `hasPlatformSessionAccessToken()`

Keep logic small and path-based so it stays easy to test.

**Step 2: Run test to verify it passes**

Run: `node --import tsx --test src/routes/sessionGuard.test.ts`

Expected: PASS.

### Task 3: Wire the guard into analytics routes

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/routes.tsx`

**Step 1: Add a route guard component**

Use React Router navigation to redirect when:
- current path requires platform session
- `hasPlatformSessionAccessToken()` is false

Do not block `/public/*`.

**Step 2: Apply the guard to both route groups**

Protect:
- fullscreen non-public screen routes
- the main `AppLayout` route tree

Leave public screen/card/dashboard routes outside the guard.

### Task 4: Add a source-level regression check for the route guard wiring

**Files:**
- Create: `source/dts-analytics-webapp/modern/src/routes.source-contract.test.ts`

**Step 1: Write a small contract test**

Assert that `routes.tsx` references the session guard helper/component so future refactors do not remove it silently.

**Step 2: Run tests**

Run: `node --import tsx --test src/routes/sessionGuard.test.ts src/routes.source-contract.test.ts`

Expected: PASS.

### Task 5: Verify build and browser behavior

**Files:**
- Verify only

**Step 1: Build**

Run: `pnpm build`

Expected: PASS.

**Step 2: Browser verification**

Verify:
- `http://127.0.0.1:3002/analytics/screens` redirects to `/#/auth/login?...` when no `userStore` exists
- `http://127.0.0.1:3002/analytics/public/screen/<uuid>` remains reachable without forcing login
