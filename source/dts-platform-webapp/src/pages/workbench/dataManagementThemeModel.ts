import type {
	GoldenChainDetail,
	GoldenChainStage,
	GoldenChainStageSnapshot,
	GoldenChainStageStatus,
	GoldenChainSummary,
} from "@/api/services/goldenChainService";

export type DataManagementThemeKey = "business" | "quality" | "delivery" | "customer";
export type DataManagementTone = "default" | "processing" | "success" | "warning" | "danger";

export type DataManagementThemeDefinition = {
	key: DataManagementThemeKey;
	title: string;
	description: string;
	keywords: string[];
};

export type DataManagementStatus = {
	label: string;
	tone: DataManagementTone;
};

export type DataManagementAction = {
	label: string;
	route: string;
};

export type DataManagementThemeState = DataManagementThemeDefinition & {
	chainCount: number;
	relatedChains: GoldenChainSummary[];
	dataAvailability: DataManagementStatus;
	governance: DataManagementStatus;
	consumption: DataManagementStatus;
	operation: DataManagementStatus;
	primaryAction: DataManagementAction;
	progressPercent: number;
	failureReason?: string;
	nextAction?: string;
	evidenceRefs: string[];
};

const STAGE_SEQUENCE: Record<GoldenChainStage, number> = {
	DRAFT: 0,
	SOURCE_READY: 10,
	INGESTION_READY: 20,
	ODS_READY: 30,
	MODEL_READY: 40,
	GOVERNANCE_READY: 50,
	RELEASE_READY: 60,
	CONSUMABLE: 70,
	OPERATED: 80,
};

export const DEFAULT_DATA_MANAGEMENT_THEMES: DataManagementThemeDefinition[] = [
	{
		key: "business",
		title: "经营分析",
		description: "面向经营日报、订单统计、收入趋势和管理层看板的主题。",
		keywords: ["经营", "订单", "销售", "营收", "收入", "分析", "报表"],
	},
	{
		key: "quality",
		title: "质量管理",
		description: "面向质量问题、整改闭环、检查结果和治理达标情况的主题。",
		keywords: ["质量", "质检", "问题", "整改", "合规", "检查"],
	},
	{
		key: "delivery",
		title: "项目交付",
		description: "面向项目进度、交付状态、负责人和里程碑跟踪的主题。",
		keywords: ["项目", "交付", "进度", "合同", "工单", "里程碑"],
	},
	{
		key: "customer",
		title: "客户服务",
		description: "面向客户服务、满意度、订阅使用和服务质量的主题。",
		keywords: ["客户", "服务", "满意", "客服", "订阅", "会员"],
	},
];

const EMPTY_THEME_STATUS = {
	dataAvailability: { label: "未接入", tone: "default" as const },
	governance: { label: "待建设", tone: "default" as const },
	consumption: { label: "待发布", tone: "default" as const },
	operation: { label: "待运行", tone: "default" as const },
	primaryAction: { label: "配置数据来源", route: "/foundation/data-sources" },
};

const statusRank = (status: GoldenChainStageStatus) => {
	if (status === "BLOCKED") return 3;
	if (status === "PENDING") return 2;
	if (status === "READY") return 1;
	return 0;
};

const normalizeText = (value: string | undefined) => (value || "").toLowerCase();

const stageFromDetail = (detail: GoldenChainDetail | undefined, stage: GoldenChainStage) =>
	detail?.stages?.find((item) => item.stage === stage);

const effectiveStageStatus = (
	chain: GoldenChainSummary,
	detail: GoldenChainDetail | undefined,
	stage: GoldenChainStage,
): GoldenChainStageStatus => {
	const snapshot = stageFromDetail(detail, stage);
	if (snapshot?.status) return snapshot.status;
	if (chain.currentStage === stage && chain.status === "BLOCKED") return "BLOCKED";
	return STAGE_SEQUENCE[chain.currentStage] >= STAGE_SEQUENCE[stage] ? "READY" : "PENDING";
};

const aggregateStageStatus = (
	chains: GoldenChainSummary[],
	detailsByChainKey: Record<string, GoldenChainDetail | undefined>,
	stage: GoldenChainStage,
) => {
	return chains.reduce<GoldenChainStageStatus>((acc, chain) => {
		const status = effectiveStageStatus(chain, detailsByChainKey[chain.chainKey], stage);
		return statusRank(status) > statusRank(acc) ? status : acc;
	}, "READY");
};

const statusText = (
	status: GoldenChainStageStatus,
	readyLabel: string,
	pendingLabel: string,
	blockedLabel: string,
): DataManagementStatus => {
	if (status === "READY" || status === "SKIPPED") {
		return { label: readyLabel, tone: "success" };
	}
	if (status === "BLOCKED") {
		return { label: blockedLabel, tone: "warning" };
	}
	return { label: pendingLabel, tone: "processing" };
};

