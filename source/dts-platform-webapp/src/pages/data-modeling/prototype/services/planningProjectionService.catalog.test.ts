// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
	listDataMarts: vi.fn(),
	listBusinessProcessesApi: vi.fn(),
	listPlanningCatalogDomains: vi.fn(),
	listSubjectDomains: vi.fn(),
	listWarehouseLayers: vi.fn(),
}));

vi.mock("@/api/dataMartApi", () => ({ listDataMarts: mocks.listDataMarts }));
vi.mock("@/api/modelSpecApi", () => ({ listModelSpecs: vi.fn() }));
vi.mock("@/api/services/modelingOverviewFactService", () => ({
	listIndicatorsForModelingOverview: vi.fn(),
	listStandardsForModelingOverview: vi.fn(),
}));
vi.mock("@/api/sprint64GovernanceApi", () => ({
	listBusinessProcessesApi: mocks.listBusinessProcessesApi,
}));
vi.mock("@/api/subjectDomainApi", () => ({
	listSubjectDomains: mocks.listSubjectDomains,
}));
vi.mock("@/api/warehouseLayerApi", () => ({
	listWarehouseLayers: mocks.listWarehouseLayers,
}));
vi.mock("./planningCatalogDomainService", () => ({
	listPlanningCatalogDomains: mocks.listPlanningCatalogDomains,
}));

import type { WarehouseLayerView } from "@/api/warehouseLayerApi";
import { loadPlanningProjection } from "./planningProjectionService";

const builtinDwd: WarehouseLayerView = {
	code: "DWD",
	name: "明细事实 / 维度层",
	systemLayerCode: "DWD",
	layerGroup: "COMMON",
	modelTypes: ["DIMENSION", "FACT"],
	kind: "DETAIL",
	responsibility: "业务明细",
	namingPrefixes: ["dwd_"],
	optional: false,
	builtin: true,
	deletable: false,
	disabledReason: "平台内置分层不可删除",
};

const customFinDetail: WarehouseLayerView = {
	code: "FIN_DETAIL",
	name: "财务明细层",
	systemLayerCode: "DWD",
	layerGroup: "COMMON",
	modelTypes: ["DIMENSION", "FACT"],
	kind: "DETAIL",
	responsibility: "财务域明细",
	namingPrefixes: ["fin_dwd_"],
	optional: false,
	builtin: false,
	deletable: true,
	disabledReason: null,
};

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
		mocks.listPlanningCatalogDomains.mockResolvedValue([]);

		await loadPlanningProjection("marts");

		expect(mocks.listDataMarts).toHaveBeenCalledWith({ limit: 100 });
	});

	it("projects subject domains with their data-mart names", async () => {
		mocks.listSubjectDomains.mockResolvedValue([
			{
				id: "subject-1",
				code: "BUDGET_COCKPIT",
				name: "预算驾驶舱",
				purpose: "预算分析主题",
				martId: "mart-1",
				status: "CURRENT",
				revision: 1,
				checksum: "c",
				createdAt: "2026-08-06T12:00:00Z",
				updatedAt: "2026-08-06T12:00:00Z",
			},
		]);
		mocks.listDataMarts.mockResolvedValue([
			{
				id: "mart-1",
				code: "FINANCE_MART",
				name: "财务分析集市",
				purpose: "财务分析",
				ownerId: "owner-1",
				businessCategoryIds: ["category-1"],
				status: "CURRENT",
				revision: 1,
				checksum: "c",
				usageCount: 0,
				createdAt: "2026-08-06T12:00:00Z",
				updatedAt: "2026-08-06T12:00:00Z",
			},
		]);

		const projection = await loadPlanningProjection("subjects");

		expect(projection.headers).toEqual(["主题域编码", "主题域名称", "数据集市", "用途说明", "状态"]);
		expect(projection.rows[0]?.cells).toEqual([
			"BUDGET_COCKPIT",
			"预算驾驶舱",
			"财务分析集市",
			"预算分析主题",
			"CURRENT",
		]);
	});

	it("projects built-in and custom layers as maintainable catalog rows", async () => {
		mocks.listWarehouseLayers.mockResolvedValue([builtinDwd, customFinDetail]);

		const projection = await loadPlanningProjection("layers");

		expect(projection.readOnlyReason).toBeNull();
		expect(projection.headers).toEqual([
			"分层编码",
			"分层名称",
			"分层归属",
			"所属系统类型",
			"模型类型",
			"加工责任",
			"命名前缀",
			"来源",
		]);
		expect(projection.rows.map((row) => row.id)).toEqual(["DWD", "FIN_DETAIL"]);
		expect(projection.rows.map((row) => row.cells[2])).toEqual(["公共层", "公共层"]);
		expect(projection.rows.map((row) => row.cells[4])).toEqual(["维度、明细表", "维度、明细表"]);
		expect(projection.rows.map((row) => row.cells[7])).toEqual(["系统", "自定义"]);
		expect(projection.rows[1]?.source).toEqual(customFinDetail);
	});

	it("states the current planning-parameter boundary in customer-facing language", async () => {
		const projection = await loadPlanningProjection("system");

		expect(projection.readOnlyReason).toBe("当前版本尚未提供可维护的建模策略；配置能力接入前，本页仅说明功能边界。");
		expect(projection.readOnlyReason).not.toMatch(/owner|旧流程|配置接口/i);
	});
});
