import { createCommandForDraft } from "./modelDraftCreateCommand";
import { copyDimensionProfile, dimensionProfileForSave, implementationConfiguration } from "./modelDraftConfiguration";
import { listDataMarts } from "@/api/dataMartApi";
import type { ModelAuthoringSnapshot, ModelAuthoringSnapshotInput } from "@/api/dbtImplementationDraftApi";
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
import { listModelFieldStandardOptions, type ModelFieldStandardOption } from "@/api/modelingStandardsApi";
import {
	type ModelDraftOperationCommand,
	getModelLifecycle,
	listModelSpecs,
	saveModelDraftOperation,
	updateModelSpec,
} from "@/api/modelSpecApi";
import catalogDomainService, { type CatalogDomain } from "@/api/services/catalogDomainService";
import {
	collectCurrentWarehousePlanSources,
	resolveDefaultModelingContextId,
} from "@/api/services/modelingImportContextService";
import { listSubjectDomains } from "@/api/subjectDomainApi";
import { listWarehouseLayers, type WarehouseLayerView } from "@/api/warehouseLayerApi";
import type { WarehousePlanSourceBindingView } from "@/api/warehousePlanApi";
import type { DataMartView } from "@/features/modeling/contracts/dataMartContract";
import type {
	DimensionDefinitionAttribute,
	DimensionDefinitionReuseScope,
	DimensionDefinitionView,
} from "@/features/modeling/contracts/dimensionDefinitionContract";
import {
	modelImplementationValidationMessage,
	type ModelImplementationCapabilities,
	type GeneratedImplementationInput,
	type ModelImplementationAggregation,
	type ModelImplementationCastType,
	type ModelImplementationFieldMapping,
	type ModelImplementationFilter,
	type ModelImplementationInput,
	type ModelImplementationInputMode,
	type ModelImplementationJoin,
	type ModelImplementationView,
	type ModelImplementationWriteCommand,
} from "@/features/modeling/contracts/modelImplementationContract";
import {
	type CanonicalModelSpecView,
	type ModelSpecFactShape,
	type ModelSpecDimensionProfile,
	type ModelSpecField,
	type ModelSpecImplementationMode,
	type ModelSpecLayer,
	type ModelSpecLoadStrategy,
	type ModelSpecRevisionRef,
	type ModelSpecScdType,
	type ModelSpecSourceRef,
	type ModelSpecStandardBinding,
	type ModelSpecTimeSemanticsType,
	type ModelSpecType,
	type ModelSpecView,
	type UpdateModelSpecCommand,
	validateModelSpecUpdate,
} from "@/features/modeling/contracts/modelSpecV2Contract";
import type { SubjectDomainView } from "@/features/modeling/contracts/subjectDomainContract";

export type ModelCreateKind = "dimension" | "dimension-table" | "source" | "fact" | "summary" | "application";
export type ModelSpecCreateKind = Exclude<ModelCreateKind, "dimension">;

export const MODEL_KIND_CONFIG: Record<
	ModelCreateKind,
	{ label: string; modelType: ModelSpecType; layer: ModelSpecLayer }
> = {
	source: { label: "贴源表", modelType: "SOURCE", layer: "ODS" },
	dimension: { label: "维度", modelType: "DIMENSION", layer: "DWD" },
	"dimension-table": { label: "维度表", modelType: "DIMENSION", layer: "DWD" },
	fact: { label: "明细表", modelType: "FACT", layer: "DWD" },
	summary: { label: "汇总表", modelType: "SUMMARY", layer: "DWS" },
	application: { label: "应用表", modelType: "APPLICATION", layer: "ADS" },
};

export type ModelWorkbenchContext = {
	planId: string;
	domains: CatalogDomain[];
	models: ModelSpecView[];
	dimensions: DimensionDefinitionView[];
	standards: ModelFieldStandardOption[];
	dataMarts: DataMartView[];
	subjectDomains: SubjectDomainView[];
	warehouseLayers: WarehouseLayerView[];
	sources: WarehousePlanSourceBindingView[];
	implementationCapabilities: ModelImplementationCapabilities;
};

export type ConceptDimensionDraft = {
	createKind: "dimension";
	base: null;
	definitionBase: DimensionDefinitionView | null;
	idempotencyKey: string;
	domainId: string;
	name: string;
	description: string;
	reuseScope: DimensionDefinitionReuseScope;
	attributes: DimensionDefinitionAttribute[];
};

export type ModelSpecDraft = {
	createKind: ModelSpecCreateKind;
	base: ModelSpecView | null;
	planId: string;
	domainId: string;
	name: string;
	description: string;
	physicalName: string;
	materialization: string;
	grainStatement: string;
	businessProcessId?: string;
	fields: ModelSpecField[];
	partitionFields: string;
	loadStrategy: ModelSpecLoadStrategy;
	scdType: ModelSpecScdType;
	dimensionProfile?: ModelSpecDimensionProfile | null;
	reuseScope: DimensionDefinitionReuseScope;
	dimensionDefinitionId: string;
	standardBindings: ModelSpecStandardBinding[];
	warehouseLayerCode: string;
	implementationMode: ModelSpecImplementationMode;
	implementationBase: ModelImplementationView | null;
	implementationInputMode: ModelImplementationInputMode | "";
	generationStrategyType: "" | "DATE_DIMENSION" | "SCHEMA_ONLY";
	implementationIdempotencyKey: string;
	creationOperationId: string;
	fieldMappings: ModelImplementationFieldMapping[];
	casts: Record<string, ModelImplementationCastType>;
	filters: ModelImplementationFilter[];
	deduplicateBy: string[];
	joins: ModelImplementationJoin[];
	groupBy: string[];
	aggregations: ModelImplementationAggregation[];
	sourceRefs: ModelSpecSourceRef[];
	dependsOn: ModelSpecRevisionRef[];
	dimensionRefs: ModelSpecRevisionRef[];
	factShape: ModelSpecFactShape | "";
	timeSemanticsType: ModelSpecTimeSemanticsType | "";
	timeSemanticsFields: string[];
	consumptionScenario: string;
	dataMartId?: string;
	subjectDomainId?: string;
};

export type ModelDraft = ConceptDimensionDraft | ModelSpecDraft;

export const isConceptDimensionDraft = (draft: ModelDraft): draft is ConceptDimensionDraft =>
	draft.createKind === "dimension";

export const isModelSpecDraft = (draft: ModelDraft): draft is ModelSpecDraft => draft.createKind !== "dimension";

export const isDimensionTableDraft = (draft: ModelDraft): draft is ModelSpecDraft =>
	draft.createKind === "dimension-table";

export type ModelSaveContext = {
	ownerId: string;
	dimensionDefinitions: DimensionDefinitionView[];
	models?: ModelSpecView[];
	implementationCapabilities?: ModelImplementationCapabilities;
};

export type ModelDraftSaveResult = {
	model: CanonicalModelSpecView;
	implementation: ModelImplementationView | null;
};

export class ModelDraftPartialSaveError extends Error {
	readonly savedModel: CanonicalModelSpecView;
	readonly failure: unknown;

	constructor(savedModel: CanonicalModelSpecView, failure: unknown) {
		super(failure instanceof Error && failure.message.trim() ? failure.message : "模型实现保存失败");
		this.name = "ModelDraftPartialSaveError";
		this.savedModel = savedModel;
		this.failure = failure;
	}
}

