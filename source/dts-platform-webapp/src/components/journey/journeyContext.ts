export const E2E_DATA_PRODUCT_JOURNEY = "e2e-data-product";

export type DataProductJourneyStageKey =
	| "integration"
	| "planning"
	| "standards"
	| "modeling"
	| "metrics"
	| "development"
	| "service"
	| "evidence";

export const JOURNEY_CONTEXT_PARAM_KEYS = [
	"sourceId",
	"standardDraftId",
	"modelId",
	"metricId",
	"serviceId",
	"runId",
	"auditId",
] as const;

export type JourneyContextParamKey = (typeof JOURNEY_CONTEXT_PARAM_KEYS)[number];
export type DataProductJourneyContextParams = Partial<Record<JourneyContextParamKey, string>>;

type JourneyStageConfig = {
	label: string;
	nextLabel: string;
	currentRoute: string;
	nextRoute: string;
	evidenceRoute: string;
};

export type DataProductJourneyContext = {
	enabled: boolean;
	stage: DataProductJourneyStageKey;
	stageLabel: string;
	returnUrl: string;
	nextUrl: string;
	evidenceUrl: string;
	nextLabel: string;
	contextLabels: Array<{ label: string; value: string }>;
	params: DataProductJourneyContextParams;
};

const STAGE_CONFIG: Record<DataProductJourneyStageKey, JourneyStageConfig> = {
	integration: {
		label: "数据集成",
		nextLabel: "继续到数仓规划",
		currentRoute: "/foundation/data-sources",
		nextRoute: "/governance/subjects",
		evidenceRoute: "/ops/instances",
	},
	planning: {
		label: "数仓规划",
		nextLabel: "继续到数据标准",
		currentRoute: "/governance/subjects",
		nextRoute: "/foundation/standard-package",
		evidenceRoute: "/catalog/metadata-management",
	},
	standards: {
		label: "数据标准",
		nextLabel: "继续到维度建模",
		currentRoute: "/foundation/standard-package",
		nextRoute: "/studio/low-code-development",
		evidenceRoute: "/governance/standards/elements",
	},
	modeling: {
		label: "维度建模",
		nextLabel: "继续到指标设计",
		currentRoute: "/studio/low-code-development",
		nextRoute: "/modeling/metric-workbench",
		evidenceRoute: "/studio/sql-modeling",
	},
	metrics: {
		label: "数据指标",
		nextLabel: "继续到数据开发",
		currentRoute: "/modeling/metric-workbench",
		nextRoute: "/studio/sql-modeling",
		evidenceRoute: "/modeling/semantic/metrics",
	},
	development: {
		label: "数据开发",
		nextLabel: "继续到数据服务",
		currentRoute: "/studio/sql-modeling",
		nextRoute: "/services/apis",
		evidenceRoute: "/ops/instances",
	},
	service: {
		label: "数据服务",
		nextLabel: "继续到运行证据",
		currentRoute: "/services/apis",
		nextRoute: "/ops/instances",
		evidenceRoute: "/services/apis",
	},
	evidence: {
		label: "运行证据",
		nextLabel: "返回验收包",
		currentRoute: "/ops/instances",
		nextRoute: "/workbench",
		evidenceRoute: "/ops/audit-evidence",
	},
};

export const JOURNEY_CONTEXT_PARAM_LABELS: Record<JourneyContextParamKey, string> = {
	sourceId: "数据源",
	standardDraftId: "标准草稿",
	modelId: "模型",
	metricId: "指标",
	serviceId: "服务",
	runId: "运行",
	auditId: "审计",
};

const toSearchParams = (input?: URLSearchParams | string | Record<string, string | null | undefined>) => {
	if (!input) return new URLSearchParams();
	if (input instanceof URLSearchParams) return new URLSearchParams(input);
	if (typeof input === "string") return new URLSearchParams(input.startsWith("?") ? input.slice(1) : input);
	const params = new URLSearchParams();
	for (const [key, value] of Object.entries(input)) {
		if (value != null && value !== "") params.set(key, value);
	}
	return params;
};

export const buildJourneyUrl = (
	route: string,
	current?: URLSearchParams | string | Record<string, string | null | undefined>,
) => {
	const [path, query = ""] = route.split("?");
	const params = new URLSearchParams(query);
	const currentParams = toSearchParams(current);
	params.set("journey", E2E_DATA_PRODUCT_JOURNEY);
	for (const key of JOURNEY_CONTEXT_PARAM_KEYS) {
		const value = currentParams.get(key);
		if (value && !params.has(key)) params.set(key, value);
	}
	return `${path}?${params.toString()}`;
};

export const parseDataProductJourneyContext = (
	searchParams: URLSearchParams,
	stage: DataProductJourneyStageKey,
): DataProductJourneyContext => {
	const journey = searchParams.get("journey");
	const config = STAGE_CONFIG[stage];
	const params = JOURNEY_CONTEXT_PARAM_KEYS.reduce<DataProductJourneyContextParams>((acc, key) => {
		const value = searchParams.get(key);
		if (value) acc[key] = value;
		return acc;
	}, {});
	const contextLabels = JOURNEY_CONTEXT_PARAM_KEYS.flatMap((key) => {
		const value = params[key];
		return value ? [{ label: JOURNEY_CONTEXT_PARAM_LABELS[key], value }] : [];
	});

	return {
		enabled: journey === E2E_DATA_PRODUCT_JOURNEY,
		stage,
		stageLabel: config.label,
		returnUrl: buildJourneyUrl("/workbench", searchParams),
		nextUrl: buildJourneyUrl(config.nextRoute, searchParams),
		evidenceUrl: buildJourneyUrl(config.evidenceRoute, searchParams),
		nextLabel: config.nextLabel,
		contextLabels,
		params,
	};
};
