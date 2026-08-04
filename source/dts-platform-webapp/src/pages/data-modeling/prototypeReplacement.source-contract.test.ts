import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

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
		expect(source).not.toMatch(/prototypeData|usePrototypeToast|建设计划上下文/);
		expect(source).not.toMatch(/项目模型Demo|monthly_execution_rate|月度预算执行率/);

		for (const label of ["建模概览", "数仓规划", "数据标准", "维度建模", "数据指标", "通用工具", "关系图"]) {
			expect(source).toContain(label);
		}
		for (const label of ["创建维度", "创建贴源表"]) {
			expect(source).toContain(label);
		}
		for (const label of ["维度表", "明细表", "汇总表", "应用表"]) expect(source).toContain(`label: "${label}"`);
	});

	it("does not expose the retired construction-plan workflow in prototype pages", () => {
		const pageSource = [
			"./prototype/ModelingWorkbenchPage.tsx",
			"./prototype/PlanningPage.tsx",
			"./prototype/ReverseModelingPage.tsx",
			"./prototype/RelationshipGraphPage.tsx",
			"./prototype/PlanningCatalogEditors.tsx",
		]
			.map(read)
			.join("\n");
		const planningService = read("./prototype/services/planningProjectionService.ts");

		expect(pageSource).not.toMatch(
			/建设计划|WarehousePlanEditor|saveWarehousePlanPolicy|loadConfirmedPlanDomains|目标建设计划/,
		);
		expect(planningService).not.toMatch(/warehousePlanApi|warehouseStageLabel|listWarehousePlans/);
	});

	it("connects production pages to canonical owners and keeps unsupported actions disabled", () => {
		const planning = read("./prototype/PlanningPage.tsx");
		const modeling = read("./prototype/ModelingWorkbenchPage.tsx");
		const modelDialogs = read("./prototype/ModelWorkbenchDialog.tsx");
		const reverse = read("./prototype/ReverseModelingPage.tsx");
		const tools = read("./prototype/ToolsPage.tsx");
		const standardsService = read("./prototype/services/standardsProjectionService.ts");
		const relationshipService = read("../../api/services/modelingRelationshipGraphService.ts");
		const metrics = read("./prototype/MetricsPage.tsx");
		const prototypeSource = collectSource(fileURLToPath(new URL("./prototype", import.meta.url)));

		expect(planning).toMatch(/createBusinessProcessApi|createDataMart|CatalogDomainEditor/);
		expect(planning).toMatch(/DataMartDomainOption|value=\{item.id\}|canMaintain=\{canMaintain\}/);
		expect(modeling).toMatch(/saveModelDraft|ModelWorkbenchDialog|创建贴源表（尚未接入）/);
		expect(modeling).toMatch(/fieldRowIds|key=\{fieldRowIds\[index\]\}/);
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
