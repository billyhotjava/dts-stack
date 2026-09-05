import { describe, expect, it, vi } from "vitest";
import {
	applySuggestedStandardMappings,
	archiveStandardsRow,
	loadStandardMappingOptions,
	loadStandardsRows,
	previewSuggestedStandardMappings,
	type StandardsRow,
	saveStandardMapping,
	saveStandardsRow,
	standardsCapability,
} from "./standardsProjectionService";

const apiMocks = vi.hoisted(() => ({
	archiveStandard: vi.fn(),
	createWordRoot: vi.fn(),
	listWordRoots: vi.fn(),
	listMetadataStandards: vi.fn(),
	listModelSpecs: vi.fn(),
	updateReferenceCode: vi.fn(),
	updateModelSpec: vi.fn(),
	applyModelSpecStandardElementBindings: vi.fn(),
	updateWordRoot: vi.fn(),
}));

vi.mock("@/api/modelingStandardsApi", () => ({
	archiveStandard: apiMocks.archiveStandard,
	createGlossaryTerm: vi.fn(),
	createReferenceCode: vi.fn(),
	createStandard: vi.fn(),
	createWordRoot: apiMocks.createWordRoot,
	listGlossaryTerms: vi.fn(),
	listMetadataStandards: apiMocks.listMetadataStandards,
	listReferenceCodes: vi.fn(),
	listStandards: vi.fn(),
	listWordRoots: apiMocks.listWordRoots,
	updateGlossaryTerm: vi.fn(),
	updateReferenceCode: apiMocks.updateReferenceCode,
	updateStandard: vi.fn(),
	updateWordRoot: apiMocks.updateWordRoot,
}));

vi.mock("@/api/modelSpecApi", () => ({
	applyModelSpecStandardElementBindings: apiMocks.applyModelSpecStandardElementBindings,
	listModelSpecs: apiMocks.listModelSpecs,
	updateModelSpec: apiMocks.updateModelSpec,
}));

const glossaryRow: StandardsRow = {
	id: "term-1",
	code: "BUDGET",
	name: "预算",
	dataType: "—",
	definition: "预算术语",
	domain: "FIN",
	scope: "—",
	version: "v1",
	state: "草稿",
	valueCount: "—",
	source: {},
};

describe("standards archive safety", () => {
	it("saves a selected published status without losing code ownership", async () => {
		await saveStandardsRow(
			"codes",
			{
				code: "TASK_STATUS",
				name: "任务状态",
				dataType: "STRING",
				domain: "PROJECT",
				definition: "",
				scope: "",
				version: "v1",
				status: "1",
			},
			{ ...glossaryRow, id: "code-1", source: { status: 0, ownerDept: "DEPT_A" } },
		);
		expect(apiMocks.updateReferenceCode).toHaveBeenCalledWith(
			"code-1",
			expect.objectContaining({ status: 1, ownerDept: "DEPT_A" }),
		);
		apiMocks.updateReferenceCode.mockClear();
	});

	it("does not advertise archive when the glossary owner only exposes permanent delete", () => {
		const capability = standardsCapability("dictionary");
		expect(capability.archive).toBe(false);
		expect(capability.archiveDisabledReason).toContain("永久删除");
	});

	it("fails closed instead of deleting a glossary term", async () => {
		await expect(archiveStandardsRow("dictionary", glossaryRow)).rejects.toThrow("禁止执行永久删除");
		expect(apiMocks.archiveStandard).not.toHaveBeenCalled();
		expect(apiMocks.updateReferenceCode).not.toHaveBeenCalled();
	});
});

describe("word root owner", () => {
	it("advertises the independent word-root read and write contract", () => {
		const capability = standardsCapability("roots");
		expect(capability).toMatchObject({ list: true, create: true, edit: true, archive: false });
		expect(capability.disabledReason).toBeUndefined();
	});

	it("loads word roots without projecting glossary terms", async () => {
		apiMocks.listWordRoots.mockResolvedValue([
			{
				id: "root-1",
				code: "AMOUNT",
				nameCn: "金额",
				nameEn: "Amount",
				abbreviation: "AMT",
				domain: "财务",
				version: "v1",
				status: "ACTIVE",
			},
		]);

		await expect(loadStandardsRows("roots", "金额")).resolves.toMatchObject([
			{ code: "AMOUNT", name: "金额", definition: "Amount", scope: "AMT", state: "已生效" },
		]);
		expect(apiMocks.listWordRoots).toHaveBeenCalledWith({ keyword: "金额" });
	});

	it("creates a word root through the dedicated endpoint", async () => {
		await saveStandardsRow("roots", {
			code: "amount",
			name: "金额",
			dataType: "",
			definition: "Amount",
			domain: "财务",
			scope: "amt",
			version: "v1",
		});

		expect(apiMocks.createWordRoot).toHaveBeenCalledWith({
			code: "amount",
			nameCn: "金额",
			nameEn: "Amount",
			abbreviation: "amt",
			domain: "财务",
			version: "v1",
		});
	});
});

