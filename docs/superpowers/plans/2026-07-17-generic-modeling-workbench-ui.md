# Generic Modeling Workbench UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fixed dimensional-modeling journey with a generic four-stage modeling journey, simplify the subject-domain workspace, and preserve all existing confirmed-candidate and publishing capabilities.

**Architecture:** Add a generic journey context as an adapter over the existing business-modeling context, then reuse the current real pages behind a four-stage navigation frame. Reorganize `/governance/subjects` into responsibility tabs, move dimensional-only helpers behind an on-demand panel, remove the editable bus matrix from the primary UI, and keep existing backend matrix data untouched for compatibility.

**Tech Stack:** React 18, TypeScript 5.6, React Router 7, Ant Design 5, Vitest 4, Node test runner, Vite legacy build for Chrome 95, Spring Boot menu seed contract tests.

## Global Constraints

- Do not add Excel inference, editable ER design, or model-version UI without real backend APIs.
- Do not delete or migrate existing bus-matrix data; only remove it from the generic primary frontend journey.
- Keep manual/template candidate confirmation and confirmed-only downstream consumption working.
- URL parameters are cross-page context truth; browser storage may only restore navigation context.
- Keep Chrome 95 compatibility; do not use `:has()`, container queries, `toSorted()`, or unsupported viewport units.
- Preserve all unrelated dirty-worktree changes. Do not reset, clean, commit, or push.
- Before editing each function/component symbol, run GitNexus upstream impact analysis and report HIGH or CRITICAL results before continuing.
- Before any future commit, run `gitnexus_detect_changes`; this plan does not authorize a commit.

---

## File Structure

### New files

- `source/dts-platform-webapp/src/pages/modeling/modelingJourneyContext.ts` — canonical journey types, legacy parameter resolution, stage route generation.
- `source/dts-platform-webapp/src/pages/modeling/modelingJourneyContext.test.ts` — journey context and legacy-route unit tests.
- `source/dts-platform-webapp/src/pages/modeling/ModelingWorkbenchPage.tsx` — stable `/modeling/workbench` entry that resumes or routes to the requested real stage.
- `source/dts-platform-webapp/src/pages/modeling/ModelingWorkbenchPage.source-contract.test.ts` — proves the entry only routes to real pages.
- `source/dts-platform-webapp/src/pages/governance/SubjectWorkspaceTabs.tsx` — responsibility-tab shell for the selected subject domain.
- `source/dts-platform-webapp/src/pages/governance/DimensionalModelingAssist.tsx` — on-demand container for legacy process/dimension candidate tools.
- `source/dts-admin/src/main/resources/config/liquibase/changelog/20260717-01_generic_modeling_workbench_menu.xml` — update the existing role-bound modeling entry in deployed databases without deleting visibility bindings.

### Modified files

- `source/dts-platform-webapp/src/pages/modeling/semantic-workspace/SemanticWorkspaceFrame.tsx` — four-stage navigation, compact status summary, no route-index completion or permanent concept cards.
- `source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx` — register `/modeling/workbench`.
- `source/dts-platform-webapp/src/pages/governance/SubjectAreasPage.tsx` — responsibility tabs, one primary action, no editable matrix in primary UI.
- `source/dts-platform-webapp/src/pages/governance/SubjectAreasPage.source-contract.test.ts` — new UI and compatibility contract.
- `source/dts-platform-webapp/src/pages/modeling/SemanticModelsPage.tsx` — compact model ledger actions and status.
- `source/dts-platform-webapp/src/pages/modeling/SemanticModelsPage.source-contract.test.ts` — model-ledger action hierarchy contract.
- `source/dts-admin/src/main/resources/config/data/portal-menu-seed.json` — expose the workbench and converge duplicate modeling entry.
- `source/dts-admin/src/main/resources/config/data/role-menu-defaults.json` — keep default route aligned with the seed.
- `source/dts-admin/src/main/resources/config/liquibase/master.xml` — include the modeling-workbench menu migration.
- `source/dts-admin/src/test/java/com/yuzhi/dts/admin/service/PortalMenuSeedDefaultsContractTest.java` — route and placement contract.

---

### Task 1: Generic Journey Context

**Files:**
- Create: `source/dts-platform-webapp/src/pages/modeling/modelingJourneyContext.ts`
- Create: `source/dts-platform-webapp/src/pages/modeling/modelingJourneyContext.test.ts`
- Reuse: `source/dts-platform-webapp/src/pages/modeling/businessModelingContext.ts`

**Interfaces:**
- Consumes: `BusinessModelingContext`, `resolveBusinessModelingContext`, `buildBusinessModelingRoute`.
- Produces: `ModelingStage`, `ModelingMethod`, `ModelingJourneyContext`, `resolveModelingJourneyContext()`, `buildModelingJourneyRoute()`, `modelingStagePath()`.

