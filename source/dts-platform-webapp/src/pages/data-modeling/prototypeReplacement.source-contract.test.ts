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
			/建设计划|saveWarehousePlanPolicy|loadConfirmedPlanDomains|listWarehousePlans|目标建设计划/,
		);
	});

	it("keeps workbench route selection and navigation guards deterministic", async () => {
		vi.doMock("@/api/modelRepresentationApi", () => ({}));
		vi.doMock("@/api/dimensionDefinitionApi", () => ({}));
		vi.doMock("@/api/modelSpecApi", () => ({}));
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
			selectedModel: null,
			normalizedModelId: "",
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
	}, 20000);

	it("integrates the approved editor contract into the workbench orchestrator", () => {
		const modeling = read("./prototype/ModelingWorkbenchPage.tsx");
		const editor = read("./prototype/ModelingWorkbenchEditor.tsx");
		const fieldTable = read("./prototype/ModelFieldEditorTable.tsx");
		const workbenchService = read("./prototype/services/modelWorkbenchService.ts");
		const implementationBinding = read("./prototype/ModelImplementationBindingFields.tsx");
		const modelingLineCount = modeling.trimEnd().split("\n").length;

		for (const label of ["数仓分层", "存储策略", "表名规则", "表中文名", "生命周期", "负责人", "质量规则", "模型开发"])
			expect(editor).toContain(label);
		expect(editor).not.toContain("<span>业务分类</span>");
		expect(fieldTable).toMatch(/"序号".*"字段名称".*"类型".*"字段显示名".*"主键".*"非空".*"维度属性编码"/s);
		expect(fieldTable).not.toContain("安全等级");
		expect(fieldTable).toContain("当前版本尚无字段级表结构导入契约");
		expect(modeling).toMatch(/import \{ ModelingWorkbenchEditor \} from "\.\/ModelingWorkbenchEditor"/);
		expect(modeling).toMatch(/import \{[^}]*modelDraftFingerprint[^}]*\} from "\.\/modelWorkbenchPresentation"/s);
		expect(modeling).toMatch(/saveDimensionDefinitionDraft/);
		expect(modeling).toMatch(/useConceptDimensionWorkflow|confirmConceptVersion/);
		expect(modeling).toMatch(/ConceptDimensionRecordDialog/);
		expect(modeling).toMatch(/saveModelDraft/);
		expect(modeling).toMatch(/ModelWorkbenchCatalogList/);
		expect(modeling).not.toMatch(
			/ModelWorkbenchCatalogPanel|catalogMode|buildWorkbenchCatalogGroups|workbenchCatalogEmptyMessage/,
		);
		expect(modeling).not.toContain("请从目录选择模型");
		expect(modeling).toMatch(/loadModelWorkbenchDraft/);
		expect(modeling).not.toContain("await load(saved.id)");
		expect(workbenchService).toMatch(/saveModelImplementation|targetPhysicalName/);
		expect(editor).toMatch(/import \{ ModelImplementationBindingFields \} from "\.\/ModelImplementationBindingFields"/);
		expect(implementationBinding).toMatch(/实现输入方式|物理来源|上游模型|事实类型|时间语义|应用场景/);
		expect(workbenchService).toMatch(
			/collectCurrentWarehousePlanSources|sourceRefs: draft\.sourceRefs|dependsOn: draft\.dependsOn/,
		);
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
		const dataArchitecturePage = read("../data-architecture/DataArchitecturePage.tsx");
		const dataArchitectureNavigation = read("../data-architecture/navigation.ts");
		const planningEditors = read("./prototype/PlanningEditors.tsx");
		const catalogEditors = read("./prototype/PlanningCatalogEditors.tsx");
		const planningStyles = read("./data-modeling.css");
		const modeling = read("./prototype/ModelingWorkbenchPage.tsx");
		const catalogList = read("./prototype/ModelWorkbenchCatalogList.tsx");
		const createMenu = read("./prototype/ModelWorkbenchCreateMenu.tsx");
		const catalogActions = read("./prototype/useCatalogActions.ts");
		const modelDialogs = read("./prototype/ModelWorkbenchDialog.tsx");
		const modelPublishDialog = read("./prototype/ModelPublishDialog.tsx");
		const reverse = read("./prototype/ReverseModelingPage.tsx");
		const tools = read("./prototype/ToolsPage.tsx");
		const toolWorkflows = read("../../features/modeling/navigation/dataModelingToolWorkflows.ts");
		const standardsService = read("./prototype/services/standardsProjectionService.ts");
		const relationshipService = read("../../api/services/modelingRelationshipGraphService.ts");
		const metrics = read("./prototype/MetricsPage.tsx");
		const prototypeSource = collectSource(fileURLToPath(new URL("./prototype", import.meta.url)));

		expect(planning).toMatch(/Drawer|新建/);
		expect(planning).not.toMatch(/PlanningSidebar|navigationSurface|sidebarActiveView|dmx-planning-layout/);
		expect(dataArchitecturePage).not.toContain("resolveDataArchitectureNavigation");
		expect(dataArchitecturePage).not.toMatch(/navigationSurface|sidebarActiveView/);
		expect(dataArchitectureNavigation).not.toMatch(/modelingSpaceDataArchitecturePath|source:\s*"modeling-space"/);
		expect(planning).toMatch(/CatalogDomainForm|BusinessProcessForm|DataMartForm|SubjectDomainForm/);
		expect(planningEditors).toMatch(/createBusinessProcessApi|createDataMart|createSubjectDomain/);
		expect(planningEditors).toMatch(/confirmDataMart|confirmSubjectDomain/);
		expect(catalogEditors).toMatch(/CatalogDomainForm|listPlanningCatalogDomains/);
		expect(catalogEditors).not.toMatch(/loadPlanningContextPolicy|warehousePlanApi|建模策略/);
		expect(existsSync(new URL("./prototype/PlanningSidebar.tsx", import.meta.url))).toBe(false);
		expect(planningStyles).not.toMatch(/\.dmx-planning-(?:layout|sidebar)/);
		const publishSectionRule = planningStyles.match(/\.dmx-publish-grid > section\s*\{([^}]*)\}/)?.[1] || "";
		expect(publishSectionRule).toContain("min-width: 0");
		expect(modeling).toMatch(/saveModelDraft|ModelWorkbenchDialog/);
		expect(modeling).not.toMatch(/ModelWorkbenchCatalogPanel|WorkbenchCatalogTree|buildWorkbenchCatalogGroups/);
		expect(catalogList).toMatch(/ModelWorkbenchCreateMenu|新建模型/);
		expect(catalogList).not.toContain("进入目录编辑器");
		expect(createMenu).toMatch(/onCreate\("dimension"/);
		expect(createMenu).toContain('kind: "dimension-table"');
		expect(createMenu).toContain("创建贴源表（尚未接入）");
		expect(createMenu).toContain('label: "创建维度表"');
		expect(createMenu).toContain('label: "创建明细表"');
		expect(createMenu).toContain('label: "创建汇总表"');
		expect(createMenu).toContain('label: "创建应用表"');
		expect(modeling).toMatch(/onConfirmDimension=\{\(\) => void confirmConceptVersion\(\)\}/);
		expect(modeling).toMatch(/fieldRowIds|key=\{fieldRowIds\[index\]\}/);
		expect(createMenu).toContain("概念模型");
		expect(createMenu).toContain("逻辑模型");
		expect(createMenu).toContain("请选择业务分类");
		expect(catalogList).toContain("关系图");
		expect(catalogList).toContain("克隆");
		expect(modeling).toMatch(/useCatalogActions/);
		expect(catalogActions).toMatch(/deleteModelSpec|deleteDimensionDefinition|retireDimensionDefinition/);
		expect(modeling).toMatch(
			/getModelLifecycle|getModelRepresentation|implementationRevision|representationScope: "BUSINESS"|useDataModelingMenuGrant/,
		);
		expect(modelDialogs).toMatch(/representationScope: "TECHNICAL"|OPEN_ADVANCED_DBT|canMaintain/);
		expect(modelDialogs).toMatch(/导入 dbt ZIP|intent=advanced/);
		expect(modelDialogs).not.toMatch(/\/api\/etl\/dbt\/files|\/etl\/dbt\/files/);
		expect(modelPublishDialog).toMatch(/getModelLifecycle|compileModelLifecycle/);
		expect(modelPublishDialog).toMatch(
			/createReleaseCandidate|lockReleaseCandidate|retryReleaseCandidate|rematerializeReleaseCandidate|publishReleaseCandidate/,
		);
		expect(modelDialogs.trimEnd().split("\n").length).toBeLessThanOrEqual(850);
		expect(modelDialogs).toMatch(/state: "COMMITTED"|创建新草稿/);
		expect(reverse).toMatch(/inspectDbtModelArchive|previewModelSpecImport|applyModelSpecImport/);
		expect(reverse).toMatch(/retryModelSpecImport|forwardUndoModelSpecImport|renameMappings/);
		expect(reverse).toMatch(/defaultImportConflictResolutions|key=\{mapping\._clientId\}/);
		expect(reverse).toContain('item.action !== "BLOCKED"');
		expect(reverse).toMatch(/conversionMode === "DBT_BACKED"|isAdvancedDbtImportResult/);
		expect(reverse).toContain("open=advanced");
		expect(modeling).toMatch(/searchParams\.get\("open"\)|requestedDialog/);
		expect(modeling).toMatch(/setDialog\("advanced"\)/);
		expect(reverse).toContain("useDataModelingMenuGrant");
		expect(prototypeSource).not.toContain("useCatalogMaintainerAccess");
		expect(standardsService).not.toContain("deleteGlossaryTerm");
		expect(relationshipService).toContain("limit: GRAPH_PAGE_SIZE");
		expect(relationshipService).not.toMatch(/kind:\s*kindForView|query:\s*query\.query/);
		expect(metrics).not.toContain("expressionSql");
		expect(metrics).not.toMatch(/loadPlanningContextPolicy|warehousePlanApi|建模策略/);
		expect(metrics).toContain('from "@/components/table"');
		expect(metrics).toMatch(/<CompactTable<IndicatorDefinition>/);
		expect(metrics).toContain('className="dmx-list-toolbar"');
		expect(metrics).toContain("返回指标列表");
		expect(metrics).not.toMatch(/<aside className="dmx-metric-catalog"/);
		expect(metrics).not.toContain("dmx-metric-tree");
		expect(tools).toMatch(/getDataModelingToolWorkflows/);
		expect(tools).not.toMatch(/mock|demo/i);
		expect(tools).toContain("本页不创建统一工具运行台账，也不拼接模拟历史");
		expect(toolWorkflows).toContain('title: "dbt ZIP 建模"');
		expect(toolWorkflows).toContain('owner: "ModelSpec / 高级 dbt 实现"');
	});

	it("keeps the product copy aligned with the approved menu corrections", () => {
		const navigation = read("./navigation.ts");
		expect(navigation).toContain('title: "建模概览"');
		expect(navigation).not.toContain('title: "建模策略"');
		expect(navigation).not.toMatch(/title: "首页"|title: "系统管理"|home\/recent|home\/tasks/);
	});
});
