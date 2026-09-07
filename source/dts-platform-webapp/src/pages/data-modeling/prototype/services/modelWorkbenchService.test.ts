// @vitest-environment jsdom

import { beforeEach, describe, expect, it, vi } from "vitest";
import { listDataMarts } from "@/api/dataMartApi";
import {
	confirmDimensionDefinition,
	createDimensionDefinition,
	listDimensionDefinitions,
	updateDimensionDefinition,
} from "@/api/dimensionDefinitionApi";
import {
	getModelImplementationCapabilities,
	saveModelImplementation,
	validateModelImplementation,
} from "@/api/modelImplementationApi";
import { listModelFieldStandardOptions } from "@/api/modelingStandardsApi";
import {
	createModelSpec,
	getModelLifecycle,
	listModelSpecs,
	saveModelDraftOperation,
	updateModelSpec,
} from "@/api/modelSpecApi";
import catalogDomainService from "@/api/services/catalogDomainService";
import {
	collectCurrentWarehousePlanSources,
	resolveDefaultModelingContextId,
} from "@/api/services/modelingImportContextService";
import { listSubjectDomains } from "@/api/subjectDomainApi";
import { listWarehouseLayers, type WarehouseLayerView } from "@/api/warehouseLayerApi";
import type { WarehousePlanSourceBindingView } from "@/api/warehousePlanApi";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type {
	ModelImplementationCapabilities,
	ModelImplementationView,
} from "@/features/modeling/contracts/modelImplementationContract";
import type { CanonicalModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { validateModelSpecUpdate } from "@/features/modeling/contracts/modelSpecV2Contract";
import type { ConceptDimensionDraft, ModelDraft, ModelSpecDraft } from "./modelWorkbenchService";
import {
	applyModelDraftFieldPatch,
	confirmDimensionDefinitionDraft,
	emptyModelDraft,
	loadModelWorkbenchContext,
	loadModelWorkbenchDraft,
	normalizeModelDraftImplementation,
	modelDraftFromAuthoringSnapshot,
	modelDraftFromView,
	modelDraftNeedsImplementationRecovery,
	modelDraftToAuthoringSnapshot,
	modelDraftToUpdateCommand,
	modelSourceRefFromBinding,
	prepareModelDraftForSave,
	reconcileModelDraftSources,
	saveDimensionDefinitionDraft,
	saveModelDraft,
	validateConceptDimensionDraftInput,
	validateModelDraftInput,
} from "./modelWorkbenchService";

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

const implementationCapabilities: ModelImplementationCapabilities = {
	adapter: "postgres",
	inputModesByModelType: {
		DIMENSION: ["PHYSICAL_ASSET", "GENERATED"],
		FACT: ["PHYSICAL_ASSET", "UPSTREAM_MODEL"],
		SUMMARY: ["UPSTREAM_MODEL"],
		APPLICATION: ["UPSTREAM_MODEL"],
	},
	loadStrategies: ["FULL", "INCREMENTAL"],
	materializationsByLoadStrategy: { FULL: ["table", "view"], INCREMENTAL: ["incremental"] },
	settingKeys: [
		"aggregations",
		"casts",
		"dedupBy",
		"deduplicateBy",
		"filters",
		"groupBy",
		"joins",
		"loadStrategy",
		"partitionFields",
		"retentionDays",
		"targetPhysicalName",
	],
	partitionFieldsSupported: false,
	incrementalKeyRequired: true,
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
	businessProcessId: "60000000-0000-0000-0000-000000000001",
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

const generatedImplementation = (
	model: CanonicalModelSpecView,
	patch: Partial<ModelImplementationView> = {},
): ModelImplementationView => ({
	id: "40000000-0000-0000-0000-000000000001",
	modelSpecId: model.id,
	planId: model.planId,
	revision: model.revision,
	modelChecksum: model.checksum,
	ownership: "DESIGNER_GENERATED",
	projectKey: "system-managed",
	dbtUniqueId: `model.${model.id}`,
	status: "ACTIVE",
	implementationRevision: 1,
	implementationChecksum: "c".repeat(64),
	inputMode: "GENERATED",
	inputs: [{ generatorType: "DATE_DIMENSION", config: {} }],
	fieldMappings: [],
	settings: {
		targetPhysicalName: "dim_finance_date",
		loadStrategy: "FULL",
		partitionFields: [],
		retentionDays: 3650,
	},
	materialization: "table",
	...patch,
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
	attributes: [],
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
	attributes: [{ code: "SUBJECT_CODE", name: "预算科目编码", definition: "唯一编码", primaryKey: true, order: 1 }],
	status: "DRAFT",
	revision: 1,
	checksum: "checksum-1",
	usageCount: 0,
	createdAt: "2026-08-04T10:00:00Z",
	updatedAt: "2026-08-04T10:00:00Z",
};

const physicalSource: WarehousePlanSourceBindingView = {
	bindingId: "50000000-0000-0000-0000-000000000001",
	sourceType: "CONNECTION_TABLE",
	locator: { namespace: "public", objectName: "ods_budget_execution" },
	sourceId: "ods_budget_execution",
	confirmationStatus: "CONFIRMED",
	displayName: "预算执行 ODS",
	confirmedVersion: "source-v1",
	resolvedVersion: "source-v1",
	resolutionStatus: "AVAILABLE",
	freshness: "CURRENT",
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
	warehouseLayerCode: "DWD",
	implementationMode: "DESIGNER_GENERATED",
	implementationBase: null,
	implementationInputMode: "GENERATED",
	generationStrategyType: "DATE_DIMENSION",
	implementationIdempotencyKey: "implementation-draft-1",
	creationOperationId: "create-draft-1",
	sourceRefs: [],
	dependsOn: [],
	factShape: "",
	timeSemanticsType: "",
	timeSemanticsFields: [],
	consumptionScenario: "",
});

beforeEach(() => {
	vi.clearAllMocks();
	vi.mocked(validateModelImplementation).mockResolvedValue({ valid: true, code: "MODEL_IMPLEMENTATION_VALID" });
	vi.mocked(getModelImplementationCapabilities).mockResolvedValue(implementationCapabilities);
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
    it("reopens an unimplemented source model with the structure generator selected", () => {
        const model = { ...canonicalFactView(), modelType: "SOURCE" as const, layer: "ODS" as const };
        expect(modelDraftFromView(model, null)).toMatchObject({
            implementationInputMode: "GENERATED", generationStrategyType: "SCHEMA_ONLY",
        });
    });

	it("recognizes a saved generated model whose implementation save must be retried", () => {
		const model = {
			...canonicalFactView(),
			modelType: "DIMENSION" as const,
			generationStrategy: { type: "DATE_DIMENSION" as const, reference: null },
			dimensionDefinitionRef: { dimensionDefinitionId: "dimension-1", revision: 1 },
		};

		expect(modelDraftNeedsImplementationRecovery(modelDraftFromView(model, null))).toBe(true);
		expect(
			modelDraftNeedsImplementationRecovery(
				modelDraftFromView(
					model,
					generatedImplementation(model, { revision: model.revision - 1, modelChecksum: "b".repeat(64) }),
				),
			),
		).toBe(true);
		expect(modelDraftNeedsImplementationRecovery(modelDraftFromView(model, generatedImplementation(model)))).toBe(
			false,
		);
	});

	it("routes an implementation with server-unsupported execution settings through direct repair", () => {
		const model = {
			...canonicalFactView(),
			modelType: "DIMENSION" as const,
			generationStrategy: { type: "DATE_DIMENSION" as const, reference: null },
			dimensionDefinitionRef: { dimensionDefinitionId: "dimension-1", revision: 1 },
		};
		const implementation = generatedImplementation(model, {
			settings: {
				targetPhysicalName: "dim_finance_date",
				loadStrategy: "FULL",
				partitionFields: ["day_value"],
				retentionDays: 3650,
			},
		});

		expect(
			modelDraftNeedsImplementationRecovery(modelDraftFromView(model, implementation), implementationCapabilities),
		).toBe(true);
	});

	it("loads physical settings from the canonical implementation instead of the legacy model projection", () => {
		const model = {
			...canonicalFactView(),
			modelType: "DIMENSION" as const,
			dimensionDefinitionRef: { dimensionDefinitionId: "dimension-1", revision: 1 },
		};
		const implementation = generatedImplementation(model);

		expect(modelDraftFromView(model, implementation)).toMatchObject({
			physicalName: "dim_finance_date",
			loadStrategy: "FULL",
			partitionFields: "",
			materialization: "table",
			implementationBase: implementation,
			implementationInputMode: "GENERATED",
			generationStrategyType: "DATE_DIMENSION",
		});
	});

	it("loads the persisted implementation input kind before inferring from logical references", () => {
		const upstream = { ...canonicalFactView(), id: "30000000-0000-0000-0000-000000000002" };
		const model = {
			...canonicalFactView(),
			sourceRefs: [
				{
					kind: "TABLE" as const,
					ref: "历史物理来源",
					layer: "ODS" as const,
					role: "PRIMARY" as const,
					sortOrder: 0,
					sourceBindingId: physicalSource.bindingId,
					resolvedVersion: "source-v1",
				},
			],
			dependsOn: [{ modelSpecId: upstream.id, revision: upstream.revision }],
		};
		const implementation = generatedImplementation(model, {
			inputMode: "UPSTREAM_MODEL",
			inputs: [{ modelSpecId: upstream.id, revision: upstream.revision, checksum: upstream.checksum }],
		});

		expect(modelDraftFromView(model, implementation).implementationInputMode).toBe("UPSTREAM_MODEL");
	});

	it("keeps imported logical upstream relations without pretending they are already visual inputs", () => {
		const upstream = canonicalFactView();
		const application = {
			...canonicalFactView(),
			id: "30000000-0000-0000-0000-000000000003",
			modelType: "APPLICATION" as const,
			layer: "ADS" as const,
			warehouseLayerCode: "ADS",
			implementationMode: "DBT_MANAGED" as const,
			businessProcessId: null,
			factShape: null,
			timeSemantics: null,
			sourceRefs: [],
			dependsOn: [{ modelSpecId: upstream.id, revision: upstream.revision }],
		};
		const implementation = generatedImplementation(application, {
			ownership: "DBT_MANAGED",
			inputMode: "GENERATED",
			inputs: [{ generatorType: "DBT_SQL", config: {} }],
		});

		expect(modelDraftFromView(application, implementation)).toMatchObject({
			implementationInputMode: "",
			dependsOn: application.dependsOn,
		});
	});

	it("restores imported SQL execution metadata without inventing a visual implementation", async () => {
		const model = { ...canonicalFactView(), implementationMode: "DBT_MANAGED" as const };
		const implementation = generatedImplementation(model, {
			ownership: "DBT_MANAGED",
			inputMode: "GENERATED",
			inputs: [{ generatorType: "DBT", config: { resourcePath: "models/dim_patent_lifecycle_status.sql" } }],
			settings: { targetPhysicalName: "dim_patent_lifecycle_status", loadStrategy: "FULL", partitionFields: [] },
			materialization: "table",
		});
		vi.mocked(getModelLifecycle).mockResolvedValue({ implementation, artifacts: [], events: [] });
		const draft = await loadModelWorkbenchDraft(model);
		expect(draft).toMatchObject({
			physicalName: "dim_patent_lifecycle_status",
			loadStrategy: "FULL",
			partitionFields: "",
			materialization: "table",
			implementationInputMode: "",
			implementationBase: implementation,
		});
		const snapshot = modelDraftToAuthoringSnapshot(draft, {
			ownerId: "owner-1",
			dimensionDefinitions: [],
			models: [model],
		});
		expect(snapshot.visualImplementation).toBeUndefined();
		expect(implementation.inputs[0]).toEqual({
			generatorType: "DBT",
			config: { resourcePath: "models/dim_patent_lifecycle_status.sql" },
		});
	});

	it("loads only the selected model lifecycle when opening a workbench draft", async () => {
		const model = canonicalFactView();
		const implementation = generatedImplementation(model);
		vi.mocked(getModelLifecycle).mockResolvedValue({ implementation, artifacts: [], events: [] });

		const draft = await loadModelWorkbenchDraft(model);

		expect(getModelLifecycle).toHaveBeenCalledWith(model.id);
		expect(draft.physicalName).toBe("dim_finance_date");
		expect(listModelSpecs).not.toHaveBeenCalled();
	});

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
	it("preserves TYPE2 history bindings when saving an existing dimension", () => {
		const policy = { type: "TYPE2" as const, effectiveFromField: "valid_from", effectiveToField: "valid_to", currentFlagField: "is_current" };
		const base = { ...canonicalFactView(), modelType: "DIMENSION" as const,
			dimensionProfile: { hierarchies: [], scdPolicy: policy } };
		const draft = modelDraftFromView(base) as ModelSpecDraft;
		expect(modelDraftToUpdateCommand(draft).dimensionProfile?.scdPolicy).toEqual(policy);
		draft.scdType = "TYPE1";
		expect(modelDraftToUpdateCommand(draft).dimensionProfile?.scdPolicy).toEqual({ type: "TYPE1" });
	});

	it("allows full-refresh keyless outputs but keeps dimension and incremental keys required", () => {
		for (const createKind of ["fact", "summary", "application"] as const) {
			const draft = { ...modelDraftFromView(canonicalFactView()), createKind, loadStrategy: "FULL" as const,
				fields: [{ name: "total_amount", displayName: "总金额", dataType: "DECIMAL", nullable: true, role: "MEASURE" as const }] } as ModelSpecDraft;
			expect(validateModelDraftInput(draft).fields).toBeUndefined();
			draft.loadStrategy = "INCREMENTAL";
			expect(validateModelDraftInput(draft).fields).toBeTruthy();
			draft.loadStrategy = "FULL";
			draft.createKind = "dimension-table";
			expect(validateModelDraftInput(draft).fields).toBeTruthy();
		}
	});

	it("saves a global aggregation without inventing grouping or key fields", async () => {
		const base = { ...canonicalFactView(), implementationMode: "DESIGNER_GENERATED" as const };
		vi.mocked(updateModelSpec).mockResolvedValue(base);
		const draft = modelDraftFromView(base) as ModelSpecDraft;
		draft.physicalName = "global_total";
		draft.factShape = "";
		draft.timeSemanticsType = "";
		draft.timeSemanticsFields = [];
		draft.implementationMode = "DESIGNER_GENERATED";
		draft.implementationInputMode = "PHYSICAL_ASSET";
		draft.sourceRefs = [modelSourceRefFromBinding(physicalSource, 0)!];
		draft.loadStrategy = "FULL";
		draft.fields = [{ name: "total_amount", displayName: "总金额", dataType: "DECIMAL", nullable: true, role: "MEASURE" }];
		draft.fieldMappings = [{ sourceField: "src_0.amount", targetField: "total_amount" }];
		draft.groupBy = [];
		draft.aggregations = [{ sourceField: "src_0.amount", targetField: "total_amount", function: "SUM", distinct: false }];
		expect(validateModelDraftInput(draft).transformations).toBeUndefined();
		await saveModelDraft(draft, { ownerId: "owner-1", dimensionDefinitions: [] });
		expect(saveModelImplementation).toHaveBeenCalledWith(expect.anything(), null,
			expect.objectContaining({ settings: expect.objectContaining({ aggregations: draft.aggregations }) }));
	});

	it("accepts a keyless full-refresh grain through the shared model contract", () => {
		const draft = modelDraftFromView(canonicalFactView()) as ModelSpecDraft;
		draft.fields = [{ name: "total_amount", displayName: "总金额", dataType: "DECIMAL", nullable: true, role: "MEASURE" }];
		const codes = validateModelSpecUpdate(modelDraftToUpdateCommand(draft)).map((issue) => issue.code);
		expect(codes).not.toContain("MODEL_SPEC_GRAIN_INVALID");
		expect(codes).not.toContain("MODEL_SPEC_GRAIN_REQUIRED");
	});

	it("uses a field explicitly marked as TIME as the fact time field", () => {
		const draft = validDimensionDraft() as ModelSpecDraft;
		draft.createKind = "fact";
		draft.fields = [{ ...draft.fields[0], name: "event_time", role: "ATTRIBUTE" }];
		draft.timeSemanticsFields = [];

		const selected = applyModelDraftFieldPatch(draft, 0, { role: "TIME" });
		expect(selected.fields[0].role).toBe("TIME");
		expect(selected.timeSemanticsFields).toEqual(["event_time"]);

		const cleared = applyModelDraftFieldPatch(selected, 0, { role: "ATTRIBUTE" });
		expect(cleared.timeSemanticsFields).toEqual([]);
	});

	it("adopts the only confirmed source returned by first-use context creation", () => {
		const draft = validDimensionDraft() as ModelSpecDraft;
		draft.implementationInputMode = "PHYSICAL_ASSET";
		draft.sourceRefs = [];

		const next = reconcileModelDraftSources(draft, "plan-created", [physicalSource]);

		expect(next.planId).toBe("plan-created");
		expect(next.sourceRefs).toEqual([
			expect.objectContaining({
				sourceBindingId: physicalSource.bindingId,
				resolvedVersion: physicalSource.resolvedVersion,
			}),
		]);
	});

	it("reports invalid physical names and duplicate trimmed field names", () => {
		const invalid = validDimensionDraft();
		invalid.physicalName = "Bad-Name";
		invalid.fields.push({ ...invalid.fields[0], name: " account_code ", displayName: "重复字段" });

		expect(validateModelDraftInput(invalid)).toMatchObject({
			physicalName: "产出表英文名只能使用小写字母、数字和下划线，且必须以字母开头",
			fields: "字段名称不能重复",
		});
	});

	it("uses customer-facing source terms in model validation", () => {
		const draft = validDimensionDraft();
		draft.implementationInputMode = "";
		expect(validateModelDraftInput(draft)).toMatchObject({ implementationInputMode: "请选择数据来源方式" });

		draft.implementationInputMode = "PHYSICAL_ASSET";
		draft.sourceRefs = [];
		expect(validateModelDraftInput(draft)).toMatchObject({
			implementationInputMode: "请至少选择一张已确认且当前有效的输入源表",
		});

		draft.createKind = "summary";
		draft.sourceRefs = [modelSourceRefFromBinding(physicalSource, 0)!];
		expect(validateModelDraftInput(draft, implementationCapabilities)).toMatchObject({
			implementationInputMode: "当前模型类型不支持所选数据来源方式，请重新选择",
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

	it("validates partition fields against the current model instead of accepting instructional text", () => {
		const draft = modelDraftFromView(canonicalFactView());
		draft.physicalName = "dwd_budget_execution";
		draft.implementationInputMode = "PHYSICAL_ASSET";

		draft.partitionFields = "首次最小 Demo 留空";
		expect(validateModelDraftInput(draft)).toMatchObject({
			partitionFields: "分区字段必须使用字段英文名，多个字段用逗号分隔",
		});

		draft.partitionFields = "missing_field";
		expect(validateModelDraftInput(draft)).toMatchObject({
			partitionFields: "分区字段必须来自当前模型字段",
		});

		draft.partitionFields = "record_id，event_time";
		expect(validateModelDraftInput(draft)).not.toHaveProperty("partitionFields");
	});

	it("accepts an existing code-authored dimension without inventing a visual input mode", () => {
		const draft = validDimensionDraft();
		const base = {
			...canonicalFactView(),
			modelType: "DIMENSION" as const,
			implementationMode: "DBT_MANAGED" as const,
			dimensionDefinitionRef: { dimensionDefinitionId: "dimension-1", revision: 1 },
		};
		draft.base = base;
		draft.implementationMode = "DBT_MANAGED";
		draft.implementationBase = generatedImplementation(base, {
			ownership: "DBT_MANAGED",
			inputMode: "GENERATED",
			inputs: [{ generatorType: "DBT_SQL", config: {} }],
		});
		draft.implementationInputMode = "";
		draft.generationStrategyType = "";
		draft.sourceRefs = [];
		draft.dependsOn = [];

		expect(validateModelDraftInput(draft)).not.toHaveProperty("implementationInputMode");
	});

	it("does not let DBT provenance bypass an invalid visual generator selection", () => {
		const draft = validDimensionDraft();
		draft.implementationMode = "DBT_MANAGED";
		draft.implementationInputMode = "GENERATED";
		draft.generationStrategyType = "";

		expect(validateModelDraftInput(draft)).toMatchObject({
			implementationInputMode: "当前模型不支持所选生成器",
		});
	});

	it("updates a saved DRAFT definition with attributes instead of blocking the edit", async () => {
		const draft = conceptDraft();
		draft.definitionBase = { ...definitionView, status: "DRAFT" };
		draft.attributes = [
			{ code: "COST_CENTER_CODE", name: "成本中心编码", definition: "唯一编码", primaryKey: true, order: 1 },
		];
		vi.mocked(updateDimensionDefinition).mockResolvedValue({ ...definitionView, attributes: draft.attributes });

		const saved = await saveDimensionDefinitionDraft(draft, "owner-1");

		expect(updateDimensionDefinition).toHaveBeenCalledWith(
			{ id: "dimension-1", revision: 1, checksum: "checksum-1" },
			expect.objectContaining({
				name: "预算科目",
				attributes: expect.arrayContaining([
					expect.objectContaining({ code: "COST_CENTER_CODE", primaryKey: true, order: 1 }),
				]),
			}),
		);
		expect(saved.attributes).toEqual(draft.attributes);
	});

	it("updates a CURRENT dimension as a new revision", async () => {
		const draft = conceptDraft();
		draft.definitionBase = { ...definitionView, status: "CURRENT" };
		vi.mocked(updateDimensionDefinition).mockResolvedValue({
			...definitionView,
			status: "CURRENT",
			revision: 2,
			name: "预算科目（调整）",
		});
		draft.name = "预算科目（调整）";

		const saved = await saveDimensionDefinitionDraft(draft, "owner-1");

		expect(updateDimensionDefinition).toHaveBeenCalledWith(
			{ id: "dimension-1", revision: 1, checksum: "checksum-1" },
			expect.objectContaining({ name: "预算科目（调整）" }),
		);
		expect(saved).toMatchObject({ status: "CURRENT", revision: 2, name: "预算科目（调整）" });
	});

	it("keeps retired dimension definitions read-only", async () => {
		const draft = conceptDraft();
		draft.definitionBase = { ...definitionView, status: "RETIRED" };

		await expect(saveDimensionDefinitionDraft(draft, "owner-1")).rejects.toThrow("已退役的维度不能修改");
		expect(updateDimensionDefinition).not.toHaveBeenCalled();
	});

	it("confirms a saved definition without attributes (DataWorks 对齐)", async () => {
		const draft = conceptDraft();
		draft.definitionBase = { ...definitionView, attributes: [] };
		vi.mocked(confirmDimensionDefinition).mockResolvedValue({ ...definitionView, status: "CURRENT", attributes: [] });

		const confirmed = await confirmDimensionDefinitionDraft(draft);
		expect(confirmDimensionDefinition).toHaveBeenCalledWith(
			expect.objectContaining({ id: "dimension-1", status: "DRAFT", attributes: [] }),
		);
		expect(confirmed.definitionBase?.status).toBe("CURRENT");
	});

	it("confirms once attributes include a primary key", async () => {
		const draft = conceptDraft();
		draft.definitionBase = {
			...definitionView,
			attributes: [{ code: "A", name: "属性", primaryKey: true, order: 1 }],
		};
		vi.mocked(confirmDimensionDefinition).mockResolvedValue({ ...definitionView, status: "CURRENT" });

		const confirmed = await confirmDimensionDefinitionDraft(draft);

		expect(confirmed.definitionBase?.status).toBe("CURRENT");
		expect(confirmDimensionDefinition).toHaveBeenCalledWith(draft.definitionBase);
	});

	it("defaults new model drafts to the canonical target layer", () => {
		const context = {
			planId: "plan-1",
			domains: [],
			models: [],
			dimensions: [],
			standards: [],
			warehouseLayers: [],
			sources: [],
			implementationCapabilities,
		} as never;
		expect(emptyModelDraft("fact", context)).toMatchObject({ warehouseLayerCode: "DWD" });
		expect(emptyModelDraft("summary", context)).toMatchObject({ warehouseLayerCode: "DWS" });
		expect(emptyModelDraft("application", context)).toMatchObject({ warehouseLayerCode: "ADS" });
		expect(emptyModelDraft("fact", context)).toMatchObject({
			planId: "plan-1",
			implementationInputMode: "PHYSICAL_ASSET",
			factShape: "",
			timeSemanticsType: "",
		});
		expect(emptyModelDraft("summary", context)).toMatchObject({ implementationInputMode: "UPSTREAM_MODEL" });
		expect(emptyModelDraft("application", context)).toMatchObject({ implementationInputMode: "UPSTREAM_MODEL" });
	});

	it("uses the canonical domain id for new draft domain binding", () => {
		const context = {
			planId: "plan-1",
			domains: [
				{
					id: "1d9a3e90-7b38-485a-8e6d-c428dc17a61e",
					code: "FinanceDomain",
					name: "财务域",
					parentCode: "Finance",
				},
			],
			models: [],
			dimensions: [],
			standards: [],
			warehouseLayers: [],
			sources: [],
			implementationCapabilities,
		} as never;
		expect(emptyModelDraft("dimension", context)).toMatchObject({
			domainId: "1d9a3e90-7b38-485a-8e6d-c428dc17a61e",
		});
		expect(emptyModelDraft("fact", context)).toMatchObject({
			domainId: "1d9a3e90-7b38-485a-8e6d-c428dc17a61e",
		});
	});

	it("loads the governed warehouse layers into the workbench context", async () => {
		vi.mocked(resolveDefaultModelingContextId).mockResolvedValue("plan-1");
		vi.mocked(collectCurrentWarehousePlanSources).mockResolvedValue([physicalSource]);
		vi.mocked(listWarehouseLayers).mockResolvedValue([customLayer]);
		vi.mocked(catalogDomainService.list).mockResolvedValue([]);
		vi.mocked(listModelSpecs).mockResolvedValue([]);
		vi.mocked(listDimensionDefinitions).mockResolvedValue([]);
		vi.mocked(listModelFieldStandardOptions).mockResolvedValue([]);
		vi.mocked(listDataMarts).mockResolvedValue([]);
		vi.mocked(listSubjectDomains).mockResolvedValue([]);

		const context = await loadModelWorkbenchContext();

		expect(context.warehouseLayers).toEqual([customLayer]);
		expect(context.planId).toBe("plan-1");
		expect(context.sources).toEqual([physicalSource]);
		expect(context.dimensions).toEqual([]);
		expect(context.dataMarts).toEqual([]);
		expect(context.subjectDomains).toEqual([]);
		expect(context.implementationCapabilities).toEqual(implementationCapabilities);
		expect(collectCurrentWarehousePlanSources).toHaveBeenCalledWith("plan-1");
		expect(listWarehouseLayers).toHaveBeenCalledTimes(1);
		expect(listDimensionDefinitions).toHaveBeenCalledWith({ offset: 0, limit: 100 });
		expect(listDataMarts).toHaveBeenCalledWith({ status: "CURRENT", offset: 0, limit: 100 });
		expect(listSubjectDomains).toHaveBeenCalledWith({ status: "CURRENT", offset: 0, limit: 100 });
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

	it("saves an ordinary two-field detail without inventing a fact shape or time field", async () => {
		const base = canonicalFactView();
		vi.mocked(updateModelSpec).mockResolvedValue(base);
		const draft = modelDraftFromView(base) as ModelSpecDraft;
		draft.factShape = "";
		draft.timeSemanticsType = "";
		draft.timeSemanticsFields = [];
		draft.fields = [
			{ name: "test_id", displayName: "测试编号", dataType: "VARCHAR", nullable: false, role: "KEY" },
			{ name: "test_name", displayName: "测试名称", dataType: "VARCHAR", nullable: true, role: "ATTRIBUTE" },
		];
		await saveModelDraft(draft, { ownerId: "owner-1", dimensionDefinitions: [] });
		expect(updateModelSpec).toHaveBeenCalledWith(
			base,
			expect.objectContaining({ factShape: null, timeSemantics: null }),
		);
	});

	it("validates business time only when an explicit snapshot is selected", () => {
		const draft = modelDraftFromView(canonicalFactView()) as ModelSpecDraft;
		draft.factShape = "PERIODIC_SNAPSHOT";
		draft.timeSemanticsType = "";
		draft.timeSemanticsFields = [];
		expect(validateModelDraftInput(draft).timeSemantics).toBeTruthy();
		draft.timeSemanticsType = "EVENT_TIME";
		draft.timeSemanticsFields = ["event_time"];
		expect(validateModelDraftInput(draft).timeSemantics).toContain("不匹配");
	});

	it("allows an incomplete FACT draft to be saved without invented business time semantics", async () => {
		const base = canonicalFactView();
		vi.mocked(updateModelSpec).mockResolvedValue(base);
		const draft = modelDraftFromView(base) as ModelSpecDraft;
		draft.timeSemanticsType = "";
		draft.timeSemanticsFields = [];

		await saveModelDraft(draft, { ownerId: "owner-1", dimensionDefinitions: [] });

		expect(updateModelSpec).toHaveBeenCalledWith(base, expect.objectContaining({ timeSemantics: null }));
	});

	it("persists FACT dimension revision bindings independently of implementation inputs", async () => {
		const base = canonicalFactView();
		const dimension = {
			...canonicalFactView(),
			id: "30000000-0000-0000-0000-000000000099",
			modelType: "DIMENSION" as const,
			businessProcessId: null,
			factShape: null,
			timeSemantics: null,
			name: "风险等级维度表",
		};
		vi.mocked(updateModelSpec).mockResolvedValue(base);
		const draft = modelDraftFromView(base);
		draft.dimensionRefs = [{ modelSpecId: dimension.id, revision: dimension.revision }];

		await saveModelDraft(draft, { ownerId: "owner-1", dimensionDefinitions: [], models: [base, dimension] });

		expect(updateModelSpec).toHaveBeenCalledWith(
			base,
			expect.objectContaining({
				dimensionRefs: [{ modelSpecId: dimension.id, revision: dimension.revision }],
			}),
		);
	});

	it("omits legacy dimension definition fields when saving a dimension table", async () => {
		const base = {
			...canonicalFactView(),
			modelType: "DIMENSION" as const,
			layer: "DWD" as const,
			name: "成本中心维度表",
			description: "统一的成本中心分析维度",
			implementationPolicy: {
				physicalName: "dim_budget_account",
				loadStrategy: "FULL" as const,
				retentionDays: null,
				partitionFields: [],
			},
			dimensionDefinitionRef: { dimensionDefinitionId: "dimension-1", revision: 1 },
			dimensionProfile: {
				dimensionCode: "COST_CENTER",
				hierarchies: [],
				scdPolicy: { type: "TYPE1" as const },
				reuseScope: "DOMAIN" as const,
			},
		};
		vi.mocked(updateModelSpec).mockResolvedValue(base);
		const draft = modelDraftFromView(base) as ModelSpecDraft;

		await saveModelDraft(draft, { ownerId: "owner-1", dimensionDefinitions: [] });

		expect(updateModelSpec).toHaveBeenCalledWith(
			base,
			expect.objectContaining({
				dimensionProfile: expect.objectContaining({
					hierarchies: [],
					scdPolicy: { type: "TYPE1" },
				}),
			}),
		);
		const dimensionProfile = vi.mocked(updateModelSpec).mock.calls[0][1].dimensionProfile;
		expect(dimensionProfile).not.toHaveProperty("dimensionCode");
		expect(dimensionProfile).not.toHaveProperty("reuseScope");
		expect(vi.mocked(updateModelSpec).mock.calls[0][1]).not.toHaveProperty("implementationPolicy");
	});

	it("saves logical design and generated physical settings through their canonical APIs", async () => {
		const base = {
			...canonicalFactView(),
			modelType: "DIMENSION" as const,
			name: "日期维度表",
			description: "统一公历日期维度",
			dimensionDefinitionRef: { dimensionDefinitionId: "dimension-1", revision: 1 },
			dimensionProfile: {
				dimensionCode: null,
				hierarchies: [],
				scdPolicy: { type: "TYPE1" as const },
				reuseScope: null,
			},
		};
		const savedModel = { ...base, revision: 2, checksum: "b".repeat(64) };
		const savedImplementation = generatedImplementation(savedModel);
		vi.mocked(updateModelSpec).mockResolvedValue(savedModel);
		vi.mocked(saveModelImplementation).mockResolvedValue(savedImplementation);
		const draft = {
			...modelDraftFromView(base),
			physicalName: "dim_finance_date",
			implementationInputMode: "GENERATED" as const,
			generationStrategyType: "DATE_DIMENSION",
		};

		const result = await saveModelDraft(draft, {
			ownerId: "owner-1",
			dimensionDefinitions: [{ ...definitionView, status: "CURRENT" }],
			models: [base],
		});

		expect(vi.mocked(updateModelSpec).mock.calls[0][1]).not.toHaveProperty("implementationPolicy");
		expect(updateModelSpec).toHaveBeenCalledWith(
			base,
			expect.objectContaining({ generationStrategy: { type: "DATE_DIMENSION", reference: null } }),
		);
		expect(saveModelImplementation).toHaveBeenCalledWith(
			savedModel,
			null,
			expect.objectContaining({
				inputMode: "GENERATED",
				inputs: [{ generatorType: "DATE_DIMENSION", config: {} }],
				settings: expect.objectContaining({
					targetPhysicalName: "dim_finance_date",
					loadStrategy: "FULL",
					partitionFields: [],
				}),
			}),
		);
		expect(result).toEqual({ model: savedModel, implementation: savedImplementation });
	});

	it("preserves the canonical implementation input while updating physical settings", async () => {
		const base = {
			...canonicalFactView(),
			sourceRefs: [
				{
					kind: "TABLE" as const,
					ref: "预算执行 ODS",
					layer: "ODS" as const,
					role: "PRIMARY" as const,
					alias: null,
					joinType: null,
					joinExpression: null,
					sortOrder: 0,
					sourceBindingId: "50000000-0000-0000-0000-000000000001",
					resolvedVersion: "source-v1",
				},
			],
		};
		const currentImplementation = generatedImplementation(base, {
			inputMode: "PHYSICAL_ASSET",
			inputs: [
				{
					sourceBindingId: "50000000-0000-0000-0000-000000000001",
					resolvedVersion: "source-v1",
				},
			],
		});
		const savedModel = { ...base, revision: 2, checksum: "d".repeat(64) };
		const savedImplementation = generatedImplementation(savedModel, {
			inputMode: "PHYSICAL_ASSET",
			inputs: currentImplementation.inputs,
			implementationRevision: 2,
			implementationChecksum: "e".repeat(64),
		});
		vi.mocked(updateModelSpec).mockResolvedValue(savedModel);
		vi.mocked(saveModelImplementation).mockResolvedValue(savedImplementation);
		const draft = {
			...modelDraftFromView(base, currentImplementation),
			physicalName: "dwd_budget_execution",
			partitionFields: "record_id， event_time",
		};

		await saveModelDraft(draft, { ownerId: "owner-1", dimensionDefinitions: [], models: [base] });

		expect(saveModelImplementation).toHaveBeenCalledWith(
			savedModel,
			currentImplementation,
			expect.objectContaining({
				inputMode: "PHYSICAL_ASSET",
				inputs: currentImplementation.inputs,
				settings: expect.objectContaining({ partitionFields: ["record_id", "event_time"] }),
			}),
		);
	});

	it("round-trips the controlled visual transformation contract", async () => {
		const base = {
			...canonicalFactView(),
			sourceRefs: [
				{
					kind: "TABLE" as const,
					ref: "预算执行 ODS",
					layer: "ODS" as const,
					role: "PRIMARY" as const,
					alias: null,
					joinType: null,
					joinExpression: null,
					sortOrder: 0,
					sourceBindingId: physicalSource.bindingId,
					resolvedVersion: "source-v1",
				},
			],
		};
		const currentImplementation = generatedImplementation(base, {
			inputMode: "PHYSICAL_ASSET",
			inputs: [{ sourceBindingId: physicalSource.bindingId, resolvedVersion: "source-v1" }],
			fieldMappings: [
				{ sourceField: "record_code", targetField: "record_id" },
				{ sourceField: "created_at", targetField: "event_time" },
			],
			settings: {
				targetPhysicalName: "dwd_budget_execution",
				loadStrategy: "FULL",
				partitionFields: [],
				casts: { event_time: "timestamp" },
				filters: [{ field: "record_id", operator: "IN", valueType: "STRING", value: ["A", "B"] }],
				deduplicateBy: ["record_id"],
			},
		});
		const draft = modelDraftFromView(base, currentImplementation);
		expect(draft).toMatchObject({
			fieldMappings: currentImplementation.fieldMappings,
			casts: { event_time: "timestamp" },
			filters: [{ field: "record_id", operator: "IN", valueType: "STRING", value: ["A", "B"] }],
			deduplicateBy: ["record_id"],
		});

		const savedModel = { ...base, revision: 2, checksum: "d".repeat(64) };
		vi.mocked(updateModelSpec).mockResolvedValue(savedModel);
		vi.mocked(saveModelImplementation).mockResolvedValue(
			generatedImplementation(savedModel, {
				inputMode: "PHYSICAL_ASSET",
				inputs: currentImplementation.inputs,
			}),
		);
		await saveModelDraft(draft, { ownerId: "owner-1", dimensionDefinitions: [], models: [base] });

		expect(saveModelImplementation).toHaveBeenCalledWith(
			savedModel,
			currentImplementation,
			expect.objectContaining({
				fieldMappings: currentImplementation.fieldMappings,
				settings: expect.objectContaining({
					casts: { event_time: "timestamp" },
					filters: [{ field: "record_id", operator: "IN", valueType: "STRING", value: ["A", "B"] }],
					deduplicateBy: ["record_id"],
				}),
			}),
		);
	});

	it("hydrates logical and structured implementation state from one versioned authoring snapshot", () => {
		const base = canonicalFactView();
		const implementation = generatedImplementation(base, {
			inputMode: "PHYSICAL_ASSET",
			inputs: [{ sourceBindingId: physicalSource.bindingId, resolvedVersion: "source-v1" }],
		});
		const modelSpec = {
			...base,
			name: "预算执行明细",
			description: "统一创作草稿",
		};
		const snapshot = {
			schemaVersion: 1 as const,
			modelSpec,
			visualImplementation: {
				projectKey: "system-managed",
				dbtUniqueId: `model.${base.id}`,
				inputMode: "PHYSICAL_ASSET" as const,
				inputs: [{ sourceBindingId: physicalSource.bindingId, resolvedVersion: "source-v1" }],
				fieldMappings: [{ sourceField: "budget_id", targetField: "record_id" }],
				settings: {
					targetPhysicalName: "dwd_budget_execution_v2",
					loadStrategy: "INCREMENTAL",
					partitionFields: ["event_time"],
					casts: { event_time: "timestamp" },
				},
				ownership: "DESIGNER_GENERATED" as const,
				materialization: "incremental",
				idempotencyKey: "authoring-visual-92",
			},
		};

		expect(modelDraftFromAuthoringSnapshot(base, implementation, snapshot)).toMatchObject({
			name: "预算执行明细",
			description: "统一创作草稿",
			physicalName: "dwd_budget_execution_v2",
			loadStrategy: "INCREMENTAL",
			partitionFields: "event_time",
			fieldMappings: [{ sourceField: "budget_id", targetField: "record_id" }],
			casts: { event_time: "timestamp" },
			implementationBase: implementation,
		});
	});

	it("serializes a structured visual implementation beside the canonical model snapshot", () => {
		const base = {
			...canonicalFactView(),
			sourceRefs: [
				{
					kind: "TABLE" as const,
					ref: "预算执行 ODS",
					layer: "ODS" as const,
					role: "PRIMARY" as const,
					alias: null,
					joinType: null,
					joinExpression: null,
					sortOrder: 0,
					sourceBindingId: physicalSource.bindingId,
					resolvedVersion: "source-v1",
				},
			],
		};
		const implementation = generatedImplementation(base, {
			inputMode: "PHYSICAL_ASSET",
			inputs: [{ sourceBindingId: physicalSource.bindingId, resolvedVersion: "source-v1" }],
		});
		const draft = {
			...modelDraftFromView(base, implementation),
			physicalName: "dwd_budget_execution_v2",
			fieldMappings: [{ sourceField: "budget_id", targetField: "record_id" }],
		};

		const snapshot = modelDraftToAuthoringSnapshot(draft, {
			ownerId: "owner-1",
			dimensionDefinitions: [],
			models: [base],
		});

		expect(snapshot).toMatchObject({
			schemaVersion: 1,
			modelSpec: { name: base.name },
			visualImplementation: {
				inputMode: "PHYSICAL_ASSET",
				ownership: "DESIGNER_GENERATED",
				settings: { targetPhysicalName: "dwd_budget_execution_v2" },
				fieldMappings: [{ sourceField: "budget_id", targetField: "record_id" }],
			},
		});
	});

	it("uses the frozen source bundle identity for a first structured visual implementation", () => {
		const base = {
			...canonicalFactView(),
			sourceRefs: [
				{
					kind: "TABLE" as const,
					ref: "项目任务 ODS",
					layer: "ODS" as const,
					role: "PRIMARY" as const,
					alias: null,
					joinType: null,
					joinExpression: null,
					sortOrder: 0,
					sourceBindingId: physicalSource.bindingId,
					resolvedVersion: "source-v1",
				},
			],
		};
		const draft = {
			...modelDraftFromView(base),
			physicalName: "dwd_project_task_snapshot",
		};
		const context = { ownerId: "owner-1", dimensionDefinitions: [], models: [base] };

		expect(() => modelDraftToAuthoringSnapshot(draft, context)).toThrow("模型创作草稿缺少服务端分配的 dbt 项目标识");

		const snapshot = modelDraftToAuthoringSnapshot(draft, context, true, "dts_model_300000000000");

		expect(snapshot.visualImplementation).toMatchObject({
			projectKey: "dts_model_300000000000",
			dbtUniqueId: "model.dts_model_300000000000.dwd_project_task_snapshot",
			inputMode: "PHYSICAL_ASSET",
			ownership: "DESIGNER_GENERATED",
		});
	});

	it("creates a structured visual snapshot after an imported model explicitly selects visual inputs", () => {
		const base = {
			...canonicalFactView(),
			implementationMode: "DBT_MANAGED" as const,
			sourceRefs: [
				{
					kind: "TABLE" as const,
					ref: "预算执行 ODS",
					layer: "ODS" as const,
					role: "PRIMARY" as const,
					alias: null,
					joinType: null,
					joinExpression: null,
					sortOrder: 0,
					sourceBindingId: physicalSource.bindingId,
					resolvedVersion: "source-v1",
				},
			],
		};
		const codeImplementation = generatedImplementation(base, {
			ownership: "DBT_MANAGED",
			inputMode: "GENERATED",
			inputs: [{ generatorType: "DBT_SQL", config: {} }],
		});
		const draft = {
			...modelDraftFromView(base, codeImplementation),
			implementationInputMode: "PHYSICAL_ASSET" as const,
			sourceRefs: base.sourceRefs,
			physicalName: "dwd_budget_execution",
		};

		const snapshot = modelDraftToAuthoringSnapshot(draft, {
			ownerId: "owner-1",
			dimensionDefinitions: [],
			models: [base],
		});

		expect(snapshot.visualImplementation).toMatchObject({
			inputMode: "PHYSICAL_ASSET",
			ownership: "DESIGNER_GENERATED",
			settings: { targetPhysicalName: "dwd_budget_execution" },
		});
	});

	it("does not resurrect a stale visual implementation after a code-authored snapshot invalidates it", () => {
		const base = canonicalFactView();
		const implementation = generatedImplementation(base, {
			inputMode: "PHYSICAL_ASSET",
			inputs: [{ sourceBindingId: physicalSource.bindingId, resolvedVersion: "source-v1" }],
			fieldMappings: [{ sourceField: "old_id", targetField: "record_id" }],
		});
		const snapshot = {
			schemaVersion: 1 as const,
			modelSpec: { ...base, sourceRefs: [] },
		};

		expect(modelDraftFromAuthoringSnapshot(base, implementation, snapshot)).toMatchObject({
			physicalName: "",
			fieldMappings: [],
			implementationBase: implementation,
		});
	});

	it("omits structured implementation when the active code edit invalidates the visual projection", () => {
		const base = canonicalFactView();
		const implementation = generatedImplementation(base);
		const draft = modelDraftFromView(base, implementation);

		const snapshot = modelDraftToAuthoringSnapshot(
			draft,
			{ ownerId: "owner-1", dimensionDefinitions: [], models: [base] },
			false,
		);

		expect(snapshot).not.toHaveProperty("visualImplementation");
	});

	it("creates a manual model and its first implementation through one atomic operation", async () => {
		const model = { ...canonicalFactView(), revision: 2, checksum: "d".repeat(64) };
		const implementation = generatedImplementation(model, {
			inputMode: "PHYSICAL_ASSET",
			inputs: [{ sourceBindingId: physicalSource.bindingId, resolvedVersion: "source-v1" }],
		});
		vi.mocked(saveModelDraftOperation).mockResolvedValue({ model, implementation, replayed: false });
		const draft: ModelSpecDraft = {
			...modelDraftFromView(canonicalFactView()),
			base: null,
			physicalName: "dwd_budget_execution",
			implementationInputMode: "PHYSICAL_ASSET",
			creationOperationId: "manual-create-1",
			sourceRefs: [
				{
					kind: "TABLE",
					ref: "预算执行 ODS",
					layer: "ODS",
					role: "PRIMARY",
					sortOrder: 0,
					sourceBindingId: physicalSource.bindingId,
					resolvedVersion: "source-v1",
				},
			],
		};

		const result = await saveModelDraft(draft, {
			ownerId: "owner-1",
			dimensionDefinitions: [],
			models: [],
			implementationCapabilities,
		});

		expect(saveModelDraftOperation).toHaveBeenCalledWith(
			expect.objectContaining({
				create: expect.objectContaining({ idempotencyKey: "manual-create-1", modelType: "FACT" }),
				modelSpec: expect.objectContaining({ sourceRefs: draft.sourceRefs }),
				implementation: expect.objectContaining({ inputMode: "PHYSICAL_ASSET", materialization: "table" }),
			}),
		);
		expect(createModelSpec).not.toHaveBeenCalled();
		expect(updateModelSpec).not.toHaveBeenCalled();
		expect(saveModelImplementation).not.toHaveBeenCalled();
		expect(result).toEqual({ model, implementation });
	});

	it("saves a selected warehouse source through logical and implementation bindings", async () => {
		const base = canonicalFactView();
		const savedModel = { ...base, revision: 2, checksum: "d".repeat(64) };
		vi.mocked(updateModelSpec).mockResolvedValue(savedModel);
		vi.mocked(saveModelImplementation).mockResolvedValue(
			generatedImplementation(savedModel, {
				inputMode: "PHYSICAL_ASSET",
				inputs: [{ sourceBindingId: physicalSource.bindingId, resolvedVersion: "source-v1" }],
			}),
		);
		const draft = {
			...modelDraftFromView(base),
			physicalName: "dwd_budget_execution",
			implementationInputMode: "PHYSICAL_ASSET" as const,
			sourceRefs: [
				{
					kind: "TABLE" as const,
					ref: "预算执行 ODS",
					layer: "ODS" as const,
					role: "PRIMARY" as const,
					alias: null,
					joinType: null,
					joinExpression: null,
					sortOrder: 0,
					sourceBindingId: physicalSource.bindingId,
					resolvedVersion: "source-v1",
				},
			],
		};

		await saveModelDraft(draft, { ownerId: "owner-1", dimensionDefinitions: [], models: [base] });

		expect(updateModelSpec).toHaveBeenCalledWith(
			base,
			expect.objectContaining({
				sourceRefs: draft.sourceRefs,
				factShape: "TRANSACTION",
				timeSemantics: { type: "EVENT_TIME", fields: ["event_time"] },
			}),
		);
		expect(saveModelImplementation).toHaveBeenCalledWith(
			savedModel,
			null,
			expect.objectContaining({
				inputMode: "PHYSICAL_ASSET",
				inputs: [{ sourceBindingId: physicalSource.bindingId, resolvedVersion: "source-v1" }],
			}),
		);
	});

	it("rejects leftover logical sources instead of silently dropping them from a single-input implementation", async () => {
		const owner = canonicalFactView();
		const upstream = { ...canonicalFactView(), id: "30000000-0000-0000-0000-000000000002" };
		const savedModel = { ...owner, revision: 2, checksum: "d".repeat(64) };
		vi.mocked(updateModelSpec).mockResolvedValue(savedModel);
		vi.mocked(saveModelImplementation).mockResolvedValue(
			generatedImplementation(savedModel, {
				inputMode: "UPSTREAM_MODEL",
				inputs: [{ modelSpecId: upstream.id, revision: upstream.revision, checksum: upstream.checksum }],
			}),
		);
		const draft = {
			...modelDraftFromView(owner),
			physicalName: "dwd_budget_execution",
			implementationInputMode: "UPSTREAM_MODEL" as const,
			sourceRefs: [
				{
					kind: "TABLE" as const,
					ref: "预算执行 ODS",
					layer: "ODS" as const,
					role: "PRIMARY" as const,
					sortOrder: 0,
					sourceBindingId: physicalSource.bindingId,
					resolvedVersion: "source-v1",
				},
			],
			dependsOn: [{ modelSpecId: upstream.id, revision: upstream.revision }],
		};

		// The dependency snapshot validates all declared sources, including inactive-mode leftovers.
		await expect(
			saveModelDraft(draft, {
				ownerId: "owner-1",
				dimensionDefinitions: [],
				models: [owner, upstream],
			}),
		).rejects.toThrow("多输入模型必须为每个目标字段配置来源字段");
		expect(updateModelSpec).not.toHaveBeenCalled();
		expect(saveModelImplementation).not.toHaveBeenCalled();
	});

	it("saves a revision-pinned upstream model for derived models", async () => {
		const upstream = canonicalFactView();
		const summary = {
			...canonicalFactView(),
			id: "30000000-0000-0000-0000-000000000002",
			modelType: "SUMMARY" as const,
			layer: "DWS" as const,
			warehouseLayerCode: "DWS",
			businessProcessId: null,
			factShape: null,
			timeSemantics: null,
			name: "项目预算汇总",
		};
		const savedModel = { ...summary, revision: 2, checksum: "e".repeat(64) };
		vi.mocked(updateModelSpec).mockResolvedValue(savedModel);
		vi.mocked(saveModelImplementation).mockResolvedValue(
			generatedImplementation(savedModel, {
				inputMode: "UPSTREAM_MODEL",
				inputs: [{ modelSpecId: upstream.id, revision: upstream.revision, checksum: upstream.checksum }],
			}),
		);
		const draft = {
			...modelDraftFromView(summary),
			physicalName: "dws_project_budget",
			implementationInputMode: "UPSTREAM_MODEL" as const,
			dependsOn: [{ modelSpecId: upstream.id, revision: upstream.revision }],
		};

		await saveModelDraft(draft, { ownerId: "owner-1", dimensionDefinitions: [], models: [upstream, summary] });

		expect(updateModelSpec).toHaveBeenCalledWith(
			summary,
			expect.objectContaining({ dependsOn: [{ modelSpecId: upstream.id, revision: upstream.revision }] }),
		);
		expect(saveModelImplementation).toHaveBeenCalledWith(
			savedModel,
			null,
			expect.objectContaining({
				inputMode: "UPSTREAM_MODEL",
				inputs: [{ modelSpecId: upstream.id, revision: upstream.revision, checksum: upstream.checksum }],
			}),
		);
	});

	it("persists the APPLICATION data mart and subject domain selected in the editor", async () => {
		const upstream = canonicalFactView();
		const application = {
			...canonicalFactView(),
			id: "30000000-0000-0000-0000-000000000003",
			modelType: "APPLICATION" as const,
			layer: "ADS" as const,
			warehouseLayerCode: "ADS",
			businessProcessId: null,
			factShape: null,
			timeSemantics: null,
			name: "技术状态月度指标",
			implementationMode: "DBT_MANAGED" as const,
			consumptionScenario: "技术状态月度看板",
			sourceRefs: [],
			dependsOn: [{ modelSpecId: upstream.id, revision: upstream.revision }],
		};
		const savedModel = { ...application, revision: 2, checksum: "f".repeat(64) };
		vi.mocked(updateModelSpec).mockResolvedValue(savedModel);
		const draft = {
			...modelDraftFromView(application),
			physicalName: "ads_tech_state_monthly",
			dataMartId: "70000000-0000-0000-0000-000000000001",
			subjectDomainId: "80000000-0000-0000-0000-000000000001",
		};

		await saveModelDraft(draft, { ownerId: "owner-1", dimensionDefinitions: [], models: [upstream, application] });

		expect(updateModelSpec).toHaveBeenCalledWith(
			application,
			expect.objectContaining({
				dataMartId: "70000000-0000-0000-0000-000000000001",
				subjectDomainId: "80000000-0000-0000-0000-000000000001",
			}),
		);
		expect(saveModelImplementation).not.toHaveBeenCalled();
	});

	it("creates a DBT-managed dimension model without inventing a designer implementation", async () => {
		const initial = {
			...canonicalFactView(),
			modelType: "DIMENSION" as const,
			businessProcessId: null,
			factShape: null,
			timeSemantics: null,
			name: "节点完成状态维度表",
			description: "统一节点完成状态口径",
			dimensionDefinitionRef: { dimensionDefinitionId: definitionView.id, revision: definitionView.revision },
			dimensionProfile: {
				dimensionCode: null,
				hierarchies: [],
				scdPolicy: { type: "TYPE1" as const },
				reuseScope: null,
			},
		};
		const saved = {
			...initial,
			implementationMode: "DBT_MANAGED" as const,
			revision: 2,
			checksum: "f".repeat(64),
		};
		vi.mocked(saveModelDraftOperation).mockResolvedValue({ model: saved, implementation: null, replayed: false });
		const draft: ModelSpecDraft = {
			...(validDimensionDraft() as ModelSpecDraft),
			planId: initial.planId,
			domainId: initial.domainId,
			name: initial.name,
			description: initial.description,
			implementationMode: "DBT_MANAGED",
			implementationInputMode: "PHYSICAL_ASSET",
			generationStrategyType: "",
			sourceRefs: [
				{
					kind: "TABLE",
					ref: physicalSource.displayName || physicalSource.sourceId,
					layer: "ODS",
					role: "PRIMARY",
					alias: null,
					joinType: null,
					joinExpression: null,
					sortOrder: 0,
					sourceBindingId: physicalSource.bindingId,
					resolvedVersion: physicalSource.resolvedVersion || physicalSource.confirmedVersion,
				},
			],
		};

		const result = await saveModelDraft(draft, {
			ownerId: "owner-1",
			dimensionDefinitions: [definitionView],
			models: [],
			implementationCapabilities,
		});

		expect(saveModelDraftOperation).toHaveBeenCalledWith(
			expect.objectContaining({
				create: expect.objectContaining({
					modelType: "DIMENSION",
					dimensionDefinitionRef: { dimensionDefinitionId: definitionView.id, revision: definitionView.revision },
				}),
				modelSpec: expect.objectContaining({ implementationMode: "DBT_MANAGED", sourceRefs: draft.sourceRefs }),
				implementation: null,
			}),
		);
		expect(createModelSpec).not.toHaveBeenCalled();
		expect(updateModelSpec).not.toHaveBeenCalled();
		expect(saveModelImplementation).not.toHaveBeenCalled();
		expect(result).toEqual({ model: saved, implementation: null });
	});
});


describe("sprint-104 draft configuration round trips", () => {
	it("restores new TYPE2 bindings and complete hierarchy without aliasing the baseline or snapshot", () => {
		const model = {
			...canonicalFactView(),
			modelType: "DIMENSION" as const,
			dimensionProfile: { hierarchies: [], scdPolicy: { type: "TYPE1" as const } },
		};
		const profile = {
			hierarchies: [{ code: "region", name: "地区", levels: [{ fieldName: "record_id", order: 1 }] }],
			scdPolicy: {
				type: "TYPE2" as const,
				effectiveFromField: "valid_from",
				effectiveToField: "valid_to",
				currentFlagField: "is_current",
			},
		};
		let snapshot = { ...modelDraftToUpdateCommand(modelDraftFromView(model)), dimensionProfile: profile };
		for (let iteration = 0; iteration < 2; iteration += 1) {
			const restored = modelDraftFromAuthoringSnapshot(model, null, snapshot);
			expect(restored.dimensionProfile).toEqual(profile);
			expect(restored.dimensionProfile).not.toBe(snapshot.dimensionProfile);
			expect(restored.dimensionProfile?.hierarchies[0].levels).not.toBe(
				snapshot.dimensionProfile.hierarchies[0].levels,
			);
			snapshot = modelDraftToUpdateCommand(restored) as typeof snapshot;
			expect(snapshot.dimensionProfile).toEqual(profile);
		}
		expect(model.dimensionProfile.scdPolicy.type).toBe("TYPE1");
		const cleared = modelDraftFromAuthoringSnapshot(model, null, { ...snapshot, dimensionProfile: null });
		expect(modelDraftToUpdateCommand(cleared).dimensionProfile).toBeNull();
		const none = { ...modelDraftFromAuthoringSnapshot(model, null, snapshot), scdType: "NONE" as const };
		expect(modelDraftToUpdateCommand(none).dimensionProfile).toEqual({
			hierarchies: profile.hierarchies,
			scdPolicy: { type: "NONE" },
		});
	});

	it("honors FULL and empty partition settings for visual and retained DBT_MANAGED implementations", () => {
		const model = {
			...canonicalFactView(),
			implementationPolicy: { loadStrategy: "INCREMENTAL" as const, partitionFields: ["event_time"] },
		};
		const visual = generatedImplementation(model, {
			settings: { targetPhysicalName: "current_table", loadStrategy: "FULL", partitionFields: [] },
		});
		const retained = {
			...visual,
			ownership: "DBT_MANAGED" as const,
			inputs: [
				{ generatorType: "DBT_MANAGED", config: { visualImplementation: { ...visual, idempotencyKey: "retained" } } },
			],
		};
		for (const implementation of [visual, retained]) {
			const draft = modelDraftFromView(model, implementation);
			expect(draft).toMatchObject({ loadStrategy: "FULL", partitionFields: "" });
			expect(normalizeModelDraftImplementation(draft, implementationCapabilities)).toMatchObject({
				loadStrategy: "FULL",
				partitionFields: "",
			});
		}
		const missing = modelDraftFromView(model, { ...visual, settings: { targetPhysicalName: "current_table" } });
		expect(missing).toMatchObject({ loadStrategy: "INCREMENTAL", partitionFields: "event_time" });
		const unsupported = { ...missing, loadStrategy: "SNAPSHOT" as const, materialization: "snapshot" };
		expect(normalizeModelDraftImplementation(unsupported, implementationCapabilities)).toEqual(unsupported);
		const invalid = modelDraftFromView(model, { ...visual, settings: { loadStrategy: null, partitionFields: null } });
		expect(invalid.loadStrategy).not.toBe("INCREMENTAL");
		expect(invalid.partitionFields).not.toBe("event_time");
	});

	it("does not lock editable context behind release completeness", () => {
		const draft = modelDraftFromView(canonicalFactView());
		expect(validateModelDraftInput({ ...draft, businessProcessId: "" })).not.toHaveProperty("businessProcessId");
		const application = { ...draft, createKind: "application" as const, dataMartId: "", subjectDomainId: "" };
		expect(validateModelDraftInput(application)).not.toHaveProperty("dataMartId");
		expect(validateModelDraftInput(application)).not.toHaveProperty("subjectDomainId");
	});
});