export type ModelDraftErrorKey =
	| "domainId"
	| "dimensionDefinitionId"
	| "physicalName"
	| "partitionFields"
	| "name"
	| "description"
	| "businessProcessId"
	| "grainStatement"
	| "fields"
	| "implementationInputMode"
	| "transformations"
	| "factShape"
	| "timeSemantics"
	| "consumptionScenario"
	| "dataMartId"
	| "subjectDomainId";

export type ModelDraftValidationErrors = Partial<Record<ModelDraftErrorKey, string>>;

const modelKind = (model: ModelSpecView): ModelSpecCreateKind =>
	model.modelType === "SOURCE" ? "source" : model.modelType === "DIMENSION"
		? "dimension-table"
		: model.modelType === "FACT"
			? "fact"
			: model.modelType === "SUMMARY"
				? "summary"
				: "application";

export function emptyModelDraft(kind: ModelCreateKind, context: ModelWorkbenchContext): ModelDraft {
	if (kind === "dimension") {
		return {
			createKind: "dimension",
			base: null,
			definitionBase: null,
			idempotencyKey: crypto.randomUUID(),
			domainId: context.domains.find((item) => Boolean(item.parentCode))?.id || "",
			name: "",
			description: "",
			reuseScope: "DOMAIN",
			attributes: [],
		};
	}
	const config = MODEL_KIND_CONFIG[kind];
	const defaultDomainId = context.domains.find((item) => Boolean(item.parentCode))?.id || "";
	const implementationInputMode = context.implementationCapabilities.inputModesByModelType[config.modelType]?.[0] || "";
	const loadStrategy = context.implementationCapabilities.loadStrategies[0];
	const materialization = loadStrategy
		? context.implementationCapabilities.materializationsByLoadStrategy[loadStrategy]?.[0]
		: undefined;
	if ((!implementationInputMode && kind !== "source") || !loadStrategy || !materialization) {
		throw new Error("服务端未提供可用的数据实现能力，请联系管理员检查建模配置");
	}
	return {
		createKind: kind,
		base: null,
		planId: context.planId,
		domainId: defaultDomainId,
		name: "",
		description: "",
		physicalName: "",
		materialization,
		grainStatement: "",
		businessProcessId: "",
		fields: [],
		partitionFields: "",
		loadStrategy,
		scdType: config.modelType === "DIMENSION" ? "TYPE1" : "NONE",
		reuseScope: "DOMAIN",
		dimensionDefinitionId: "",
		standardBindings: [],
		warehouseLayerCode: config.layer,
		implementationMode: "DESIGNER_GENERATED",
		implementationBase: null,
		implementationInputMode,
		generationStrategyType: kind === "source" ? "SCHEMA_ONLY" : "",
		implementationIdempotencyKey: crypto.randomUUID(),
		creationOperationId: crypto.randomUUID(),
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
		dataMartId: "",
		subjectDomainId: "",
	};
}

export function conceptDimensionDraftFromView(definition: DimensionDefinitionView): ConceptDimensionDraft {
	return {
		createKind: "dimension",
		base: null,
		definitionBase: definition,
		idempotencyKey: crypto.randomUUID(),
		domainId: definition.domainId,
		name: definition.name,
		description: definition.definition,
		reuseScope: definition.reuseScope,
		attributes: definition.attributes.map((attribute) => ({ ...attribute })),
	};
}

const implementationSetting = (implementation: ModelImplementationView | null, key: string): unknown =>
	implementation?.settings?.[key];

const implementationText = (implementation: ModelImplementationView | null, key: string): string => {
	const value = implementationSetting(implementation, key);
	return typeof value === "string" ? value : "";
};

const implementationStringList = (implementation: ModelImplementationView | null, key: string): string[] => {
	const value = implementationSetting(implementation, key);
	return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
};

const implementationObject = (implementation: ModelImplementationView | null, key: string): Record<string, unknown> => {
	const value = implementationSetting(implementation, key);
	return value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : {};
};

const implementationObjectList = (
	implementation: ModelImplementationView | null,
	key: string,
): Record<string, unknown>[] => {
	const value = implementationSetting(implementation, key);
	return Array.isArray(value)
		? value.filter(
				(item): item is Record<string, unknown> => Boolean(item) && typeof item === "object" && !Array.isArray(item),
			)
		: [];
};

const implementationCasts = (
	implementation: ModelImplementationView | null,
): Record<string, ModelImplementationCastType> =>
	Object.fromEntries(
		Object.entries(implementationObject(implementation, "casts")).filter(
			(entry): entry is [string, ModelImplementationCastType] =>
				typeof entry[1] === "string" &&
				["string", "integer", "bigint", "decimal", "date", "timestamp", "boolean"].includes(entry[1]),
		),
	);

const implementationFilters = (implementation: ModelImplementationView | null): ModelImplementationFilter[] =>
	implementationObjectList(implementation, "filters").flatMap((filter) =>
		typeof filter.field === "string" &&
		typeof filter.operator === "string" &&
		typeof filter.valueType === "string" &&
		"value" in filter
			? [filter as ModelImplementationFilter]
			: [],
	);

const implementationJoins = (implementation: ModelImplementationView | null): ModelImplementationJoin[] =>
	implementationObjectList(implementation, "joins").flatMap((join) =>
		typeof join.inputIndex === "number" &&
		(join.type === "INNER" || join.type === "LEFT") &&
		typeof join.leftField === "string" &&
		typeof join.rightField === "string"
			? [join as ModelImplementationJoin]
			: [],
	);

const implementationAggregations = (implementation: ModelImplementationView | null): ModelImplementationAggregation[] =>
	implementationObjectList(implementation, "aggregations").flatMap((aggregation) =>
		typeof aggregation.targetField === "string" &&
		typeof aggregation.function === "string" &&
		typeof aggregation.sourceField === "string" &&
		typeof aggregation.distinct === "boolean"
			? [aggregation as ModelImplementationAggregation]
			: [],
	);

const generatedInput = (implementation: ModelImplementationView | null): GeneratedImplementationInput | null => {
	if (implementation?.inputMode !== "GENERATED") return null;
	const input = implementation.inputs[0];
	return input && "generatorType" in input ? input : null;
};

const isModelImplementationWriteCommand = (value: unknown): value is ModelImplementationWriteCommand => {
	if (!value || typeof value !== "object" || Array.isArray(value)) return false;
	const candidate = value as Partial<ModelImplementationWriteCommand>;
	return (
		typeof candidate.projectKey === "string" &&
		typeof candidate.dbtUniqueId === "string" &&
		(candidate.inputMode === "PHYSICAL_ASSET" ||
			candidate.inputMode === "UPSTREAM_MODEL" ||
			candidate.inputMode === "GENERATED") &&
		Array.isArray(candidate.inputs) &&
		typeof candidate.materialization === "string" &&
		typeof candidate.idempotencyKey === "string"
	);
};

const retainedVisualImplementation = (
	implementation: ModelImplementationView | null,
): ModelImplementationWriteCommand | null => {
	if (!implementation || implementation.ownership !== "DBT_MANAGED") return null;
	for (const input of implementation.inputs) {
		if (!("generatorType" in input) || !input.config) continue;
		const retained = input.config.visualImplementation;
		if (isModelImplementationWriteCommand(retained)) return retained;
	}
	return null;
};

const implementationViewFromCommand = (
	model: ModelSpecView,
	implementation: ModelImplementationView | null,
	command: ModelImplementationWriteCommand,
): ModelImplementationView => ({
	id: implementation?.id || `authoring-visual:${model.id}`,
	modelSpecId: model.id,
	planId: model.planId || command.projectKey,
	revision: model.revision,
	modelChecksum: model.checksum,
	ownership: command.ownership,
	projectKey: command.projectKey,
	dbtUniqueId: command.dbtUniqueId,
	status: implementation?.status || "DRAFT",
	implementationRevision: implementation?.implementationRevision || 1,
	implementationChecksum: implementation?.implementationChecksum || model.checksum,
	inputMode: command.inputMode,
	inputs: command.inputs,
	fieldMappings: command.fieldMappings || [],
	settings: command.settings || {},
	materialization: command.materialization,
});

