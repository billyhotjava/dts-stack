import { parseModelFieldNames } from "./modelSpecFieldRules.ts";
import type {
	CanonicalModelSpecView,
	CreateModelSpecCommand,
	ModelSpecFactShape,
	ModelSpecField,
	ModelSpecImplementationMode,
	ModelSpecLayer,
	ModelSpecMetricRef,
	ModelSpecRevisionConflictDetails,
	ModelSpecRevisionRef,
	ModelSpecSourceKind,
	ModelSpecSourceRef,
	ModelSpecSourceRole,
	ModelSpecStandardBinding,
	ModelSpecTimeSemanticsType,
	ModelSpecType,
	UpdateModelSpecCommand,
} from "./modelSpecV2Contract";

export const MODEL_TYPE_LABELS: Record<ModelSpecType, string> = {
	DIMENSION: "维度表",
	FACT: "明细表",
	SUMMARY: "汇总表",
	APPLICATION: "应用表",
};

export const MODEL_TYPE_DESCRIPTIONS: Record<ModelSpecType, string> = {
	DIMENSION: "登记组织、人员、项目、状态、时间等分析角度",
	FACT: "记录业务发生了什么，并明确一行数据代表什么",
	SUMMARY: "按稳定粒度汇总上游明细或维度模型",
	APPLICATION: "面向报表、接口或业务场景组织可直接消费的数据",
};

export const MODEL_STATUS_LABELS: Record<string, string> = {
	DRAFT: "草稿",
	DESIGNING: "设计中",
	VALIDATING: "验证中",
	READY_TO_PUBLISH: "待发布",
	PUBLISHED: "已发布",
	ARCHIVED: "已归档",
};

export function modelSpecEditorCopy(modelType: ModelSpecType) {
	if (modelType === "DIMENSION") {
		return {
			nameLabel: "维度名称",
			nameRequiredMessage: "请输入维度名称",
			descriptionLabel: "维度定义",
			descriptionRequiredMessage: "请输入维度定义",
		};
	}
	return {
		nameLabel: "模型名称",
		nameRequiredMessage: "请输入模型名称",
		descriptionLabel: "用途说明",
		descriptionRequiredMessage: undefined,
	};
}

export type ModelSpecSourceDraft = {
	kind: ModelSpecSourceKind;
	ref: string;
	layer: ModelSpecLayer;
	role: ModelSpecSourceRole;
	sourceBindingId: string;
	resolvedVersion: string;
	alias?: string;
	joinType?: ModelSpecSourceRef["joinType"];
	joinExpression?: string;
};

export type ModelSpecDraft = {
	planId: string;
	domainId: string;
	modelType: ModelSpecType;
	layer: ModelSpecLayer;
	name: string;
	description: string;
	implementationMode: ModelSpecImplementationMode;
	materialization: string;
	grainStatement: string;
	grainKeysText: string;
	keyDataType: string;
	sources: ModelSpecSourceDraft[];
	factShape?: ModelSpecFactShape;
	timeSemanticsType?: ModelSpecTimeSemanticsType;
	timeFieldsText: string;
	businessActivityRef: string;
	upstreamIds: string[];
	dimensionRefIds: string[];
	consumptionScenario: string;
	generationStrategyType: string;
	generationStrategyReference: string;
	fields: ModelSpecField[];
	metricRefs: ModelSpecMetricRef[];
	standardBindings: ModelSpecStandardBinding[];
	existingUpstreamPins: ModelSpecRevisionRef[];
	existingDimensionPins: ModelSpecRevisionRef[];
};

export type ModelSpecRevisionCandidate = Pick<CanonicalModelSpecView, "id" | "revision" | "modelType">;

const defaultLayer = (modelType: ModelSpecType): ModelSpecLayer => {
	if (modelType === "SUMMARY") return "DWS";
	if (modelType === "APPLICATION") return "ADS";
	return "DWD";
};

