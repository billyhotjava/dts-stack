// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
	listDataMarts: vi.fn(),
	listBusinessProcessesApi: vi.fn(),
	listPlanningCatalogDomains: vi.fn(),
}));

vi.mock("@/api/dataMartApi", () => ({ listDataMarts: mocks.listDataMarts }));
vi.mock("@/api/modelSpecApi", () => ({ listModelSpecs: vi.fn() }));
vi.mock("@/api/services/modelingOverviewFactService", () => ({
	listIndicatorsForModelingOverview: vi.fn(),
	listStandardsForModelingOverview: vi.fn(),
}));
vi.mock("@/api/sprint64GovernanceApi", () => ({
	listBusinessProcessesApi: mocks.listBusinessProcessesApi,
	listWarehouseLayersApi: vi.fn(),
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
});
