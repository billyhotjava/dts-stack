# BI Board Generic Drilldown Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a domain-neutral drilldown channel for DTS screens that maps click payload fields to target parameters, switches any supported data source, and restores navigation history without requiring semantic or industry models.

**Architecture:** Extend the existing `DrillLevel`, `ScreenComponentAction`, and `ComponentInteractionMapping` contracts instead of adding a parallel protocol. Put normalization and state calculation in a pure `drillRuntime.ts` module; keep `useDrillDown` as a state wrapper, let `DataLayer` consume the effective DataSourceConfig, and let `InteractionLayer` execute all mapped actions.

**Tech Stack:** React 18, TypeScript, Node test runner with `--experimental-strip-types`, existing ScreenConfig v2 JSON, existing data-source adapters, Vite legacy build for Chrome 95.

## Global Constraints

- Core code and configuration MUST NOT contain project, BOM, voucher, work-order, or other domain-specific branches.
- Do not add database tables, backend orchestration services, semantic-model prerequisites, or new action protocols.
- Reuse `DataSourceConfig`, `ComponentInteractionMapping`, `ScreenComponentAction`, and runtime variables.
- SQL, API, Card, Dataset, and Metric MUST use the same drilldown state and parameter mapping path.
- Legacy `cardId + paramName` configurations MUST remain readable and executable without bulk migration.
- Chrome 95 production build compatibility is required.
- Before editing each existing symbol, run GitNexus upstream impact analysis and stop for user review on HIGH or CRITICAL risk.
- Before committing, run GitNexus detect-changes and verify only expected screen execution flows changed.

---

### Task 1: Generalize the drilldown contract

**Sprint tasks:** F1/T01, F1/T02

**Files:**
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/types.ts:41-91`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/screenSpec.ts:95-117,680-736`
- Create: `source/dts-platform-webapp/src/analytics/pages/screens/screenSpec.drillDown.test.ts`

**Interfaces:**
- Consumes: existing `DataSourceConfig`, `ComponentInteractionMapping`, `DrillDownConfig`.
- Produces: generalized `DrillLevel` accepted by Task 2.

- [ ] **Step 1: Run impact analysis before editing**

Use GitNexus upstream impact analysis for `DrillLevel`, `DrillDownConfig`, and `validateScreenPayload`. Record callers and risk in F1/T01 and F1/T02 evidence.

- [ ] **Step 2: Write failing Schema tests**

Create cases that accept a SQL target with mappings and a legacy Card target, then reject an empty generic target:

```ts
import assert from 'node:assert/strict';
import test from 'node:test';
import { buildScreenPayload, validateScreenPayload } from './screenSpec';
import type { DrillLevel, ScreenConfig } from './types';

function screenWithDrillLevel(level: DrillLevel) {
    const config: ScreenConfig = {
        id: 'draft',
        name: 'Neutral drill fixture',
        width: 1920,
        height: 1080,
        backgroundColor: '#08121f',
        components: [{
            id: 'chart-1',
            type: 'bar-chart',
            name: 'Neutral chart',
            x: 0,
            y: 0,
            width: 640,
            height: 360,
            zIndex: 1,
            locked: false,
            visible: true,
            config: {},
            dataSource: { type: 'sql', sqlConfig: { databaseId: 1, query: 'select 1' } },
            drillDown: { enabled: true, levels: [level] },
        }],
    };
    return buildScreenPayload(config);
}

test('accepts a generic drill level', () => {
    const payload = screenWithDrillLevel({
        label: 'Level 1',
        dataSource: { type: 'sql', sqlConfig: { databaseId: 1, query: 'select 1' } },
        mappings: [{ sourcePath: 'data.key', variableKey: 'selectedKey', transform: 'string' }],
        inheritContext: true,
    });
    assert.deepEqual(validateScreenPayload(payload), []);
});

test('accepts a legacy card drill level', () => {
    const payload = screenWithDrillLevel({ cardId: 12, paramName: 'selectedKey', label: 'Level 1' });
    assert.deepEqual(validateScreenPayload(payload), []);
});
```

- [ ] **Step 3: Run tests and verify red**

Run:

```bash
cd source/dts-platform-webapp
node --test --experimental-strip-types src/analytics/pages/screens/screenSpec.drillDown.test.ts
```

Expected: generic SQL case fails because the current contract requires `cardId` and `paramName`.

- [ ] **Step 4: Implement the minimal type contract**