export function createEmptyModelSpecDraft(
	modelType: ModelSpecType,
	context: { planId?: string; domainId?: string } = {},
): ModelSpecDraft {
	return {
		planId: context.planId?.trim() || "",
		domainId: context.domainId?.trim() || "",
		modelType,
		layer: defaultLayer(modelType),
		name: "",
		description: "",
		implementationMode: "DESIGNER_GENERATED",
		materialization: "table",
		grainStatement: "",
		grainKeysText: "",
		keyDataType: "string",
		sources: [],
		timeFieldsText: "",
		businessActivityRef: "",
		upstreamIds: [],
		dimensionRefIds: [],
		consumptionScenario: "",
		generationStrategyType: "",
		generationStrategyReference: "",
		fields: [],
		metricRefs: [],
		standardBindings: [],
		existingUpstreamPins: [],
		existingDimensionPins: [],
	};
}

const optionalText = (value: string | null | undefined) => value?.trim() || undefined;

const buildFields = (draft: ModelSpecDraft, grainKeys: string[]): ModelSpecField[] => {
	const fields = [...(draft.fields ?? [])];
	const names = new Set(fields.map((field) => field.name.trim()).filter(Boolean));
	for (const key of grainKeys) {
		if (!names.has(key)) {
			fields.push({
				name: key,
				dataType: draft.keyDataType.trim() || "string",
				nullable: false,
				role: "KEY",
			});
			names.add(key);
		}
	}
	return fields;
};

const buildSources = (sources: ModelSpecSourceDraft[]): ModelSpecSourceRef[] =>
	sources.map((source, index) => ({
		kind: source.kind,
		ref: source.ref.trim(),
		layer: source.layer,
		role: source.role,
		alias: optionalText(source.alias),
		joinType: source.role === "JOINED" ? source.joinType || undefined : undefined,
		joinExpression: source.role === "JOINED" ? optionalText(source.joinExpression) : undefined,
		sortOrder: index,
		sourceBindingId: source.sourceBindingId.trim(),
		resolvedVersion: source.resolvedVersion.trim(),
	}));

const pinSelectedModels = (
	ids: string[],
	candidates: ModelSpecRevisionCandidate[],
	existing: ModelSpecRevisionRef[],
): ModelSpecRevisionRef[] => {
	const candidateById = new Map(candidates.map((candidate) => [candidate.id, candidate]));
	const existingById = new Map(existing.map((reference) => [reference.modelSpecId, reference]));
	return ids.map((id) => {
		const candidate = candidateById.get(id);
		if (candidate) return { modelSpecId: candidate.id, revision: candidate.revision };
		return existingById.get(id) || { modelSpecId: id, revision: 0 };
	});
};

export function buildModelSpecCreateCommand(
	draft: ModelSpecDraft,
	candidates: ModelSpecRevisionCandidate[],
	idempotencyKey: string,
): CreateModelSpecCommand {
	const grainKeys = parseModelFieldNames(draft.grainKeysText);
	const isFact = draft.modelType === "FACT";
	const isDerived = draft.modelType === "SUMMARY" || draft.modelType === "APPLICATION";
	const isApplication = draft.modelType === "APPLICATION";
	const timeFields = isFact ? parseModelFieldNames(draft.timeFieldsText || "") : [];
	const generationType = optionalText(draft.generationStrategyType);
	return {
		planId: draft.planId.trim(),
		domainId: draft.domainId.trim(),
		modelType: draft.modelType,
		layer: draft.layer,
		name: draft.name.trim(),
		description: optionalText(draft.description),
		implementationMode: draft.implementationMode,
		materialization: optionalText(draft.materialization),
		grain:
			draft.grainStatement.trim() || grainKeys.length > 0
				? { statement: draft.grainStatement.trim(), keys: grainKeys }
				: undefined,
		fields: buildFields(draft, grainKeys),
		sourceRefs:
			draft.modelType === "SUMMARY" || draft.modelType === "APPLICATION" ? [] : buildSources(draft.sources ?? []),
		dependsOn: isDerived
			? pinSelectedModels(draft.upstreamIds ?? [], candidates, draft.existingUpstreamPins ?? [])
			: [],
		dimensionRefs: isFact
			? pinSelectedModels(draft.dimensionRefIds ?? [], candidates, draft.existingDimensionPins ?? [])
			: [],
		metricRefs: draft.metricRefs ?? [],
		standardBindings: draft.standardBindings ?? [],
		businessActivityRef: isFact ? optionalText(draft.businessActivityRef) : undefined,
		factShape: isFact ? draft.factShape : undefined,
		timeSemantics:
			isFact && draft.timeSemanticsType && timeFields.length > 0
				? { type: draft.timeSemanticsType, fields: timeFields }
				: undefined,
		consumptionScenario: isApplication ? optionalText(draft.consumptionScenario) : undefined,
		generationStrategy: generationType
			? { type: generationType, reference: optionalText(draft.generationStrategyReference) }
			: undefined,
		idempotencyKey,
	};
}

