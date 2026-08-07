// @vitest-environment jsdom

import { describe, expect, it } from "vitest";
import type { CatalogDomain } from "@/api/services/catalogDomainService";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import {
	buildWorkbenchCatalogGroups,
	workbenchCatalogEmptyMessage,
} from "./modelWorkbenchPresentation";

const financeCategory: CatalogDomain = {
	id: "category-finance",
	code: "Finance",
	name: "财务业务",
	owner: "",
	description: "",
	parentId: null,
	parentCode: null,
};

const financeDomain: CatalogDomain = {
	id: "domain-finance",
	code: "FinanceDomain",
	name: "财务域",
	owner: "",
	description: "",
	parentId: financeCategory.id,
	parentCode: financeCategory.code,
};

const model = (patch: Partial<ModelSpecView> = {}): ModelSpecView =>
	({
		id: "model-1",
		name: "E2E 明细表",
		domainId: financeDomain.id,
		layer: "DWD",
		modelType: "FACT",
		...patch,
	}) as ModelSpecView;

const dimension = (patch: Partial<DimensionDefinitionView> = {}): DimensionDefinitionView =>
	({
		id: "dimension-1",
		systemCode: "DIM000001",
		domainId: financeDomain.id,
		name: "成本中心",
		status: "DRAFT",
		...patch,
	}) as DimensionDefinitionView;

const domainByCode = new Map<string, CatalogDomain>([
	[financeCategory.code, financeCategory],
	[financeDomain.code, financeDomain],
]);

describe("workbench catalog grouping (DataWorks 视角)", () => {
	it("贴源/应用层固定业务分类视角：财务域上的模型归财务业务，不出现财务域节点", () => {
		const groups = buildWorkbenchCatalogGroups({
			dataDomains: [financeDomain],
			categoryRoots: [financeCategory],
			domainByCode,
			effectiveView: "category",
			visibleModels: [model()],
			visibleDimensions: [],
		});

		expect(groups).toHaveLength(1);
		expect(groups[0]).toMatchObject({ key: financeCategory.id, label: "财务业务", kind: "category" });
		expect(groups[0].models).toHaveLength(1);
		expect(groups.map((group) => group.label)).not.toContain("财务域");
	});

	it("公共层数据域视角：模型归财务域节点", () => {
		const groups = buildWorkbenchCatalogGroups({
			dataDomains: [financeDomain],
			categoryRoots: [financeCategory],
			domainByCode,
			effectiveView: "domain",
			visibleModels: [model()],
			visibleDimensions: [],
		});

		expect(groups.map((group) => group.label)).toEqual(["财务域"]);
	});

	it("没有模型时即使存在数据域也不渲染分组（空分组过滤）", () => {
		const groups = buildWorkbenchCatalogGroups({
			dataDomains: [financeDomain],
			categoryRoots: [financeCategory],
			domainByCode,
			effectiveView: "domain",
			visibleModels: [],
			visibleDimensions: [],
		});

		expect(groups).toEqual([]);
	});

	it("模型直接挂根业务分类时两个视角都归根分类", () => {
		const rooted = model({ id: "model-root", domainId: financeCategory.id });
		for (const effectiveView of ["domain", "category"] as const) {
			const groups = buildWorkbenchCatalogGroups({
				dataDomains: [financeDomain],
				categoryRoots: [financeCategory],
				domainByCode,
				effectiveView,
				visibleModels: [rooted],
				visibleDimensions: [],
			});
			expect(groups).toHaveLength(1);
			expect(groups[0]).toMatchObject({ label: "财务业务", kind: "category" });
		}
	});

	it("概念维度与模型同域归组：数据域视角归财务域，业务分类视角归财务业务", () => {
		for (const effectiveView of ["domain", "category"] as const) {
			const groups = buildWorkbenchCatalogGroups({
				dataDomains: [financeDomain],
				categoryRoots: [financeCategory],
				domainByCode,
				effectiveView,
				visibleModels: [],
				visibleDimensions: [dimension()],
			});
			expect(groups).toHaveLength(1);
			expect(groups[0].dimensions).toHaveLength(1);
			expect(groups[0].dimensions[0].name).toBe("成本中心");
		}
		const domainGroups = buildWorkbenchCatalogGroups({
			dataDomains: [financeDomain],
			categoryRoots: [financeCategory],
			domainByCode,
			effectiveView: "domain",
			visibleModels: [model()],
			visibleDimensions: [dimension()],
		});
		expect(domainGroups[0].models).toHaveLength(1);
		expect(domainGroups[0].dimensions).toHaveLength(1);
		expect(domainGroups[0].label).toBe("财务域");
	});

	it("只有维度没有模型时分组仍然渲染", () => {
		const groups = buildWorkbenchCatalogGroups({
			dataDomains: [financeDomain],
			categoryRoots: [financeCategory],
			domainByCode,
			effectiveView: "domain",
			visibleModels: [],
			visibleDimensions: [dimension({ domainId: financeCategory.id })],
		});
		expect(groups).toHaveLength(1);
		expect(groups[0].dimensions).toHaveLength(1);
	});

	it("空态文案按视角与分层区分", () => {
		expect(
			workbenchCatalogEmptyMessage({
				effectiveView: "domain",
				dataDomains: [],
				categoryRoots: [financeCategory],
				hasAnyModels: false,
				hasLayerModels: false,
				layer: "公共层",
			}),
		).toBe("当前没有数据域，请先在数仓规划中创建数据域。");

		expect(
			workbenchCatalogEmptyMessage({
				effectiveView: "category",
				dataDomains: [financeDomain],
				categoryRoots: [],
				hasAnyModels: false,
				hasLayerModels: false,
				layer: "贴源层",
			}),
		).toBe("当前没有业务分类，请先在数仓规划中创建业务分类。");

		expect(
			workbenchCatalogEmptyMessage({
				effectiveView: "category",
				dataDomains: [financeDomain],
				categoryRoots: [financeCategory],
				hasAnyModels: false,
				hasLayerModels: false,
				layer: "贴源层",
			}),
		).toBe("当前尚无模型。");

		expect(
			workbenchCatalogEmptyMessage({
				effectiveView: "category",
				dataDomains: [financeDomain],
				categoryRoots: [financeCategory],
				hasAnyModels: true,
				hasLayerModels: false,
				layer: "贴源层",
			}),
		).toBe("当前“贴源层”暂无模型；切换分层查看其他模型，或在右侧新建模型。");

		expect(
			workbenchCatalogEmptyMessage({
				effectiveView: "category",
				dataDomains: [financeDomain],
				categoryRoots: [financeCategory],
				hasAnyModels: true,
				hasLayerModels: true,
				layer: "贴源层",
			}),
		).toBe("没有符合搜索条件的模型。");
	});
});
