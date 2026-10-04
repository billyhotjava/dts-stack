import { afterEach, describe, expect, it, vi } from "vitest";

const { get, post, put, del } = vi.hoisted(() => ({
	get: vi.fn(),
	post: vi.fn(),
	put: vi.fn(),
	del: vi.fn(),
}));

vi.mock("./apiClient", () => ({ default: { get, post, put, delete: del } }));

import {
	confirmModelingCandidatesApi,
	createBusinessProcessApi,
	createConformedDimensionApi,
	deleteBusinessProcessApi,
	deleteConformedDimensionApi,
	installModelingTemplateApi,
	listBusinessProcessesApi,
	listConformedDimensionsApi,
	listModelingTemplatesApi,
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

		expect(get).toHaveBeenNthCalledWith(1, {
			url: "/modeling/business-processes?domainId=domain-1",
			_skipErrorToast: true,
		});
		expect(post).toHaveBeenCalledWith({
			url: "/modeling/business-processes?domainId=domain-1",
			data: { processId: "node-plan-loop", name: "节点计划闭环" },
			_skipErrorToast: true,
		});
		expect(del).toHaveBeenCalledWith({
			url: "/modeling/business-processes/node-plan-loop?domainId=domain-1",
			_skipErrorToast: true,
		});
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
		expect(get).toHaveBeenCalledWith({
			url: "/governance/sprint64/domains/domain-1/conformed-dimensions",
			_skipErrorToast: true,
		});
		expect(post).toHaveBeenCalledWith({
			url: "/governance/sprint64/grain/validate",
			data: { warehouseLayer: "DWD", statement: "订单明细", grainKeys: ["order_id"] },
			_skipErrorToast: true,
		});
	});

	it("uses explicit routes for dimension lifecycle, candidate confirmation and optional templates", async () => {
		get.mockResolvedValueOnce([]);
		post
			.mockResolvedValueOnce({ dimensionId: "organization" })
			.mockResolvedValueOnce({ confirmedProcesses: 1, confirmedDimensions: 1 })
			.mockResolvedValueOnce({ status: "INSTALLED", createdProcesses: 2, createdDimensions: 3 });
		del.mockResolvedValueOnce(true);

		await createConformedDimensionApi("domain / 1", {
			dimensionId: "organization",
			name: "组织机构",
			sourceModel: "dim_organization",
		});
		await deleteConformedDimensionApi("domain / 1", "organization / code");
		await confirmModelingCandidatesApi("domain / 1", {
			processIds: ["process-a"],
			dimensionIds: ["organization"],
		});
		await listModelingTemplatesApi();
		await installModelingTemplateApi("industry / pack", "domain / 1");

		expect(post).toHaveBeenNthCalledWith(1, {
			url: "/governance/sprint64/domains/domain%20%2F%201/conformed-dimensions",
			data: { dimensionId: "organization", name: "组织机构", sourceModel: "dim_organization" },
			_skipErrorToast: true,
		});
		expect(del).toHaveBeenCalledWith({
			url: "/governance/sprint64/domains/domain%20%2F%201/conformed-dimensions/organization%20%2F%20code",
			_skipErrorToast: true,
		});
		expect(post).toHaveBeenNthCalledWith(2, {
			url: "/governance/sprint64/domains/domain%20%2F%201/modeling-candidates/confirm",
			data: { processIds: ["process-a"], dimensionIds: ["organization"] },
			_skipErrorToast: true,
		});
		expect(get).toHaveBeenCalledWith({ url: "/governance/modeling-templates", _skipErrorToast: true });
		expect(post).toHaveBeenNthCalledWith(3, {
			url: "/governance/modeling-templates/industry%20%2F%20pack/install?domainId=domain%20%2F%201",
			data: undefined,
			_skipErrorToast: true,
		});
	});
});