const structuredImplementationView = (
	model: ModelSpecView,
	implementation: ModelImplementationView | null,
): ModelImplementationView | null => {
	if (!implementation) return null;
	if (implementation.ownership === "DESIGNER_GENERATED") return implementation;
	const retained = retainedVisualImplementation(implementation);
	return retained ? implementationViewFromCommand(model, implementation, retained) : null;
};

export function modelDraftFromView(
	model: ModelSpecView,
	implementation: ModelImplementationView | null = null,
): ModelSpecDraft {
	const structuredImplementation = structuredImplementationView(model, implementation);
	// Restore execution metadata without treating imported SQL as a visual transformation.
	const executionImplementation = structuredImplementation || implementation;
	const configuration = implementationConfiguration(executionImplementation, model.implementationPolicy);
	const generationStrategyType =
		generatedInput(structuredImplementation)?.generatorType || model.generationStrategy?.type || "";
	const logicalInputMode = model.sourceRefs.length
		? "PHYSICAL_ASSET"
		: model.dependsOn.length
			? "UPSTREAM_MODEL"
			: null;
	return {
		createKind: modelKind(model),
		base: model,
		planId: model.planId || "",
		domainId: model.domainId || "",
		name: model.name,
		description: model.description || "",
		physicalName:
			implementationText(executionImplementation, "targetPhysicalName") ||
			model.implementationPolicy?.physicalName ||
			"",
		materialization: executionImplementation
			? executionImplementation.materialization
			: model.materialization || "table",
		grainStatement: model.grain?.statement || "",
		businessProcessId: model.businessProcessId || "",
		fields: model.fields.map((field) => ({ ...field })),
		...configuration,
		scdType: model.dimensionProfile?.scdPolicy.type || "NONE",
		dimensionProfile: copyDimensionProfile(model.dimensionProfile),
		reuseScope: model.dimensionProfile?.reuseScope === "TENANT" ? "TENANT" : "DOMAIN",
		dimensionDefinitionId: model.dimensionDefinitionRef?.dimensionDefinitionId || "",
		standardBindings: model.standardBindings.map((binding) => ({ ...binding })),
		warehouseLayerCode: model.warehouseLayerCode || model.layer,
		implementationMode: model.implementationMode,
		implementationBase: implementation,
		implementationInputMode:
			implementation?.ownership === "DBT_MANAGED" && !structuredImplementation
				? ""
				: structuredImplementation?.inputMode ||
					logicalInputMode ||
					(generationStrategyType === "DATE_DIMENSION"
						? "GENERATED"
						: model.modelType === "SUMMARY" || model.modelType === "APPLICATION"
							? "UPSTREAM_MODEL"
							: ""),
		generationStrategyType: generationStrategyType === "DATE_DIMENSION" || generationStrategyType === "SCHEMA_ONLY" ? generationStrategyType : "",
		implementationIdempotencyKey: crypto.randomUUID(),
		creationOperationId: crypto.randomUUID(),
		fieldMappings: (structuredImplementation?.fieldMappings || []).map((mapping) => ({ ...mapping })),
		casts: implementationCasts(structuredImplementation),
		filters: implementationFilters(structuredImplementation),
		deduplicateBy: implementationStringList(structuredImplementation, "deduplicateBy"),
		joins: implementationJoins(structuredImplementation),
		groupBy: implementationStringList(structuredImplementation, "groupBy"),
		aggregations: implementationAggregations(structuredImplementation),
		sourceRefs: model.compatibilityMode === "CANONICAL" ? model.sourceRefs.map((source) => ({ ...source })) : [],
		dependsOn: model.dependsOn.map((dependency) => ({ ...dependency })),
		dimensionRefs: model.dimensionRefs.map((dimension) => ({ ...dimension })),
		factShape: model.factShape || "",
		timeSemanticsType: model.timeSemantics?.type || "",
		timeSemanticsFields: [...(model.timeSemantics?.fields || [])],
		consumptionScenario: model.consumptionScenario || "",
		dataMartId: model.dataMartId || "",
		subjectDomainId: model.subjectDomainId || "",
	};
}

const isVersionedAuthoringSnapshot = (snapshot: ModelAuthoringSnapshotInput): snapshot is ModelAuthoringSnapshot =>
	"modelSpec" in snapshot && snapshot.schemaVersion === 1;

export function modelDraftFromAuthoringSnapshot(
	model: ModelSpecView,
	implementation: ModelImplementationView | null,
	snapshot: ModelAuthoringSnapshotInput,
): ModelSpecDraft {
	const versioned = isVersionedAuthoringSnapshot(snapshot);
	const modelSnapshot = versioned ? snapshot.modelSpec : snapshot;
	const visualSnapshot = versioned ? snapshot.visualImplementation || null : null;
	const visualView = visualSnapshot
		? implementationViewFromCommand(model, implementation, visualSnapshot)
		: versioned
			? null
			: structuredImplementationView(model, implementation);
	const base = modelDraftFromView(model, visualView);
	return {
		...base,
		planId: modelSnapshot.planId,
		domainId: modelSnapshot.domainId,
		name: modelSnapshot.name,
		description: modelSnapshot.description || "",
		materialization: modelSnapshot.materialization || base.materialization,
		grainStatement: modelSnapshot.grain?.statement || "",
		businessProcessId: modelSnapshot.businessProcessId || "",
		fields: (modelSnapshot.fields || []).map((field) => ({ ...field })),
		scdType:
			(modelSnapshot.dimensionProfile === undefined ? base.dimensionProfile : modelSnapshot.dimensionProfile)?.scdPolicy
				.type || "NONE",
		dimensionProfile: copyDimensionProfile(
			modelSnapshot.dimensionProfile === undefined ? base.dimensionProfile : modelSnapshot.dimensionProfile,
		),
		standardBindings: (modelSnapshot.standardBindings || []).map((binding) => ({ ...binding })),
		warehouseLayerCode: modelSnapshot.warehouseLayerCode || modelSnapshot.layer,
		implementationMode: modelSnapshot.implementationMode,
		generationStrategyType:
			modelSnapshot.generationStrategy?.type === "DATE_DIMENSION" ? "DATE_DIMENSION" : base.generationStrategyType,
		sourceRefs: (modelSnapshot.sourceRefs || []).map((source) => ({ ...source })),
		dependsOn: (modelSnapshot.dependsOn || []).map((dependency) => ({ ...dependency })),
		dimensionRefs: (modelSnapshot.dimensionRefs || []).map((dimension) => ({ ...dimension })),
		factShape: modelSnapshot.factShape || "",
		timeSemanticsType: modelSnapshot.timeSemantics?.type || "",
		timeSemanticsFields: [...(modelSnapshot.timeSemantics?.fields || [])],
		consumptionScenario: modelSnapshot.consumptionScenario || "",
		dataMartId: modelSnapshot.dataMartId || "",
		subjectDomainId: modelSnapshot.subjectDomainId || "",
		implementationBase: implementation,
		implementationIdempotencyKey: crypto.randomUUID(),
	};
}

export async function loadModelWorkbenchDraft(model: ModelSpecView): Promise<ModelSpecDraft> {
	const lifecycle = await getModelLifecycle(model.id);
	return modelDraftFromView(model, lifecycle.implementation);
}

