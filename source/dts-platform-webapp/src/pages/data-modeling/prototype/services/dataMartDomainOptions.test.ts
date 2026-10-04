import { beforeEach, describe, expect, it, vi } from "vitest";
import { loadDataMartCategoryOptions } from "./dataMartDomainOptions";

const apiMocks = vi.hoisted(() => ({ listPlanningCatalogDomains: vi.fn() }));

vi.mock("./planningCatalogDomainService", () => ({
	listPlanningCatalogDomains: apiMocks.listPlanningCatalogDomains,
}));

beforeEach(() => apiMocks.listPlanningCatalogDomains.mockReset());

describe("loadDataMartCategoryOptions", () => {
	it("exposes only business-category roots as data-mart scopes", async () => {
		apiMocks.listPlanningCatalogDomains.mockResolvedValue([
			{
				id: "9bb4e351-41f7-4e2f-b27e-adb55ca6cb11",
				code: "FIN",
				name: "财务管理",
				parentId: null,
			},
			{
				id: "7aa4e351-0000-0000-0000-000000000002",
				code: "FIN_DOMAIN",
				name: "财务域",
				parentId: "9bb4e351-41f7-4e2f-b27e-adb55ca6cb11",
			},
		]);

		await expect(loadDataMartCategoryOptions()).resolves.toEqual([
			{
				id: "9bb4e351-41f7-4e2f-b27e-adb55ca6cb11",
				code: "FIN",
				name: "财务管理",
			},
		]);
	});
});