export function buildModelSpecUpdateCommand(
	draft: ModelSpecDraft,
	candidates: ModelSpecRevisionCandidate[],
): UpdateModelSpecCommand {
	const { idempotencyKey: _idempotencyKey, ...command } = buildModelSpecCreateCommand(
		draft,
		candidates,
		"model-spec-update",
	);
	return command;
}

export function modelSpecDraftFromView(view: CanonicalModelSpecView): ModelSpecDraft {
	return {
		...createEmptyModelSpecDraft(view.modelType, { planId: view.planId, domainId: view.domainId }),
		layer: view.layer,
		name: view.name,
		description: view.description || "",
		implementationMode: view.implementationMode,
		materialization: view.materialization || "",
		grainStatement: view.grain?.statement || "",
		grainKeysText: view.grain?.keys.join(", ") || "",
		keyDataType: view.fields.find((field) => field.role === "KEY")?.dataType || "string",
		sources: view.sourceRefs.map((source) => ({
			kind: source.kind,
			ref: source.ref,
			layer: source.layer,
			role: source.role,
			sourceBindingId: source.sourceBindingId,
			resolvedVersion: source.resolvedVersion,
			alias: source.alias || undefined,
			joinType: source.joinType || undefined,
			joinExpression: source.joinExpression || undefined,
		})),
		factShape: view.factShape || undefined,
		timeSemanticsType: view.timeSemantics?.type,
		timeFieldsText: view.timeSemantics?.fields.join(", ") || "",
		businessActivityRef: view.businessActivityRef || "",
		upstreamIds: view.dependsOn.map((reference) => reference.modelSpecId),
		dimensionRefIds: view.dimensionRefs.map((reference) => reference.modelSpecId),
		consumptionScenario: view.consumptionScenario || "",
		generationStrategyType: view.generationStrategy?.type || "",
		generationStrategyReference: view.generationStrategy?.reference || "",
		fields: view.fields,
		metricRefs: view.metricRefs,
		standardBindings: view.standardBindings,
		existingUpstreamPins: view.dependsOn,
		existingDimensionPins: view.dimensionRefs,
	};
}

type ErrorLike = {
	response?: {
		status?: number;
		data?: { code?: string; data?: unknown };
	};
};

export function modelSpecRevisionConflict(error: unknown): ModelSpecRevisionConflictDetails | null {
	const candidate = error as ErrorLike;
	if (candidate?.response?.status !== 409 || candidate.response.data?.code !== "MODEL_SPEC_REVISION_CONFLICT") {
		return null;
	}
	const data = candidate.response.data.data as Partial<ModelSpecRevisionConflictDetails> | undefined;
	if (
		!data ||
		!Number.isInteger(data.currentRevision) ||
		typeof data.currentChecksum !== "string" ||
		typeof data.currentEtag !== "string"
	) {
		return null;
	}
	return data as ModelSpecRevisionConflictDetails;
}