Change the interface to:

```ts
export interface DrillLevel {
    label: string;
    dataSource?: DataSourceConfig;
    mappings?: ComponentInteractionMapping[];
    inheritContext?: boolean;
    cardId?: number;
    paramName?: string;
}
```

Extend ScreenConfig validation so a level is valid when either:

```ts
const isLegacy = Number(level.cardId) > 0 && asTrimmedString(level.paramName) !== '';
const sourceType = asTrimmedString((level.dataSource as Record<string, unknown> | undefined)?.type);
const isGeneric = ['api', 'card', 'sql', 'dataset', 'metric'].includes(sourceType)
    && Array.isArray(level.mappings)
    && level.mappings.length > 0;
```

Reuse the existing mapping-key, source-path, and transform validation rather than copying divergent rules.

- [ ] **Step 5: Run focused tests and build typecheck**

```bash
node --test --experimental-strip-types src/analytics/pages/screens/screenSpec.drillDown.test.ts
pnpm exec tsc --noEmit
```

Expected: all focused tests pass and TypeScript exits 0.

- [ ] **Step 6: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/types.ts \
  source/dts-platform-webapp/src/analytics/pages/screens/screenSpec.ts \
  source/dts-platform-webapp/src/analytics/pages/screens/screenSpec.drillDown.test.ts
git commit -m "feat(F1/T01): generalize screen drilldown contract"
```

### Task 2: Build the pure drill runtime

**Sprint tasks:** F2/T01

**Files:**
- Create: `source/dts-platform-webapp/src/analytics/pages/screens/drillRuntime.ts`
- Create: `source/dts-platform-webapp/src/analytics/pages/screens/drillRuntime.test.ts`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/hooks/useDrillDown.ts:1-73`

**Interfaces:**
- Consumes: generalized `DrillLevel`, root `DataSourceConfig`, click payload record.
- Produces: `NormalizedDrillLevel`, `DrillEntry`, `GenericDrillSnapshot`, `resolveNextDrillEntry`, and hook `DrillState` with `effectiveDataSource`.

- [ ] **Step 1: Run impact analysis before editing**

Use GitNexus upstream impact analysis for `useDrillDown` and `DrillState`. Confirm all callers are within screen renderers before proceeding.

- [ ] **Step 2: Write failing pure-function tests**

Cover generic mapping, inherited parameters, isolated parameters, missing source path, roll-up, and legacy adaptation:

```ts
const genericLevel = {
    label: 'Level 1',
    dataSource: { type: 'api', apiConfig: { url: '/example', method: 'GET' } },
    mappings: [{ sourcePath: 'data.key', variableKey: 'selectedKey', transform: 'string' }],
};

const normalizedLevel = normalizeDrillLevel(genericLevel);
assert.ok(normalizedLevel);

assert.deepEqual(
    resolveNextDrillEntry(normalizedLevel, { data: { key: 'A-01' } }),
    { label: 'Level 1: A-01', parameters: { selectedKey: 'A-01' } },
);
```

- [ ] **Step 3: Run tests and verify red**

```bash
node --test --experimental-strip-types src/analytics/pages/screens/drillRuntime.test.ts
```

Expected: FAIL because `drillRuntime.ts` does not exist.

- [ ] **Step 4: Implement normalization and state calculation**

Export focused pure functions with these exact signatures:

```ts
export function normalizeDrillLevel(level: DrillLevel): NormalizedDrillLevel | null;
export function resolveNextDrillEntry(
    level: NormalizedDrillLevel,
    clickPayload: Record<string, unknown>,
): DrillEntry | null;
export function buildDrillSnapshot(
    rootDataSource: DataSourceConfig | undefined,
    levels: NormalizedDrillLevel[],
    stack: DrillEntry[],
): GenericDrillSnapshot;
```

Use a `Map<string,string>` to merge inherited parameters, with later values overwriting earlier keys. Do not use domain-specific fallback fields; legacy adaptation may use only `name`, `data.name`, then `row[0]`.

- [ ] **Step 5: Refactor the hook into a state wrapper**

Change the hook boundary to:

```ts
export function useDrillDown(
    rootDataSource: DataSourceConfig | undefined,
    drillConfig: DrillDownConfig | undefined,
): DrillState;
```

Expose `handleDrill(clickPayload: Record<string, unknown>)`, `handleRollUp(targetDepth)`, and `reset()`.