- [ ] **Step 1: Write the failing context tests**

```ts
import { describe, expect, it } from "vitest";
import {
	buildModelingJourneyRoute,
	modelingStagePath,
	resolveModelingJourneyContext,
} from "./modelingJourneyContext";

describe("generic modeling journey context", () => {
	it("maps legacy subject parameters into the scope stage", () => {
		const context = resolveModelingJourneyContext(
			new URLSearchParams("active=domain-1&focus=business-processes&processId=process-1"),
		);

		expect(context.domainId).toBe("domain-1");
		expect(context.scopeId).toBe("process-1");
		expect(context.scopeKind).toBe("PROCESS");
		expect(context.stage).toBe("SCOPE");
		expect(context.method).toBe("DIMENSIONAL");
	});

	it("keeps generic and legacy identifiers across stage links", () => {
		const context = resolveModelingJourneyContext(
			new URLSearchParams("domainId=domain-1&modelSpecId=model-1&stage=implementation"),
		);
		const route = buildModelingJourneyRoute(modelingStagePath("RELEASE"), {
			...context,
			stage: "RELEASE",
		});

		expect(route).toContain("domainId=domain-1");
		expect(route).toContain("modelSpecId=model-1");
		expect(route).toContain("stage=release");
	});
});
```

- [ ] **Step 2: Run the focused test and verify RED**

Run from `source/dts-platform-webapp`:

```bash
pnpm exec vitest run src/pages/modeling/modelingJourneyContext.test.ts
```

Expected: FAIL because `modelingJourneyContext.ts` does not exist.

- [ ] **Step 3: Run GitNexus impact analysis before extending context behavior**

```text
gitnexus_impact(target="resolveBusinessModelingContext", direction="upstream", repo="s10-stack")
gitnexus_impact(target="buildBusinessModelingRoute", direction="upstream", repo="s10-stack")
```

Expected: report the callers; do not edit the legacy functions when the adapter can preserve them unchanged.

- [ ] **Step 4: Implement the generic adapter**

```ts
import {
	buildBusinessModelingRoute,
	resolveBusinessModelingContext,
	type BusinessModelingContext,
} from "./businessModelingContext";

export type ModelingStage = "SCOPE" | "LOGICAL" | "IMPLEMENTATION" | "RELEASE";
export type ModelingMethod = "RELATIONAL" | "DIMENSIONAL" | "DBT_NATIVE";
export type ModelingScopeKind = "DOMAIN" | "PROCESS" | "OBJECT" | "MODEL";

export type ModelingJourneyContext = BusinessModelingContext & {
	scopeId?: string;
	scopeKind?: ModelingScopeKind;
	modelId?: string;
	method?: ModelingMethod;
	stage: ModelingStage;
};

const stageFrom = (value?: string | null): ModelingStage => {
	switch (value?.trim().toLowerCase()) {
		case "logical": return "LOGICAL";
		case "implementation": return "IMPLEMENTATION";
		case "release": return "RELEASE";
		default: return "SCOPE";
	}
};

const scopeKindFrom = (value?: string | null): ModelingScopeKind | undefined => {
	switch (value?.trim().toLowerCase()) {
		case "domain": return "DOMAIN";
		case "process": return "PROCESS";
		case "object": return "OBJECT";
		case "model": return "MODEL";
		default: return undefined;
	}
};

const methodFrom = (value?: string | null, modelingMode?: string): ModelingMethod | undefined => {
	switch (value?.trim().toLowerCase()) {
		case "relational": return "RELATIONAL";
		case "dimensional": return "DIMENSIONAL";
		case "dbt_native":
		case "dbt-native": return "DBT_NATIVE";
		default: return modelingMode === "dimension" ? "DIMENSIONAL" : undefined;
	}
};

export const modelingStagePath = (stage: ModelingStage): string => ({
	SCOPE: "/governance/subjects?tab=scope",
	LOGICAL: "/modeling/semantic/objects",
	IMPLEMENTATION: "/modeling/semantic/models",
	RELEASE: "/modeling/semantic/publish",
})[stage];

export const resolveModelingJourneyContext = (searchParams: URLSearchParams): ModelingJourneyContext => {
	const legacy = resolveBusinessModelingContext(searchParams);
	const domainId = searchParams.get("domainId")?.trim() || searchParams.get("active")?.trim() || legacy.domainId;
	const modelId = searchParams.get("modelId")?.trim() || legacy.modelSpecId;
	const scopeId = searchParams.get("scopeId")?.trim() || legacy.processId || legacy.objectId || modelId;
	const scopeKind: ModelingScopeKind | undefined =
		scopeKindFrom(searchParams.get("scopeKind")) ||
		(legacy.processId
			? "PROCESS"
			: legacy.objectId
				? "OBJECT"
				: modelId
					? "MODEL"
					: domainId
						? "DOMAIN"
						: undefined);
	return {
		...legacy,
		domainId,
		scopeId,
		scopeKind,
		modelId,
		method: methodFrom(searchParams.get("method"), legacy.modelingMode || (searchParams.has("focus") ? "dimension" : undefined)),
		stage: stageFrom(searchParams.get("stage")),
	};
};

export const buildModelingJourneyRoute = (
	route: string,
	context: Partial<ModelingJourneyContext>,
): string => {
	const legacyRoute = buildBusinessModelingRoute(route, context);
	const [path, query = ""] = legacyRoute.split("?");
	const params = new URLSearchParams(query);
	if (context.domainId) params.set("domainId", context.domainId);
	if (context.scopeId) params.set("scopeId", context.scopeId);
	if (context.scopeKind) params.set("scopeKind", context.scopeKind.toLowerCase());
	if (context.modelId) params.set("modelId", context.modelId);
	if (context.method) params.set("method", context.method.toLowerCase());
	if (context.stage) params.set("stage", context.stage.toLowerCase());
	return params.toString() ? `${path}?${params.toString()}` : path;
};
```

