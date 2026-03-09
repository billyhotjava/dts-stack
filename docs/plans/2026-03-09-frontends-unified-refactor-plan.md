# Unified Frontend Refactor Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Refactor the `2.2.1` customer line frontends into one light-first product experience and remove production fake/demo/placeholder materials.

**Architecture:** Keep the current three-app topology, but impose one design contract across shared console shell, analytics workspace shell, and analytics runtime shell. Delete invalid materials first, then refactor shells, then refactor customer-visible page families, with build verification after each task.

**Tech Stack:** React, React Router, Tailwind CSS, Ant Design, local CSS token layers, Vite, TypeScript

---

### Task 1: Land the shared light-first design contract

**Files:**
- Modify: `source/dts-platform-webapp/src/global.css`
- Modify: `source/dts-admin-webapp/src/global.css`
- Modify: `source/dts-analytics-webapp/modern/src/styles/tokens.css`
- Modify: `source/dts-analytics-webapp/modern/src/styles.css`
- Modify: `source/dts-analytics-webapp/modern/src/layouts/layout.css`
- Reference: `docs/plans/2026-03-09-frontends-unified-refactor-design.md`

**Step 1: Freeze the canonical token names**

Create one canonical mapping for:

- page/card/floating/workspace surfaces
- primary/secondary/muted/disabled text
- border/shadow semantics
- status colors
- chart palette
- radius and spacing scale

Record any legacy aliases that must be preserved temporarily.

**Step 2: Update platform/admin global tokens**

Refactor `:root` and `[data-theme-mode="dark"]` token blocks so both apps expose the same contract and the same light-first defaults.

**Step 3: Update analytics token and base style layers**

Refactor analytics tokens and global base styles so the console shell and workspace shell consume the same contract as platform/admin.

**Step 4: Verify builds**

Run:

- `pnpm -C source/dts-platform-webapp build`
- `pnpm -C source/dts-admin-webapp build`
- `pnpm -C source/dts-analytics-webapp/modern build`

Expected:

- all three builds pass
- no missing CSS variable failures

**Step 5: Commit**

```bash
git add source/dts-platform-webapp/src/global.css source/dts-admin-webapp/src/global.css source/dts-analytics-webapp/modern/src/styles/tokens.css source/dts-analytics-webapp/modern/src/styles.css source/dts-analytics-webapp/modern/src/layouts/layout.css
git commit -m "refactor: align frontend design tokens"
```

### Task 2: Refactor the shared console shell for platform and admin

**Files:**
- Modify: `source/dts-platform-webapp/src/layouts/dashboard/header.tsx`
- Modify: `source/dts-platform-webapp/src/layouts/dashboard/main.tsx`
- Modify: `source/dts-platform-webapp/src/layouts/dashboard/nav/nav-vertical-layout.tsx`
- Modify: `source/dts-admin-webapp/src/layouts/dashboard/header.tsx`
- Modify: `source/dts-admin-webapp/src/layouts/dashboard/main.tsx`
- Modify: `source/dts-admin-webapp/src/layouts/dashboard/nav/nav-vertical-layout.tsx`
- Optional: `source/dts-platform-webapp/src/layouts/components/search-bar.tsx`
- Optional: `source/dts-admin-webapp/src/layouts/components/search-bar.tsx`

**Step 1: Refactor shell spacing and header structure**

Make header, breadcrumb zone, action zone, and content spacing match the new console shell geometry from the design doc.

**Step 2: Refactor sidebar styling**

Keep the sidebar dark and prominent, but align active states, section headers, hover states, and icon rhythm to the new design contract.

**Step 3: Verify route rendering assumptions**

Review pages under:

- `source/dts-platform-webapp/src/routes/sections`
- `source/dts-admin-webapp/src/routes/sections`

Confirm the shell refactor does not break layout assumptions for normal pages.

**Step 4: Verify builds**

Run:

- `pnpm -C source/dts-platform-webapp build`
- `pnpm -C source/dts-admin-webapp build`

Expected:

- both builds pass
- no broken imports or TSX layout errors

**Step 5: Commit**

```bash
git add source/dts-platform-webapp/src/layouts/dashboard source/dts-admin-webapp/src/layouts/dashboard source/dts-platform-webapp/src/layouts/components/search-bar.tsx source/dts-admin-webapp/src/layouts/components/search-bar.tsx
git commit -m "refactor: unify admin and platform console shell"
```

### Task 3: Remove platform and admin fake/demo/placeholder materials

**Files:**
- Modify or delete: `source/dts-platform-webapp/src/layouts/components/notice.tsx`
- Modify or delete: `source/dts-admin-webapp/src/layouts/components/notice.tsx`
- Delete or replace: `source/dts-platform-webapp/src/api/services/demoService.ts`
- Delete or replace: `source/dts-admin-webapp/src/api/services/demoService.ts`
- Delete or replace: `source/dts-platform-webapp/src/pages/common/FeaturePlaceholder.tsx`
- Delete or replace: `source/dts-platform-webapp/src/pages/common/V3Placeholder.tsx`
- Review: `source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx`
- Review: `source/dts-admin-webapp/src/routes/sections/dashboard/frontend.tsx`
- Review: `worklog/v2.2.1/frontend-refactor-sprint-01/fake-material-inventory.md`

**Step 1: Remove fake notice datasets**

Replace fake notification content with:

- real empty state behavior, or
- no entry at all if the feature is not customer-facing

**Step 2: Remove demo service references**

Delete or replace every production import of `demoService`.

**Step 3: Remove placeholder page surfaces**

Delete placeholder-only pages and repair route/menu references so customer-visible routes no longer depend on them.

**Step 4: Verify builds**

Run:

- `pnpm -C source/dts-platform-webapp build`
- `pnpm -C source/dts-admin-webapp build`

Expected:

- builds pass
- no unresolved imports
- no route module failures

**Step 5: Commit**

```bash
git add source/dts-platform-webapp/src/layouts/components/notice.tsx source/dts-admin-webapp/src/layouts/components/notice.tsx source/dts-platform-webapp/src/api/services source/dts-admin-webapp/src/api/services source/dts-platform-webapp/src/pages/common source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx source/dts-admin-webapp/src/routes/sections/dashboard/frontend.tsx worklog/v2.2.1/frontend-refactor-sprint-01/fake-material-inventory.md
git commit -m "refactor: remove platform and admin fake surfaces"
```

### Task 4: Refactor customer-visible admin pages

**Files:**
- Modify: `source/dts-admin-webapp/src/admin/views/infra-settings.tsx`
- Modify: `source/dts-admin-webapp/src/admin/views/workflow-config.tsx`
- Modify: `source/dts-admin-webapp/src/admin/views/system/other-config.tsx`
- Review: `source/dts-admin-webapp/src/routes/admin-routes.tsx`

**Step 1: Replace placeholder copy and dead material**

Remove reserved wording and dummy sections. Replace them with:

- real configuration group cards
- explicit empty states
- constrained unavailable states when backend capability is absent

**Step 2: Apply the shared page primitives**

Use the new `PageHeader`, `SectionCard`, and `FilterBar` language consistently in the admin views above.

**Step 3: Verify build**

Run:

- `pnpm -C source/dts-admin-webapp build`

Expected:

- build passes
- no route-level breakage in admin pages

**Step 4: Commit**

```bash
git add source/dts-admin-webapp/src/admin/views source/dts-admin-webapp/src/routes/admin-routes.tsx
git commit -m "refactor: refresh customer-facing admin pages"
```