export async function loadModelWorkbenchContext(): Promise<ModelWorkbenchContext> {
	const planId = await resolveDefaultModelingContextId();
	const [
		domains,
		models,
		dimensions,
		standards,
		dataMarts,
		subjectDomains,
		warehouseLayers,
		sources,
		implementationCapabilities,
	] = await Promise.all([
		catalogDomainService.list(),
		listModelSpecs(),
		listDimensionDefinitions({ offset: 0, limit: 100 }),
		listModelFieldStandardOptions(),
		listDataMarts({ status: "CURRENT", offset: 0, limit: 100 }),
		listSubjectDomains({ status: "CURRENT", offset: 0, limit: 100 }),
		listWarehouseLayers(),
		planId ? collectCurrentWarehousePlanSources(planId) : Promise.resolve([]),
		getModelImplementationCapabilities(),
	]);
	return {
		planId,
		domains,
		models: models.filter((model) => model.status !== "ARCHIVED"),
		dimensions: dimensions.filter((definition) => definition.status !== "RETIRED"),
		standards,
		dataMarts,
		subjectDomains,
		warehouseLayers,
		sources,
		implementationCapabilities,
	};
}

export function modelSourceRefFromBinding(
	binding: WarehousePlanSourceBindingView,
	sortOrder: number,
): ModelSpecSourceRef | null {
	const resolvedVersion = binding.resolvedVersion?.trim() || binding.confirmedVersion?.trim() || "";
	if (!binding.bindingId.trim() || !resolvedVersion) return null;
	const locatorRef = [
		binding.locator?.connectionId?.trim(),
		binding.locator?.namespace?.trim(),
		binding.locator?.objectName?.trim(),
	]
		.filter(Boolean)
		.join(":");
	const stableRef = binding.sourceId?.trim() || binding.locator?.uniqueId?.trim() || locatorRef || binding.bindingId;
	return {
		kind: binding.sourceType === "DBT_NODE" ? "DBT_MODEL" : binding.sourceType === "EXCEL_FILE" ? "DATASET" : "TABLE",
		ref: stableRef,
		layer: "ODS",
		role: sortOrder === 0 ? "PRIMARY" : "JOINED",
		alias: null,
		joinType: null,
		joinExpression: null,
		sortOrder,
		sourceBindingId: binding.bindingId,
		resolvedVersion,
	};
}

export function reconcileModelDraftSources(
	draft: ModelSpecDraft,
	planId: string,
	sources: WarehousePlanSourceBindingView[],
): ModelSpecDraft {
	const currentSources = sources.filter(
		(source) =>
			source.confirmationStatus === "CONFIRMED" &&
			source.resolutionStatus === "AVAILABLE" &&
			source.freshness === "CURRENT",
	);
	const source =
		draft.implementationInputMode === "PHYSICAL_ASSET" && !draft.sourceRefs.length && currentSources.length === 1
			? modelSourceRefFromBinding(currentSources[0], 0)
			: null;
	return {
		...draft,
		planId: planId.trim() || draft.planId,
		sourceRefs: source ? [source] : draft.sourceRefs,
	};
}

export function applyModelDraftFieldPatch(
	draft: ModelSpecDraft,
	index: number,
	patch: Partial<ModelSpecField>,
): ModelSpecDraft {
	const currentField = draft.fields[index];
	if (!currentField) return draft;
	const oldName = currentField.name;
	const nextField = { ...currentField, ...patch };
	const nextName = nextField.name;
	let timeSemanticsFields =
		oldName === nextName
			? draft.timeSemanticsFields
			: draft.timeSemanticsFields.map((fieldName) => (fieldName === oldName ? nextName : fieldName));
	if (currentField.role === "TIME" && nextField.role !== "TIME") {
		timeSemanticsFields = timeSemanticsFields.filter((fieldName) => fieldName !== nextName);
	} else if (
		nextField.role === "TIME" &&
		nextName.trim() &&
		(currentField.role !== "TIME" || !oldName.trim()) &&
		!timeSemanticsFields.includes(nextName)
	) {
		timeSemanticsFields = [...timeSemanticsFields, nextName];
	}
	return {
		...draft,
		fields: draft.fields.map((field, row) => (row === index ? nextField : field)),
		standardBindings:
			oldName === nextName
				? draft.standardBindings
				: draft.standardBindings.map((binding) =>
						binding.fieldName === oldName ? { ...binding, fieldName: nextName } : binding,
					),
		timeSemanticsFields,
	};
}

/**
 * 维度表可绑定的维度选项：返回数据域下全部未退役定义（DRAFT/CURRENT），
 * 由编辑器区分“现行（可绑定）”与“草稿（需先确认定义）”。
 */
export const loadDimensionDefinitionOptions = (domainId: string) =>
	domainId
		? listDimensionDefinitions({ domainId, offset: 0, limit: 100 }).then((items) =>
				items.filter((definition) => definition.status !== "RETIRED"),
			)
		: Promise.resolve([]);

const isDimensionDraft = (draft: ModelSpecDraft): boolean => draft.createKind === "dimension-table";

export function prepareModelDraftForSave(
	draft: ModelSpecDraft,
	definitions: DimensionDefinitionView[],
): ModelSpecDraft {
	if (!isDimensionDraft(draft) || draft.grainStatement.trim()) return draft;
	const definition = definitions.find((item) => item.id === draft.dimensionDefinitionId);
	const grainName = definition?.name.trim() || draft.name.trim();
	return grainName ? { ...draft, grainStatement: `一个${grainName}一行` } : draft;
}

export function normalizeModelDraftImplementation(
	draft: ModelSpecDraft,
	_capabilities: ModelImplementationCapabilities,
): ModelSpecDraft {
	// Existing explicit choices must reach validation, never be replaced by a default.
	return { ...draft };
}

export function validateConceptDimensionDraftInput(draft: ConceptDimensionDraft): ModelDraftValidationErrors {
	const errors: ModelDraftValidationErrors = {};
	if (!draft.domainId.trim()) errors.domainId = "请选择数据域";
	if (!/[\u4e00-\u9fff]/.test(draft.name.trim())) errors.name = "请填写中文名称";
	return errors;
}

export async function saveDimensionDefinitionDraft(
	draft: ConceptDimensionDraft,
	ownerId: string,
): Promise<DimensionDefinitionView> {
	if (draft.definitionBase?.status === "RETIRED") {
		throw new Error("已退役的维度不能修改。");
	}
	if (!ownerId.trim()) throw new Error("当前登录身份缺少人员 ID，不能创建维度定义");
	const errors = Object.values(validateConceptDimensionDraftInput(draft));
	if (errors.length) throw new Error(`请补齐：${errors.join("、")}`);
	const command = {
		name: draft.name.trim(),
		definition: draft.description.trim() || draft.name.trim(),
		ownerId: ownerId.trim(),
		reuseScope: draft.reuseScope,
		scopeType: "DOMAIN" as const,
		dataMartId: null,
		attributes: normalizedDimensionAttributes(draft.attributes),
		hierarchies: [],
	};
	if (draft.definitionBase) {
		return updateDimensionDefinition(
			{
				id: draft.definitionBase.id,
				revision: draft.definitionBase.revision,
				checksum: draft.definitionBase.checksum,
			},
			command,
		);
	}
	return createDimensionDefinition({
		...command,
		domainId: draft.domainId.trim(),
		idempotencyKey: draft.idempotencyKey,
	});
}