const classifyChain = (chain: GoldenChainSummary): DataManagementThemeKey => {
	const text = normalizeText(`${chain.displayName} ${chain.chainKey} ${chain.currentStageLabel} ${chain.owner}`);
	const matched = DEFAULT_DATA_MANAGEMENT_THEMES.find((theme) =>
		theme.keywords.some((keyword) => text.includes(keyword.toLowerCase())),
	);
	return matched?.key || "business";
};

const collectSnapshots = (
	chains: GoldenChainSummary[],
	detailsByChainKey: Record<string, GoldenChainDetail | undefined>,
) =>
	chains.flatMap((chain) => detailsByChainKey[chain.chainKey]?.stages || []);

const mostImportantIssue = (snapshots: GoldenChainStageSnapshot[]) =>
	snapshots.find((item) => item.status === "BLOCKED" && (item.nextAction || item.failureReason));

const evidenceRefs = (snapshots: GoldenChainStageSnapshot[]) =>
	Array.from(new Set(snapshots.map((item) => item.evidenceRef).filter((item): item is string => Boolean(item))));

const routeForIssue = (issue?: GoldenChainStageSnapshot) => {
	if (!issue) return "/bi/report-factory";
	if (issue.stage === "SOURCE_READY" || issue.stage === "INGESTION_READY" || issue.stage === "ODS_READY") {
		return "/explore/etl/transform";
	}
	if (issue.stage === "MODEL_READY") return "/modeling/semantic-center";
	if (issue.stage === "GOVERNANCE_READY" || issue.stage === "RELEASE_READY") return "/governance/quality";
	if (issue.stage === "CONSUMABLE") return "/services/apis";
	if (issue.stage === "OPERATED") return "/ops/overview";
	return "/foundation/data-sources";
};

const progressFor = (themeChains: GoldenChainSummary[], detailsByChainKey: Record<string, GoldenChainDetail | undefined>) => {
	if (themeChains.length === 0) return 0;
	const chainProgress = themeChains.map((chain) => {
		const detail = detailsByChainKey[chain.chainKey];
		if (detail?.stages?.length) {
			const ready = detail.stages.filter((item) => item.status === "READY" || item.status === "SKIPPED").length;
			return Math.round((ready / detail.stages.length) * 100);
		}
		return Math.round((STAGE_SEQUENCE[chain.currentStage] / STAGE_SEQUENCE.OPERATED) * 100);
	});
	return Math.round(chainProgress.reduce((sum, item) => sum + item, 0) / chainProgress.length);
};

const buildThemeState = (
	definition: DataManagementThemeDefinition,
	themeChains: GoldenChainSummary[],
	detailsByChainKey: Record<string, GoldenChainDetail | undefined>,
): DataManagementThemeState => {
	if (themeChains.length === 0) {
		return {
			...definition,
			chainCount: 0,
			relatedChains: [],
			...EMPTY_THEME_STATUS,
			progressPercent: 0,
			evidenceRefs: [],
		};
	}

	const snapshots = collectSnapshots(themeChains, detailsByChainKey);
	const issue = mostImportantIssue(snapshots);
	const dataAvailability = statusText(
		aggregateStageStatus(themeChains, detailsByChainKey, "ODS_READY"),
		"可用",
		"建设中",
		"接入异常",
	);
	const governance = statusText(
		aggregateStageStatus(themeChains, detailsByChainKey, "GOVERNANCE_READY"),
		"已达标",
		"待治理",
		"待治理",
	);
	const consumption = statusText(
		aggregateStageStatus(themeChains, detailsByChainKey, "CONSUMABLE"),
		"已发布",
		"待发布",
		"待发布",
	);
	const operation = statusText(
		aggregateStageStatus(themeChains, detailsByChainKey, "OPERATED"),
		"运行健康",
		"待运行",
		"运行异常",
	);

	return {
		...definition,
		chainCount: themeChains.length,
		relatedChains: themeChains,
		dataAvailability,
		governance,
		consumption,
		operation,
		primaryAction: {
			label: issue?.nextAction || issue?.failureReason || "查看可用成果",
			route: routeForIssue(issue),
		},
		progressPercent: progressFor(themeChains, detailsByChainKey),
		failureReason: issue?.failureReason,
		nextAction: issue?.nextAction,
		evidenceRefs: evidenceRefs(snapshots),
	};
};

export function buildDataManagementThemes(
	chains: GoldenChainSummary[],
	detailsByChainKey: Record<string, GoldenChainDetail | undefined>,
): DataManagementThemeState[] {
	const grouped = DEFAULT_DATA_MANAGEMENT_THEMES.reduce<Record<DataManagementThemeKey, GoldenChainSummary[]>>((acc, theme) => {
		acc[theme.key] = [];
		return acc;
	}, {} as Record<DataManagementThemeKey, GoldenChainSummary[]>);

	for (const chain of chains) {
		grouped[classifyChain(chain)].push(chain);
	}

	return DEFAULT_DATA_MANAGEMENT_THEMES.map((theme) => buildThemeState(theme, grouped[theme.key], detailsByChainKey));
}