- [ ] **Step 5: Run focused and neighboring context tests**

```bash
pnpm exec vitest run src/pages/modeling/modelingJourneyContext.test.ts src/pages/modeling/businessModelingContext.test.ts
```

Expected: both files PASS.

- [ ] **Step 6: Record a no-commit checkpoint**

```bash
git diff --check -- source/dts-platform-webapp/src/pages/modeling/modelingJourneyContext.ts source/dts-platform-webapp/src/pages/modeling/modelingJourneyContext.test.ts
git status --short
```

Expected: no whitespace errors; existing dirty files remain present.

---

### Task 2: Four-Stage Journey Navigation and Workbench Entry

**Files:**
- Create: `source/dts-platform-webapp/src/pages/modeling/ModelingWorkbenchPage.tsx`
- Create: `source/dts-platform-webapp/src/pages/modeling/ModelingWorkbenchPage.source-contract.test.ts`
- Modify: `source/dts-platform-webapp/src/pages/modeling/semantic-workspace/SemanticWorkspaceFrame.tsx`
- Modify: `source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx`

**Interfaces:**
- Consumes: `resolveModelingJourneyContext()`, `buildModelingJourneyRoute()`, `modelingStagePath()` from Task 1.
- Produces: `/modeling/workbench` route and four visible stages shared by semantic pages.

- [ ] **Step 1: Write source-contract tests for the new entry and frame**

```ts
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const entry = readFileSync(new URL("./ModelingWorkbenchPage.tsx", import.meta.url), "utf8");
const frame = readFileSync(new URL("./semantic-workspace/SemanticWorkspaceFrame.tsx", import.meta.url), "utf8");

test("modeling workbench resumes one of four real stages", () => {
	assert.match(entry, /resolveModelingJourneyContext/);
	assert.match(entry, /modelingStagePath/);
	assert.doesNotMatch(entry, /版本记录|Excel 自动分析|可编辑 ER/);
});

test("semantic workspace exposes the generic four-stage journey", () => {
	for (const label of ["范围与来源", "逻辑模型", "实现与验证", "发布与运行"]) {
		assert.ok(frame.includes(label));
	}
	assert.doesNotMatch(frame, /index < activeIndex/);
	assert.doesNotMatch(frame, /ModelingConceptCards/);
});
```

- [ ] **Step 2: Run the source-contract test and verify RED**

```bash
node --test --experimental-strip-types src/pages/modeling/ModelingWorkbenchPage.source-contract.test.ts
```

Expected: FAIL because `ModelingWorkbenchPage.tsx` does not exist and the old frame still uses route-index completion.

- [ ] **Step 3: Run GitNexus impact analysis**

```text
gitnexus_impact(target="SemanticWorkspaceFrame", direction="upstream", repo="s10-stack")
gitnexus_impact(target="STATIC_DASHBOARD_ROUTES", direction="upstream", repo="s10-stack")
```

Expected: enumerate all semantic pages importing the frame and all consumers of static routes; stop and report if risk is HIGH or CRITICAL.

- [ ] **Step 4: Add the real-stage workbench redirect**

```tsx
import { Navigate } from "react-router";
import { useSearchParams } from "@/routes/hooks";
import {
	buildModelingJourneyRoute,
	modelingStagePath,
	resolveModelingJourneyContext,
} from "./modelingJourneyContext";

export default function ModelingWorkbenchPage() {
	const searchParams = useSearchParams();
	const context = resolveModelingJourneyContext(searchParams);
	return (
		<Navigate
			replace
			to={buildModelingJourneyRoute(modelingStagePath(context.stage), context)}
		/>
	);
}
```