const normalizedDimensionAttributes = (attributes: DimensionDefinitionAttribute[]): DimensionDefinitionAttribute[] =>
	attributes
		.map((attribute, index) => ({
			code: attribute.code.trim(),
			name: attribute.name.trim(),
			definition: attribute.definition?.trim() || "",
			primaryKey: attribute.primaryKey === true,
			standardRef: attribute.standardRef?.trim() || null,
			standardVersion: attribute.standardVersion?.trim() || null,
			order: index + 1,
		}))
		.filter((attribute) => Boolean(attribute.code) && Boolean(attribute.name));

export async function confirmDimensionDefinitionDraft(draft: ConceptDimensionDraft): Promise<ConceptDimensionDraft> {
	const definition = draft.definitionBase;
	if (!definition) throw new Error("请先保存维度草稿");
	if (definition.status === "CURRENT") return draft;
	if (definition.status !== "DRAFT") throw new Error("只有草稿状态的维度可以确认");
	return conceptDimensionDraftFromView(await confirmDimensionDefinition(definition));
}

export function validateModelDraftInput(
	draft: ModelSpecDraft,
	capabilities?: ModelImplementationCapabilities,
): ModelDraftValidationErrors {
	const errors: ModelDraftValidationErrors = {};
	if (!draft.domainId.trim()) errors.domainId = "请选择数据域";
	if (draft.createKind === "dimension-table" && !draft.dimensionDefinitionId.trim()) {
		errors.dimensionDefinitionId = "请选择一个维度";
	}
	if (isDimensionDraft(draft)) {
		if (!/[\u4e00-\u9fff]/.test(draft.name.trim())) errors.name = "请填写中文表名";
		if (!/^[a-z][a-z0-9_]*$/.test(draft.physicalName.trim())) {
			errors.physicalName = "产出表英文名只能使用小写字母、数字和下划线，且必须以字母开头";
		}
		if (!draft.description.trim()) errors.description = "请填写维度定义（描述）";
	} else {
		if (!draft.grainStatement.trim()) errors.grainStatement = "请填写模型粒度";
		if (draft.implementationInputMode && !/^[a-z][a-z0-9_]*$/.test(draft.physicalName.trim())) {
			errors.physicalName = "产出表英文名只能使用小写字母、数字和下划线，且必须以字母开头";
		}
	}
	const allowedInputModes = capabilities?.inputModesByModelType[MODEL_KIND_CONFIG[draft.createKind].modelType];
	if (
		draft.implementationInputMode &&
		allowedInputModes &&
		!allowedInputModes.includes(draft.implementationInputMode)
	) {
		errors.implementationInputMode = "当前模型类型不支持所选数据来源方式，请重新选择";
	} else if (!draft.implementationInputMode && !draft.base) {
		errors.implementationInputMode = "请选择数据来源方式";
	} else if (draft.implementationInputMode === "PHYSICAL_ASSET" && !draft.sourceRefs.length) {
		errors.implementationInputMode = "请至少选择一张已确认且当前有效的输入源表";
	} else if (draft.implementationInputMode === "UPSTREAM_MODEL" && !draft.dependsOn.length) {
		errors.implementationInputMode = "请至少选择一个当前修订的上游模型";
	} else if (
		draft.implementationInputMode === "GENERATED" &&
		draft.generationStrategyType !== "SCHEMA_ONLY" &&
		(draft.createKind !== "dimension-table" || draft.generationStrategyType !== "DATE_DIMENSION")
	) {
		errors.implementationInputMode = "当前模型不支持所选生成器";
	}
	if (draft.implementationInputMode === "GENERATED" && draft.generationStrategyType === "SCHEMA_ONLY" &&
		(draft.materialization !== "table" || draft.loadStrategy !== "FULL" || draft.partitionFields.trim() ||
		 draft.fieldMappings.length || Object.keys(draft.casts).length || draft.filters.length ||
		 draft.deduplicateBy.length || draft.joins.length || draft.groupBy.length || draft.aggregations.length)) {
		errors.implementationInputMode = "仅创建表结构需要普通表、全量策略和空的映射、转换及分区配置，请先清除不兼容配置";
	}
	const partitionFields = parsePartitionFields(draft.partitionFields);
	const modelFields = new Set(draft.fields.map((field) => field.name.trim()).filter(Boolean));
	if (draft.implementationInputMode && partitionFields.some((field) => !/^[A-Za-z_][A-Za-z0-9_]*$/.test(field))) {
		errors.partitionFields = "分区字段必须使用字段英文名，多个字段用逗号分隔";
	} else if (draft.implementationInputMode && new Set(partitionFields).size !== partitionFields.length) {
		errors.partitionFields = "分区字段不能重复";
	} else if (draft.implementationInputMode && partitionFields.some((field) => !modelFields.has(field))) {
		errors.partitionFields = "分区字段必须来自当前模型字段";
	}
	if (draft.createKind === "fact") {
		const snapshot = draft.factShape === "PERIODIC_SNAPSHOT" || draft.factShape === "ACCUMULATING_SNAPSHOT";
		const hasTimeSemanticsInput = Boolean(draft.timeSemanticsType) || draft.timeSemanticsFields.length > 0;
		if ((snapshot || hasTimeSemanticsInput) && (!draft.timeSemanticsType || !draft.timeSemanticsFields.length)) {
			errors.timeSemantics = "请选择时间语义和至少一个时间字段";
		} else if (
			hasTimeSemanticsInput &&
			((draft.factShape === "TRANSACTION" && draft.timeSemanticsType !== "EVENT_TIME") ||
				(draft.factShape === "PERIODIC_SNAPSHOT" && !["SNAPSHOT_DATE", "PERIOD"].includes(draft.timeSemanticsType)) ||
				(draft.factShape === "ACCUMULATING_SNAPSHOT" && draft.timeSemanticsType !== "MILESTONE_DATES"))
		) {
			errors.timeSemantics = "业务时间与事实形态不匹配";
		} else if (
			hasTimeSemanticsInput &&
			draft.timeSemanticsFields.some(
				(name) => !draft.fields.some((field) => field.name === name && field.role === "TIME"),
			)
		) {
			errors.timeSemantics = "时间字段必须来自字段作用为“时间”的当前模型字段";
		}
	}
	if (draft.createKind === "application" && !draft.consumptionScenario.trim()) {
		errors.consumptionScenario = "请填写应用场景";
	}
	if (supportsStructuredVisualAuthoring(draft) && draft.implementationInputMode !== "GENERATED") {
		const transformationError = validateDesignerTransformations(draft);
		if (transformationError) errors.transformations = transformationError;
	}

	if (!draft.fields.length) {
		errors.fields = "请至少添加一个字段";
	} else if (draft.fields.some((field) => !field.name.trim() || !field.dataType.trim())) {
		errors.fields = "请补齐字段名称和数据类型";
	} else if (
		(isDimensionDraft(draft) || draft.loadStrategy !== "FULL" || draft.materialization === "incremental") &&
		!draft.fields.some((field) => field.role === "KEY")
	) {
		errors.fields = "维度表或非全量加载模型需至少设置一个主键字段";
	} else if (new Set(draft.fields.map((field) => field.name.trim())).size !== draft.fields.length) {
		errors.fields = "字段名称不能重复";
	} else if (
		isDimensionDraft(draft) &&
		draft.fields.some(
			(field) =>
				field.dimensionAttributeCode?.trim() && !/^[A-Z][A-Z0-9_]{0,63}$/.test(field.dimensionAttributeCode.trim()),
		)
	) {
		errors.fields = "维度属性编码只能使用大写字母、数字和下划线，且必须以字母开头";
	}
	return errors;
}

