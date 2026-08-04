// @vitest-environment jsdom

import type { CatalogDomain } from "@/api/services/catalogDomainService";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import { describe, expect, it } from "vitest";
import type { ModelDraft } from "./services/modelWorkbenchService";
import {
	DIMENSION_STORAGE_OPTIONS,
	isDimensionDraft,
	modelDraftFingerprint,
	resolveDimensionFormPresentation,
} from "./modelWorkbenchPresentation";

const makeDraft = (patch: Partial<ModelDraft> = {}): ModelDraft => ({
	createKind: "dimension-table",
	base: null,
	planId: "",
	domainId: "finance",
	name: "预算科目维度表",
	description: "统一维护预算科目",
	physicalName: "dim_budget_account",
	materialization: "table",
	grainStatement: "",
	fields: [],
	partitionFields: "",
	loadStrategy: "FULL",
	scdType: "TYPE1",
	reuseScope: "DOMAIN",
	dimensionDefinitionId: "dimension-1",
	standardBindings: [],
	...patch,
});

describe("dimension workbench presentation", () => {
	it("derives dimension form labels from the selected domain and definition", () => {
		const view = resolveDimensionFormPresentation({
			draft: makeDraft({ physicalName: "dim_budget_account" }),
			domains: [
				{ code: "business", name: "财务业务" },
				{ code: "finance", name: "财务域", parentCode: "business" },
			] satisfies CatalogDomain[],
			definition: { id: "dimension-1", name: "预算科目", ownerId: "owner-1" } as DimensionDefinitionView,
			currentOwnerId: "current-user",
		});

		expect(view).toMatchObject({
			warehouseLayer: "公共层 / 维度层",
			businessCategory: "财务业务",
			tableNamingRule: "DIM 表命名规范",
			lifecycle: "未配置",
			owner: "owner-1",
		});
	});

	it("marks unavailable authority and invalid physical names as unconfigured", () => {
		const missing = resolveDimensionFormPresentation({
			draft: makeDraft({ physicalName: "Bad-Name" }),
			domains: [{ code: "finance", name: "财务域" }],
			definition: null,
			currentOwnerId: "",
		});

		expect(missing.businessCategory).toBe("未配置");
		expect(missing.owner).toBe("未配置");
		expect(missing.tableNamingRule).toBe("不符合 DIM 表命名规范");
	});

	it("uses the current owner for a new dimension draft", () => {
		const view = resolveDimensionFormPresentation({
			draft: makeDraft({
				createKind: "dimension",
				dimensionDefinitionId: "",
			}),
			domains: [],
			definition: null,
			currentOwnerId: "current-user",
		});

		expect(view.owner).toBe("current-user");
	});

	it("renders configured retention days from the saved implementation policy", () => {
		const view = resolveDimensionFormPresentation({
			draft: makeDraft({
				base: {
					implementationPolicy: { retentionDays: 30 },
				} as ModelDraft["base"],
			}),
			domains: [],
			definition: null,
			currentOwnerId: "",
		});

		expect(view.lifecycle).toBe("30 天");
	});

	it("identifies dimension drafts and fingerprints editable values only", () => {
		expect(isDimensionDraft(makeDraft())).toBe(true);
		expect(isDimensionDraft(makeDraft({ createKind: "fact" }))).toBe(false);
		expect(DIMENSION_STORAGE_OPTIONS).toEqual([
		{ value: "table", label: "表存储" },
		{ value: "incremental", label: "增量表" },
		{ value: "view", label: "视图" },
		{ value: "ephemeral", label: "临时模型" },
	]);
		expect(modelDraftFingerprint(makeDraft({ name: "A" }))).not.toBe(modelDraftFingerprint(makeDraft({ name: "B" })));
		expect(modelDraftFingerprint(makeDraft({ planId: "plan-a" }))).toBe(
			modelDraftFingerprint(makeDraft({ planId: "plan-b" })),
		);
	});
});