- [ ] **Step 6: Run tests**

```bash
node --test --experimental-strip-types src/analytics/pages/screens/drillRuntime.test.ts
pnpm exec tsc --noEmit
```

Expected: all cases pass and no type errors remain.

- [ ] **Step 7: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/drillRuntime.ts \
  source/dts-platform-webapp/src/analytics/pages/screens/drillRuntime.test.ts \
  source/dts-platform-webapp/src/analytics/pages/screens/hooks/useDrillDown.ts
git commit -m "feat(F2/T01): add generic drilldown runtime"
```

### Task 3: Connect every data source through DataLayer

**Sprint tasks:** F2/T02

**Files:**
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/renderers/DataLayer.tsx:83-125`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/drillRuntime.test.ts`

**Interfaces:**
- Consumes: `DrillState.effectiveDataSource`, `DrillState.queryParameters`.
- Produces: one data-loading path for SQL, API, Card, Dataset, and Metric.

- [ ] **Step 1: Run impact analysis before editing**

Use GitNexus upstream impact analysis for `useComponentData` and `useCardDataSource`. Warn before implementation if risk is HIGH or CRITICAL.

- [ ] **Step 2: Add failing five-source matrix tests**

Build five root/target fixtures that differ only in `DataSourceConfig.type`; assert each snapshot returns the target source and identical `selectedKey=A-01` query parameters.

- [ ] **Step 3: Verify the existing Card gate fails the requirement**

Run the matrix test and confirm at least SQL/API/Dataset/Metric cases fail under the existing `rootCardId` path.

- [ ] **Step 4: Replace the Card-only hook input**

Use:

```ts
const drillState = useDrillDown(
    drillRuntimeEnabled ? dataSource : undefined,
    drillRuntimeEnabled ? drillDown : undefined,
);

const effectiveDataSource = drillRuntimeEnabled
    ? drillState.effectiveDataSource
    : dataSource;
```

Pass `effectiveDataSource` and merged parameters to the existing data-source hook. Do not branch by business domain or query content.

- [ ] **Step 5: Run focused tests and TypeScript**

```bash
node --test --experimental-strip-types src/analytics/pages/screens/drillRuntime.test.ts
pnpm exec tsc --noEmit
```

Expected: all five source cases pass.

- [ ] **Step 6: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/renderers/DataLayer.tsx \
  source/dts-platform-webapp/src/analytics/pages/screens/drillRuntime.test.ts
git commit -m "feat(F2/T02): enable drilldown for all screen sources"
```

### Task 4: Unify component action execution

**Sprint tasks:** F2/T03

**Files:**
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/renderers/shared/actionUtils.ts:4-100`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/renderers/shared/actionUtils.test.ts`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/renderers/InteractionLayer.tsx:186-330`
- Create: `source/dts-platform-webapp/src/analytics/pages/screens/renderers/InteractionLayer.chartCases.test.tsx`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/components/ComponentRenderer.tsx`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/renderers/TableRenderer.tsx`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/types.ts:129-139`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/screenSpec.ts:109-116`

**Interfaces:**
- Consumes: action mappings, click payload, `DrillState.handleDrill`, `runtime.drillView`.
- Produces: consistent mapped values for variables, drilldown, drill-view, panels, URLs, and intents.

- [ ] **Step 1: Run impact analysis before editing**

Use GitNexus upstream impact analysis for `executeComponentActions`, `resolvePreferredDrillValue`, `normalizeScreenActionType`, `ComponentRenderer`, and `TableRenderer`.

- [ ] **Step 2: Write failing action tests**

Add assertions that `drill-view` normalizes, mapped values are identical across actions, and no resolver checks a Chinese business key:

```ts
assert.equal(normalizeScreenActionType('drill-view'), 'drill-view');
assert.deepEqual(
    resolveActionMappingValues({ data: { key: 'A-01' } }, [
        { sourcePath: 'data.key', variableKey: 'selectedKey', transform: 'string' },
    ]),
    { selectedKey: 'A-01' },
);
```

- [ ] **Step 3: Verify red**

```bash
node --test --experimental-strip-types src/analytics/pages/screens/renderers/shared/actionUtils.test.ts
```

Expected: `drill-view` normalization fails under the current action set.

- [ ] **Step 4: Implement one action dispatch path**

- Add `drill-view` to validation and normalization sets.
- Compute `mappedValues` exactly once per action.
- Call `drillState.handleDrill(actionParams)` for drill-down.
- Call `runtime.drillView.drillToView(action.drillViewId, label, mappedValues)` for drill-view.
- Remove the `项目` fallback from `resolvePreferredDrillValue`; delete the helper if no caller remains.
- Normalize ECharts data-item events, table row events, and card scalar events into the same `Record<string, unknown>` click payload.
- Register `map-chart`, `table`, legacy `scroll-board`, `number-card`, `stat-card`, and `gauge-chart` as drill-capable data components while keeping titles, images, shapes, and containers outside data-point drilldown.
- Ignore legend, axis, blank canvas, table header/scroll/pagination, and map pan/zoom events. While the target query is loading, ignore repeated drill clicks for the same component and depth.

- [ ] **Step 5: Run focused tests and TypeScript**

```bash
node --test --experimental-strip-types \
  src/analytics/pages/screens/renderers/shared/actionUtils.test.ts \
  src/analytics/pages/screens/screenSpec.drillDown.test.ts
