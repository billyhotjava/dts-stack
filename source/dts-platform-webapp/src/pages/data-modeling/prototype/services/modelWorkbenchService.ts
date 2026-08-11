import {
	confirmDimensionDefinition,
	createDimensionDefinition,
	listDimensionDefinitions,
	updateDimensionDefinition,
} from "@/api/dimensionDefinitionApi";
import { saveModelImplementation } from "@/api/modelImplementationApi";
import { listModelFieldStandardOptions, type ModelFieldStandardOption } from "@/api/modelingStandardsApi";
import {
	type CreateDimensionModelCommand,
	createDimensionModel,
	createModelSpec,
	getModelLifecycle,
	listModelSpecs,
	updateModelSpec,
} from "@/api/modelSpecApi";
import catalogDomainService, { type CatalogDomain } from "@/api/services/catalogDomainService";
import { resolveDefaultModelingContextId } from "@/api/services/modelingImportContextService";
import { listWarehouseLayers, type WarehouseLayerView } from "@/api/warehouseLayerApi";
import type {
	DimensionDefinitionAttribute,
	DimensionDefinitionReuseScope,
	DimensionDefinitionView,
} from "@/features/modeling/contracts/dimensionDefinitionContract";
import type {
	GeneratedImplementationInput,
	ModelImplementationInput,
	ModelImplementationInputMode,
	ModelImplementationView,
	ModelImplementationWriteCommand,
} from "@/features/modeling/contracts/modelImplementationContract";
import {
	type CanonicalModelSpecView,
	type ModelSpecField,
	type ModelSpecLayer,
	type ModelSpecLoadStrategy,
	type ModelSpecScdType,
	type ModelSpecStandardBinding,
	type ModelSpecType,
	type ModelSpecView,
	type UpdateModelSpecCommand,
	validateModelSpecUpdate,
} from "@/features/modeling/contracts/modelSpecV2Contract";

export type ModelCreateKind = "dimension" | "dimension-table" | "fact" | "summary" | "application";
export type ModelSpecCreateKind = Exclude<ModelCreateKind, "dimension">;

export const MODEL_KIND_CONFIG: Record<
	ModelCreateKind,
	{ label: string; modelType: ModelSpecType; layer: ModelSpecLayer }
> = {
	dimension: { label: "维度", modelType: "DIMENSION", layer: "DWD" },
	"dimension-table": { label: "维度表", modelType: "DIMENSION", layer: "DWD" },
	fact: { label: "明细表", modelType: "FACT", layer: "DWD" },
	summary: { label: "汇总表", modelType: "SUMMARY", layer: "DWS" },
	application: { label: "应用表", modelType: "APPLICATION", layer: "ADS" },
};

export type ModelWorkbenchContext = {
	domains: CatalogDomain[];
	models: ModelSpecView[];
	dimensions: DimensionDefinitionView[];
	standards: ModelFieldStandardOption[];
	warehouseLayers: WarehouseLayerView[];
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
	reuseScope: DimensionDefinitionReuseScope;
	dimensionDefinitionId: string;
	standardBindings: ModelSpecStandardBinding[];
	warehouseLayerCode: string;
	implementationBase: ModelImplementationView | null;
	implementationInputMode: ModelImplementationInputMode | "";
	generationStrategyType: "" | "DATE_DIMENSION";
	implementationIdempotencyKey: string;
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
};

export type ModelDraftSaveResult = {
	model: CanonicalModelSpecView;
	implementation: ModelImplementationView | null;
};

export type ModelDraftErrorKey =
	| "domainId"
	| "dimensionDefinitionId"
	| "physicalName"
	| "name"
	| "description"
	| "businessProcessId"
	| "grainStatement"
	| "fields";

export type ModelDraftValidationErrors = Partial<Record<ModelDraftErrorKey, string>>;

