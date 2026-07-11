import { afterEach, describe, expect, it, vi } from "vitest";

const { get, post, put, del } = vi.hoisted(() => ({
	get: vi.fn(),
	post: vi.fn(),
	put: vi.fn(),
	del: vi.fn(),
}));

vi.mock("./apiClient", () => ({ default: { get, post, put, delete: del } }));

import {
	createBusinessProcessApi,
	deleteBusinessProcessApi,
	listBusinessProcessesApi,
	listConformedDimensionsApi,
	listWarehouseLayersApi,
	saveBusMatrixLinkApi,
	validateGrainApi,
} from "./sprint64GovernanceApi";

describe("sprint64 governance API", () => {
	afterEach(() => vi.clearAllMocks());

	it("uses stable domain-scoped process and matrix routes", async () => {
		get.mockResolvedValueOnce([]).mockResolvedValueOnce([]);
		post.mockResolvedValueOnce({ processId: "node-plan-loop" });
		put.mockResolvedValueOnce({ processId: "node-plan-loop", dimensionId: "node-type", enabled: true });
		del.mockResolvedValueOnce(true);

		await listBusinessProcessesApi("domain-1");
		await createBusinessProcessApi("domain-1", { processId: "node-plan-loop", name: "节点计划闭环" });
		await deleteBusinessProcessApi("domain-1", "node-plan-loop");
		await saveBusMatrixLinkApi("domain-1", { processId: "node-plan-loop", dimensionId: "node-type", enabled: true });

		expect(get).toHaveBeenNthCalledWith(1, { url: "/governance/sprint64/domains/domain-1/processes", _skipErrorToast: true });
		expect(post).toHaveBeenCalledWith({
		url: "/governance/sprint64/domains/domain-1/processes",
		data: { processId: "node-plan-loop", name: "节点计划闭环" },
		_skipErrorToast: true,
		});
		expect(del).toHaveBeenCalledWith({ url: "/governance/sprint64/domains/domain-1/processes/node-plan-loop", _skipErrorToast: true });
		expect(put).toHaveBeenCalledWith({
		url: "/governance/sprint64/domains/domain-1/bus-matrix",
		data: { processId: "node-plan-loop", dimensionId: "node-type", enabled: true },
		});
	});

	it("keeps layer, dimension and grain validation contracts explicit", async () => {
		get.mockResolvedValue([]);
		post.mockResolvedValue({ status: "ready" });
		await listWarehouseLayersApi();
		await listConformedDimensionsApi("domain-1");
		await validateGrainApi({ warehouseLayer: "DWD", statement: "订单明细", grainKeys: ["order_id"] });
		expect(get).toHaveBeenCalledWith({ url: "/governance/sprint64/warehouse-layers", _skipErrorToast: true });
		expect(get).toHaveBeenCalledWith({ url: "/governance/sprint64/domains/domain-1/conformed-dimensions", _skipErrorToast: true });
		expect(post).toHaveBeenCalledWith({
		url: "/governance/sprint64/grain/validate",
		data: { warehouseLayer: "DWD", statement: "订单明细", grainKeys: ["order_id"] },
		_skipErrorToast: true,
		});
	});
});
