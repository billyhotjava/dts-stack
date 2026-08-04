import { listDimensionDefinitions } from "@/api/dimensionDefinitionApi";
import { listModelFieldStandardOptions, type ModelFieldStandardOption } from "@/api/modelingStandardsApi";
import {
	type CreateDimensionModelCommand,
	createDimensionModel,
	createModelSpec,
	listModelSpecs,
	updateModelSpec,
} from "@/api/modelSpecApi";
import catalogDomainService, { type CatalogDomain } from "@/api/services/catalogDomainService";
import { resolveDefaultModelingContextId } from "@/api/services/modelingImportContextService";
import type {
	DimensionDefinitionReuseScope,
	DimensionDefinitionView,
} from "@/features/modeling/contracts/dimensionDefinitionContract";
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
	standards: ModelFieldStandardOption[];
};

export type ModelDraft = {
	createKind: ModelCreateKind;
	base: ModelSpecView | null;
	planId: string;
	domainId: string;
	name: string;
	description: string;
	physicalName: string;
	materialization: string;
	grainStatement: string;
	fields: ModelSpecField[];
	partitionFields: string;
	loadStrategy: ModelSpecLoadStrategy;
	scdType: ModelSpecScdType;
	reuseScope: DimensionDefinitionReuseScope;
	dimensionDefinitionId: string;
	standardBindings: ModelSpecStandardBinding[];
};

export type ModelSaveContext = {
	ownerId: string;
	dimensionDefinitions: DimensionDefinitionView[];
};

export type ModelDraftErrorKey =
	| "domainId"
	| "dimensionDefinitionId"
	| "physicalName"
	| "name"
	| "grainStatement"
	| "fields";

export type ModelDraftValidationErrors = Partial<Record<ModelDraftErrorKey, string>>;

const modelKind = (model: ModelSpecView): ModelCreateKind =>
	model.modelType === "DIMENSION"
		? "dimension-table"
		: model.modelType === "FACT"
			? "fact"
			: model.modelType === "SUMMARY"
				? "summary"
				: "application";

export function emptyModelDraft(kind: ModelCreateKind, context: ModelWorkbenchContext): ModelDraft {
	const config = MODEL_KIND_CONFIG[kind];
	return {
		createKind: kind,
		base: null,
		planId: "",
		domainId: context.domains[0]?.code || "",
		name: "",
		description: "",
		physicalName: "",
		materialization: "table",
		grainStatement: "",
		fields: [],
		partitionFields: "",
		loadStrategy: "FULL",
		scdType: config.modelType === "DIMENSION" ? "TYPE1" : "NONE",
		reuseScope: "DOMAIN",
		dimensionDefinitionId: "",
		standardBindings: [],
	};
}

export function modelDraftFromView(model: ModelSpecView): ModelDraft {
	return {
		createKind: modelKind(model),
		base: model,
		planId: model.planId || "",
		domainId: model.domainId || "",
		name: model.name,
		description: model.description || "",
		physicalName: model.implementationPolicy?.physicalName || "",
		materialization: model.materialization || "table",
		grainStatement: model.grain?.statement || "",
		fields: model.fields.map((field) => ({ ...field })),
		partitionFields: model.implementationPolicy?.partitionFields?.join(",") || "",
		loadStrategy: model.implementationPolicy?.loadStrategy || "FULL",
		scdType: model.dimensionProfile?.scdPolicy.type || "NONE",
		reuseScope: model.dimensionProfile?.reuseScope === "TENANT" ? "TENANT" : "DOMAIN",
		dimensionDefinitionId: model.dimensionDefinitionRef?.dimensionDefinitionId || "",
		standardBindings: model.standardBindings.map((binding) => ({ ...binding })),
	};
}

export async function loadModelWorkbenchContext(): Promise<ModelWorkbenchContext> {
	const [domains, models, standards] = await Promise.all([
		catalogDomainService.list(),
		listModelSpecs(),
		listModelFieldStandardOptions(),
	]);
	return {
		domains,
		models: models.filter((model) => model.status !== "ARCHIVED"),
		standards,
	};
}

export const loadCurrentDimensionDefinitions = (domainId: string) =>
	domainId ? listDimensionDefinitions({ domainId, status: "CURRENT", offset: 0, limit: 500 }) : Promise.resolve([]);

const isDimensionDraft = (draft: ModelDraft): boolean => MODEL_KIND_CONFIG[draft.createKind].modelType === "DIMENSION";

export function prepareModelDraftForSave(draft: ModelDraft, definitions: DimensionDefinitionView[]): ModelDraft {
	if (!isDimensionDraft(draft) || draft.grainStatement.trim()) return draft;
	const definition = definitions.find((item) => item.id === draft.dimensionDefinitionId);
	const grainName = definition?.name.trim() || draft.name.trim();
	return grainName ? { ...draft, grainStatement: `一个${grainName}一行` } : draft;
}

