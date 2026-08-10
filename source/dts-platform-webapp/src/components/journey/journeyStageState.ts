import type { ArtifactValidationMap, ArtifactValidationResult } from "./journeyArtifactValidation";
import {
	buildJourneyUrl,
	type DataProductJourneyContextParams,
	type DataProductJourneyStageKey,
	type JourneyContextParamKey,
} from "./journeyContext";

export type DataProductJourneyStageStatus = "not_started" | "blocked" | "ready" | "in_progress" | "done";

// artifact 验真状态：verified=对象已验真；invalid=对象确认无效；unverified=无校验能力或无对象可验。
export type JourneyArtifactVerification = "verified" | "invalid" | "unverified";

type JourneyStageTone = "default" | "processing" | "success" | "warning";

export type DataProductJourneyAction = {
	label: string;
	route: string;
	url: string;
	description: string;
	disabled?: boolean;
};

export type DataProductJourneyBlocker = {
	reason: string;
	apiName: string;
	recoveryAction: string;
};

export type DataProductJourneyEvidenceRef = {
	label: string;
	route: string;
	url: string;
};

export type DataProductJourneyStageState = {
	key: DataProductJourneyStageKey;
	stageKey: DataProductJourneyStageKey;
	title: string;
	desc: string;
	result: string;
	evidence: string;
	owner: string;
	gap: string;
	nextStep: string;
	nextAction: DataProductJourneyAction;
	evidenceRefs: DataProductJourneyEvidenceRef[];
	blocker?: DataProductJourneyBlocker;
	status: DataProductJourneyStageStatus;
	verification: JourneyArtifactVerification;
	artifactParam?: JourneyContextParamKey;
	tone: JourneyStageTone;
	route: string;
	action: string;
	supportingRoute: string;
	supportingAction: string;
	tagColor: string;
};

type JourneyStageDefinition = {
	stageKey: DataProductJourneyStageKey;
	title: string;
	desc: string;
	result: string;
	evidence: string;
	owner: string;
	defaultGap: string;
	doneGap: string;
	nextStep: string;
	route: string;
	action: string;
	supportingRoute: string;
	supportingAction: string;
	tagColor: string;
	requiredParams: JourneyContextParamKey[];
	artifactParam?: JourneyContextParamKey;
	apiName?: string;
	apiRequiredForCompletion?: boolean;
};

const BLOCKED_STATUS_STATE = {
	status: "blocked",
	tone: "warning",
} as const;

const PARAM_LABELS: Record<JourneyContextParamKey, string> = {
	sourceId: "数据源",
	planId: "建设计划",
	domainId: "主题域",
	standardDraftId: "标准草稿",
	modelSpecId: "模型",
	revision: "模型版本",
	modelType: "模型类型",
	metricId: "指标",
	serviceId: "服务",
	runId: "运行",
	auditId: "审计",
};