const SOURCE_FIELD_PATTERN = /^(?:src_([0-9]+)\.)?[A-Za-z_][A-Za-z0-9_]*$/;

export const parsePartitionFields = (value: string): string[] =>
	value
		.split(/[,，]/)
		.map((field) => field.trim())
		.filter(Boolean);

const sourceFieldIndex = (value: string): number | null => {
	const match = SOURCE_FIELD_PATTERN.exec(value.trim());
	return match?.[1] === undefined ? null : Number(match[1]);
};

const filterValueMatchesType = (filter: ModelImplementationFilter): boolean => {
	if (filter.operator === "IS_NULL" || filter.operator === "IS_NOT_NULL") return true;
	const values = Array.isArray(filter.value) ? filter.value : [filter.value];
	if ((filter.operator === "IN" || filter.operator === "NOT_IN") && values.length < 1) return false;
	if (filter.operator === "BETWEEN" && values.length !== 2) return false;
	if (!["IN", "NOT_IN", "BETWEEN"].includes(filter.operator) && Array.isArray(filter.value)) return false;
	return values.every((value) => {
		if (filter.valueType === "NUMBER") return typeof value === "number" && Number.isFinite(value);
		if (filter.valueType === "BOOLEAN") return typeof value === "boolean";
		return typeof value === "string" && Boolean(value.trim());
	});
};

const validateDesignerTransformations = (draft: ModelSpecDraft): string | null => {
	const inputCount = draft.sourceRefs.length + draft.dependsOn.length + (draft.dimensionRefs?.length ?? 0);
	const outputFields = new Set(draft.fields.map((field) => field.name.trim()).filter(Boolean));
	const fieldMappings = draft.fieldMappings ?? [];
	const casts = draft.casts ?? {};
	const deduplicateBy = draft.deduplicateBy ?? [];
	const joins = draft.joins ?? [];
	const filters = draft.filters ?? [];
	const groupBy = draft.groupBy ?? [];
	const aggregations = draft.aggregations ?? [];
	const mappings = new Map<string, string>();
	for (const mapping of fieldMappings) {
		const sourceField = mapping.sourceField.trim();
		const targetField = mapping.targetField.trim();
		if (!outputFields.has(targetField) || mappings.has(targetField)) return "字段映射的目标字段不存在或重复";
		const sourceIndex = sourceFieldIndex(sourceField);
		if (!SOURCE_FIELD_PATTERN.test(sourceField) || (inputCount > 1 && sourceIndex === null)) {
			return "多输入模型的来源字段必须使用 src_序号.字段名";
		}
		if (sourceIndex !== null && sourceIndex >= inputCount) return "字段映射引用了不存在的输入别名";
		mappings.set(targetField, sourceField);
	}
	if (inputCount > 1 && [...outputFields].some((field) => !mappings.has(field))) {
		return "多输入模型必须为每个目标字段配置来源字段";
	}
	if (Object.keys(casts).some((field) => !outputFields.has(field))) return "类型转换只能绑定目标字段";
	if (deduplicateBy.some((field) => !outputFields.has(field))) return "去重键只能选择目标字段";
	if (inputCount > 1) {
		const ordered = [...joins].sort((left, right) => left.inputIndex - right.inputIndex);
		if (ordered.length !== inputCount - 1) return "每个附加输入都必须配置一条关联关系";
		for (let inputIndex = 1; inputIndex < inputCount; inputIndex += 1) {
			const join = ordered[inputIndex - 1];
			if (join?.inputIndex !== inputIndex) return "关联关系必须按输入顺序完整配置";
			const leftIndex = sourceFieldIndex(join.leftField);
			const rightIndex = sourceFieldIndex(join.rightField);
			if (leftIndex === null || rightIndex !== inputIndex || leftIndex >= inputIndex) {
				return "关联字段必须把当前输入连接到一个更早的输入";
			}
		}
	} else if (joins.length) {
		return "单输入模型不能配置关联关系";
	}
	for (const filter of filters) {
		const field = filter.field.trim();
		const sourceIndex = sourceFieldIndex(field);
		if (
			(!outputFields.has(field) && !SOURCE_FIELD_PATTERN.test(field)) ||
			(sourceIndex !== null && sourceIndex >= inputCount)
		) {
			return "过滤字段必须是目标字段或当前输入的字段";
		}
		if (!filterValueMatchesType(filter)) return "过滤值与所选值类型或操作符不匹配";
	}
	const hasAggregation = groupBy.length > 0 || aggregations.length > 0;
	if (hasAggregation) {
		if (!aggregations.length) return "请为分组配置聚合字段；全表聚合可不设置分组字段";
		if (deduplicateBy.length) return "聚合与去重不能同时启用";
		const grouped = new Set(groupBy);
		const aggregateTargets = new Set<string>();
		if ([...grouped].some((field) => !outputFields.has(field))) return "分组字段只能选择目标字段";
		for (const aggregation of aggregations) {
			if (!outputFields.has(aggregation.targetField) || grouped.has(aggregation.targetField)) {
				return "聚合目标字段必须存在且不能同时作为分组字段";
			}
			if (!aggregateTargets.add(aggregation.targetField)) return "一个目标字段只能配置一个聚合";
			if (aggregation.distinct && aggregation.function !== "COUNT") return "仅 COUNT 支持去重计数";
			if (!SOURCE_FIELD_PATTERN.test(aggregation.sourceField.trim())) return "聚合来源字段格式不正确";
			const sourceIndex = sourceFieldIndex(aggregation.sourceField);
			if (sourceIndex !== null && sourceIndex >= inputCount) return "聚合引用了不存在的输入别名";
		}
		if ([...outputFields].some((field) => !grouped.has(field) && !aggregateTargets.has(field))) {
			return "聚合模型的每个输出字段必须配置为分组字段或聚合结果";
		}
	}
	return null;
};

export const modelDraftToUpdateCommand = (draft: ModelSpecDraft): UpdateModelSpecCommand => {
	const config = MODEL_KIND_CONFIG[draft.createKind];
	const keyNames = draft.fields.filter((field) => field.role === "KEY").map((field) => field.name.trim());
	const updateFieldNames = new Set(draft.fields.map((field) => field.name.trim()).filter(Boolean));
	const base = draft.base?.compatibilityMode === "CANONICAL" ? draft.base : null;
	return {
		planId: draft.planId,
		domainId: draft.domainId,
		modelType: config.modelType,
		layer: config.layer,
		warehouseLayerCode: draft.warehouseLayerCode,
		name: draft.name.trim(),
		description: draft.description.trim() || null,
		implementationMode: base?.implementationMode || draft.implementationMode,
		materialization: draft.materialization || null,
		businessActivityRef: base?.businessActivityRef || null,
		businessProcessId: config.modelType === "FACT" ? draft.businessProcessId?.trim() || null : null,
		consumptionScenario: config.modelType === "APPLICATION" ? draft.consumptionScenario.trim() || null : null,
		grain: { statement: draft.grainStatement.trim(), keys: keyNames },
		factShape: config.modelType === "FACT" ? draft.factShape || null : null,
		timeSemantics:
			config.modelType === "FACT" && draft.timeSemanticsType && draft.timeSemanticsFields.length
				? { type: draft.timeSemanticsType, fields: draft.timeSemanticsFields }
				: null,
		generationStrategy:
			config.modelType === "DIMENSION" && draft.implementationInputMode === "GENERATED" && draft.generationStrategyType === "DATE_DIMENSION"
				? draft.generationStrategyType
					? { type: draft.generationStrategyType, reference: null }
					: null
				: null,
		dimensionProfile: config.modelType === "DIMENSION" ? dimensionProfileForSave(draft) : null,
		dataMartId: config.modelType === "APPLICATION" ? draft.dataMartId?.trim() || null : null,
		subjectDomainId: config.modelType === "APPLICATION" ? draft.subjectDomainId?.trim() || null : null,
		variantCode: base?.variantCode || null,
		fields: draft.fields.map((field) => ({
			...field,
			name: field.name.trim(),
			displayName: field.displayName?.trim() || null,
			dataType: field.dataType.trim(),
			dimensionAttributeCode: field.dimensionAttributeCode?.trim() || null,
		})),
		sourceRefs: draft.sourceRefs,
		dependsOn: draft.dependsOn,
		dimensionRefs: config.modelType === "FACT" ? draft.dimensionRefs : [],
		metricRefs: base?.metricRefs || [],
		standardBindings: draft.standardBindings.filter((binding) => updateFieldNames.has(binding.fieldName)),
	};
};

