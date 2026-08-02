import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
	listPlans: vi.fn(),
	listModels: vi.fn(),
	listStandards: vi.fn(),
	listIndicators: vi.fn(),
	listDomains: vi.fn(),
	listProcesses: vi.fn(),
	listLayers: vi.fn(),
	listMarts: vi.fn(),
	getPolicy: vi.fn(),
	getStage: vi.fn(),
}));

vi.mock("@/api/dataMartApi", () => ({ listDataMarts: mocks.listMarts }));
vi.mock("@/api/modelSpecApi", () => ({ listModelSpecs: mocks.listModels }));
vi.mock("@/api/services/catalogDomainService", () => ({ default: { list: mocks.listDomains } }));
vi.mock("@/api/services/modelingOverviewFactService", () => ({
	listStandardsForModelingOverview: mocks.listStandards,
	listIndicatorsForModelingOverview: mocks.listIndicators,
}));
vi.mock("@/api/sprint64GovernanceApi", () => ({
	listBusinessProcessesApi: mocks.listProcesses,
	listWarehouseLayersApi: mocks.listLayers,
}));
vi.mock("@/api/warehousePlanApi", () => ({
	listWarehousePlans: mocks.listPlans,
	getWarehousePlanPolicy: mocks.getPolicy,
	getWarehousePlanStageProjection: mocks.getStage,
}));
vi.mock("@/features/modeling/navigation/warehousePlanViewModel", () => ({
	stageStatusLabel: (status: string) => status,
	warehousePlanLifecycleLabel: (status: string) => status,
	warehouseStageLabel: (code: string) => code,
}));

import { classifyModelingLoadFailure, loadModelingHomeProjection, loadPlanningCatalog } from "./planningHomeAdapter";

const plan = {
	id: "plan-1",
	tenantId: "tenant-1",
	code: "PLAN_1",
	name: "统一建设计划",
	ownerId: "owner-1",
	onboardingMode: "BUSINESS_FIRST" as const,
	lifecycleStatus: "DESIGNING" as const,
	version: 2,
};

beforeEach(() => {
	vi.clearAllMocks();
	mocks.listPlans.mockResolvedValue([plan]);
	mocks.listModels.mockResolvedValue([]);
	mocks.listStandards.mockResolvedValue({ content: [] });
	mocks.listIndicators.mockResolvedValue({ data: { content: [] } });
	mocks.listDomains.mockResolvedValue([]);
	mocks.listProcesses.mockResolvedValue([]);
	mocks.listLayers.mockResolvedValue([]);
	mocks.listMarts.mockResolvedValue([]);
	mocks.getStage.mockResolvedValue({
		planId: plan.id,
		currentStage: "WAREHOUSE_PLANNING",
		primaryBlocker: null,
		nextAction: null,
		stages: [],
		computedAt: "2026-08-02T00:00:00Z",
	});
});

describe("planningHomeAdapter", () => {
	it("projects only canonical facts into overview counts and recent models", async () => {
		mocks.listModels.mockResolvedValue([
			{
				id: "model-1",
				name: "订单明细",
				modelType: "FACT",
				domainId: "sales",
				revision: 3,
				status: "PUBLISHED",
				updatedAt: "2026-08-02T08:00:00Z",
			},
		]);
		mocks.listStandards.mockResolvedValue({ content: [{ id: "s1" }, { id: "s2" }] });
		mocks.listIndicators.mockResolvedValue({ data: { content: [{ id: "i1" }] } });

		const projection = await loadModelingHomeProjection();

		expect(projection.stats.map((item) => item.value)).toEqual([1, 1, 2, 1]);
		expect(projection.recentModels).toEqual([
			expect.objectContaining({ id: "model-1", name: "订单明细", version: "r3" }),
		]);
		expect(JSON.stringify(projection)).not.toContain("示例");
	});

	it("returns an honest empty catalog when an object has no canonical owner", async () => {
		const catalog = await loadPlanningCatalog("subjects", { plans: [plan], planId: plan.id });

		expect(catalog.rows).toEqual([]);
		expect(catalog.columns).toEqual([]);
		expect(catalog.readOnlyReason).toContain("尚无统一权威台账");
	});

	it("distinguishes permission denial from retryable load errors", () => {
		expect(classifyModelingLoadFailure({ response: { status: 403 } }, "fallback")).toEqual({
			kind: "permission",
			message: "当前账号无权访问该建模数据，请联系管理员授权。",
		});
		expect(classifyModelingLoadFailure(new Error("offline"), "请重试")).toEqual({
			kind: "error",
			message: "请重试",
		});
	});
});