### Task 5: Refactor customer-visible platform pages

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/foundation/TaskSchedulingPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/explore/etl/TransformDetailPage.tsx` if shell-level styling needs follow-up
- Review: `source/dts-platform-webapp/src/routes/sections/main.tsx`
- Review: `source/dts-platform-webapp/src/routes/sections/dashboard/index.tsx`

**Step 1: Remove reserved and misleading content**

Replace customer-visible placeholder blocks with:

- actual page structure
- empty state cards
- operator-readable messages

**Step 2: Apply new page shell patterns**

Align page headers, cards, information grouping, and status surfaces to the new console shell.

**Step 3: Verify build**

Run:

- `pnpm -C source/dts-platform-webapp build`

Expected:

- build passes
- no broken route chunks

**Step 4: Commit**

```bash
git add source/dts-platform-webapp/src/pages/foundation/TaskSchedulingPage.tsx source/dts-platform-webapp/src/pages/explore/etl/TransformDetailPage.tsx source/dts-platform-webapp/src/routes/sections
git commit -m "refactor: refresh customer-facing platform pages"
```

### Task 6: Refactor analytics console shell

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/layouts/AppLayout.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/layouts/layout.css`
- Modify: `source/dts-analytics-webapp/modern/src/routes.tsx`
- Review: analytics list/detail pages mounted under `AppLayout`

**Step 1: Rebuild analytics navigation shell**

Make analytics navigation, breadcrumb, header, user menu, and sidebar interactions match the unified console shell.

**Step 2: Align list/detail page containers**

Ensure normal analytics pages under `AppLayout` render within the same page paddings, card surfaces, and action header structure as admin/platform.

**Step 3: Verify build**

Run:

- `pnpm -C source/dts-analytics-webapp/modern build`

Expected:

- build passes
- analytics routing still works under `/analytics`

**Step 4: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/layouts/AppLayout.tsx source/dts-analytics-webapp/modern/src/layouts/layout.css source/dts-analytics-webapp/modern/src/routes.tsx
git commit -m "refactor: align analytics console shell"
```

### Task 7: Refactor analytics workspace shell

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/ScreenDesignerPage.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/ScreenDesigner.css`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/components/ScreenHeader.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/components/CanvasToolbar.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/components/PropertyPanel.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/components/LayerPanel.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/components/PageManagerPanel.tsx`

**Step 1: Define workspace density rules**

Apply the new workspace shell without flattening the designer into a generic admin list page.

**Step 2: Refactor top toolbar and side panels**

Make toolbars, tabs, inspectors, and layer/page panels visually match the new product family.

**Step 3: Remove inline-style drift where touched**

Prefer the token contract and structured CSS over ad-hoc inline styling in touched workspace components.

**Step 4: Verify build**

Run:

- `pnpm -C source/dts-analytics-webapp/modern build`

Expected:

- build passes
- no workspace component import errors

**Step 5: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/ScreenDesignerPage.tsx source/dts-analytics-webapp/modern/src/pages/screens/ScreenDesigner.css source/dts-analytics-webapp/modern/src/pages/screens/components/ScreenHeader.tsx source/dts-analytics-webapp/modern/src/pages/screens/components/CanvasToolbar.tsx source/dts-analytics-webapp/modern/src/pages/screens/components/PropertyPanel.tsx source/dts-analytics-webapp/modern/src/pages/screens/components/LayerPanel.tsx source/dts-analytics-webapp/modern/src/pages/screens/components/PageManagerPanel.tsx
git commit -m "refactor: unify analytics workspace shell"
```

