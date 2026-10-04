# Platform 承载 Analytics 菜单与路由统一 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 让 `dts-platform-webapp` 成为 BI 唯一入口壳，将 analytics 页面并入 platform 菜单体系，并由 `dts-admin` 统一管理 BI 菜单与默认入口。

**Architecture:** 使用 platform 现有菜单树、主布局、动态路由和权限裁剪作为唯一导航真源。`src/analytics/**` 保留页面与业务能力，但不再保留独立应用壳；建立 `/dashboard/bi/*` canonical route，并将旧 `/analytics/*` 收口为兼容跳转；公开分享页单独保留匿名路由。

**Tech Stack:** React 18, React Router 7, Vite, Zustand, Ant Design, JHipster Java services, Liquibase/menu seed.

---

### Task 1: 建立 BI canonical route 与兼容跳转骨架

**Files:**
- Create: `source/dts-platform-webapp/src/analytics/routes/routePaths.ts`
- Modify: `source/dts-platform-webapp/src/routes/sections/analytics.tsx`
- Modify: `source/dts-platform-webapp/src/routes/sections/index.tsx`
- Modify: `source/dts-platform-webapp/src/routes/sections/dashboard/index.tsx`
- Modify: `source/dts-platform-webapp/src/constants/portal-navigation.ts`
- Test: `source/dts-platform-webapp/src/analytics/routes/routePaths.test.ts`
- Test: `source/dts-platform-webapp/src/routes/sections/analyticsRoutes.test.tsx`

**Step 1: Write the failing tests**

Add route contract tests that assert:
- BI canonical paths resolve to `/dashboard/bi/*`
- legacy `/analytics/*` paths redirect to matching canonical routes
- public routes are not nested under authenticated BI shell

Example assertions:

```ts
test("analytics home canonical path points to /dashboard/bi/home", () => {
  expect(analyticsRoutePaths.home()).toBe("/dashboard/bi/home");
});

test("legacy analytics screens path redirects to canonical screen list", () => {
  expect(resolveLegacyAnalyticsRedirect("/analytics/screens")).toBe("/dashboard/bi/screens");
});
```

**Step 2: Run tests to verify they fail**

Run:

```bash
cd source/dts-platform-webapp
node --import ./node_modules/.pnpm/tsx@*/node_modules/tsx/dist/loader.mjs --test \
  src/analytics/routes/routePaths.test.ts \
  src/routes/sections/analyticsRoutes.test.tsx
```

Expected:
- FAIL because route helper and redirect behavior do not exist yet

**Step 3: Write minimal implementation**

- Add `routePaths.ts` exporting canonical BI routes and legacy redirect resolvers
- Refactor `analytics.tsx` to:
  - register canonical `/dashboard/bi/*` routes
  - register `/analytics/*` compatibility redirects
  - move `/public/card/:uuid`、`/public/dashboard/:uuid`、`/public/screen/:uuid` outside authenticated shell
- Stop using `/analytics` as default formal route in platform constants

**Step 4: Run tests to verify they pass**

Run:

```bash
cd source/dts-platform-webapp
node --import ./node_modules/.pnpm/tsx@*/node_modules/tsx/dist/loader.mjs --test \
  src/analytics/routes/routePaths.test.ts \
  src/routes/sections/analyticsRoutes.test.tsx
```

Expected:
- PASS

**Step 5: Commit**

```bash
git add \
  source/dts-platform-webapp/src/analytics/routes/routePaths.ts \
  source/dts-platform-webapp/src/analytics/routes/routePaths.test.ts \
  source/dts-platform-webapp/src/routes/sections/analytics.tsx \
  source/dts-platform-webapp/src/routes/sections/index.tsx \
  source/dts-platform-webapp/src/routes/sections/dashboard/index.tsx \
  source/dts-platform-webapp/src/constants/portal-navigation.ts \
  source/dts-platform-webapp/src/routes/sections/analyticsRoutes.test.tsx
git commit -m "feat(F1/T01): define BI canonical routes and legacy redirects"
```