Register it in `static-routes.tsx`:

```tsx
const ModelingWorkbenchPage = lazy(() => import("@/pages/modeling/ModelingWorkbenchPage"));
// ...
{ path: "modeling/workbench", element: <S><ModelingWorkbenchPage /></S> },
```

- [ ] **Step 5: Replace route-index progress with stage navigation**

Keep `activeKey` for caller compatibility, map it to a stage, and render compact navigation:

```tsx
type JourneyStage = {
	key: ModelingStage;
	label: string;
	path: string;
	icon: LucideIcon;
};

const JOURNEY_STAGES: JourneyStage[] = [
	{ key: "SCOPE", label: "范围与来源", path: modelingStagePath("SCOPE"), icon: Layers3 },
	{ key: "LOGICAL", label: "逻辑模型", path: modelingStagePath("LOGICAL"), icon: Box },
	{ key: "IMPLEMENTATION", label: "实现与验证", path: modelingStagePath("IMPLEMENTATION"), icon: Database },
	{ key: "RELEASE", label: "发布与运行", path: modelingStagePath("RELEASE"), icon: Rocket },
];

const STAGE_BY_ACTIVE_KEY: Record<SemanticWorkspaceKey, ModelingStage> = {
	workbench: "LOGICAL",
	objects: "LOGICAL",
	metrics: "LOGICAL",
	models: "IMPLEMENTATION",
	publish: "RELEASE",
};
```

Render only active-state styling; do not infer completed stages. Replace statistic cards with compact text tags and remove the permanent `ModelingConceptCards` render.

- [ ] **Step 6: Run the frame contract and TypeScript build gate**

```bash
node --test --experimental-strip-types src/pages/modeling/ModelingWorkbenchPage.source-contract.test.ts
pnpm exec tsc --noEmit
```

Expected: contract PASS and TypeScript exits 0.

- [ ] **Step 7: Record a no-commit checkpoint**

```bash
git diff --check -- source/dts-platform-webapp/src/pages/modeling/ModelingWorkbenchPage.tsx source/dts-platform-webapp/src/pages/modeling/semantic-workspace/SemanticWorkspaceFrame.tsx source/dts-platform-webapp/src/routes/sections/dashboard/static-routes.tsx
```

Expected: no whitespace errors.

---

### Task 3: Subject-Domain Responsibility Tabs and Dimensional Assist

**Files:**
- Create: `source/dts-platform-webapp/src/pages/governance/SubjectWorkspaceTabs.tsx`
- Create: `source/dts-platform-webapp/src/pages/governance/DimensionalModelingAssist.tsx`
- Modify: `source/dts-platform-webapp/src/pages/governance/SubjectAreasPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/governance/SubjectAreasPage.source-contract.test.ts`

**Interfaces:**
- Consumes: current subject-domain state, process actions, `ConformedDimensionCatalogCard`, and Task 1 journey routing.
- Produces: `SubjectWorkspaceTab`, `SubjectWorkspaceTabs`, `DimensionalModelingAssist`.

- [ ] **Step 1: Replace the old matrix-oriented contract with the approved UI contract**

```ts
test("subject areas separate modeling scope, domain details and governance overview", () => {
	assert.match(source, /SubjectWorkspaceTabs/);
	assert.match(source, /建模范围/);
	assert.match(source, /主题域信息/);
	assert.match(source, /治理概览/);
	assert.match(source, /tab/);
});

test("dimensional-only helpers are on demand and bus matrix is not in the primary UI", () => {
	assert.match(source, /DimensionalModelingAssist/);
	assert.match(source, /维度建模辅助/);
	assert.doesNotMatch(source, /saveBusMatrixLinkApi/);
	assert.doesNotMatch(source, /title="总线矩阵"/);
});

test("selected domain exposes one primary continuation action", () => {
	assert.match(source, /继续逻辑模型/);
	assert.match(source, /modeling\/semantic\/objects/);
	assert.doesNotMatch(source, /\+ 挂载术语/);
});
```

- [ ] **Step 2: Run the subject contract and verify RED**

```bash
node --test --experimental-strip-types src/pages/governance/SubjectAreasPage.source-contract.test.ts
```

Expected: FAIL on missing tab components and the still-visible matrix.

- [ ] **Step 3: Run GitNexus impact analysis**

```text
gitnexus_impact(target="SubjectAreasPage", direction="upstream", repo="s10-stack")
```

Expected: report route/import consumers before editing; stop and report HIGH or CRITICAL risk.

- [ ] **Step 4: Add the responsibility-tab shell**