const modelKind = (model: ModelSpecView): ModelSpecCreateKind =>
	model.modelType === "DIMENSION"
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
	return {
		createKind: kind,
		base: null,
		planId: "",
		domainId: defaultDomainId,
		name: "",
		description: "",
		physicalName: "",
		materialization: "table",
		grainStatement: "",
		businessProcessId: "",
		fields: [],
		partitionFields: "",
		loadStrategy: "FULL",
		scdType: config.modelType === "DIMENSION" ? "TYPE1" : "NONE",
		reuseScope: "DOMAIN",
		dimensionDefinitionId: "",
		standardBindings: [],
		warehouseLayerCode: config.layer,
		implementationBase: null,
		implementationInputMode: "",
		generationStrategyType: "",
		implementationIdempotencyKey: crypto.randomUUID(),
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

const generatedInput = (implementation: ModelImplementationView | null): GeneratedImplementationInput | null => {
	if (implementation?.inputMode !== "GENERATED") return null;
	const input = implementation.inputs[0];
	return input && "generatorType" in input ? input : null;
};

export function modelDraftFromView(
	model: ModelSpecView,
	implementation: ModelImplementationView | null = null,
): ModelSpecDraft {
	const implementationLoadStrategy = implementationText(implementation, "loadStrategy");
	const generationStrategyType = generatedInput(implementation)?.generatorType || model.generationStrategy?.type || "";
	return {
		createKind: modelKind(model),
		base: model,
		planId: model.planId || "",
		domainId: model.domainId || "",
		name: model.name,
		description: model.description || "",
		physicalName:
			implementationText(implementation, "targetPhysicalName") || model.implementationPolicy?.physicalName || "",
		materialization: implementation?.materialization || model.materialization || "table",
		grainStatement: model.grain?.statement || "",
		businessProcessId: model.businessProcessId || "",
		fields: model.fields.map((field) => ({ ...field })),
		partitionFields:
			implementationStringList(implementation, "partitionFields").join(",") ||
			model.implementationPolicy?.partitionFields?.join(",") ||
			"",
		loadStrategy:
			implementationLoadStrategy === "INCREMENTAL" || implementationLoadStrategy === "SNAPSHOT"
				? implementationLoadStrategy
				: model.implementationPolicy?.loadStrategy || "FULL",
		scdType: model.dimensionProfile?.scdPolicy.type || "NONE",
		reuseScope: model.dimensionProfile?.reuseScope === "TENANT" ? "TENANT" : "DOMAIN",
		dimensionDefinitionId: model.dimensionDefinitionRef?.dimensionDefinitionId || "",
		standardBindings: model.standardBindings.map((binding) => ({ ...binding })),
		warehouseLayerCode: model.warehouseLayerCode || model.layer,
		implementationBase: implementation,
		implementationInputMode:
			implementation?.inputMode || (generationStrategyType === "DATE_DIMENSION" ? "GENERATED" : ""),
		generationStrategyType: generationStrategyType === "DATE_DIMENSION" ? "DATE_DIMENSION" : "",
		implementationIdempotencyKey: crypto.randomUUID(),
	};
}

export async function loadModelWorkbenchDraft(model: ModelSpecView): Promise<ModelSpecDraft> {
	const lifecycle = await getModelLifecycle(model.id);
	return modelDraftFromView(model, lifecycle.implementation);
}

export async function loadModelWorkbenchContext(): Promise<ModelWorkbenchContext> {
	const [domains, models, dimensions, standards, warehouseLayers] = await Promise.all([
		catalogDomainService.list(),
		listModelSpecs(),
		listDimensionDefinitions({ offset: 0, limit: 100 }),
		listModelFieldStandardOptions(),
		listWarehouseLayers(),
	]);
	return {
		domains,
		models: models.filter((model) => model.status !== "ARCHIVED"),
		dimensions: dimensions.filter((definition) => definition.status !== "RETIRED"),
		standards,
		warehouseLayers,
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
	if (draft.definitionBase && draft.definitionBase.status !== "DRAFT") {
		throw new Error("已确认或已退役的维度不能修改，请新建维度。");
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

export function validateModelDraftInput(draft: ModelSpecDraft): ModelDraftValidationErrors {
	const errors: ModelDraftValidationErrors = {};
	if (!draft.domainId.trim()) errors.domainId = "请选择数据域";
	if (draft.createKind === "dimension-table" && !draft.dimensionDefinitionId.trim()) {
		errors.dimensionDefinitionId = "请选择一个维度";
	}
	if (isDimensionDraft(draft)) {
		if (!/[\u4e00-\u9fff]/.test(draft.name.trim())) errors.name = "请填写中文表名";
		if (!/^[a-z][a-z0-9_]*$/.test(draft.physicalName.trim())) {
			errors.physicalName = "表名只能使用小写字母、数字和下划线，且必须以字母开头";
		}
		if (!draft.description.trim()) errors.description = "请填写维度定义（描述）";
	} else if (!draft.grainStatement.trim()) {
		errors.grainStatement = "请填写模型粒度";
	}
	if (draft.createKind === "fact" && !draft.businessProcessId?.trim()) {
		errors.businessProcessId = "请选择业务过程";
	}

	if (!draft.fields.length) {
		errors.fields = "请至少添加一个字段";
	} else if (draft.fields.some((field) => !field.name.trim() || !field.dataType.trim())) {
		errors.fields = "请补齐字段名称和数据类型";
	} else if (!draft.fields.some((field) => field.role === "KEY")) {
		errors.fields = "请至少设置一个主键字段";
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

const buildUpdate = (draft: ModelSpecDraft): UpdateModelSpecCommand => {
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
		implementationMode: base?.implementationMode || "DESIGNER_GENERATED",
		materialization: draft.materialization || null,
		businessActivityRef: base?.businessActivityRef || null,
		businessProcessId: config.modelType === "FACT" ? draft.businessProcessId?.trim() || null : null,
		consumptionScenario: base?.consumptionScenario || null,
		grain: { statement: draft.grainStatement.trim(), keys: keyNames },
		factShape: config.modelType === "FACT" ? base?.factShape || "TRANSACTION" : null,
		timeSemantics: config.modelType === "FACT" ? base?.timeSemantics || null : null,
		generationStrategy:
			config.modelType === "DIMENSION"
				? draft.generationStrategyType
					? { type: draft.generationStrategyType, reference: null }
					: base?.generationStrategy || null
				: null,
		dimensionProfile:
			config.modelType === "DIMENSION"
				? {
						hierarchies: base?.dimensionProfile?.hierarchies || [],
						scdPolicy: { type: draft.scdType },
					}
				: null,
		dataMartId: base?.dataMartId || null,
		variantCode: base?.variantCode || null,
		fields: draft.fields.map((field) => ({
			...field,
			name: field.name.trim(),
			displayName: field.displayName?.trim() || null,
			dataType: field.dataType.trim(),
			dimensionAttributeCode: field.dimensionAttributeCode?.trim() || null,
		})),
		sourceRefs: base?.sourceRefs || [],
		dependsOn: base?.dependsOn || [],
		dimensionRefs: base?.dimensionRefs || [],
		metricRefs: base?.metricRefs || [],
		standardBindings: draft.standardBindings.filter((binding) => updateFieldNames.has(binding.fieldName)),
	};
};

const validateDraft = (draft: ModelSpecDraft, update: UpdateModelSpecCommand) => {
	const missing: string[] = [];
	if (!draft.planId) missing.push("可写建模上下文");
	if (!draft.warehouseLayerCode) missing.push("请选择数仓分层");
	const validationErrors = validateModelDraftInput(draft);
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

const implementationInputs = (
	draft: ModelSpecDraft,
	context: ModelSaveContext,
): ResolvedImplementationInputs | null => {
	if (draft.implementationBase) {
		return {
			inputMode: draft.implementationBase.inputMode,
			inputs: draft.implementationBase.inputs,
		};
	}
	if (draft.implementationInputMode === "GENERATED" && draft.generationStrategyType === "DATE_DIMENSION") {
		return {
			inputMode: "GENERATED",
			inputs: [{ generatorType: "DATE_DIMENSION", config: {} }],
		};
	}
	const sourceRefs = draft.base?.sourceRefs || [];
	const resolvedSourceRefs = sourceRefs.flatMap((source) =>
		typeof source.sourceBindingId === "string" &&
		source.sourceBindingId.trim() &&
		typeof source.resolvedVersion === "string" &&
		source.resolvedVersion.trim()
			? [{ sourceBindingId: source.sourceBindingId, resolvedVersion: source.resolvedVersion }]
			: [],
	);
	if (sourceRefs.length && resolvedSourceRefs.length === sourceRefs.length) {
		return {
			inputMode: "PHYSICAL_ASSET",
			inputs: resolvedSourceRefs,
		};
	}
	const dependencies = draft.base?.dependsOn || [];
	if (dependencies.length) {
		const models = context.models || [];
		const inputs = dependencies.map((dependency) => {
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
		draft.physicalName.trim() !== (legacy?.physicalName || "") ||
		draft.loadStrategy !== (legacy?.loadStrategy || "FULL") ||
		draft.partitionFields.trim() !== (legacy?.partitionFields?.join(",") || "")
	);
};

export const modelDraftNeedsImplementationRecovery = (draft: ModelDraft | null): boolean =>
	Boolean(
		draft &&
			isModelSpecDraft(draft) &&
			draft.base &&
			(!draft.implementationBase ||
				draft.implementationBase.revision !== draft.base.revision ||
				draft.implementationBase.modelChecksum !== draft.base.checksum) &&
			implementationNeedsSave(draft),
	);

const buildImplementationCommand = (
	draft: ModelSpecDraft,
	model: CanonicalModelSpecView,
	resolved: ResolvedImplementationInputs,
): ModelImplementationWriteCommand => {
	const materialization = draft.loadStrategy === "INCREMENTAL" ? "incremental" : draft.materialization;
	if (!new Set(["table", "view", "incremental"]).has(materialization)) {
		throw new Error("当前数据实现仅支持表、视图或增量物化方式");
	}
	return {
		inputMode: resolved.inputMode,
		inputs: resolved.inputs as never,
		fieldMappings: draft.implementationBase?.fieldMappings || [],
		settings: {
			...(draft.implementationBase?.settings || {}),
			targetPhysicalName: draft.physicalName.trim(),
			loadStrategy: draft.loadStrategy,
			partitionFields: draft.partitionFields
				.split(",")
				.map((field) => field.trim())
				.filter(Boolean),
		},
		ownership: model.implementationMode,
		materialization,
		projectKey: draft.implementationBase?.projectKey || "system-managed",
		dbtUniqueId: draft.implementationBase?.dbtUniqueId || `model.${model.id}`,
		idempotencyKey: draft.implementationIdempotencyKey,
	};
};

export async function saveModelDraft(draft: ModelSpecDraft, context: ModelSaveContext): Promise<ModelDraftSaveResult> {
	if (draft.base && draft.base.compatibilityMode !== "CANONICAL") throw new Error("历史只读模型不能在工作台中修改");
	const backendContextId = draft.planId || (await resolveDefaultModelingContextId());
	if (!backendContextId) throw new Error("服务端尚未提供可写建模上下文，请联系管理员初始化");
	const writableDraft = draft.planId ? draft : { ...draft, planId: backendContextId };
	const preparedDraft = prepareModelDraftForSave(writableDraft, context.dimensionDefinitions);
	const update = buildUpdate(preparedDraft);
	validateDraft(preparedDraft, update);
	const needsImplementationSave = implementationNeedsSave(preparedDraft);
	const resolvedImplementationInputs = needsImplementationSave ? implementationInputs(preparedDraft, context) : null;
	if (needsImplementationSave && !resolvedImplementationInputs) {
		throw new Error("请先配置数据实现来源；日期维度可选择受控日期维度生成器，普通模型需关联物理来源或上游模型");
	}
	if (preparedDraft.implementationBase?.ownership === "DBT_MANAGED" && needsImplementationSave) {
		throw new Error("DBT 管理的物理实现不能在基础信息中修改，请进入模型开发处理");
	}
	let savedModel: CanonicalModelSpecView;
	if (draft.base) savedModel = await updateModelSpec(draft.base, update);
	else if (update.modelType === "DIMENSION") {
		const definition = context.dimensionDefinitions.find((item) => item.id === preparedDraft.dimensionDefinitionId);
		if (!definition) throw new Error("请选择一个维度");
		const command: CreateDimensionModelCommand = {
			operationId: crypto.randomUUID(),
			definitionBinding: {
				mode: "EXISTING",
				dimensionDefinitionRef: { dimensionDefinitionId: definition.id, revision: definition.revision },
			},
			modelSpec: {
				...update,
				modelType: "DIMENSION",
				layer: "DWD",
				implementationMode: "DESIGNER_GENERATED",
				dimensionProfile: {
					hierarchies: update.dimensionProfile?.hierarchies || [],
					scdPolicy: update.dimensionProfile?.scdPolicy || { type: "NONE" },
				},
				fields: update.fields || [],
				sourceRefs: update.sourceRefs || [],
				dependsOn: update.dependsOn || [],
				dimensionRefs: update.dimensionRefs || [],
				metricRefs: update.metricRefs || [],
				standardBindings: update.standardBindings || [],
			},
		};
		savedModel = (await createDimensionModel(command)).currentModelSpec;
	} else {
		const created = await createModelSpec({
			planId: preparedDraft.planId,
			domainId: preparedDraft.domainId,
			modelType: update.modelType,
			name: preparedDraft.name.trim(),
			description: preparedDraft.description.trim() || null,
			warehouseLayerCode: update.warehouseLayerCode,
			businessProcessId: update.modelType === "FACT" ? update.businessProcessId : null,
			idempotencyKey: crypto.randomUUID(),
		});
		savedModel = await updateModelSpec(created, update);
	}
	const implementation = resolvedImplementationInputs
		? await saveModelImplementation(
				savedModel,
				preparedDraft.implementationBase,
				buildImplementationCommand(preparedDraft, savedModel, resolvedImplementationInputs),
			)
		: preparedDraft.implementationBase;
	return { model: savedModel, implementation };
}
