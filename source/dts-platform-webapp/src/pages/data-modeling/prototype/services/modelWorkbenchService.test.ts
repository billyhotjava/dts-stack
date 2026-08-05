// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from "vitest";
import { confirmDimensionDefinition, createDimensionDefinition } from "@/api/dimensionDefinitionApi";
import { listModelFieldStandardOptions } from "@/api/modelingStandardsApi";
import { listModelSpecs, updateModelSpec } from "@/api/modelSpecApi";
import catalogDomainService from "@/api/services/catalogDomainService";
import { listWarehouseLayers, type WarehouseLayerView } from "@/api/warehouseLayerApi";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { CanonicalModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import type { ConceptDimensionDraft, ModelDraft, ModelSpecDraft } from "./modelWorkbenchService";
import {
	confirmDimensionDefinitionDraft,
	emptyModelDraft,
	loadModelWorkbenchContext,
	modelDraftFromView,
	prepareModelDraftForSave,
	saveDimensionDefinitionDraft,
	saveModelDraft,
	validateConceptDimensionDraftInput,
	validateModelDraftInput,
} from "./modelWorkbenchService";

vi.mock("@/api/dimensionDefinitionApi", () => ({
	confirmDimensionDefinition: vi.fn(),
	createDimensionDefinition: vi.fn(),
	listDimensionDefinitions: vi.fn(),
}));
vi.mock("@/api/modelingStandardsApi", () => ({ listModelFieldStandardOptions: vi.fn() }));
vi.mock("@/api/modelSpecApi", () => ({
	listModelSpecs: vi.fn(),
	updateModelSpec: vi.fn(),
	createModelSpec: vi.fn(),
}));
vi.mock("@/api/services/catalogDomainService", () => ({ default: { list: vi.fn() } }));
vi.mock("@/api/warehouseLayerApi", () => ({ listWarehouseLayers: vi.fn() }));

const customLayer: WarehouseLayerView = {
	code: "FIN_DETAIL",
	name: "财务明细层",
	systemLayerCode: "DWD",
	kind: "DETAIL",
	responsibility: "财务域明细",
	namingPrefixes: ["fin_dwd_"],
	optional: false,
	builtin: false,
	deletable: true,
	disabledReason: null,
};

const canonicalFactView = (): CanonicalModelSpecView => ({
	contractVersion: 2,
	id: "30000000-0000-0000-0000-000000000001",
	planId: "10000000-0000-0000-0000-000000000001",
	domainId: "20000000-0000-0000-0000-000000000001",
	modelType: "FACT",
	layer: "DWD",
	warehouseLayerCode: "DWD",
	name: "budget_execution_detail",
	description: null,
	implementationMode: "DESIGNER_GENERATED",
	materialization: "table",
	businessActivityRef: null,
	consumptionScenario: null,
	grain: { statement: "one row per record", keys: ["record_id"] },
	factShape: "TRANSACTION",
	timeSemantics: { type: "EVENT_TIME", fields: ["event_time"] },
	generationStrategy: null,
	dimensionProfile: null,
	dimensionDefinitionRef: null,
	status: "DRAFT",
	revision: 1,
	checksum: "a".repeat(64),
	createdAt: "2026-08-04T10:00:00Z",
	updatedAt: "2026-08-04T10:00:00Z",
	compatibilityMode: "CANONICAL",
	legacyRefs: null,
	dataMartId: null,
	variantCode: null,
	implementationPolicy: null,
	fields: [
		{ name: "record_id", dataType: "varchar", nullable: false, role: "KEY", securityLevel: "INTERNAL" },
		{ name: "event_time", dataType: "timestamp", nullable: false, role: "TIME", securityLevel: "INTERNAL" },
	],
	sourceRefs: [],
	dependsOn: [],
	dimensionRefs: [],
	metricRefs: [],
	standardBindings: [],
});

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

	it("defaults new model drafts to the canonical target layer", () => {
		const context = { domains: [], models: [], standards: [], warehouseLayers: [] } as never;
		expect(emptyModelDraft("fact", context)).toMatchObject({ warehouseLayerCode: "DWD" });
		expect(emptyModelDraft("summary", context)).toMatchObject({ warehouseLayerCode: "DWS" });
		expect(emptyModelDraft("application", context)).toMatchObject({ warehouseLayerCode: "ADS" });
	});

	it("uses the canonical domain id for new draft domain binding", () => {
		const context = {
			domains: [{ id: "1d9a3e90-7b38-485a-8e6d-c428dc17a61e", code: "FinanceDomain", name: "财务域" }],
			models: [],
			standards: [],
			warehouseLayers: [],
		} as never;
		expect(emptyModelDraft("dimension", context)).toMatchObject({
			domainId: "1d9a3e90-7b38-485a-8e6d-c428dc17a61e",
		});
		expect(emptyModelDraft("fact", context)).toMatchObject({
			domainId: "1d9a3e90-7b38-485a-8e6d-c428dc17a61e",
		});
	});

	it("loads the governed warehouse layers into the workbench context", async () => {
		vi.mocked(listWarehouseLayers).mockResolvedValue([customLayer]);
		vi.mocked(catalogDomainService.list).mockResolvedValue([]);
		vi.mocked(listModelSpecs).mockResolvedValue([]);
		vi.mocked(listModelFieldStandardOptions).mockResolvedValue([]);

		const context = await loadModelWorkbenchContext();

		expect(context.warehouseLayers).toEqual([customLayer]);
		expect(listWarehouseLayers).toHaveBeenCalledTimes(1);
	});

	it("carries the custom selection into the update command", async () => {
		const base = canonicalFactView();
		vi.mocked(updateModelSpec).mockResolvedValue(base);
		const draft = modelDraftFromView(base) as ModelSpecDraft;
		draft.warehouseLayerCode = "FIN_DETAIL";

		await saveModelDraft(draft, { ownerId: "owner-1", dimensionDefinitions: [] });

		expect(updateModelSpec).toHaveBeenCalledWith(
			base,
			expect.objectContaining({ layer: "DWD", warehouseLayerCode: "FIN_DETAIL" }),
		);
	});
});