```tsx
import { Tabs } from "antd";
import type { ReactNode } from "react";

export type SubjectWorkspaceTab = "scope" | "details" | "governance";

export function SubjectWorkspaceTabs({
	activeKey,
	onChange,
	scope,
	details,
	governance,
}: {
	activeKey: SubjectWorkspaceTab;
	onChange: (key: SubjectWorkspaceTab) => void;
	scope: ReactNode;
	details: ReactNode;
	governance: ReactNode;
}) {
	return (
		<Tabs
			activeKey={activeKey}
			onChange={(key) => onChange(key as SubjectWorkspaceTab)}
			items={[
				{ key: "scope", label: "建模范围", children: scope },
				{ key: "details", label: "主题域信息", children: details },
				{ key: "governance", label: "治理概览", children: governance },
			]}
		/>
	);
}
```

- [ ] **Step 5: Add the on-demand dimensional helper**

```tsx
import { Button, Drawer, Tag } from "antd";
import type { ReactNode } from "react";
import { useEffect, useState } from "react";

export function DimensionalModelingAssist({
	candidateCount,
	openOnMount = false,
	children,
}: {
	candidateCount: number;
	openOnMount?: boolean;
	children: ReactNode;
}) {
	const [open, setOpen] = useState(openOnMount);
	useEffect(() => setOpen(openOnMount), [openOnMount]);
	return (
		<>
			<Button onClick={() => setOpen(true)}>
				维度建模辅助 {candidateCount > 0 ? <Tag color="gold">待确认 {candidateCount}</Tag> : null}
			</Button>
			<Drawer title="维度建模辅助" width={720} open={open} onClose={() => setOpen(false)}>
				{children}
			</Drawer>
		</>
	);
}
```

- [ ] **Step 6: Recompose `SubjectAreasPage` without the editable matrix**

Use `tab=scope|details|governance`, default to `scope`, and map `focus=business-processes` to `scope` with `openOnMount=true`. Remove `busMatrix`, `listBusMatrixApi`, `saveBusMatrixLinkApi`, `toggleMatrix`, `processDimensionIds`, and the matrix card from this page; do not delete backend APIs.

Add the route action before rendering:

```tsx
const continueLogicalModel = () => {
	if (!activeDomain?.id) return;
	router.push(
		buildModelingJourneyRoute(modelingStagePath("LOGICAL"), {
			domainId: activeDomain.id,
			domainName: activeDomain.name,
			stage: "LOGICAL",
		}),
	);
};
```

The selected-domain body must have this shape; the marked existing blocks are moved intact rather than copied into new state:

```tsx
<SubjectWorkspaceTabs
	activeKey={workspaceTab}
	onChange={setWorkspaceTabAndQuery}
	scope={(
		<div className="space-y-4">
			<div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white p-4">
				<div>
					<div className="font-medium text-slate-900">当前范围：{activeDomain.name}</div>
					<div className="mt-1 text-sm text-slate-500">从业务对象、数据表和已有模型开始组织逻辑模型。</div>
				</div>
				<Button type="primary" onClick={continueLogicalModel}>继续逻辑模型</Button>
			</div>
			<DimensionalModelingAssist candidateCount={candidateCount} openOnMount={focusBusinessProcesses}>
				<div className="space-y-4">
					{/* Move the existing <Card title="业务过程"> block here intact. */}
					<ConformedDimensionCatalogCard
						domainId={activeDomain.id as string}
						canManage={canManage}
						processes={businessProcesses}
						dimensions={conformedDimensionCatalog}
						onChanged={() => loadDomainModelingFacts(activeDomain.id as string)}
					/>
				</div>
			</DimensionalModelingAssist>
		</div>
	)}
	details={(
		<div className="space-y-4">
			{/* Move the existing data-testid="warehouse-layer-plan" Card here intact. */}
			{/* Move the existing data-testid="warehouse-planning-card" Alert here intact. */}
		</div>
	)}
	governance={(
		<div className="space-y-4">
			{/* Move the existing domain-level asset/indicator statistic cards here intact. */}
			{/* Move the existing child-domain list here intact. */}
		</div>
	)}
/>
```

Delete the disabled “关联术语 / + 挂载术语” control. Keep the process create/confirm/delete actions and the dimension candidate confirmation card inside the dimensional helper. Do not leave the old editable matrix table, matrix imports, state, callbacks, or backend calls in this page.

- [ ] **Step 7: Run subject and candidate contracts**

```bash
node --test --experimental-strip-types \
  src/pages/governance/SubjectAreasPage.source-contract.test.ts \
  src/pages/governance/ConformedDimensionCatalogCard.source-contract.test.ts
```

Expected: all tests PASS.

- [ ] **Step 8: Run the focused TypeScript gate**

