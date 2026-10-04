# Finance Screen Family Plugin Refactor Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a reusable finance screen component family for analytics big screens, then migrate the four finance templates onto it without breaking designer editability.

**Architecture:** Keep the existing screen designer and runtime protocol intact, add finance-specific renderer plugins as reusable block components, then rewrite the four finance templates to compose those blocks with existing chart renderers. The migration is template-level only; existing created screens are not auto-migrated.

**Tech Stack:** React, TypeScript, Vite, analytics modern screen designer/runtime, renderer plugin registry, screen template system, Node test runner, existing chart renderers.

---

### Task 1: 财务插件基础层

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/plugins/builtinPluginAdapters.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/componentLibrary.ts`
- Create: `source/dts-analytics-webapp/modern/src/pages/screens/plugins/custom/finance__shell.tsx`
- Create: `source/dts-analytics-webapp/modern/src/pages/screens/plugins/custom/finance__header-bar.tsx`
- Create: `source/dts-analytics-webapp/modern/src/pages/screens/plugins/custom/finance__filter-strip.tsx`
- Create: `source/dts-analytics-webapp/modern/src/pages/screens/plugins/custom/finance__kpi-card.tsx`
- Create: `source/dts-analytics-webapp/modern/src/pages/screens/plugins/custom/finance__ranking-list.tsx`
- Create: `source/dts-analytics-webapp/modern/src/pages/screens/plugins/custom/finance__summary-table.tsx`
- Create: `source/dts-analytics-webapp/modern/src/pages/screens/plugins/custom/finance__note-panel.tsx`
- Create: `source/dts-analytics-webapp/modern/src/pages/screens/plugins/custom/finance__status-grid.tsx`
- Create: `source/dts-analytics-webapp/modern/src/pages/screens/plugins/custom/financeShared.tsx`
- Test: `source/dts-analytics-webapp/modern/src/pages/screens/plugins/custom/financePluginAdapters.test.tsx`

**Step 1: Write failing plugin registration tests**

- Verify each finance plugin adapter can be discovered and registered
- Verify plugin ids and base types are stable
- Verify property schema exists for finance components

**Step 2: Run focused test and verify failure**

Run:

```bash
cd source/dts-analytics-webapp/modern
node --import ./node_modules/.pnpm/tsx@4.19.4/node_modules/tsx/dist/loader.mjs --test src/pages/screens/plugins/custom/financePluginAdapters.test.tsx
```

**Step 3: Implement finance shared rendering layer**

- Build a shared white-card finance design token helper
- Implement each finance block as a renderer plugin
- Keep each block independent and designer-editable

**Step 4: Register adapters and expose library entries**

- Hook adapters into builtin plugin installation
- Add a “财务组件” category to component library

**Step 5: Re-run focused test**

Use the same command and make sure it passes.

### Task 2: 财务组件属性面板与设计器契约

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/plugins/types.ts`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/components/PropertyPanel.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/specV2.ts`
- Test: `source/dts-analytics-webapp/modern/src/pages/screens/specV2.finance-plugin.test.ts`
- Test: `source/dts-analytics-webapp/modern/src/pages/screens/componentLibraryPlugins.test.ts`

**Step 1: Write failing contract tests**

- Finance components survive spec normalization
- Finance component configs can round-trip through designer state
- Property schema fields are surfaced in property panel

**Step 2: Run tests and confirm failure**

Run:

```bash
cd source/dts-analytics-webapp/modern
node --import ./node_modules/.pnpm/tsx@4.19.4/node_modules/tsx/dist/loader.mjs --test src/pages/screens/specV2.finance-plugin.test.ts src/pages/screens/componentLibraryPlugins.test.ts
```

**Step 3: Implement minimal support**

- Ensure finance component config is preserved by spec normalization
- Ensure property panel can edit the schema-backed fields
- Avoid introducing bespoke one-off panel logic if schema-driven rendering already fits

**Step 4: Re-run tests**

Use the same command and make sure it passes.

### Task 3: 四套财务模板迁移

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/screenTemplates.ts`
- Test: `source/dts-analytics-webapp/modern/src/pages/screens/financeTemplates.test.ts`

**Step 1: Write failing template tests**

- The four finance template ids still exist
- Each template now contains finance block components
- Each template keeps key chart regions and key content sections

**Step 2: Run the test and verify failure**

Run:

```bash
cd source/dts-analytics-webapp/modern
node --import ./node_modules/.pnpm/tsx@4.19.4/node_modules/tsx/dist/loader.mjs --test src/pages/screens/financeTemplates.test.ts
```

**Step 3: Rewrite the four templates**

- Replace low-fidelity dark `glacier` finance template definitions
- Compose the new finance plugins with existing chart components
- Preserve existing template ids:
  - `fin-auxiliary-balance`
  - `fin-own-fund`
  - `fin-personal-balance`
  - `fin-project-fund`

**Step 4: Re-run template tests**

Use the same command and make sure it passes.

### Task 4: 运行时与预览回归

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/components/ComponentRenderer.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/PublicScreenPage.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/ScreenPreviewPage.tsx`
- Test: `source/dts-analytics-webapp/modern/src/pages/screens/financePreviewRuntime.test.tsx`

**Step 1: Write failing runtime regression tests**

- Finance plugins render in preview mode
- Finance plugins render in public/runtime mode
- Missing optional config does not crash rendering

**Step 2: Run tests and confirm failure**

Run:

```bash
cd source/dts-analytics-webapp/modern
node --import ./node_modules/.pnpm/tsx@4.19.4/node_modules/tsx/dist/loader.mjs --test src/pages/screens/financePreviewRuntime.test.tsx
```

**Step 3: Implement runtime fixes**

- Patch renderer integration if any finance plugin path is missing
- Keep runtime fallback behavior defensive

**Step 4: Re-run runtime tests**

Use the same command and make sure it passes.

### Task 5: 验证、文档与交付记录

**Files:**
- Modify: `worklog/v2.2.2/sprint-27-202603/README.md`
- Modify: `worklog/v2.2.2/sprint-27-202603/features/F1-财务大屏模板族插件化重构/README.md`
- Modify: `worklog/v2.2.2/sprint-27-202603/it/README.md`

**Step 1: Run targeted automated verification**

Run:

```bash
cd source/dts-analytics-webapp/modern
node --import ./node_modules/.pnpm/tsx@4.19.4/node_modules/tsx/dist/loader.mjs --test src/pages/screens/plugins/custom/financePluginAdapters.test.tsx src/pages/screens/specV2.finance-plugin.test.ts src/pages/screens/financeTemplates.test.ts src/pages/screens/financePreviewRuntime.test.tsx
pnpm typecheck
pnpm build
```

**Step 2: Record actual outcomes**

- Mark sprint/task states honestly
- Record any known unrelated build blockers explicitly

**Step 3: Fill manual acceptance checklist**

- Designer can create each finance template
- Components can be dragged and resized
- Property panel changes are persisted
- Preview/runtime rendering is stable

**Step 4: Commit**

```bash
git add source/dts-analytics-webapp/modern docs/plans worklog/v2.2.2/sprint-27-202603 worklog/v2.2.2/sprint-queue.md
git commit -m "feat: refactor finance screen family into plugins"
```