pnpm vitest run src/analytics/pages/screens/renderers/InteractionLayer.chartCases.test.tsx
pnpm exec tsc --noEmit
```

- [ ] **Step 6: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/renderers/shared/actionUtils.ts \
  source/dts-platform-webapp/src/analytics/pages/screens/renderers/shared/actionUtils.test.ts \
  source/dts-platform-webapp/src/analytics/pages/screens/renderers/InteractionLayer.chartCases.test.tsx \
  source/dts-platform-webapp/src/analytics/pages/screens/renderers/InteractionLayer.tsx \
  source/dts-platform-webapp/src/analytics/pages/screens/components/ComponentRenderer.tsx \
  source/dts-platform-webapp/src/analytics/pages/screens/renderers/TableRenderer.tsx \
  source/dts-platform-webapp/src/analytics/pages/screens/types.ts \
  source/dts-platform-webapp/src/analytics/pages/screens/screenSpec.ts
git commit -m "feat(F2/T03): unify mapped screen actions"
```

### Task 5: Generalize designer drill configuration

**Sprint tasks:** F3/T01, F3/T02

**Files:**
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/components/propertyPanel/BehaviorConfigSection.tsx:234-641`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/components/propertyPanel/PropertyPanel.tsx:420-435`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/components/propertyPanel/BehaviorConfigSection.source.test.ts`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/components/propertyPanel/PropertyPanel.behavior-source.test.ts`

**Interfaces:**
- Consumes: generalized `DrillLevel`, existing data-source editor controls, source-path candidates.
- Produces: saved generic levels with source, mappings, label, and inheritContext.

- [ ] **Step 1: Run impact analysis before editing**

Use GitNexus upstream impact analysis for `renderDrillDownConfig`, `renderActionConfig`, and `PropertyPanel`.

- [ ] **Step 2: Write failing source-contract tests**

Assert that the Card-only predicate is absent, generic field labels are present, and a new level does not default to `cardId: 0`:

```ts
assert.doesNotMatch(source, /dataSource\?\.type\s*!==\s*['"]card['"]/);
assert.match(source, /来源字段/);
assert.match(source, /目标参数/);
assert.match(source, /继承上层筛选/);
```

- [ ] **Step 3: Verify red**

```bash
node --test --experimental-strip-types \
  src/analytics/pages/screens/components/propertyPanel/BehaviorConfigSection.source.test.ts \
  src/analytics/pages/screens/components/propertyPanel/PropertyPanel.behavior-source.test.ts
```

- [ ] **Step 4: Implement the minimum generic editor**

- Show drill configuration for drillable components with any executable DataSourceConfig.
- Add levels as `{ label: '', dataSource: undefined, mappings: [], inheritContext: true }`.
- Reuse a shared mapping editor for action and drill mappings.
- Pass `config.globalVariables` from PropertyPanel into `renderDrillDownConfig`; render the existing `renderDataSourceConfig` against a shallow temporary component whose `dataSource` is the current level, and route its update callback back to that level.
- Label fields as `来源字段`, `目标参数`, `值转换`, `继承上层筛选`, and `下一层数据源`.
- Preserve CardIdPicker rendering when a loaded legacy level contains `cardId`.

- [ ] **Step 5: Run tests, formatting, and TypeScript**

