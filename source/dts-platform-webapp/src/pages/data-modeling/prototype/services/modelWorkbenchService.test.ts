// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from "vitest";
import { confirmDimensionDefinition, createDimensionDefinition } from "@/api/dimensionDefinitionApi";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ConceptDimensionDraft, ModelDraft } from "./modelWorkbenchService";
import {
	confirmDimensionDefinitionDraft,
	prepareModelDraftForSave,
	saveDimensionDefinitionDraft,
	validateConceptDimensionDraftInput,
	validateModelDraftInput,
} from "./modelWorkbenchService";

vi.mock("@/api/dimensionDefinitionApi", () => ({
	confirmDimensionDefinition: vi.fn(),
	createDimensionDefinition: vi.fn(),
	listDimensionDefinitions: vi.fn(),
}));

const conceptDraft = (): ConceptDimensionDraft => ({
	createKind: "dimension",
	base: null,
	definitionBase: null,
	idempotencyKey: "concept-draft-1",
	domainId: "finance",
	name: "预算科目",
	description: "统一预算科目定义",
	reuseScope: "DOMAIN",
});

const definitionView: DimensionDefinitionView = {
	id: "dimension-1",
	systemCode: "dim_generated_001",
	domainId: "finance",
	name: "预算科目",
	definition: "统一预算科目定义",
	ownerId: "owner-1",
	reuseScope: "DOMAIN",
	hierarchies: [],
	scopeType: "DOMAIN",
	dataMartId: null,
	attributes: [],
	status: "DRAFT",
	revision: 1,
	checksum: "checksum-1",
	usageCount: 0,
	createdAt: "2026-08-04T10:00:00Z",
	updatedAt: "2026-08-04T10:00:00Z",
};

const validDimensionDraft = (): ModelDraft => ({
	createKind: "dimension-table",
	base: null,
	planId: "",
	domainId: "domain-1",
	name: "预算科目维度表",
	description: "",
	physicalName: "dim_budget_account",
	materialization: "table",
	grainStatement: "",
	fields: [
		{
			name: "account_code",
			displayName: "科目编码",
			dataType: "STRING",
			nullable: false,
			role: "KEY",
			dimensionAttributeCode: "ACCOUNT_CODE",
		},
	],
	partitionFields: "",
	loadStrategy: "FULL",
	scdType: "TYPE1",
	reuseScope: "DOMAIN",
	dimensionDefinitionId: "dimension-1",
	standardBindings: [],
});

beforeEach(() => {
	vi.clearAllMocks();
});

describe("concept dimension draft", () => {
	it("creates only a DimensionDefinition and never sends a system code", async () => {
		vi.mocked(createDimensionDefinition).mockResolvedValue(definitionView);

		const saved = await saveDimensionDefinitionDraft(conceptDraft(), "owner-1");

		expect(createDimensionDefinition).toHaveBeenCalledWith({
			domainId: "finance",
			name: "预算科目",
			definition: "统一预算科目定义",
			ownerId: "owner-1",
			reuseScope: "DOMAIN",
			scopeType: "DOMAIN",
			dataMartId: null,
			attributes: [],
			hierarchies: [],
			idempotencyKey: expect.any(String),
		});
		expect(vi.mocked(createDimensionDefinition).mock.calls[0]?.[0]).not.toHaveProperty("systemCode");
		expect(saved.systemCode).toBe("dim_generated_001");
	});

	it("keeps one idempotency key when the same draft is retried", async () => {
		vi.mocked(createDimensionDefinition).mockResolvedValue(definitionView);
		const draft = conceptDraft();

		await saveDimensionDefinitionDraft(draft, "owner-1");
		await saveDimensionDefinitionDraft(draft, "owner-1");

		const firstKey = vi.mocked(createDimensionDefinition).mock.calls[0]?.[0].idempotencyKey;
		const retryKey = vi.mocked(createDimensionDefinition).mock.calls[1]?.[0].idempotencyKey;
		expect(firstKey).toBeTruthy();
		expect(retryKey).toBe(firstKey);
	});

	it("confirms a saved draft so dimension tables can bind the current revision", async () => {
		const current = { ...definitionView, status: "CURRENT" as const, revision: 2 };
		vi.mocked(confirmDimensionDefinition).mockResolvedValue(current);

		const confirmed = await confirmDimensionDefinitionDraft({
			...conceptDraft(),
			definitionBase: definitionView,
		});

		expect(confirmDimensionDefinition).toHaveBeenCalledWith(definitionView);
		expect(confirmed.definitionBase).toEqual(current);
	});

	it("allows a concept dimension with no model fields", () => {
		expect(validateConceptDimensionDraftInput(conceptDraft())).toEqual({});
	});

	it("requires a domain and Chinese name without asking for table fields", () => {
		expect(
			validateConceptDimensionDraftInput({
				...conceptDraft(),
				domainId: "",
				name: "budget_account",
			}),
		).toEqual({
			domainId: "请选择数据域",
			name: "请填写中文名称",
		});
	});
});

describe("model workbench draft preparation", () => {
	it("derives an empty dimension-table grain from the selected definition", () => {
		expect(
			prepareModelDraftForSave(validDimensionDraft(), [
				{ id: "dimension-1", name: "预算科目" } as DimensionDefinitionView,
			]),
		).toMatchObject({ grainStatement: "一个预算科目一行" });
	});

	it("falls back to the typed name when a selected dimension is no longer available", () => {
		expect(prepareModelDraftForSave(validDimensionDraft(), [])).toMatchObject({
			grainStatement: "一个预算科目维度表一行",
		});
	});

	it("preserves an explicitly entered dimension-table grain", () => {
		const existing = validDimensionDraft();
		existing.grainStatement = "一个科目版本一行";

		expect(prepareModelDraftForSave(existing, [])).toMatchObject({ grainStatement: "一个科目版本一行" });
	});
});

describe("model workbench draft validation", () => {
	it("reports invalid physical names and duplicate trimmed field names", () => {
		const invalid = validDimensionDraft();
		invalid.physicalName = "Bad-Name";
		invalid.fields.push({ ...invalid.fields[0], name: " account_code ", displayName: "重复字段" });

		expect(validateModelDraftInput(invalid)).toMatchObject({
			physicalName: "表名只能使用小写字母、数字和下划线，且必须以字母开头",
			fields: "字段名称不能重复",
		});
	});

	it("enforces prototype-specific dimension draft requirements", () => {
		const invalid = validDimensionDraft();
		invalid.domainId = "";
		invalid.dimensionDefinitionId = "";
		invalid.name = "dimension_table";
		invalid.fields = [{ ...invalid.fields[0], name: "", dataType: "", dimensionAttributeCode: "invalid-code" }];

		expect(validateModelDraftInput(invalid)).toMatchObject({
			domainId: "请选择数据域",
			dimensionDefinitionId: "请选择一个维度",
			name: "请填写中文表名",
			fields: "请补齐字段名称和数据类型",
		});
	});

	it("requires grain for non-dimension drafts", () => {
		const draft = validDimensionDraft();
		draft.createKind = "fact";
		draft.grainStatement = "";

		expect(validateModelDraftInput(draft)).toMatchObject({ grainStatement: "请填写模型粒度" });
	});

	it("rejects an invalid dimension attribute code on an otherwise complete field", () => {
		const invalid = validDimensionDraft();
		invalid.fields[0].dimensionAttributeCode = "invalid-code";

		expect(validateModelDraftInput(invalid)).toMatchObject({
			fields: "维度属性编码只能使用大写字母、数字和下划线，且必须以字母开头",
		});
	});
});
