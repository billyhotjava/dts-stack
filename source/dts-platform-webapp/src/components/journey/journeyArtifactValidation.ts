// 阶段真实性校验：判断旅程上下文参数是否指向真实存在的对象。
// 原则：valid=已验真，invalid=确认不存在（阶段应 blocked），unknown=缺少校验能力（展示"待确认"，不给 done 的纯绿）。
// standardDraftId 可用本地草稿清单验真；其余对象需后端接口，缺口以 apiName 显式标注。
import {
	JOURNEY_CONTEXT_PARAM_KEYS,
	JOURNEY_CONTEXT_PARAM_LABELS,
	type DataProductJourneyContextParams,
	type JourneyContextParamKey,
} from "./journeyContext";

export type ArtifactValidationStatus = "valid" | "invalid" | "unknown";

export type ArtifactValidationResult = {
	key: JourneyContextParamKey;
	value: string;
	status: ArtifactValidationStatus;
	reason?: string;
	apiName?: string;
};

export type ArtifactValidator = (key: JourneyContextParamKey, value: string) => ArtifactValidationStatus;

export type ArtifactValidationMap = Partial<Record<JourneyContextParamKey, ArtifactValidationResult>>;

export const ARTIFACT_VALIDATION_API_NAMES: Record<JourneyContextParamKey, string> = {
	sourceId: "GET /api/infra/data-sources/{id}",
	planId: "GET /api/modeling/warehouse-plans/{id}",
	planningId: "GET /api/governance/warehouse-plannings/{id}",
	domainId: "GET /api/governance/subject-domains/{id}",
	processId: "GET /api/governance/subject-domains/{domainId}/processes/{processId}",
	warehouseLayer: "GET /api/governance/warehouse-plannings/{id}",
	modelingMode: "GET /api/modeling/models/{id}",
	standardDraftId: "GET /api/modeling/standard-binding-drafts/{id}",
	modelId: "GET /api/modeling/models/{id}",
	metricId: "GET /api/metrics/{id}",
	serviceId: "GET /api/services/{id}",
	runId: "GET /api/ops/runs/{id}",
	auditId: "GET /api/audit/records/{id}",
};

export type StandardDraftLookup = {
	findDraft: (id: string) => unknown | null;
	isBackendDraftId: (id: string) => boolean;
};

export type DataProductArtifactValidatorDeps = {
	standardDraft?: StandardDraftLookup;
};

// 默认校验器：standardDraftId 用注入的本地草稿清单验真（本地命中=valid，UUID 后端形态=unknown 等待接口，
// 其余写法=invalid）；其他参数一律 unknown（后端校验接口未提供，见 ARTIFACT_VALIDATION_API_NAMES）。
export const createDataProductArtifactValidator = (
	deps: DataProductArtifactValidatorDeps = {},
): ArtifactValidator => {
	return (key, value) => {
		if (key === "standardDraftId" && deps.standardDraft) {
			if (deps.standardDraft.findDraft(value)) return "valid";
			if (deps.standardDraft.isBackendDraftId(value)) return "unknown";
			return "invalid";
		}
		return "unknown";
	};
};

export const resolveArtifactValidations = (
	params: DataProductJourneyContextParams,
	validator: ArtifactValidator,
): ArtifactValidationResult[] =>
	JOURNEY_CONTEXT_PARAM_KEYS.flatMap((key) => {
		const value = params[key];
		if (!value) return [];
		let status: ArtifactValidationStatus;
		try {
			status = validator(key, value);
		} catch {
			status = "unknown";
		}
		const result: ArtifactValidationResult = {
			key,
			value,
			status,
			reason:
				status === "invalid"
					? `${JOURNEY_CONTEXT_PARAM_LABELS[key]}「${value}」不存在或已失效`
					: status === "unknown"
						? "缺少后端校验接口，按待确认处理"
						: undefined,
			apiName: status === "valid" ? undefined : ARTIFACT_VALIDATION_API_NAMES[key],
		};
		return [result];
	});

export const toArtifactValidationMap = (results: ArtifactValidationResult[]): ArtifactValidationMap =>
	results.reduce<ArtifactValidationMap>((acc, result) => {
		acc[result.key] = result;
		return acc;
	}, {});