### Task 2: 下线 analytics 独立应用壳并接入 platform 主布局

**Files:**
- Modify: `source/dts-platform-webapp/src/routes/sections/analytics.tsx`
- Modify: `source/dts-platform-webapp/src/analytics/layouts/AppLayout.tsx`
- Modify: `source/dts-platform-webapp/src/layouts/dashboard/main.tsx`
- Modify: `source/dts-platform-webapp/src/layouts/dashboard/nav/nav-data/index.tsx`
- Test: `source/dts-platform-webapp/src/analytics/layouts/AppLayout.integration-contract.test.tsx`

**Step 1: Write the failing test**

Add a test that asserts BI routes render under platform dashboard shell and no longer rely on analytics-owned menu items.

Example:

```tsx
test("BI routes no longer require analytics layout sidebar ownership", () => {
  expect(renderedBiRouteUsesPlatformShell()).toBe(true);
});
```

**Step 2: Run test to verify it fails**

Run:

```bash
cd source/dts-platform-webapp
node --import ./node_modules/.pnpm/tsx@*/node_modules/tsx/dist/loader.mjs --test \
  src/analytics/layouts/AppLayout.integration-contract.test.tsx
```

Expected:
- FAIL because current BI routes still mount `AnalyticsLayout`

**Step 3: Write minimal implementation**

- Remove BI route dependency on `AnalyticsLayout` as the outer shell
- Either retire `AppLayout.tsx` from route use or reduce it to local presentation helper only
- Ensure BI pages render inside platform dashboard layout with standard menu/tabs/breadcrumb lifecycle

**Step 4: Run test to verify it passes**

Run:

```bash
cd source/dts-platform-webapp
node --import ./node_modules/.pnpm/tsx@*/node_modules/tsx/dist/loader.mjs --test \
  src/analytics/layouts/AppLayout.integration-contract.test.tsx
```

Expected:
- PASS

**Step 5: Commit**

```bash
git add \
  source/dts-platform-webapp/src/routes/sections/analytics.tsx \
  source/dts-platform-webapp/src/analytics/layouts/AppLayout.tsx \
  source/dts-platform-webapp/src/layouts/dashboard/main.tsx \
  source/dts-platform-webapp/src/layouts/dashboard/nav/nav-data/index.tsx \
  source/dts-platform-webapp/src/analytics/layouts/AppLayout.integration-contract.test.tsx
git commit -m "feat(F1/T02): move BI pages under platform shell"
```

### Task 3: 统一 analytics 页面内部导航到 canonical route helper

**Files:**
- Modify: `source/dts-platform-webapp/src/analytics/routes/routePaths.ts`
- Modify: `source/dts-platform-webapp/src/analytics/pages/CardEditorPage.tsx`
- Modify: `source/dts-platform-webapp/src/analytics/pages/DashboardEditorPage.tsx`
- Modify: `source/dts-platform-webapp/src/analytics/pages/SearchPage.tsx`
- Modify: `source/dts-platform-webapp/src/analytics/pages/TableDetailPage.tsx`
- Modify: `source/dts-platform-webapp/src/analytics/pages/DatabaseNewPage.tsx`
- Modify: `source/dts-platform-webapp/src/analytics/pages/DatabaseEditPage.tsx`
- Modify: `source/dts-platform-webapp/src/analytics/pages/DatabaseDetailPage.tsx`
- Modify: `source/dts-platform-webapp/src/analytics/pages/FieldDetailPage.tsx`
- Modify: `source/dts-platform-webapp/src/analytics/pages/CollectionItemsPage.tsx`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/ScreensPage.tsx`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/ScreenDesignerPage.tsx`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/components/ScreenHeader.tsx`
- Modify: additional files discovered by:

```bash
rg -n 'navigate\\(|Link to=|href: \"/|window\\.location\\.href|window\\.location\\.assign|window\\.location\\.replace' \
  source/dts-platform-webapp/src/analytics -g '!**/*.test.*'
