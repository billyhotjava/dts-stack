import { beforeEach, describe, expect, it, vi } from "vitest";
import {
	classifyModelingRelationshipGraphFailure,
	loadModelingRelationshipGraph,
	modelingRelationshipNodePath,
} from "./modelingRelationshipGraphService";

const apiMocks = vi.hoisted(() => ({
	getGraph: vi.fn(),
	listPlans: vi.fn(),
}));

vi.mock("../warehousePlanApi", () => ({
	getWarehousePlanRelationshipGraph: apiMocks.getGraph,
	listWarehousePlans: apiMocks.listPlans,
}));

beforeEach(() => {
	apiMocks.getGraph.mockReset();
	apiMocks.listPlans.mockReset();
});

describe("modelingRelationshipGraphService", () => {
	it("loads an unfiltered bounded graph so both ends of standard and indicator edges survive", async () => {
		apiMocks.getGraph.mockResolvedValue({ planId: "plan-1", nodes: [], edges: [], truncated: false });

		await loadModelingRelationshipGraph("plan-1", { view: "standards", query: "  客户  ", cursor: "cursor-1" });

		expect(apiMocks.getGraph).toHaveBeenCalledWith("plan-1", { limit: 500 });
	});

	it("translates the canonical backend legacy detail route into the new modeling workspace", () => {
		expect(
			modelingRelationshipNodePath({
				kind: "MODEL",
				route: "/modeling/workbench?planId=plan-1&module=models&assetId=model-1&revision=3",
			}),
		).toBe("/data-modeling/dimensions/workbench?planId=plan-1&modelSpecId=model-1&revision=3");
		expect(modelingRelationshipNodePath({ kind: "MODEL", route: "https://example.com/unsafe" })).toBeNull();
		expect(
			modelingRelationshipNodePath({
				kind: "STANDARD",
				route: "/modeling/workbench?assetId=standard-1&version=2",
			}),
		).toBeNull();
	});

	it("classifies authorization failures without falling back to local data", () => {
		expect(classifyModelingRelationshipGraphFailure({ response: { status: 403 } })).toEqual({
			kind: "permission",
			message: "当前账号无权访问该建设计划的关系图，请联系管理员授权。",
		});
	});
});
