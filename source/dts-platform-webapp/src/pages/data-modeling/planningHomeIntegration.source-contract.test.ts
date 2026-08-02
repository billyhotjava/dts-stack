import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), "utf8");

const planningSource = read("./pages/PlanningWorkspace.tsx");
const homeSource = read("./pages/HomeWorkspace.tsx");
const adapterSource = read("./adapters/planningHomeAdapter.ts");

describe("Sprint-84 planning and overview real capability contract", () => {
	it("uses canonical planning owners without built-in customer scenarios", () => {
		expect(planningSource).toContain("loadPlanningCatalog");
		expect(adapterSource).toContain("listWarehousePlans");
		expect(adapterSource).toContain("catalogDomainService");
		expect(adapterSource).toContain("listBusinessProcessesApi");
		expect(adapterSource).toContain("listWarehouseLayersApi");
		expect(adapterSource).not.toContain('from "@/api/platformApi"');
		expect(planningSource).not.toMatch(/planningViews|example_|界面示例|UiStageNotice|BackendPendingButton/);
	});

	it("loads the overview from authoritative facts and exposes honest states", () => {
		expect(homeSource).toContain("loadModelingHomeProjection");
		expect(adapterSource).toContain("listModelSpecs");
		expect(adapterSource).toContain("listStandardsForModelingOverview");
		expect(adapterSource).toContain("listIndicatorsForModelingOverview");
		expect(homeSource).not.toMatch(/recentRows|taskRows|overviewStats|deliveryStages|界面示例|BackendPendingButton/);
		for (const stateCopy of ["正在加载", "暂无", "重新加载", "无权访问"]) {
			expect(`${planningSource}\n${homeSource}`).toContain(stateCopy);
		}
	});

	it("routes model creation into the canonical workbench instead of a local draft", () => {
		expect(homeSource).toContain("/data-modeling/dimensions/workbench?create=1");
	});
});