```

- Test: `source/dts-platform-webapp/src/analytics/routes/legacyPathSweep.source-contract.test.ts`

**Step 1: Write the failing test**

Add a source-contract test that forbids legacy root-path BI navigation strings such as:
- `"/questions"`
- `"/dashboards"`
- `"/screens"`
- `"/data"`
- `"/metrics"`
- `"/collections"`

outside the centralized route helper.

**Step 2: Run test to verify it fails**

Run:

```bash
cd source/dts-platform-webapp
node --import ./node_modules/.pnpm/tsx@*/node_modules/tsx/dist/loader.mjs --test \
  src/analytics/routes/legacyPathSweep.source-contract.test.ts
```

Expected:
- FAIL with multiple offending files

**Step 3: Write minimal implementation**

- Expand `routePaths.ts` to cover all BI pages and object-detail links
- Replace hardcoded paths in all analytics pages with helper calls
- Leave public share URLs explicit only if they intentionally point to anonymous public routes

**Step 4: Run tests to verify they pass**

Run:

```bash
cd source/dts-platform-webapp
node --import ./node_modules/.pnpm/tsx@*/node_modules/tsx/dist/loader.mjs --test \
  src/analytics/routes/legacyPathSweep.source-contract.test.ts
```

Expected:
- PASS

**Step 5: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/routes/routePaths.ts \
  source/dts-platform-webapp/src/analytics/routes/legacyPathSweep.source-contract.test.ts \
  source/dts-platform-webapp/src/analytics/pages \
  source/dts-platform-webapp/src/analytics/routes
git commit -m "fix(F1/T03): unify analytics internal navigation"
```

### Task 4: 将 BI 菜单与默认入口统一收口到 dts-admin

**Files:**
- Modify: `source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/PortalMenuService.java`
- Modify: `source/dts-admin/src/main/resources/config/liquibase/master.xml`
- Create: `source/dts-admin/src/main/resources/config/liquibase/changelog/20260330_01_portal_menu_bi_unification_seed.xml`
- Modify: existing seed resource loaded by `PortalMenuService` if applicable after confirming actual menu seed source
- Modify: `source/dts-platform-webapp/src/global-config.ts`
- Modify: `source/dts-platform-webapp/src/pages/sys/login/login-form.tsx`
- Test: `source/dts-admin/src/test/java/com/yuzhi/dts/admin/service/PortalMenuServiceTest.java`
- Test: `source/dts-platform-webapp/src/routes/defaultRoute.contract.test.ts`

**Step 1: Write the failing tests**

Add tests that assert:
- BI menu nodes exist in portal menu tree with canonical `/dashboard/bi/*` paths
- platform default route is not hardcoded to `/analytics`
- login redirect honors configured default BI menu route instead of a literal analytics path

**Step 2: Run tests to verify they fail**

Run:

```bash
cd source/dts-admin && ./mvnw -q -Dtest=PortalMenuServiceTest test
cd /opt/prod/s10/s10-stack/source/dts-platform-webapp
node --import ./node_modules/.pnpm/tsx@*/node_modules/tsx/dist/loader.mjs --test \
  src/routes/defaultRoute.contract.test.ts
```

Expected:
- FAIL because BI menu seed/default entry are not yet unified

**Step 3: Write minimal implementation**

- Add BI menu seed nodes under platform portal menus in `dts-admin`
- Point all BI menu paths at canonical `/dashboard/bi/*`
- Ensure platform default route resolves from menu/config, not a frontend literal `/analytics`

**Step 4: Run tests to verify they pass**

Run:

```bash
cd source/dts-admin && ./mvnw -q -Dtest=PortalMenuServiceTest test
cd /opt/prod/s10/s10-stack/source/dts-platform-webapp
node --import ./node_modules/.pnpm/tsx@*/node_modules/tsx/dist/loader.mjs --test \
  src/routes/defaultRoute.contract.test.ts
```

