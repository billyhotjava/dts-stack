// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
	listDataMarts: vi.fn(),
	listBusinessProcessesApi: vi.fn(),
	listPlanningCatalogDomains: vi.fn(),
	listWarehouseLayersApi: vi.fn(),
}));

vi.mock("@/api/dataMartApi", () => ({ listDataMarts: mocks.listDataMarts }));
vi.mock("@/api/modelSpecApi", () => ({ listModelSpecs: vi.fn() }));
vi.mock("@/api/services/modelingOverviewFactService", () => ({
	listIndicatorsForModelingOverview: vi.fn(),
	listStandardsForModelingOverview: vi.fn(),
}));
vi.mock("@/api/sprint64GovernanceApi", () => ({
	listBusinessProcessesApi: mocks.listBusinessProcessesApi,
	listWarehouseLayersApi: mocks.listWarehouseLayersApi,
}));
vi.mock("./planningCatalogDomainService", () => ({
	listPlanningCatalogDomains: mocks.listPlanningCatalogDomains,
}));

import { loadPlanningProjection } from "./planningProjectionService";

describe("loadPlanningProjection catalog writes", () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it("does not project an unrelated owner into the modeling-space page", async () => {
		const projection = await loadPlanningProjection("spaces");

		expect(projection.rows).toEqual([]);
		expect(projection.readOnlyReason).toContain("独立服务端 owner");
	});

	it("uses the stable domain id instead of the display code for business-process APIs", async () => {
		mocks.listPlanningCatalogDomains.mockResolvedValue([
			{ id: "category-1", code: "FINANCE", name: "财务管理", parentId: null },
			{ id: "domain-uuid", code: "FIN", name: "财务域", parentId: "category-1" },
		]);
		mocks.listBusinessProcessesApi.mockResolvedValue([
			{ domainId: "domain-uuid", processId: "BUDGET", name: "预算管理", confirmed: true },
		]);

		const projection = await loadPlanningProjection("processes");

		expect(mocks.listBusinessProcessesApi).toHaveBeenCalledWith("domain-uuid");
		expect(projection.rows[0]?.id).toBe("domain-uuid:BUDGET");
	});

	it("keeps the data-mart list window within the backend contract", async () => {
		mocks.listDataMarts.mockResolvedValue([]);

		await loadPlanningProjection("marts");

		expect(mocks.listDataMarts).toHaveBeenCalledWith({ limit: 100 });
	});

	it("describes the built-in layer dictionary without pointing to an unavailable configuration page", async () => {
		mocks.listWarehouseLayersApi.mockResolvedValue([]);

		const projection = await loadPlanningProjection("layers");

		expect(projection.readOnlyReason).toBe(
			"系统分层字典由平台内置并统一生效，当前版本只读，暂无独立的分层策略配置入口。",
		);
		expect(projection.readOnlyReason).not.toContain("规划参数配置中维护");
	});

	it("states the current planning-parameter boundary in customer-facing language", async () => {
		const projection = await loadPlanningProjection("system");

		expect(projection.readOnlyReason).toBe("当前版本尚未提供可维护的规划参数；配置能力接入前，本页仅说明功能边界。");
		expect(projection.readOnlyReason).not.toMatch(/owner|旧流程|配置接口/i);
	});
});