export const DATA_PRODUCT_JOURNEY_STAGE_DEFINITIONS: JourneyStageDefinition[] = [
	{
		stageKey: "integration",
		title: "数据集成",
		desc: "接入业务系统或文件，完成连接、结构探测和同步任务。",
		result: "拿到可运行的数据链路",
		evidence: "连接测试、字段探测、同步预检",
		owner: "数据工程师",
		defaultGap: "缺少数据源：请选择业务系统并完成连接测试。",
		doneGap: "数据源上下文已就绪，可继续进入数仓规划。",
		nextStep: "选择业务系统并完成连接测试",
		route: "/foundation/data-sources",
		action: "配置数据源",
		supportingRoute: "/foundation/data-sources",
		supportingAction: "生成同步任务",
		tagColor: "blue",
		requiredParams: [],
		artifactParam: "sourceId",
	},
	{
		stageKey: "planning",
		title: "数仓规划",
		desc: "定义主题域、分层、数据域和业务过程，决定数据进仓后的组织方式。",
		result: "确认数据应该落到哪个业务主题",
		evidence: "主题域、业务过程、资产归属",
		owner: "数据架构师",
		defaultGap: "缺少数据源：数仓规划需要先知道业务表来源。",
		doneGap: "数仓规划入口已具备数据来源，可确认主题域和分层策略。",
		nextStep: "确认主题域、业务过程和分层策略",
		route: "/data-architecture?view=business-domains",
		action: "确认数仓规划",
		supportingRoute: "/catalog/metadata-management",
		supportingAction: "核对资产目录",
		tagColor: "purple",
		requiredParams: ["sourceId"],
	},
	{
		stageKey: "standards",
		title: "数据标准",
		desc: "把业务术语、数据元、码表和标准模板绑定到字段。",
		result: "让字段命名、类型和口径可复用",
		evidence: "数据元、码表、落标覆盖率",
		owner: "数据管家",
		defaultGap: "缺少标准草稿：请套用标准包并生成字段落标草稿。",
		doneGap: "标准草稿已进入上下文，可继续生成模型候选。",
		nextStep: "维护数据元并生成字段落标草稿",
		route: "/governance/standards/elements",
		action: "维护数据元",
		supportingRoute: "/governance/standards/reference",
		supportingAction: "维护码表",
		tagColor: "cyan",
		requiredParams: ["sourceId"],
		artifactParam: "standardDraftId",
	},
	{
		stageKey: "modeling",
		title: "维度建模",
		desc: "从标准字段生成模型草稿，再在模型中心或 SQL 建模里完善。",
		result: "形成事实、维度和汇总模型",
		evidence: "模型字段、血缘、校验结果",
		owner: "建模工程师",
		defaultGap: "缺少模型：请先用标准草稿生成模型候选。",
		doneGap: "模型上下文已就绪，可继续沉淀指标口径。",
		nextStep: "进入模型中心并生成 SQL 草稿",
		route: "/data-modeling/dimensions/workbench",
		action: "进入模型工作台",
		supportingRoute: "/data-modeling/dimensions/workbench?mode=implementation",
		supportingAction: "高级建模",
		tagColor: "geekblue",
		requiredParams: ["standardDraftId"],
		artifactParam: "modelSpecId",
	},
	{
		stageKey: "metrics",
		title: "数据指标",
		desc: "基于模型定义原子指标、派生指标和口径说明。",
		result: "把业务问题沉淀为指标资产",
		evidence: "指标口径、计算逻辑、责任人",
		owner: "业务分析师",
		defaultGap: "缺少指标：请基于模型字段绑定指标口径。",
		doneGap: "指标上下文已就绪，可进入开发和发布门禁。",
		nextStep: "基于模型字段设计指标口径",
		route: "/data-modeling/metrics/atomic",
		action: "设计指标",
		supportingRoute: "/data-modeling/home/workspace",
		supportingAction: "查看建模概览",
		tagColor: "green",
		requiredParams: ["modelSpecId"],
		artifactParam: "metricId",
	},
	{
		stageKey: "development",
		title: "数据开发",
		desc: "把同步、清洗、模型生成和指标汇总编排成可运行任务。",
		result: "让数据产品能按调度稳定产出",
		evidence: "任务 DAG、运行日志、补数记录",
		owner: "数据开发工程师",
		defaultGap: "缺少模型：数据开发需要先确定可运行模型。",
		doneGap: "运行上下文已就绪，可继续发布服务。",
		nextStep: "编排开发任务并执行编译测试",
		route: "/explore/etl/scripts",
		action: "编排数据开发",
		supportingRoute: "/explore/etl/orchestration",
		supportingAction: "查看调度",
		tagColor: "orange",
		requiredParams: ["modelSpecId"],
		artifactParam: "runId",
	},
	{
		stageKey: "service",
		title: "数据服务",
		desc: "把可信资产发布为报表、API 或数据产品，纳入权限和消费验收。",
		result: "交付业务可用的数据消费入口",
		evidence: "API、报表、授权记录",
		owner: "服务发布人",
		defaultGap: "缺少服务：请从模型或指标上下文发布 API、报表或数据产品。",
		doneGap: "服务上下文已就绪，可继续收集运行证据。",
		nextStep: "选择消费目标并配置授权",
		route: "/services/apis",
		action: "发布数据 API",
		supportingRoute: "/bi/dashboards",
		supportingAction: "创建报表",
		tagColor: "magenta",
		requiredParams: ["modelSpecId"],
		artifactParam: "serviceId",
	},
	{
		stageKey: "evidence",
		title: "运行证据",
		desc: "回看任务、质量、服务调用和告警，把交付状态变成客户可验收证据。",
		result: "证明链路持续可用",
		evidence: "实例、告警、质量检查、服务日志",
		owner: "运维与数据管家",
		defaultGap: "缺少服务：运行证据需要先绑定已发布服务。",
		doneGap: "审计证据已进入上下文，可形成客户验收包。",
		nextStep: "查看实例日志并形成验收证据",
		route: "/ops/instances",
		action: "查看运行证据",
		supportingRoute: "/ops/overview",
		supportingAction: "运行概览",
		tagColor: "red",
		requiredParams: ["serviceId"],
		artifactParam: "auditId",
		apiName: "GET /api/data-product/acceptance-package",
		apiRequiredForCompletion: true,
	},
];

const toParams = (input?: URLSearchParams | DataProductJourneyContextParams): DataProductJourneyContextParams => {
	if (!input) return {};
	if (!(input instanceof URLSearchParams)) return input;
	return Object.fromEntries(
		Object.entries(PARAM_LABELS).flatMap(([key]) => {
			const value = input.get(key);
			return value ? [[key, value]] : [];
		}),
	) as DataProductJourneyContextParams;
};

const missingRequiredParams = (definition: JourneyStageDefinition, params: DataProductJourneyContextParams) =>
	definition.requiredParams.filter((key) => !params[key]);

