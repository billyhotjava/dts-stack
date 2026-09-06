// @vitest-environment jsdom
import { beforeEach, expect, it, vi } from "vitest";
import { saveModelDraftOperation } from "@/api/modelSpecApi";
import { saveModelImplementation, validateModelImplementation } from "@/api/modelImplementationApi";
import type { ModelSpecDraft } from "./modelWorkbenchService";
import { saveModelDefinitionDraft, validateModelDefinitionInput } from "./modelDefinitionCreation";
vi.mock("@/api/dimensionDefinitionApi", () => ({
	confirmDimensionDefinition: vi.fn(),
	createDimensionDefinition: vi.fn(),
	listDimensionDefinitions: vi.fn(),
	updateDimensionDefinition: vi.fn(),
}));
vi.mock("@/api/dataMartApi", () => ({ listDataMarts: vi.fn() }));
vi.mock("@/api/modelingStandardsApi", () => ({ listModelFieldStandardOptions: vi.fn() }));
vi.mock("@/api/modelImplementationApi", () => ({
	getModelImplementationCapabilities: vi.fn(),
	saveModelImplementation: vi.fn(),
	validateModelImplementation: vi.fn(),
}));
vi.mock("@/api/modelSpecApi", () => ({
	getModelLifecycle: vi.fn(),
	listModelSpecs: vi.fn(),
	updateModelSpec: vi.fn(),
	createModelSpec: vi.fn(),
	saveModelDraftOperation: vi.fn(),
}));
vi.mock("@/api/subjectDomainApi", () => ({ listSubjectDomains: vi.fn() }));
vi.mock("@/api/services/catalogDomainService", () => ({ default: { list: vi.fn() } }));
vi.mock("@/api/services/modelingImportContextService", () => ({
	collectCurrentWarehousePlanSources: vi.fn(),
	resolveDefaultModelingContextId: vi.fn(),
}));
vi.mock("@/api/warehouseLayerApi", () => ({ listWarehouseLayers: vi.fn() }));

const makeDraft = (patch: Partial<ModelSpecDraft> = {}): ModelSpecDraft => ({
	createKind: "dimension-table",
	base: null,
	planId: "plan-1",
	domainId: "finance",
	name: "预算科目表",
	description: "统一维护预算科目",
	physicalName: "dim_budget_account",
	materialization: "table",
	grainStatement: "",
	fields: [
		{
			name: "subject_code",
			displayName: "科目编码",
			dataType: "STRING",
			nullable: false,
			role: "KEY",
			dimensionAttributeCode: "SUBJECT_CODE",
		},
	],
	partitionFields: "",
	loadStrategy: "FULL",
	scdType: "TYPE1",
	reuseScope: "DOMAIN",
	dimensionDefinitionId: "dimension-1",
	standardBindings: [],
	warehouseLayerCode: "DWD",
	implementationMode: "DESIGNER_GENERATED",
	implementationBase: null,
	implementationInputMode: "",
	generationStrategyType: "",
	implementationIdempotencyKey: "implementation-draft-1",
	creationOperationId: "create-draft-1",
	fieldMappings: [],
	casts: {},
	filters: [],
	deduplicateBy: [],
	joins: [],
	groupBy: [],
	aggregations: [],
	sourceRefs: [],
	dependsOn: [],
	dimensionRefs: [],
	factShape: "",
	timeSemanticsType: "",
	timeSemanticsFields: [],
	consumptionScenario: "",
	...patch,
});

const context = { ownerId: "owner-1", dimensionDefinitions: [] };
const definition = (createKind: "summary" | "application" = "summary") =>
	makeDraft({
		createKind,
		planId: "10000000-0000-0000-0000-000000000001",
		domainId: "20000000-0000-0000-0000-000000000001",
		warehouseLayerCode: createKind === "summary" ? "DWS" : "ADS",
		grainStatement: "一个项目一行",
		physicalName: "",
		name: "项目汇总",
		consumptionScenario: createKind === "application" ? "项目分析" : "",
		dataMartId: "30000000-0000-0000-0000-000000000001",
		subjectDomainId: "40000000-0000-0000-0000-000000000001",
		fields: [{ name: "project_id", dataType: "STRING", role: "KEY", nullable: false }],
		dimensionDefinitionId: "",
	});
beforeEach(() => {
	vi.clearAllMocks();
	vi.mocked(saveModelDraftOperation).mockResolvedValue({ model: { id: "created" }, implementation: null } as Awaited<
		ReturnType<typeof saveModelDraftOperation>
	>);
});
it.each(["summary", "application"] as const)("saves %s design without implementation or upstream", async (kind) => {
	const draft = definition(kind);
	expect(validateModelDefinitionInput(draft)).toEqual({});
	await saveModelDefinitionDraft(draft, context);
	expect(saveModelDraftOperation).toHaveBeenCalledWith(
		expect.objectContaining({
			saveMode: "DEFINITION_ONLY",
			implementation: null,
			modelSpec: expect.objectContaining({ dependsOn: [], name: "项目汇总" }),
		}),
	);
	expect(saveModelImplementation).not.toHaveBeenCalled();
	expect(validateModelImplementation).not.toHaveBeenCalled();
});
it("retains logical validation and never sends invalid fields", async () => {
	const draft = { ...definition(), fields: [] };
	expect(validateModelDefinitionInput(draft).fields).toBeTruthy();
	await expect(saveModelDefinitionDraft(draft, context)).rejects.toThrow("至少添加一个字段");
	expect(saveModelDraftOperation).not.toHaveBeenCalled();
});
it("does not waive malformed upstream references", async () => {
	await expect(
		saveModelDefinitionDraft({ ...definition(), dependsOn: [{ modelSpecId: "bad", revision: 0 }] }, context),
	).rejects.toThrow();
	expect(saveModelDraftOperation).not.toHaveBeenCalled();
});
it("retains operation identity and input through a failed request and retry", async () => {
	const draft = definition();
	const original = JSON.stringify(draft);
	vi.mocked(saveModelDraftOperation).mockRejectedValueOnce(new Error("offline"));
	await expect(saveModelDefinitionDraft(draft, context)).rejects.toThrow("offline");
	await saveModelDefinitionDraft(draft, context);
	const calls = vi.mocked(saveModelDraftOperation).mock.calls;
	expect(calls[0][0]).toEqual(calls[1][0]);
	expect(JSON.stringify(draft)).toBe(original);
});
it("rejects existing models in the creation-only path", async () => {
	await expect(
		saveModelDefinitionDraft({ ...definition(), base: { id: "existing" } as ModelSpecDraft["base"] }, context),
	).rejects.toThrow("新建模型");
	expect(saveModelDraftOperation).not.toHaveBeenCalled();
});