```bash
pnpm exec tsc --noEmit
```

Expected: exit 0.

---

### Task 4: Model Ledger Action Convergence

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/modeling/SemanticModelsPage.tsx`
- Modify: `source/dts-platform-webapp/src/pages/modeling/SemanticModelsPage.source-contract.test.ts`

**Interfaces:**
- Consumes: existing model preview, dbt hand-off, artifact generation, run trigger, review submission and release-gate state.
- Produces: one visible row action plus a `Dropdown` for secondary actions; no API changes.

- [ ] **Step 1: Add failing action-hierarchy assertions**

```ts
test("model ledger keeps one visible row action and moves specialist actions into more", () => {
	assert.match(PAGE, /Dropdown/);
	assert.match(PAGE, /更多/);
	assert.match(PAGE, /查看模型/);
	assert.doesNotMatch(PAGE, />\s*生成制品\s*<\/Button>/);
	assert.doesNotMatch(PAGE, />\s*触发运行\s*<\/Button>/);
	assert.doesNotMatch(PAGE, />\s*提交审核\s*<\/Button>/);
});

test("advanced dbt remains available as a secondary action", () => {
	assert.match(PAGE, /semantic-model-dbt-entry/);
	assert.match(PAGE, /高级 dbt SQL/);
});
```

- [ ] **Step 2: Run the model ledger contract and verify RED**

```bash
node --test --experimental-strip-types src/pages/modeling/SemanticModelsPage.source-contract.test.ts
```

Expected: FAIL because five row buttons are still visible.

- [ ] **Step 3: Run GitNexus impact analysis**

```text
gitnexus_impact(target="SemanticModelsPage", direction="upstream", repo="s10-stack")
```

Expected: report consumers before editing.

- [ ] **Step 4: Replace row buttons with one primary action and dropdown**

Import `Dropdown` and `MoreHorizontal`, then render:

```tsx
<Space size="small">
	<Button type="link" size="small" onClick={() => handlePreview(row.id)}>
		查看模型
	</Button>
	<Dropdown
		menu={{
			items: [
				{ key: "dbt", label: <span data-testid="semantic-model-dbt-entry">高级 dbt SQL</span> },
				{ key: "generate", label: "生成制品" },
				{ key: "run", label: "触发运行" },
				{ key: "review", label: "提交审核" },
			],
			onClick: ({ key }) => {
				if (key === "dbt") navigate(`/modeling/dbt-files?modelId=${encodeURIComponent(row.id)}&from=model-ledger`);
				if (key === "generate") void handleGenerate(row.id);
				if (key === "run") void handleTrigger(row.id);
				if (key === "review") void handleSubmitReview(row.id);
			},
		}}
	>
		<Button type="text" size="small" aria-label="更多模型操作"><MoreHorizontal size={16} /> 更多</Button>
	</Dropdown>
</Space>
```

Remove the three-button “专业入口” header card. Keep release-gate evidence in the table and reduce top stats to model total, pending review, and blocked count.

- [ ] **Step 5: Run the model ledger and context tests**

```bash
node --test --experimental-strip-types src/pages/modeling/SemanticModelsPage.source-contract.test.ts
pnpm exec vitest run src/pages/modeling/modelingLedger.test.ts
pnpm exec tsc --noEmit
```

Expected: all commands exit 0.

---

### Task 5: Menu Convergence and Full Verification

**Files:**
- Modify: `source/dts-admin/src/main/resources/config/data/portal-menu-seed.json`
- Modify: `source/dts-admin/src/main/resources/config/data/role-menu-defaults.json`
- Create: `source/dts-admin/src/main/resources/config/liquibase/changelog/20260717-01_generic_modeling_workbench_menu.xml`
- Modify: `source/dts-admin/src/main/resources/config/liquibase/master.xml`
- Modify: `source/dts-admin/src/test/java/com/yuzhi/dts/admin/service/PortalMenuSeedDefaultsContractTest.java`
- Verify: all files from Tasks 1-4.

**Interfaces:**
- Consumes: `/modeling/workbench` from Task 2.
- Produces: a discoverable workbench entry and aligned role-default route without deleting existing role codes.

- [ ] **Step 1: Read and follow the DTS menu-route convergence skill before editing seeds**

Read `/home/billy/.codex/skills/dts-menu-route-convergence/SKILL.md` completely. Preserve existing menu codes and bindings; change placement/title/route instead of deleting and recreating role-bound entries.

- [ ] **Step 2: Add a failing Java seed contract**

Add assertions that:

```java
Map<String, Object> workbench = modelingChildren.get(0);
assertEquals("modeling-workbench", workbench.get("key"));
assertEquals("sys.nav.portal.studioBusinessProcesses", workbench.get("titleKey"));
assertEquals("建模工作台", workbench.get("title"));
assertEquals("/modeling/workbench", workbench.get("externalLink"));
assertEquals("warehouse-planning", modelingChildren.get(1).get("key"));
assertEquals("standards", modelingChildren.get(2).get("key"));
assertEquals("dimensional-modeling", modelingChildren.get(3).get("key"));