export function isModelSpecStatusReadonly(error: unknown): boolean {
	const candidate = error as ErrorLike;
	return candidate?.response?.status === 409 && candidate.response.data?.code === "MODEL_SPEC_STATUS_READONLY";
}

export function modelSpecErrorMessage(error: unknown): string {
	const candidate = error as ErrorLike;
	const status = candidate?.response?.status;
	const code = candidate?.response?.data?.code;
	if (status === 403) return "当前账号只能浏览模型，请联系计划负责人申请编辑权限";
	if (status === 404) return "模型或其计划上下文已不存在，请返回模型中心刷新列表";
	if (status === 409 && code === "MODEL_SPEC_REVISION_CONFLICT") {
		return "模型已被其他人更新，请选择保留当前输入重试或加载最新版本";
	}
	if (status === 409 && code === "MODEL_SPEC_STATUS_READONLY") {
		return "模型状态已变化，当前输入已保留；请加载最新状态后继续";
	}
	if (status === 409) return "当前名称或请求标识已被占用，请调整后重试";
	if (status === 422 || status === 400) return "请检查标红字段；当前输入已保留";
	if (status === 428) return "模型版本信息已失效，请刷新后再保存";
	return "操作未完成，当前输入已保留，请稍后重试";
}

const MODEL_SPEC_ISSUE_MESSAGES: Record<string, string> = {
	MODEL_SPEC_PLAN_REQUIRED: "请选择建设计划",
	MODEL_SPEC_DOMAIN_REQUIRED: "请选择业务分类",
	MODEL_SPEC_TYPE_REQUIRED: "请选择表类型",
	MODEL_SPEC_LAYER_REQUIRED: "请选择数仓分层",
	MODEL_SPEC_NAME_REQUIRED: "请输入模型名称",
	MODEL_SPEC_GRAIN_REQUIRED: "请说明一行数据代表什么，并填写粒度键",
	MODEL_SPEC_GRAIN_INVALID: "请完整填写一行含义和粒度键",
	MODEL_SPEC_DIMENSION_KEY_REQUIRED: "请至少填写一个稳定的维度键",
	MODEL_SPEC_SOURCE_REQUIRED: "请至少选择一个已确认的数据来源",
	MODEL_SPEC_SOURCE_INVALID: "请完整填写来源引用、登记 ID 和版本",
	MODEL_SPEC_UPSTREAM_REQUIRED: "请至少选择一个上游模型",
	MODEL_SPEC_DEPENDENCY_INVALID: "请选择带有效版本的上游模型",
	MODEL_SPEC_CONSUMPTION_SCENARIO_REQUIRED: "请说明应用表服务的报表、接口或业务场景",
	MODEL_SPEC_TIME_SEMANTICS_INVALID: "请同时选择业务时间类型并填写对应字段",
	MODEL_SPEC_FIELD_INVALID: "请检查字段名称、类型和角色",
};

export function modelSpecIssueMessage(code: string): string {
	return MODEL_SPEC_ISSUE_MESSAGES[code] || "请检查当前字段";
}

type CryptoRandomSource = {
	randomUUID?: () => string;
	getRandomValues?: (values: Uint8Array) => Uint8Array;
};

export function createModelSpecIdempotencyKey(
	randomSource: CryptoRandomSource | undefined = globalThis.crypto as CryptoRandomSource | undefined,
): string {
	if (typeof randomSource?.randomUUID === "function") return randomSource.randomUUID();
	if (typeof randomSource?.getRandomValues !== "function") {
		throw new Error("A cryptographic random source is required to create a ModelSpec");
	}
	const bytes = randomSource.getRandomValues(new Uint8Array(16));
	bytes[6] = (bytes[6] & 0x0f) | 0x40;
	bytes[8] = (bytes[8] & 0x3f) | 0x80;
	const value = Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
	return `${value.slice(0, 8)}-${value.slice(8, 12)}-${value.slice(12, 16)}-${value.slice(16, 20)}-${value.slice(20)}`;
}
