// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
	getWarehousePlanCategories: vi.fn(),
	getWarehousePlanSources: vi.fn(),
	getDomainTree: vi.fn(),
	listDataMarts: vi.fn(),
	listBusinessProcessesApi: vi.fn(),
	listSubjectDomains: vi.fn(),
}));

vi.mock("../warehousePlanApi", async (importOriginal) => ({
	...(await importOriginal<typeof import("../warehousePlanApi")>()),
	getWarehousePlanCategories: mocks.getWarehousePlanCategories,
	getWarehousePlanSources: mocks.getWarehousePlanSources,
}));
vi.mock("../dataMartApi", () => ({ listDataMarts: mocks.listDataMarts }));
vi.mock("../platformApi", () => ({ getDomainTree: mocks.getDomainTree }));
vi.mock("../sprint64GovernanceApi", () => ({ listBusinessProcessesApi: mocks.listBusinessProcessesApi }));
vi.mock("../subjectDomainApi", () => ({ listSubjectDomains: mocks.listSubjectDomains }));

import { loadModelingImportContext } from "./modelingImportContextService";

describe("modeling import planning context", () => {
	beforeEach(() => {
		vi.clearAllMocks();
		mocks.getWarehousePlanSources.mockResolvedValue({ bindings: [], totalPages: 1 });
		mocks.getDomainTree.mockResolvedValue([]);
		mocks.listDataMarts.mockResolvedValue([{ id: "mart-1", status: "CURRENT" }]);
		mocks.listSubjectDomains.mockResolvedValue([{ id: "subject-1", martId: "mart-1", status: "CURRENT" }]);
	});

	it("loads only confirmed domains and their active confirmed business processes", async () => {
		mocks.getWarehousePlanCategories.mockResolvedValue({
			value: {
				domainBindings: [
					{
						domainId: "domain-1",
						confirmationStatus: "CONFIRMED",
						resolutionStatus: "AVAILABLE",
					},
					{
						domainId: "domain-candidate",
						confirmationStatus: "CANDIDATE",
						resolutionStatus: "AVAILABLE",
					},
				],
			},
		});
		mocks.listBusinessProcessesApi.mockResolvedValue([
			{ id: "process-current", domainId: "domain-1", confirmed: true, lifecycleStatus: "ACTIVE" },
			{ id: "process-retired", domainId: "domain-1", confirmed: true, lifecycleStatus: "RETIRED" },
			{ id: "process-candidate", domainId: "domain-1", confirmed: false, lifecycleStatus: "ACTIVE" },
		]);

		const context = await loadModelingImportContext("plan-1");

		expect(mocks.listBusinessProcessesApi).toHaveBeenCalledOnce();
		expect(mocks.listBusinessProcessesApi).toHaveBeenCalledWith("domain-1");
		expect(context.domains).toHaveLength(1);
		expect(context.businessProcesses).toEqual([
			{ id: "process-current", domainId: "domain-1", confirmed: true, lifecycleStatus: "ACTIVE" },
		]);
		expect(context.dataMarts).toEqual([{ id: "mart-1", status: "CURRENT" }]);
		expect(context.subjectDomains).toEqual([{ id: "subject-1", martId: "mart-1", status: "CURRENT" }]);
	});

	it("uses active platform-global data domains instead of a stale retired-plan snapshot", async () => {
		mocks.getWarehousePlanCategories.mockResolvedValue({
			value: {
				domainBindings: [
					{
						domainId: "domain-legacy",
						confirmationStatus: "CONFIRMED",
						resolutionStatus: "AVAILABLE",
					},
				],
			},
		});
		mocks.getDomainTree.mockResolvedValue({
			data: [
				{
				id: "category-1",
				code: "INSTITUTE",
				name: "研究所业务",
				lifecycleStatus: "ACTIVE",
				children: [
					{
						id: "domain-quality",
						code: "QUALITY",
						name: "质量管理域",
						lifecycleStatus: "ACTIVE",
						parentId: "category-1",
						children: [],
					},
					{
						id: "domain-retired",
						code: "RETIRED",
						name: "已停用域",
						lifecycleStatus: "RETIRED",
						parentId: "category-1",
					},
				],
				},
			],
		});
		mocks.listBusinessProcessesApi.mockResolvedValue([]);

		const context = await loadModelingImportContext("plan-1");

		expect(context.domains).toEqual([
			{
				domainId: "domain-quality",
				confirmationStatus: "CONFIRMED",
				resolutionStatus: "AVAILABLE",
				name: "质量管理域",
				code: "QUALITY",
			},
		]);
		expect(mocks.listBusinessProcessesApi).toHaveBeenCalledWith("domain-quality");
	});
});