export const resolveJourneyGap = (
	definition: JourneyStageDefinition,
	params: DataProductJourneyContextParams,
	missingParams = missingRequiredParams(definition, params),
) => {
	if (missingParams.length > 0) {
		return `缺少${PARAM_LABELS[missingParams[0]]}：请先补齐上游阶段上下文。`;
	}
	if (definition.artifactParam && params[definition.artifactParam]) {
		return definition.doneGap;
	}
	if (definition.apiRequiredForCompletion && definition.apiName) {
		return `API 缺口：需要 ${definition.apiName} 返回验收证据聚合状态。`;
	}
	return definition.defaultGap;
};

export const resolveJourneyNextAction = (
	definition: JourneyStageDefinition,
	params: DataProductJourneyContextParams,
	gap = resolveJourneyGap(definition, params),
): DataProductJourneyAction => ({
	label: definition.action,
	route: definition.route,
	url: buildJourneyUrl(definition.route, params),
	description: gap,
});

const toValidationMap = (validations?: ArtifactValidationMap | ArtifactValidationResult[]): ArtifactValidationMap => {
	if (!validations) return {};
	if (Array.isArray(validations)) {
		return validations.reduce<ArtifactValidationMap>((acc, result) => {
			acc[result.key] = result;
			return acc;
		}, {});
	}
	return validations;
};

export const resolveDataProductJourneyStageState = (
	stageKey: DataProductJourneyStageKey,
	input?: URLSearchParams | DataProductJourneyContextParams,
	validations?: ArtifactValidationMap | ArtifactValidationResult[],
): DataProductJourneyStageState => {
	const params = toParams(input);
	const definition =
		DATA_PRODUCT_JOURNEY_STAGE_DEFINITIONS.find((item) => item.stageKey === stageKey) ||
		DATA_PRODUCT_JOURNEY_STAGE_DEFINITIONS[0];
	const missingParams = missingRequiredParams(definition, params);
	const validationMap = toValidationMap(validations);
	const artifactValue = definition.artifactParam ? params[definition.artifactParam] : undefined;
	const artifactValidation =
		definition.artifactParam && artifactValue ? validationMap[definition.artifactParam] : undefined;
	const invalidArtifact = artifactValidation?.status === "invalid";
	const verification: JourneyArtifactVerification = artifactValue
		? artifactValidation?.status === "valid"
			? "verified"
			: invalidArtifact
				? "invalid"
				: "unverified"
		: "unverified";
	const blocker =
		missingParams.length > 0
			? {
					reason: `缺少${PARAM_LABELS[missingParams[0]]}`,
					apiName: "前端上下文参数",
					recoveryAction: "回到上游阶段补齐上下文",
				}
			: invalidArtifact && definition.artifactParam
				? {
						reason: `上下文对象无效：${PARAM_LABELS[definition.artifactParam]}`,
						apiName: artifactValidation?.apiName ?? "前端上下文参数",
						recoveryAction: "清除该参数并重新选择",
					}
				: definition.apiRequiredForCompletion &&
						definition.apiName &&
						definition.artifactParam &&
						!params[definition.artifactParam]
					? {
							reason: "API 缺口",
							apiName: definition.apiName,
							recoveryAction: "补充验收包聚合接口或先查看运行实例",
						}
					: undefined;
	const hasArtifact = definition.artifactParam
		? Boolean(artifactValue) && !invalidArtifact
		: missingParams.length === 0;
	const status: DataProductJourneyStageStatus = blocker
		? BLOCKED_STATUS_STATE.status
		: hasArtifact
			? "done"
			: definition.requiredParams.length === 0
				? "ready"
				: "in_progress";
	const tone: JourneyStageTone =
		status === "blocked"
			? BLOCKED_STATUS_STATE.tone
			: status === "done"
				? "success"
				: status === "in_progress"
					? "processing"
					: "default";
	const gap =
		invalidArtifact && artifactValidation?.reason
			? artifactValidation.reason
			: resolveJourneyGap(definition, params, missingParams);
	const nextAction = resolveJourneyNextAction(definition, params, gap);

	return {
		key: definition.stageKey,
		stageKey: definition.stageKey,
		title: definition.title,
		desc: definition.desc,
		result: definition.result,
		evidence: definition.evidence,
		owner: definition.owner,
		gap,
		nextStep: nextAction.description,
		nextAction,
		evidenceRefs: [
			{
				label: definition.supportingAction,
				route: definition.supportingRoute,
				url: buildJourneyUrl(definition.supportingRoute, params),
			},
		],
		blocker: blocker,
		status,
		verification,
		artifactParam: definition.artifactParam,
		tone,
		route: definition.route,
		action: definition.action,
		supportingRoute: definition.supportingRoute,
		supportingAction: definition.supportingAction,
		tagColor: definition.tagColor,
	};
};

export const resolveDataProductJourneyStageStates = (
	input?: URLSearchParams | DataProductJourneyContextParams,
	validations?: ArtifactValidationMap | ArtifactValidationResult[],
) =>
	DATA_PRODUCT_JOURNEY_STAGE_DEFINITIONS.map((definition) =>
		resolveDataProductJourneyStageState(definition.stageKey, input, validations),
	);