Map<String, Object> workbenchDefault = defaults.stream()
	.filter(rule -> "sys.nav.portal.studioBusinessProcesses".equals(rule.get("code")))
	.findFirst()
	.orElse(null);
assertNotNull(workbenchDefault, "workbench role default should keep its existing code");
assertEquals("建模工作台", workbenchDefault.get("title"));
assertEquals("/modeling/workbench", workbenchDefault.get("route"));
```

Use the existing JSON traversal helpers in `PortalMenuSeedDefaultsContractTest`; do not introduce a second parser.

Also add a migration-preservation test:

```java
@Test
void genericModelingWorkbenchMigrationPreservesVisibilityBindings() throws Exception {
	ClassPathResource master = new ClassPathResource("config/liquibase/master.xml");
	String masterXml = master.getContentAsString(StandardCharsets.UTF_8);
	assertTrue(masterXml.contains("20260717-01_generic_modeling_workbench_menu.xml"));

	ClassPathResource changelog = new ClassPathResource(
		"config/liquibase/changelog/20260717-01_generic_modeling_workbench_menu.xml"
	);
	String xml = changelog.getContentAsString(StandardCharsets.UTF_8);
	assertTrue(xml.contains("sys.nav.portal.studioBusinessProcesses"));
	assertTrue(xml.contains("/modeling/workbench"));
	assertFalse(xml.toLowerCase(Locale.ROOT).contains("delete from portal_menu_visibility"));
}
```

- [ ] **Step 3: Run the Java contract and verify RED**

Run from `source/dts-admin`:

```bash
./mvnw -Dtest=PortalMenuSeedDefaultsContractTest test
```

Expected: FAIL because the old business-process route still differs between seed and role defaults.

- [ ] **Step 4: Run impact analysis for the route/menu contract**

Use GitNexus route/context tools for `/modeling/workbench`, `portal-menu-seed.json`, and `PortalMenuSeedDefaultsContractTest`. If the index does not represent JSON seed dependencies, report that limitation and rely on the focused Java contract plus runtime seed verification.

- [ ] **Step 5: Reuse the existing role-bound code as the workbench entry**

In `portal-menu-seed.json`, keep `titleKey=sys.nav.portal.studioBusinessProcesses` but change its customer-facing metadata:

```json
{
  "key": "modeling-workbench",
  "path": "modeling-workbench",
  "icon": "solar:layers-minimalistic-bold-duotone",
  "titleKey": "sys.nav.portal.studioBusinessProcesses",
  "title": "建模工作台",
  "externalLink": "/modeling/workbench"
}
```

Place it as the first direct child under “数据建模”, not under “数仓规划”. Keep the separate subject-domain entry under planning and keep all existing role codes.

In `role-menu-defaults.json`, update the same code:

```json
{
  "code": "sys.nav.portal.studioBusinessProcesses",
  "title": "建模工作台",
  "route": "/modeling/workbench",
  "requiredRoles": []
}
```

- [ ] **Step 6: Add an in-place menu migration for existing deployments**

Create `20260717-01_generic_modeling_workbench_menu.xml`. Resolve the “数据建模” parent row first, then update the existing row identified by `sys.nav.portal.studioBusinessProcesses` so that it becomes the direct child `studio/modeling/modeling-workbench`, title “建模工作台”, route `/modeling/workbench`, and sort order `1`. Do not delete or recreate that row, and do not modify `portal_menu_visibility`.

Update sibling sort orders in place: warehouse planning `2`, standards `3`, dimensional modeling `4`, low-code development `5`, data metrics `6`. Include this changelog from `master.xml` after the existing 20260712 modeling-menu migration.

- [ ] **Step 7: Run focused frontend tests**

Run from `source/dts-platform-webapp`:

```bash
pnpm exec vitest run \
  src/pages/modeling/modelingJourneyContext.test.ts \
  src/pages/modeling/businessModelingContext.test.ts \
  src/pages/modeling/modelingLedger.test.ts

node --test --experimental-strip-types \
  src/pages/modeling/ModelingWorkbenchPage.source-contract.test.ts \
  src/pages/governance/SubjectAreasPage.source-contract.test.ts \
  src/pages/governance/ConformedDimensionCatalogCard.source-contract.test.ts \
  src/pages/modeling/SemanticModelsPage.source-contract.test.ts