```bash
node --test --experimental-strip-types \
  src/analytics/pages/screens/components/propertyPanel/BehaviorConfigSection.source.test.ts \
  src/analytics/pages/screens/components/propertyPanel/PropertyPanel.behavior-source.test.ts
pnpm exec biome check src/analytics/pages/screens/components/propertyPanel/BehaviorConfigSection.tsx
pnpm exec tsc --noEmit
```

- [ ] **Step 6: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/components/propertyPanel/BehaviorConfigSection.tsx \
  source/dts-platform-webapp/src/analytics/pages/screens/components/propertyPanel/PropertyPanel.tsx \
  source/dts-platform-webapp/src/analytics/pages/screens/components/propertyPanel/BehaviorConfigSection.source.test.ts \
  source/dts-platform-webapp/src/analytics/pages/screens/components/propertyPanel/PropertyPanel.behavior-source.test.ts
git commit -m "feat(F3/T02): add generic drilldown editor"
```

### Task 6: Complete drill-view and runtime navigation UX

**Sprint tasks:** F3/T03

**Files:**
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/components/propertyPanel/BehaviorConfigSection.tsx:288-465`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/components/ComponentRenderer.tsx:1636-1660`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/ScreenRuntimeShell.css`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/components/propertyPanel/BehaviorConfigSection.source.test.ts`

**Interfaces:**
- Consumes: `runtime.drillView`, generic drill breadcrumbs, configured screen view IDs.
- Produces: designer target selection plus preview back, roll-up, and reset controls.

- [ ] **Step 1: Run impact analysis before editing**

Use GitNexus upstream impact analysis for `ComponentRenderer`, `renderActionConfig`, and `useDrillView`.

- [ ] **Step 2: Add failing source-contract assertions**

Require a `drill-view` option, target view ID field, mapping editor visibility, and reset affordance.

- [ ] **Step 3: Implement the minimal UI**

- Add `<option value="drill-view">切换内部视图</option>`.
- Show target-view selection, label, and mappings for drill-view.
- Add `reset()` to the breadcrumb overlay alongside clickable depth links.
- Use existing breadcrumb theme tokens; do not add a separate navigation component or menu.

- [ ] **Step 4: Run focused tests and legacy build**

```bash
node --test --experimental-strip-types \
  src/analytics/pages/screens/components/propertyPanel/BehaviorConfigSection.source.test.ts
pnpm build
```

Expected: tests and Chrome 95-targeted build pass.

- [ ] **Step 5: Commit**

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens/components/propertyPanel/BehaviorConfigSection.tsx \
  source/dts-platform-webapp/src/analytics/pages/screens/components/ComponentRenderer.tsx \
  source/dts-platform-webapp/src/analytics/pages/screens/ScreenRuntimeShell.css \
  source/dts-platform-webapp/src/analytics/pages/screens/components/propertyPanel/BehaviorConfigSection.source.test.ts
git commit -m "feat(F3/T03): complete screen drill navigation"
```

### Task 7: Prove compatibility and source neutrality

**Sprint tasks:** F4/T01, F4/T02

**Files:**
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/drillRuntime.test.ts`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/screenSpec.drillDown.test.ts`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/ScreenPreviewPage.hooks.test.ts`
- Modify: `source/dts-platform-webapp/src/analytics/pages/screens/renderers/InteractionLayer.chartCases.test.tsx`
- Create evidence under: `workflow/v2.2.3/sprint-66-202607-bi-board-drilldown-refactoring/it/evidence/unit/`

**Interfaces:**
- Consumes: completed contract, runtime, action execution, and editor.
- Produces: automated evidence for legacy compatibility and five-source neutrality.

- [ ] **Step 1: Add legacy fixture tests**

Load `{ cardId: 12, paramName: 'selectedKey', label: 'Legacy' }`, drill twice, roll up, and assert effective Card IDs and query parameters match the pre-refactor contract.

- [ ] **Step 2: Add unconfigured-screen tests**

Render a screen component without actions, interaction, or drillDown and assert no click handler, clickable cursor, breadcrumb, or runtime event is added.

- [ ] **Step 3: Add the five-source table test**

Use neutral `keyA`, `keyB`, `label`, and `amount` fields. Iterate SQL, API, Card, Dataset, and Metric targets through one test body and assert identical mapping results.

- [ ] **Step 4: Add the UI click-event matrix test**

In `InteractionLayer.chartCases.test.tsx`, parameterize the same neutral click payload across bar, pie, line, scatter, funnel, radar, combo, treemap, sunburst, map, table, and KPI/card cases. Assert one click produces one mapped action and one drill transition. Add negative cases for chart blank area, legend, axis, table header/scroll/pagination, map pan/zoom, repeated clicks while loading, and missing source paths.

- [ ] **Step 5: Run all Sprint-66 focused tests**

```bash
node --test --experimental-strip-types \
  src/analytics/pages/screens/drillRuntime.test.ts \
  src/analytics/pages/screens/renderers/shared/actionUtils.test.ts \
  src/analytics/pages/screens/screenSpec.drillDown.test.ts \
  src/analytics/pages/screens/ScreenPreviewPage.hooks.test.ts \
  src/analytics/pages/screens/components/propertyPanel/BehaviorConfigSection.source.test.ts \
  src/analytics/pages/screens/components/propertyPanel/PropertyPanel.behavior-source.test.ts