export function validateModelDraftInput(draft: ModelDraft): ModelDraftValidationErrors {
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
	} else if (!draft.grainStatement.trim()) {
		errors.grainStatement = "请填写模型粒度";
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

const buildUpdate = (draft: ModelDraft): UpdateModelSpecCommand => {
	const config = MODEL_KIND_CONFIG[draft.createKind];
	const keyNames = draft.fields.filter((field) => field.role === "KEY").map((field) => field.name.trim());
	const updateFieldNames = new Set(draft.fields.map((field) => field.name.trim()).filter(Boolean));
	const base = draft.base?.compatibilityMode === "CANONICAL" ? draft.base : null;
	return {
		planId: draft.planId,
		domainId: draft.domainId,
		modelType: config.modelType,
		layer: config.layer,
		name: draft.name.trim(),
		description: draft.description.trim() || null,
		implementationMode: base?.implementationMode || "DESIGNER_GENERATED",
		materialization: draft.materialization || null,
		businessActivityRef: base?.businessActivityRef || null,
		consumptionScenario: base?.consumptionScenario || null,
		grain: { statement: draft.grainStatement.trim(), keys: keyNames },
		factShape: config.modelType === "FACT" ? base?.factShape || "TRANSACTION" : null,
		timeSemantics: config.modelType === "FACT" ? base?.timeSemantics || null : null,
		generationStrategy: config.modelType === "DIMENSION" ? base?.generationStrategy || null : null,
		dimensionProfile:
			config.modelType === "DIMENSION"
				? {
						dimensionCode: base?.dimensionProfile?.dimensionCode || null,
						hierarchies: base?.dimensionProfile?.hierarchies || [],
						scdPolicy: { type: draft.scdType },
						reuseScope: draft.reuseScope,
					}
				: null,
		dataMartId: base?.dataMartId || null,
		variantCode: base?.variantCode || null,
		implementationPolicy: {
			physicalName: draft.physicalName.trim() || null,
			loadStrategy: draft.loadStrategy,
			retentionDays: base?.implementationPolicy?.retentionDays || null,
			partitionFields: draft.partitionFields
				.split(",")
				.map((field) => field.trim())
				.filter(Boolean),
		},
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

const validateDraft = (draft: ModelDraft, update: UpdateModelSpecCommand) => {
	const missing: string[] = [];
	if (!draft.planId) missing.push("可写建模上下文");
	const validationErrors = validateModelDraftInput(draft);
	missing.push(...Object.values(validationErrors));
	if (missing.length) throw new Error(`请补齐：${Array.from(new Set(missing)).join("、")}`);
	const issues = validateModelSpecUpdate(update);
	if (issues.length)
		throw new Error(issues.map((issue) => `${issue.field}：${issue.message || issue.code}`).join("；"));
};

export async function saveModelDraft(draft: ModelDraft, context: ModelSaveContext): Promise<CanonicalModelSpecView> {
	if (draft.base && draft.base.compatibilityMode !== "CANONICAL") throw new Error("历史只读模型不能在工作台中修改");
	const backendContextId = draft.planId || (await resolveDefaultModelingContextId());
	if (!backendContextId) throw new Error("服务端尚未提供可写建模上下文，请联系管理员初始化");
	const writableDraft = draft.planId ? draft : { ...draft, planId: backendContextId };
	const preparedDraft = prepareModelDraftForSave(writableDraft, context.dimensionDefinitions);
	const update = buildUpdate(preparedDraft);
	validateDraft(preparedDraft, update);
	if (draft.base) return updateModelSpec(draft.base, update);
	if (update.modelType === "DIMENSION") {
		if (!context.ownerId) throw new Error("当前登录身份缺少人员 ID，不能创建维度定义");
		const definition = context.dimensionDefinitions.find((item) => item.id === preparedDraft.dimensionDefinitionId);
		if (preparedDraft.createKind === "dimension-table" && !definition) throw new Error("请选择一个维度");
		const existingDefinitionBinding = () => {
			if (!definition) throw new Error("请选择一个维度");
			return {
				mode: "EXISTING" as const,
				dimensionDefinitionRef: { dimensionDefinitionId: definition.id, revision: definition.revision },
			};
		};
		const command: CreateDimensionModelCommand = {
			operationId: crypto.randomUUID(),
			definitionBinding:
				preparedDraft.createKind === "dimension"
					? {
							mode: "CREATE",
							definition: {
								domainId: preparedDraft.domainId,
								name: preparedDraft.name.trim(),
								definition: preparedDraft.description.trim() || preparedDraft.name.trim(),
								ownerId: context.ownerId,
								reuseScope: preparedDraft.reuseScope,
								scopeType: "DOMAIN",
								attributes: update.fields?.map((field, index) => ({
									...(() => {
										const binding = update.standardBindings?.find((item) => item.fieldName === field.name);
										return {
											standardRef: binding?.standardElementId || null,
											standardVersion:
												binding?.standardElementVersion == null ? null : String(binding.standardElementVersion),
										};
									})(),
									code: (field.dimensionAttributeCode || field.name).toUpperCase(),
									name: field.displayName || field.name,
									definition: field.displayName || field.name,
									primaryKey: field.role === "KEY",
									order: index + 1,
								})),
							},
						}
					: existingDefinitionBinding(),
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
		return (await createDimensionModel(command)).currentModelSpec;
	}
	const created = await createModelSpec({
		planId: preparedDraft.planId,
		domainId: preparedDraft.domainId,
		modelType: update.modelType,
		name: preparedDraft.name.trim(),
		description: preparedDraft.description.trim() || null,
		idempotencyKey: crypto.randomUUID(),
	});
	return updateModelSpec(created, update);
}