Expected:
- PASS

**Step 5: Commit**

```bash
git add \
  source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/PortalMenuService.java \
  source/dts-admin/src/main/resources/config/liquibase/master.xml \
  source/dts-admin/src/main/resources/config/liquibase/changelog/20260330_01_portal_menu_bi_unification_seed.xml \
  source/dts-admin/src/test/java/com/yuzhi/dts/admin/service/PortalMenuServiceTest.java \
  source/dts-platform-webapp/src/global-config.ts \
  source/dts-platform-webapp/src/pages/sys/login/login-form.tsx \
  source/dts-platform-webapp/src/routes/defaultRoute.contract.test.ts
git commit -m "feat(F1/T04): unify BI menu ownership in dts-admin"
```

### Task 5: 完成公开分享、构建与发布收口验证

**Files:**
- Modify: `worklog/v2.2.2/sprint-28-202603/README.md`
- Modify: `worklog/v2.2.2/sprint-28-202603/features/F1-Platform承载Analytics菜单与路由统一/README.md`
- Modify: `worklog/v2.2.2/sprint-28-202603/it/README.md`
- Modify: `worklog/v2.2.2/sprint-queue.md`
- Test: `source/dts-platform-webapp/src/routes/publicShareRoutes.test.tsx`

**Step 1: Write the failing test**

Add a route test that asserts:
- `/public/card/:uuid`
- `/public/dashboard/:uuid`
- `/public/screen/:uuid`

remain reachable without platform login shell.

**Step 2: Run test to verify it fails**

Run:

```bash
cd source/dts-platform-webapp
node --import ./node_modules/.pnpm/tsx@*/node_modules/tsx/dist/loader.mjs --test \
  src/routes/publicShareRoutes.test.tsx
```

Expected:
- FAIL if any public route still sits behind authenticated shell

**Step 3: Write minimal implementation**

- Finalize public route separation in router
- Update worklog statuses and IT checklist with actual verification commands
- Record compatibility mapping and rollout notes

**Step 4: Run verification**

Run:

```bash
cd source/dts-platform-webapp && pnpm build
cd /opt/prod/s10/s10-stack/source/dts-admin && ./mvnw -q -DskipTests compile
cd /opt/prod/s10/s10-stack/source/dts-platform-webapp
node --import ./node_modules/.pnpm/tsx@*/node_modules/tsx/dist/loader.mjs --test \
  src/routes/publicShareRoutes.test.tsx \
  src/analytics/routes/routePaths.test.ts \
  src/routes/sections/analyticsRoutes.test.tsx \
  src/analytics/routes/legacyPathSweep.source-contract.test.ts
```

Expected:
- PASS
- `pnpm build` may emit chunk-size warnings only

**Step 5: Commit**

```bash
git add \
  worklog/v2.2.2/sprint-28-202603/README.md \
  worklog/v2.2.2/sprint-28-202603/features/F1-Platform承载Analytics菜单与路由统一/README.md \
  worklog/v2.2.2/sprint-28-202603/it/README.md \
  worklog/v2.2.2/sprint-queue.md \
  source/dts-platform-webapp/src/routes/publicShareRoutes.test.tsx
git commit -m "chore(F1/T05): verify BI menu unification rollout"
```

### Execution Notes

- Use `rg` before code edits to sweep remaining hardcoded BI routes:

```bash
rg -n 'navigate\\(|Link to=|href: \"/|window\\.location\\.' \
  source/dts-platform-webapp/src/analytics -g '!**/*.test.*'
```

- Keep `public` routes outside `LoginAuthGuard`.
- Do not let `src/analytics/layouts/AppLayout.tsx` continue owning application navigation after Task 2.
- Prefer route helper usage everywhere; do not introduce fresh hardcoded `/dashboard/bi/*` strings in page files except tests.
- Update sprint statuses only after corresponding verification commands actually pass.
