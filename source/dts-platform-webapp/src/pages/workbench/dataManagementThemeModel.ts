import type {
	GoldenChainDetail,
	GoldenChainStage,
	GoldenChainStageSnapshot,
	GoldenChainStageStatus,
	GoldenChainSummary,
} from "@/api/services/goldenChainService";

export type DataManagementThemeKey = string;
export type DataManagementTone = "default" | "processing" | "success" | "warning" | "danger";

export type DataManagementThemeDefinition = {
	key: DataManagementThemeKey;
	title: string;
	description: string;
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

export const DEFAULT_DATA_MANAGEMENT_THEMES: DataManagementThemeDefinition[] = [];

const statusRank = (status: GoldenChainStageStatus) => {
	if (status === "BLOCKED") return 3;
	if (status === "PENDING") return 2;
	if (status === "READY") return 1;
	return 0;
};

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

const themeDefinitionFromChain = (chain: GoldenChainSummary): DataManagementThemeDefinition => ({
	key: chain.chainKey,
	title: chain.displayName || chain.chainKey,
	description: "来自现场已配置的黄金链路；业务主题名称和口径以客户现场确认为准。",
});

const buildThemeState = (
	definition: DataManagementThemeDefinition,
	themeChains: GoldenChainSummary[],
	detailsByChainKey: Record<string, GoldenChainDetail | undefined>,
): DataManagementThemeState => {
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
	return chains.map((chain) => buildThemeState(themeDefinitionFromChain(chain), [chain], detailsByChainKey));
}