```

Expected: all tests PASS.

- [ ] **Step 8: Run admin seed and frontend build gates**

```bash
cd /opt/prod/s10/v2.2.3/source/dts-admin
./mvnw -Dtest=PortalMenuSeedDefaultsContractTest test

cd /opt/prod/s10/v2.2.3/source/dts-platform-webapp
pnpm build
```

Expected: Java contract PASS; TypeScript and legacy Vite build exit 0. Existing Browserslist-age and chunk-size warnings may remain non-blocking.

- [ ] **Step 9: Use the DTS Chrome 95 regression skill**

Read `/home/billy/.codex/skills/dts-chrome95-regression/SKILL.md` completely, start its prescribed browser/runtime checks, and verify at 1366×768:

- `/modeling/workbench` reaches the scope stage;
- subject-domain tree and all three responsibility tabs are usable;
- the primary “继续逻辑模型” action stays above the fold;
- the dimensional helper drawer opens and candidate actions remain usable;
- no editable “总线矩阵” card appears in the generic primary UI;
- semantic pages show four generic stages without false completed checkmarks;
- model ledger row has one visible action and a working “更多” menu.

- [ ] **Step 10: Run pre-handoff change detection and dirty-worktree review**

```text
gitnexus_detect_changes(scope="unstaged", repo="s10-stack")
```

Then run:

```bash
git diff --check
git status --short
```

Expected: only planned symbols plus the pre-existing user changes are reported; no commit or push is performed.

---

## Execution Evidence (2026-07-18)

### Implemented outcome

- Added the stable `/modeling/workbench` entry and a generic four-stage journey: 范围与来源、逻辑模型、实现与验证、发布与运行.
- Reorganized subject-domain management into 建模范围、主题域信息、治理概览 tabs, with one create entry, one overflow menu, and one primary continuation action.
- Moved dimensional-only business-process and conformed-dimension helpers into an on-demand drawer; the editable bus matrix is no longer part of the generic primary journey, while its backend data remains untouched.
- Simplified the model ledger so each row exposes one primary action and keeps specialist actions under “更多”.
- Reused `sys.nav.portal.studioBusinessProcesses` for the workbench menu entry and updated the deployed row in place, preserving its ID and visibility bindings.

### Verification gates

- Frontend unit tests: 9/9 passed across the journey context, model ledger, and confirmed-candidate filtering.
- Frontend source contracts: 39/39 passed across scope workspace, optional dimensional helpers, workbench routing, model ledger, development hand-off, and menu convergence.
- TypeScript: `pnpm exec tsc --noEmit` exited 0.
- Biome: 11 touched core files checked with no fixes or errors.
- Chrome 95 production build: 10,582 modules transformed; `pnpm build` exited 0. Existing `caniuse-lite` age and large-chunk notices remain warnings only.
- Admin menu contract: `./mvnw clean -Dtest=PortalMenuSeedDefaultsContractTest test` passed 14/14. The `clean` was required because the first non-clean run encountered the repository's stale `target/classes` failure mode.
- JSON/XML validation: both menu seed JSON files and both Liquibase XML files validated successfully.

### Runtime and browser evidence

- Running frontend container image: `sha256:6ed1cdb7482bd13ed05bddf347006e576fa6eb74bb16e44cfb0eb428ca96fde0`.
- Running admin container is healthy; Liquibase records `20260717-01-generic-modeling-workbench-menu` as `EXECUTED`.
- Persisted menu row remains ID `9423`, now points to `studio/modeling/modeling-workbench` and `/modeling/workbench`; `portal_menu_visibility` still has 2 bindings for that ID.
- `https://bi.yuzhicloud.com/` returns HTTP 200. `https://192.168.1.9/` also returns 200 when the configured `Host: bi.yuzhicloud.com` header is supplied; raw-IP browser routing remains governed by the existing Traefik host rule.
- Desktop 1366×768 and narrow 390×844 checks confirmed one create action, one overflow action, visible responsibility tabs, the primary continuation action, an on-demand dimensional helper drawer, no editable bus-matrix card in the primary UI, and four generic stages without false “已完成” labels.
- Browser console had 0 current errors after the final container replacement; relevant page APIs returned HTTP 200.

Screenshots:

- `docs/superpowers/evidence/generic-modeling-workbench/generic-modeling-scope-desktop-verified.png`
- `docs/superpowers/evidence/generic-modeling-workbench/generic-modeling-scope-narrow-verified.png`
- `docs/superpowers/evidence/generic-modeling-workbench/generic-modeling-dimensional-assist.png`
- `docs/superpowers/evidence/generic-modeling-workbench/generic-modeling-logical-desktop.png`

### Handoff boundary

- The worktree still contains earlier architecture, template-boundary, Sprint 64, and backend changes. They were preserved and were not reset or folded into this UI verification scope.
- No commit or push was performed.