pnpm vitest run src/analytics/pages/screens/renderers/InteractionLayer.chartCases.test.tsx
```

Expected: all tests pass with no skipped cases.

- [ ] **Step 6: Scan production code for domain coupling**

```bash
rg -n "项目|BOM|凭证|工单|MES|PLM|ERP|财务|考勤|专利" \
  src/analytics/pages/screens/drillRuntime.ts \
  src/analytics/pages/screens/hooks/useDrillDown.ts \
  src/analytics/pages/screens/renderers/DataLayer.tsx \
  src/analytics/pages/screens/renderers/InteractionLayer.tsx \
  src/analytics/pages/screens/components/propertyPanel/BehaviorConfigSection.tsx
```

Expected: no production-code matches introduced by Sprint-66.

- [ ] **Step 7: Save evidence and commit**

Save the command output with date, commit, and exit code under `it/evidence/unit/`, then:

```bash
git add source/dts-platform-webapp/src/analytics/pages/screens \
  workflow/v2.2.3/sprint-66-202607-bi-board-drilldown-refactoring/it/evidence/unit
git commit -m "test(F4/T02): prove generic drilldown compatibility"
```

### Task 8: Chrome 95 and delivery closure

**Sprint tasks:** F4/T03

**Files:**
- Modify: `workflow/v2.2.3/sprint-66-202607-bi-board-drilldown-refactoring/it/README.md`
- Modify status files under: `workflow/v2.2.3/sprint-66-202607-bi-board-drilldown-refactoring/`
- Modify: `workflow/v2.2.3/sprint-queue.md`
- Create evidence under: `workflow/v2.2.3/sprint-66-202607-bi-board-drilldown-refactoring/it/evidence/`

**Interfaces:**
- Consumes: implementation commits and deployed preview.
- Produces: Go/No-Go result and synchronized Sprint status.

- [ ] **Step 1: Run repository checks**

```bash
cd source/dts-platform-webapp
pnpm exec biome check src/analytics/pages/screens
pnpm build
cd ../..
git diff --check
```

Expected: every command exits 0.

- [ ] **Step 2: Run GitNexus change detection**

Use GitNexus `detect_changes(scope="all")`. Save changed symbols, affected processes, and risk level under `it/evidence/gitnexus/`. Stop and review any unexpected HIGH or CRITICAL result.

- [ ] **Step 3: Execute Chrome 95 smoke**

Using username/password login, verify:

1. Configure a neutral SQL two-level drill.
2. Click root data and observe the mapped request parameter.
3. Roll up and reset to the root source.
4. Open a detail panel.
5. Switch an internal drill-view and return.
6. Open one external URL using only mapped whitelist parameters.
7. Open an existing unconfigured screen and confirm unchanged behavior.

Store screenshots, console output, and redacted Network evidence under `it/evidence/chrome95/`.

- [ ] **Step 4: Apply the Go/No-Go gate**

Mark No-Go if any condition in `it/README.md` section 6 occurs. Otherwise record environment, date, commit, commands, and final conclusion.

- [ ] **Step 5: Synchronize statuses**

Only after all evidence passes, update 11 Task files, four Feature READMEs, Sprint README, IT README, and sprint queue from READY to DONE with correct counts.

- [ ] **Step 6: Commit delivery evidence**

```bash
git add workflow/v2.2.3/sprint-66-202607-bi-board-drilldown-refactoring \
  workflow/v2.2.3/sprint-queue.md
git commit -m "chore(F4/T03): close generic drilldown sprint evidence"
```
