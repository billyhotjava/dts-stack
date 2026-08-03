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
import {
	getWarehousePlanCategories,
	listWarehousePlans,
	type WarehousePlanCategoryBindingView,
	type WarehousePlanHeader,
} from "@/api/warehousePlanApi";
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
	plans: WarehousePlanHeader[];
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
		planId: context.plans[0]?.id || "",
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
		reuseScope: model.dimensionProfile?.reuseScope || "DOMAIN",
		dimensionDefinitionId: model.dimensionDefinitionRef?.dimensionDefinitionId || "",
		standardBindings: model.standardBindings.map((binding) => ({ ...binding })),
	};
}

export async function loadModelWorkbenchContext(): Promise<ModelWorkbenchContext> {
	const [plans, domains, models, standards] = await Promise.all([
		listWarehousePlans(),
		catalogDomainService.list(),
		listModelSpecs(),
		listModelFieldStandardOptions(),
	]);
	return {
		plans: plans.filter((plan) => plan.lifecycleStatus !== "ARCHIVED"),
		domains,
		models: models.filter((model) => model.status !== "ARCHIVED"),
		standards,
	};
}

export async function loadConfirmedPlanDomains(planId: string): Promise<WarehousePlanCategoryBindingView[]> {
	if (!planId) return [];
	const categories = await getWarehousePlanCategories(planId);
	return categories.value.domainBindings.filter(
		(item) => item.confirmationStatus === "CONFIRMED" && item.resolutionStatus === "AVAILABLE",
	);
}

export const loadCurrentDimensionDefinitions = (domainId: string) =>
	domainId ? listDimensionDefinitions({ domainId, status: "CURRENT", offset: 0, limit: 500 }) : Promise.resolve([]);

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
	if (!draft.planId) missing.push("建设计划");
	if (!draft.domainId) missing.push("数据域");
	if (!draft.name.trim()) missing.push("模型名称");
	if (!draft.grainStatement.trim()) missing.push("模型粒度");
	if (!draft.fields.length) missing.push("字段");
	if (draft.fields.some((field) => !field.name.trim() || !field.dataType.trim())) missing.push("完整字段定义");
	if (!draft.fields.some((field) => field.role === "KEY")) missing.push("业务主键字段");
	if (missing.length) throw new Error(`请补齐：${Array.from(new Set(missing)).join("、")}`);
	const issues = validateModelSpecUpdate(update);
	if (issues.length)
		throw new Error(issues.map((issue) => `${issue.field}：${issue.message || issue.code}`).join("；"));
};

export async function saveModelDraft(draft: ModelDraft, context: ModelSaveContext): Promise<CanonicalModelSpecView> {
	if (draft.base && draft.base.compatibilityMode !== "CANONICAL") throw new Error("历史只读模型不能在工作台中修改");
	const update = buildUpdate(draft);
	validateDraft(draft, update);
	if (draft.base) return updateModelSpec(draft.base, update);
	if (update.modelType === "DIMENSION") {
		if (!context.ownerId) throw new Error("当前登录身份缺少人员 ID，不能创建维度定义");
		const definition = context.dimensionDefinitions.find((item) => item.id === draft.dimensionDefinitionId);
		if (draft.createKind === "dimension-table" && !definition) throw new Error("请选择一个现行维度定义");
		const existingDefinitionBinding = () => {
			if (!definition) throw new Error("请选择一个现行维度定义");
			return {
				mode: "EXISTING" as const,
				dimensionDefinitionRef: { dimensionDefinitionId: definition.id, revision: definition.revision },
			};
		};
		const command: CreateDimensionModelCommand = {
			operationId: crypto.randomUUID(),
			definitionBinding:
				draft.createKind === "dimension"
					? {
							mode: "CREATE",
							definition: {
								domainId: draft.domainId,
								name: draft.name.trim(),
								definition: draft.description.trim() || draft.name.trim(),
								ownerId: context.ownerId,
								reuseScope: draft.reuseScope,
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
		planId: draft.planId,
		domainId: draft.domainId,
		modelType: update.modelType,
		name: draft.name.trim(),
		description: draft.description.trim() || null,
		idempotencyKey: crypto.randomUUID(),
	});
	return updateModelSpec(created, update);
}