const validateDraft = (
	draft: ModelSpecDraft,
	update: UpdateModelSpecCommand,
	capabilities?: ModelImplementationCapabilities,
) => {
	const missing: string[] = [];
	if (!draft.planId) missing.push("可写建模上下文");
	if (!draft.warehouseLayerCode) missing.push("请选择数仓分层");
	const validationErrors = validateModelDraftInput(draft, capabilities);
	missing.push(...Object.values(validationErrors));
	if (missing.length) throw new Error(`请补齐：${Array.from(new Set(missing)).join("、")}`);
	const issues = validateModelSpecUpdate(update);
	if (issues.length)
		throw new Error(issues.map((issue) => `${issue.field}：${issue.message || issue.code}`).join("；"));
};

type ResolvedImplementationInputs = {
	inputMode: ModelImplementationInputMode;
	inputs: ModelImplementationInput[];
};

type ImplementationIdentity = Pick<ModelImplementationWriteCommand, "projectKey" | "dbtUniqueId">;

const implementationIdentityOf = (
	candidate?: { projectKey?: string | null; dbtUniqueId?: string | null } | null,
): ImplementationIdentity | null => {
	if (!candidate?.projectKey?.trim() || !candidate.dbtUniqueId?.trim()) return null;
	return { projectKey: candidate.projectKey, dbtUniqueId: candidate.dbtUniqueId };
};

const implementationInputs = (
	draft: ModelSpecDraft,
	context: ModelSaveContext,
): ResolvedImplementationInputs | null => {
	if (draft.implementationInputMode === "GENERATED" && (draft.generationStrategyType === "DATE_DIMENSION" || draft.generationStrategyType === "SCHEMA_ONLY")) {
		return {
			inputMode: "GENERATED",
			inputs: [{ generatorType: draft.generationStrategyType, config: {} }],
		};
	}
	if (draft.implementationInputMode === "PHYSICAL_ASSET") {
		const sourceRefs = draft.sourceRefs;
		const resolvedSourceRefs = sourceRefs.flatMap((source) =>
			typeof source.sourceBindingId === "string" &&
			source.sourceBindingId.trim() &&
			typeof source.resolvedVersion === "string" &&
			source.resolvedVersion.trim()
				? [{ sourceBindingId: source.sourceBindingId, resolvedVersion: source.resolvedVersion }]
				: [],
		);
		if (!sourceRefs.length || resolvedSourceRefs.length !== sourceRefs.length) return null;
		return {
			inputMode: "PHYSICAL_ASSET",
			inputs: resolvedSourceRefs,
		};
	}
	if (draft.implementationInputMode === "UPSTREAM_MODEL") {
		const dependencies = draft.dependsOn;
		if (!dependencies.length) return null;
		const models = context.models || [];
		const inputs = dependencies.map((dependency) => {
			const persisted = draft.implementationBase?.inputs.find(
				(input) =>
					"modelSpecId" in input &&
					input.modelSpecId === dependency.modelSpecId &&
					input.revision === dependency.revision,
			);
			if (persisted && "checksum" in persisted && persisted.checksum.trim()) return persisted;
			const model = models.find((item) => item.id === dependency.modelSpecId && item.revision === dependency.revision);
			return model
				? { modelSpecId: model.id, revision: model.revision, checksum: model.checksum }
				: { modelSpecId: dependency.modelSpecId, revision: dependency.revision, checksum: "" };
		});
		if (inputs.every((input) => input.checksum)) return { inputMode: "UPSTREAM_MODEL", inputs };
	}
	return null;
};

const implementationNeedsSave = (draft: ModelSpecDraft): boolean => {
	if (draft.implementationMode === "DBT_MANAGED") return false;
	if (draft.implementationBase?.ownership === "DESIGNER_GENERATED") return true;
	if (draft.implementationBase?.ownership === "DBT_MANAGED") {
		return (
			draft.physicalName.trim() !== implementationText(draft.implementationBase, "targetPhysicalName") ||
			draft.loadStrategy !== implementationText(draft.implementationBase, "loadStrategy") ||
			draft.partitionFields.trim() !==
				implementationStringList(draft.implementationBase, "partitionFields").join(",") ||
			draft.materialization !== draft.implementationBase.materialization
		);
	}
	const legacy = draft.base?.implementationPolicy;
	return (
		Boolean(draft.generationStrategyType) ||
		Boolean(draft.implementationInputMode) ||
		draft.physicalName.trim() !== (legacy?.physicalName || "") ||
		draft.loadStrategy !== (legacy?.loadStrategy || "FULL") ||
		draft.partitionFields.trim() !== (legacy?.partitionFields?.join(",") || "")
	);
};

const implementationNeedsCapabilityRepair = (
	draft: ModelSpecDraft,
	capabilities: ModelImplementationCapabilities,
): boolean => {
	const implementation = draft.implementationBase;
	if (!implementation) return false;
	const modelType = MODEL_KIND_CONFIG[draft.createKind].modelType;
	const loadStrategy = implementationText(implementation, "loadStrategy") as ModelSpecLoadStrategy;
	return (
		!(capabilities.inputModesByModelType[modelType] || []).includes(implementation.inputMode) ||
		!capabilities.loadStrategies.includes(loadStrategy) ||
		!(capabilities.materializationsByLoadStrategy[loadStrategy] || []).includes(implementation.materialization) ||
		Object.keys(implementation.settings || {}).some((key) => !capabilities.settingKeys.includes(key)) ||
		(!capabilities.partitionFieldsSupported && implementationStringList(implementation, "partitionFields").length > 0)
	);
};

export const modelDraftNeedsImplementationRecovery = (
	draft: ModelDraft | null,
	capabilities?: ModelImplementationCapabilities,
): boolean =>
	Boolean(
		draft &&
			isModelSpecDraft(draft) &&
			draft.base &&
			(!draft.implementationBase ||
				draft.implementationBase.revision !== draft.base.revision ||
				draft.implementationBase.modelChecksum !== draft.base.checksum ||
				(capabilities && implementationNeedsCapabilityRepair(draft, capabilities))) &&
			implementationNeedsSave(draft),
	);