### Task 8: Refactor analytics runtime shell

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/PublicScreenPage.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/ScreenPreviewPage.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/ScreenExportPage.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/components/PreviewScaleControl.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/components/DeviceModeSwitcher.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/components/GlobalVariablePanel.tsx`

**Step 1: Refactor runtime control surfaces**

Unify the visible control bars, zoom controls, device switches, and variable panels with the new shell contract.

**Step 2: Keep screen canvas theming intact**

Do not hard-force all public screens into the admin surface palette. Only the shell and control layer are standardized.

**Step 3: Verify build**

Run:

- `pnpm -C source/dts-analytics-webapp/modern build`

Expected:

- build passes
- public screen runtime still compiles

**Step 4: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/PublicScreenPage.tsx source/dts-analytics-webapp/modern/src/pages/screens/ScreenPreviewPage.tsx source/dts-analytics-webapp/modern/src/pages/screens/ScreenExportPage.tsx source/dts-analytics-webapp/modern/src/pages/screens/components/PreviewScaleControl.tsx source/dts-analytics-webapp/modern/src/pages/screens/components/DeviceModeSwitcher.tsx source/dts-analytics-webapp/modern/src/pages/screens/components/GlobalVariablePanel.tsx
git commit -m "refactor: align analytics runtime shell"
```

### Task 9: Remove analytics fake/demo/placeholder materials

**Files:**
- Modify or delete: `source/dts-analytics-webapp/modern/src/i18n.ts`
- Modify or delete: `source/dts-analytics-webapp/modern/src/pages/screens/plugins/builtinPluginAdapters.tsx`
- Modify or replace: `source/dts-analytics-webapp/modern/src/pages/screens/components/ScreenHealthPanel.tsx`
- Modify or replace: `source/dts-analytics-webapp/modern/src/pages/screens/components/ComponentRenderer.tsx`
- Review: `source/dts-analytics-webapp/modern/src/pages/screens/plugins/useScreenPluginRuntime.ts`

**Step 1: Remove demo-only plugin and placeholder flows**

Delete demo plugin adapters and any production path that depends on fake screen widgets.

**Step 2: Replace placeholder logic with real empty or unavailable states**

Where analytics components still need a visible fallback, use product-grade empty states instead of demo data.

**Step 3: Verify build**

Run:

- `pnpm -C source/dts-analytics-webapp/modern build`

Expected:

- build passes
- no dead imports into removed demo modules

**Step 4: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/i18n.ts source/dts-analytics-webapp/modern/src/pages/screens/plugins/builtinPluginAdapters.tsx source/dts-analytics-webapp/modern/src/pages/screens/components/ScreenHealthPanel.tsx source/dts-analytics-webapp/modern/src/pages/screens/components/ComponentRenderer.tsx source/dts-analytics-webapp/modern/src/pages/screens/plugins/useScreenPluginRuntime.ts
git commit -m "refactor: remove analytics fake surfaces"
```

### Task 10: Verify the sprint and close the visual audit backlog

**Files:**
- Modify: `worklog/v2.2.1/frontend-refactor-sprint-01/status-board.md`
- Modify: `worklog/v2.2.1/frontend-refactor-sprint-01/README.md`
- Modify: `worklog/v2.2.1/frontend-refactor-sprint-01/fake-material-inventory.md`

**Step 1: Run full frontend build verification**

Run:

- `pnpm -C source/dts-platform-webapp build`
- `pnpm -C source/dts-admin-webapp build`
- `pnpm -C source/dts-analytics-webapp/modern build`

Expected:

- all builds pass

**Step 2: Complete manual visual audit checklist**

Verify:

- shared console shell alignment
- no customer-visible placeholder copy
- no fake notice surfaces
- analytics designer and runtime shells visually match the unified product family

**Step 3: Update sprint evidence**

Mark task states, record verification commands, and note any residual risks that remain outside the sprint.

**Step 4: Commit**

```bash
git add worklog/v2.2.1/frontend-refactor-sprint-01
git commit -m "docs: close frontend refactor sprint verification"
```

Plan complete and saved to `docs/plans/2026-03-09-frontends-unified-refactor-plan.md`. Two execution options:

1. Subagent-Driven (this session) - I dispatch fresh subagent per task, review between tasks, fast iteration
2. Parallel Session (separate) - Open new session with executing-plans, batch execution with checkpoints

Which approach?
