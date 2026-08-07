import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it, vi } from "vitest";

const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), "utf8");

function collectSource(directory: string): string {
	return readdirSync(directory)
		.flatMap((entry) => {
			const path = `${directory}/${entry}`;
			return statSync(path).isDirectory()
				? collectSource(path)
				: /\.tsx?$/.test(path) && !/\.test\./.test(path)
					? readFileSync(path, "utf8")
					: "";
		})
		.join("\n");
}

describe("prototype-owned data modeling frontend", () => {
	it("physically removes the retired workspace implementation", () => {
		for (const path of [
			"./pages/PlanningWorkspace.tsx",
			"./pages/HomeWorkspace.tsx",
			"./pages/StandardsWorkspace.tsx",
			"./pages/DimensionalModelingWorkspace.tsx",
			"./pages/MetricsWorkspace.tsx",
			"./pages/ToolsWorkspace.tsx",
			"./pages/RelationshipGraphWorkspace.tsx",
			"./components/WorkspacePage.tsx",
			"./components/ModelingEditor.tsx",
			"./components/ReverseModelingWizard.tsx",
		]) {
			expect(existsSync(new URL(path, import.meta.url)), `${path} must be deleted`).toBe(false);
		}
	});

	it("uses the prototype surface without retired pages or embedded demo facts", () => {
		const entry = read("./DataModelingPage.tsx");
		const prototypeRoot = fileURLToPath(new URL("./prototype", import.meta.url));
		const source = `${entry}\n${collectSource(prototypeRoot)}`;

		expect(entry).toContain('from "./prototype/DataModelingSurface"');
		expect(source).not.toMatch(
			/PlanningWorkspace|HomeWorkspace|StandardsWorkspace|DimensionalModelingWorkspace|MetricsWorkspace/,
		);
		expect(source).not.toMatch(/prototypeData|usePrototypeToast|建设计划/);
		expect(source).not.toMatch(/项目模型Demo|monthly_execution_rate|月度预算执行率/);

		for (const label of ["建模概览", "数仓规划", "数据标准", "维度建模", "数据指标", "通用工具", "关系图"]) {
			expect(source).toContain(label);
		}
		for (const label of ["创建维度", "创建贴源表"]) {
			expect(source).toContain(label);
		}
		for (const label of ["维度表", "明细表", "汇总表", "应用表"]) expect(source).toContain(`label: "${label}"`);
	});

	it("does not expose the retired construction-plan workflow in the workbench owner", () => {
		const planningService = read("./prototype/services/planningProjectionService.ts");
		const workbenchService = read("./prototype/services/modelWorkbenchService.ts");
		const workbenchEditor = read("./prototype/ModelingWorkbenchEditor.tsx");
		const workbenchSource = `${read("./prototype/ModelingWorkbenchPage.tsx")}\n${workbenchService}\n${workbenchEditor}`;

		expect(planningService).not.toMatch(/warehousePlanApi|warehouseStageLabel|listWarehousePlans/);
		expect(workbenchSource).not.toMatch(
			/建设计划|WarehousePlan|warehousePlanApi|saveWarehousePlanPolicy|loadConfirmedPlanDomains|listWarehousePlans|目标建设计划/,
		);
	});

	it("keeps workbench route selection and navigation guards deterministic", async () => {
		vi.doMock("@/api/modelRepresentationApi", () => ({}));
		vi.doMock("@/api/dimensionDefinitionApi", () => ({}));
		vi.doMock("@/store/userStore", () => ({}));
		vi.doMock("./navigation", () => ({}));
		vi.doMock("./prototype/ModelFieldEditorTable", () => ({}));
		vi.doMock("./prototype/ModelingWorkbenchEditor", () => ({}));
		vi.doMock("./prototype/ModelWorkbenchDialog", () => ({}));
		vi.doMock("./prototype/modelWorkbenchPresentation", () => ({}));
		vi.doMock("./prototype/PrototypePrimitives", () => ({}));
		vi.doMock("./prototype/services/modelWorkbenchService", () => ({}));
		vi.doMock("./prototype/services/planningProjectionService", () => ({}));
		vi.doMock("./prototype/useDataModelingMenuGrant", () => ({}));
		const module = (await import("./prototype/ModelingWorkbenchPage")) as Record<string, unknown>;
		const resolveRequestedModelSelection = module.resolveRequestedModelSelection as
			| ((
					models: Array<{ id: string }>,
					requestedModelId: string,
			  ) => { selectedModel: { id: string } | null; normalizedModelId: string })
			| undefined;
		const shouldBlockWorkbenchNavigation = module.shouldBlockWorkbenchNavigation as
			| ((dirty: boolean, currentPathname: string, nextPathname: string) => boolean)
			| undefined;
		const fallback = { id: "model-1" };
		const requested = { id: "model-2" };

		expect(resolveRequestedModelSelection).toBeTypeOf("function");
		if (!resolveRequestedModelSelection) return;
		const normalized = resolveRequestedModelSelection([fallback, requested], "missing-model");
		expect(normalized).toEqual({
			selectedModel: fallback,
			normalizedModelId: fallback.id,
		});
		expect(resolveRequestedModelSelection([fallback, requested], normalized.normalizedModelId)).toEqual(normalized);
		expect(resolveRequestedModelSelection([fallback, requested], requested.id)).toEqual({
			selectedModel: requested,
			normalizedModelId: requested.id,
		});
		expect(resolveRequestedModelSelection([], "missing-model")).toEqual({
			selectedModel: null,
			normalizedModelId: "",
		});

		expect(shouldBlockWorkbenchNavigation).toBeTypeOf("function");
		if (!shouldBlockWorkbenchNavigation) return;
		expect(
			shouldBlockWorkbenchNavigation(true, "/data-modeling/dimensions/workbench", "/data-modeling/dimensions/reverse"),
		).toBe(true);
		expect(
			shouldBlockWorkbenchNavigation(
				true,
				"/data-modeling/dimensions/workbench",
				"/data-modeling/dimensions/workbench",
			),
		).toBe(false);
		expect(
			shouldBlockWorkbenchNavigation(false, "/data-modeling/dimensions/workbench", "/data-modeling/dimensions/reverse"),
		).toBe(false);
	});

	it("integrates the approved editor contract into the workbench orchestrator", () => {
		const modeling = read("./prototype/ModelingWorkbenchPage.tsx");
		const editor = read("./prototype/ModelingWorkbenchEditor.tsx");
		const fieldTable = read("./prototype/ModelFieldEditorTable.tsx");
		const modelingLineCount = modeling.trimEnd().split("\n").length;

		for (const label of [
			"数仓分层",
			"业务分类",
			"存储策略",
			"表名规则",
			"表中文名",
			"生命周期",
			"负责人",
			"质量规则",
			"模型开发",
		])
			expect(editor).toContain(label);
		expect(fieldTable).toContain('["序号", "字段名称", "类型", "字段显示名", "主键", "非空", "维度属性编码"]');
		expect(fieldTable).not.toContain("安全等级");
		expect(fieldTable).toContain("当前版本尚无字段级表结构导入契约");
		expect(modeling).toMatch(/import \{ ModelingWorkbenchEditor \} from "\.\/ModelingWorkbenchEditor"/);
		expect(modeling).toMatch(/import \{[^}]*modelDraftFingerprint[^}]*\} from "\.\/modelWorkbenchPresentation"/s);
		expect(modeling).toMatch(/saveDimensionDefinitionDraft/);
		expect(modeling).toMatch(/confirmDimensionDefinitionDraft/);
		expect(modeling).toMatch(/ConceptDimensionRecordDialog/);
		expect(modeling).toMatch(/saveModelDraft/);
		expect(modeling).toMatch(/isConceptDimensionDraft/);
		expect(modeling).toMatch(/conceptDimensionDraftFromView/);
		expect(modeling).toMatch(/\{selectedModel\?\.modelType === "FACT" \? \(\s*<aside className="dmx-record-rail"/s);
		expect(modeling).toContain('"beforeunload"');
		expect(modeling).toMatch(/const blocker = useBlocker\(/);
		expect(modeling).toContain("blocker.proceed()");
		expect(modeling).toContain("blocker.reset()");
		expect(modeling).not.toMatch(/function ModelEditor|function FieldTable/);
		expect(modelingLineCount).toBeLessThanOrEqual(800);
	});

	it("connects production pages to canonical owners and keeps unsupported actions disabled", () => {
		const planning = read("./prototype/PlanningPage.tsx");
		const planningEditors = read("./prototype/PlanningEditors.tsx");
		const catalogEditors = read("./prototype/PlanningCatalogEditors.tsx");
		const planningSidebar = read("./prototype/PlanningSidebar.tsx");
		const modeling = read("./prototype/ModelingWorkbenchPage.tsx");
		const modelDialogs = read("./prototype/ModelWorkbenchDialog.tsx");
		const reverse = read("./prototype/ReverseModelingPage.tsx");
		const tools = read("./prototype/ToolsPage.tsx");
		const standardsService = read("./prototype/services/standardsProjectionService.ts");
		const relationshipService = read("../../api/services/modelingRelationshipGraphService.ts");
		const metrics = read("./prototype/MetricsPage.tsx");
		const prototypeSource = collectSource(fileURLToPath(new URL("./prototype", import.meta.url)));

		expect(planning).toMatch(/PlanningSidebar|Drawer|新建/);
		expect(planning).toMatch(/CatalogDomainForm|BusinessProcessForm|DataMartForm|SubjectDomainForm/);
		expect(planningEditors).toMatch(/createBusinessProcessApi|createDataMart|createSubjectDomain/);
		expect(planningEditors).toMatch(/confirmDataMart|confirmSubjectDomain/);
		expect(catalogEditors).toMatch(/CatalogDomainForm|listPlanningCatalogDomains/);
		expect(planningSidebar).not.toContain("建模空间");
		expect(modeling).toMatch(/saveModelDraft|ModelWorkbenchDialog|创建贴源表（尚未接入）/);
		expect(modeling).toMatch(/创建维度\s*<\/button>[\s\S]*创建维度表\s*<\/button>/);
		expect(modeling).toMatch(/createModel\("dimension"\)[\s\S]*createModel\("dimension-table"\)/);
		expect(modeling).toMatch(/onConfirmDimension=\{\(\) => void confirmConceptVersion\(\)\}/);
		expect(modeling).toMatch(/fieldRowIds|key=\{fieldRowIds\[index\]\}/);
		expect(modeling).toMatch(/dataDomains/);
		expect(modeling).toMatch(/group\.models\.length/);
		expect(modeling).toMatch(/数据域视角|业务分类视角/);
		expect(modeling).toMatch(/effectiveView/);
		expect(modeling).toMatch(/getModelRepresentation|representationScope: "BUSINESS"|useDataModelingMenuGrant/);
		expect(modelDialogs).toMatch(/representationScope: "TECHNICAL"|OPEN_ADVANCED_DBT|canMaintain/);
		expect(modelDialogs).toMatch(
			/createReleaseCandidate|lockReleaseCandidate|retryReleaseCandidate|publishReleaseCandidate/,
		);
		expect(modelDialogs).toMatch(/state: "COMMITTED"|创建新草稿/);
		expect(reverse).toMatch(/inspectDbtModelArchive|previewModelSpecImport|applyModelSpecImport/);
		expect(reverse).toMatch(/retryModelSpecImport|forwardUndoModelSpecImport|renameMappings/);
		expect(reverse).toMatch(/defaultImportConflictResolutions|key=\{mapping\._clientId\}/);
		expect(reverse).toContain('item.action !== "BLOCKED"');
		expect(reverse).toContain("useDataModelingMenuGrant");
		expect(prototypeSource).not.toContain("useCatalogMaintainerAccess");
		expect(standardsService).not.toContain("deleteGlossaryTerm");
		expect(relationshipService).toContain("limit: GRAPH_PAGE_SIZE");
		expect(relationshipService).not.toMatch(/kind:\s*kindForView|query:\s*query\.query/);
		expect(metrics).not.toContain("expressionSql");
		expect(tools).toMatch(/getDataModelingToolWorkflows/);
		expect(tools).not.toMatch(/mock|demo/i);
		expect(tools).toContain("本页不创建统一工具运行台账，也不拼接模拟历史");
	});

	it("keeps the product copy aligned with the approved menu corrections", () => {
		const navigation = read("./navigation.ts");
		expect(navigation).toContain('title: "建模概览"');
		expect(navigation).toContain('title: "规划参数配置"');
		expect(navigation).not.toMatch(/title: "首页"|title: "系统管理"|home\/recent|home\/tasks/);
	});
});