const buildImplementationCommand = (
	draft: ModelSpecDraft,
	model: Pick<CanonicalModelSpecView, "id" | "implementationMode">,
	resolved: ResolvedImplementationInputs,
	initialIdentity?: ImplementationIdentity | null,
	capabilities?: ModelImplementationCapabilities,
): ModelImplementationWriteCommand => {
	const materialization = draft.materialization.trim();
	const allowedMaterializations = capabilities?.materializationsByLoadStrategy[draft.loadStrategy];
	if (!materialization || (allowedMaterializations && !allowedMaterializations.includes(materialization))) {
		throw new Error("当前加载策略不支持所选物化方式，请重新选择");
	}
	const controlledSettingKeys = new Set([
		"casts",
		"deduplicateBy",
		"dedupBy",
		"filters",
		"joins",
		"groupBy",
		"aggregations",
	]);
	const retainedVisual = retainedVisualImplementation(draft.implementationBase);
	const supportedSettingKeys = capabilities ? new Set(capabilities.settingKeys) : null;
	const retainedSettings = Object.fromEntries(
		Object.entries(retainedVisual?.settings || draft.implementationBase?.settings || {}).filter(
			([key]) => !controlledSettingKeys.has(key) && (!supportedSettingKeys || supportedSettingKeys.has(key)),
		),
	);
	const identity =
		implementationIdentityOf(retainedVisual) ||
		implementationIdentityOf(draft.implementationBase) ||
		implementationIdentityOf(initialIdentity);
	return {
		inputMode: resolved.inputMode,
		inputs: resolved.inputs as never,
		fieldMappings: draft.fieldMappings.map((mapping) => ({
			sourceField: mapping.sourceField.trim(),
			targetField: mapping.targetField.trim(),
		})),
		settings: {
			...retainedSettings,
			targetPhysicalName: draft.physicalName.trim(),
			loadStrategy: draft.loadStrategy,
			partitionFields: parsePartitionFields(draft.partitionFields),
			...(Object.keys(draft.casts).length ? { casts: draft.casts } : {}),
			...(draft.filters.length ? { filters: draft.filters } : {}),
			...(draft.deduplicateBy.length ? { deduplicateBy: draft.deduplicateBy } : {}),
			...(draft.joins.length ? { joins: draft.joins } : {}),
			...(draft.aggregations.length
				? { ...(draft.groupBy.length ? { groupBy: draft.groupBy } : {}), aggregations: draft.aggregations }
				: {}),
		},
		ownership: model.implementationMode,
		materialization,
		projectKey: identity?.projectKey || "system-managed",
		dbtUniqueId: identity?.dbtUniqueId || `model.${model.id}`,
		idempotencyKey: draft.implementationIdempotencyKey,
	};
};

const initialAuthoringImplementationIdentity = (
	draft: ModelSpecDraft,
	sourceBundleProjectKey?: string | null,
): ImplementationIdentity | null => {
	const retainedVisual = retainedVisualImplementation(draft.implementationBase);
	if (implementationIdentityOf(retainedVisual) || implementationIdentityOf(draft.implementationBase)) {
		return null;
	}
	const projectKey = sourceBundleProjectKey?.trim();
	if (!projectKey) {
		throw new Error("模型创作草稿缺少服务端分配的 dbt 项目标识，请关闭后重新打开模型再试");
	}
	const resourceName = draft.physicalName.trim();
	if (!resourceName) throw new Error("请先填写产出表英文名");
	return { projectKey, dbtUniqueId: `model.${projectKey}.${resourceName}` };
};

const supportsStructuredVisualAuthoring = (draft: ModelSpecDraft): boolean =>
	draft.implementationBase?.ownership === "DESIGNER_GENERATED" ||
	Boolean(retainedVisualImplementation(draft.implementationBase)) ||
	Boolean(draft.implementationInputMode) ||
	(!draft.implementationBase && draft.base?.implementationMode === "DESIGNER_GENERATED");

export const modelDraftToAuthoringSnapshot = (
	draft: ModelSpecDraft,
	context: ModelSaveContext,
	includeStructuredVisual = true,
	sourceBundleProjectKey?: string | null,
): ModelAuthoringSnapshot => {
	const normalizedDraft = context.implementationCapabilities
		? normalizeModelDraftImplementation(draft, context.implementationCapabilities)
		: draft;
	const modelSpec = modelDraftToUpdateCommand(normalizedDraft);
	const snapshot: ModelAuthoringSnapshot = { schemaVersion: 1, modelSpec };
	if (
		!includeStructuredVisual ||
		!supportsStructuredVisualAuthoring(normalizedDraft) ||
		normalizedDraft.base?.compatibilityMode !== "CANONICAL"
	) {
		return snapshot;
	}
	const resolved = implementationInputs(normalizedDraft, context);
	if (!resolved) return snapshot;
	const visualImplementation = buildImplementationCommand(
		normalizedDraft,
		normalizedDraft.base,
		resolved,
		initialAuthoringImplementationIdentity(normalizedDraft, sourceBundleProjectKey),
		context.implementationCapabilities,
	);
	return {
		...snapshot,
		visualImplementation: {
			...visualImplementation,
			ownership: "DESIGNER_GENERATED",
		},
	};
};

const validatedImplementationSave = async (
	draft: ModelSpecDraft,
	model: CanonicalModelSpecView,
	resolved: ResolvedImplementationInputs,
	capabilities?: ModelImplementationCapabilities,
): Promise<ModelImplementationView> => {
	const command = buildImplementationCommand(draft, model, resolved, null, capabilities);
	const validation = await validateModelImplementation(model, command);
	if (!validation.valid) throw new Error(modelImplementationValidationMessage(validation));
	return saveModelImplementation(model, draft.implementationBase, command);
};

export async function saveModelDraft(draft: ModelSpecDraft, context: ModelSaveContext): Promise<ModelDraftSaveResult> {
	if (draft.base && draft.base.compatibilityMode !== "CANONICAL") throw new Error("历史只读模型不能在工作台中修改");
	const backendContextId = draft.planId || (await resolveDefaultModelingContextId());
	if (!backendContextId) throw new Error("服务端尚未提供可写建模上下文，请联系管理员初始化");
	const writableDraft = draft.planId ? draft : { ...draft, planId: backendContextId };
	const prepared = prepareModelDraftForSave(writableDraft, context.dimensionDefinitions);
	const preparedDraft = context.implementationCapabilities
		? normalizeModelDraftImplementation(prepared, context.implementationCapabilities)
		: prepared;
	const update = modelDraftToUpdateCommand(preparedDraft);
	validateDraft(preparedDraft, update, context.implementationCapabilities);
	const needsImplementationSave = implementationNeedsSave(preparedDraft);
	const resolvedImplementationInputs = needsImplementationSave ? implementationInputs(preparedDraft, context) : null;
	if (needsImplementationSave && !resolvedImplementationInputs) {
		throw new Error("请先选择数据来源方式；日期维度可由系统生成，其他模型请选择输入源表或上游模型");
	}
	if (!draft.base) {
		const operation: ModelDraftOperationCommand = {
			create: createCommandForDraft(preparedDraft, update, context),
			modelSpec: update,
			implementation: resolvedImplementationInputs
				? buildImplementationCommand(
						preparedDraft,
						{ id: "pending", implementationMode: update.implementationMode },
						resolvedImplementationInputs,
						null,
						context.implementationCapabilities,
					)
				: null,
		};
		const saved = await saveModelDraftOperation(operation);
		return { model: saved.model, implementation: saved.implementation };
	}

	const savedModel = await updateModelSpec(draft.base, update);
	if (!resolvedImplementationInputs) return { model: savedModel, implementation: preparedDraft.implementationBase };
	try {
		const implementation = await validatedImplementationSave(
			preparedDraft,
			savedModel,
			resolvedImplementationInputs,
			context.implementationCapabilities,
		);
		return { model: savedModel, implementation };
	} catch (failure) {
		throw new ModelDraftPartialSaveError(savedModel, failure);
	}
}