const model = {
	id: "10000000-0000-0000-0000-000000000001",
	contractVersion: 2,
	compatibilityMode: "CANONICAL",
	planId: "20000000-0000-0000-0000-000000000001",
	domainId: "30000000-0000-0000-0000-000000000001",
	modelType: "FACT",
	layer: "DWD",
	warehouseLayerCode: "DWD",
	name: "应付账款事实",
	description: null,
	implementationMode: "DBT_MANAGED",
	materialization: "table",
	businessActivityRef: null,
	businessProcessId: null,
	consumptionScenario: null,
	grain: { statement: "一笔应付账款", keys: ["payable_amount"] },
	factShape: "TRANSACTION",
	timeSemantics: null,
	generationStrategy: null,
	dimensionProfile: null,
	dataMartId: null,
	subjectDomainId: null,
	variantCode: null,
	implementationPolicy: null,
	fields: [
		{
			name: "payable_amount",
			displayName: "应付账款金额",
			dataType: "DECIMAL",
			nullable: false,
			role: "MEASURE",
		},
	],
	sourceRefs: [],
	dependsOn: [],
	dimensionRefs: [],
	metricRefs: [],
	standardBindings: [],
	status: "DRAFT",
	revision: 3,
	checksum: "model-checksum",
	createdAt: "2026-08-22T00:00:00Z",
	updatedAt: "2026-08-22T00:00:00Z",
};

const standard = {
	id: "40000000-0000-0000-0000-000000000001",
	fieldNameEn: "payable_amount",
	fieldNameCn: "应付账款金额",
	dataType: "DECIMAL",
	domain: "项目预算管理",
	version: 2,
};

describe("model-field standard mappings", () => {
	it("advertises mapping creation through the existing ModelSpec owner", () => {
		expect(standardsCapability("mappings")).toMatchObject({ list: true, create: true, edit: true, archive: false });
	});

	it("lists persisted ModelSpec standard bindings instead of metadata-standard rows", async () => {
		apiMocks.listModelSpecs.mockResolvedValue([
			{
				...model,
				standardBindings: [
					{
						fieldName: "payable_amount",
						standardElementId: standard.id,
						standardElementVersion: 2,
					},
				],
			},
		]);
		apiMocks.listMetadataStandards.mockResolvedValue({ content: [standard] });

		await expect(loadStandardsRows("mappings", "应付")).resolves.toMatchObject([
			{
				code: "payable_amount",
				name: "应付账款金额",
				definition: "应付账款金额（payable_amount）",
				domain: "应付账款事实",
				version: "v2",
			},
		]);
	});

	it("creates a revision-pinned binding by updating the selected canonical ModelSpec", async () => {
		apiMocks.listModelSpecs.mockResolvedValue([model]);
		apiMocks.listMetadataStandards.mockResolvedValue({ content: [standard] });

		await expect(loadStandardMappingOptions()).resolves.toMatchObject({
			models: [{ id: model.id, name: model.name, fields: [{ name: "payable_amount" }] }],
			standards: [{ id: standard.id, code: "payable_amount", version: 2 }],
		});
		await saveStandardMapping({ modelId: model.id, fieldName: "payable_amount", standardId: standard.id });

		expect(apiMocks.updateModelSpec).toHaveBeenCalledWith(
			expect.objectContaining({ id: model.id, revision: 3, checksum: "model-checksum" }),
			expect.objectContaining({
				name: model.name,
				fields: model.fields,
				standardBindings: [
					{
						fieldName: "payable_amount",
						standardElementId: standard.id,
						standardElementVersion: 2,
					},
				],
			}),
		);
	});

	it("previews unique type-compatible standard suggestions for published models", async () => {
		apiMocks.listModelSpecs.mockResolvedValue([
			{
				...model,
				status: "PUBLISHED",
				standardBindings: [{ fieldName: "payable_amount", securityLevel: "CONFIDENTIAL" }],
			},
		]);
		apiMocks.listMetadataStandards.mockResolvedValue({ content: [standard] });

		await expect(previewSuggestedStandardMappings()).resolves.toMatchObject([
			{
				modelId: model.id,
				modelName: model.name,
				modelStatus: "PUBLISHED",
				fieldName: "payable_amount",
				standardId: standard.id,
				standardVersion: 2,
				createsDraft: true,
			},
		]);
	});

	it("rejects ambiguous or type-incompatible name matches from the suggestion preview", async () => {
		apiMocks.listModelSpecs.mockResolvedValue([{ ...model, status: "PUBLISHED" }]);
		apiMocks.listMetadataStandards.mockResolvedValue({
			content: [standard, { ...standard, id: "50000000-0000-0000-0000-000000000001" }],
		});
		await expect(previewSuggestedStandardMappings()).resolves.toEqual([]);

		apiMocks.listMetadataStandards.mockResolvedValue({ content: [{ ...standard, dataType: "BOOLEAN" }] });
		await expect(previewSuggestedStandardMappings()).resolves.toEqual([]);
	});

	it("groups confirmed suggestions by model and reports each result", async () => {
		apiMocks.applyModelSpecStandardElementBindings.mockResolvedValue({
			...model,
			status: "DRAFT",
			revision: 4,
		});
		const suggestions = [
			{
				id: `${model.id}:payable_amount:${standard.id}@2`,
				modelId: model.id,
				modelName: model.name,
				modelStatus: "PUBLISHED" as const,
				modelRevision: 3,
				modelChecksum: "model-checksum",
				fieldName: "payable_amount",
				fieldLabel: "应付账款金额",
				fieldDataType: "DECIMAL",
				standardId: standard.id,
				standardCode: "payable_amount",
				standardName: "应付账款金额",
				standardVersion: 2,
				createsDraft: true,
			},
		];

		await expect(applySuggestedStandardMappings(suggestions)).resolves.toMatchObject([
			{ modelId: model.id, success: true, revision: 4 },
		]);
		expect(apiMocks.applyModelSpecStandardElementBindings).toHaveBeenCalledWith(
			{ id: model.id, revision: 3, checksum: "model-checksum" },
			[
				{
					fieldName: "payable_amount",
					standardElementId: standard.id,
					standardElementVersion: 2,
				},
			],
		);
	});
});
