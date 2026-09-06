export type QualityRouteKey =
	| "overview"
	| "rule-list"
	| "rule-template"
	| "rule-by-table"
	| "rule-by-template"
	| "monitor"
	| "run-records"
	| "report"
	| "rule-detail"
	| "rule-editor"
	| "template-detail"
	| "table-detail"
	| "batch-wizard"
	| "monitor-detail"
	| "monitor-editor"
	| "run-detail"
	| "noise"
	| "report-editor"
	| "report-preview";

export type QualityRouteSpec = {
	key: QualityRouteKey;
	label: string;
	group: string;
	level: "primary" | "secondary";
	menuOwner: "/governance/rules" | "/governance/quality";
	path: string;
};

export const QUALITY_ROUTE_SPECS: QualityRouteSpec[] = [
	{
		key: "overview",
		label: "质量大盘",
		group: "质量概览",
		level: "primary",
		menuOwner: "/governance/rules",
		path: "/governance/rules",
	},
	{
		key: "rule-list",
		label: "规则列表",
		group: "质量资产",
		level: "primary",
		menuOwner: "/governance/rules",
		path: "/governance/rules/catalog",
	},
	{
		key: "rule-template",
		label: "规则模板库",
		group: "质量资产",
		level: "primary",
		menuOwner: "/governance/rules",
		path: "/governance/rules/templates",
	},
	{
		key: "rule-by-table",
		label: "按表配置",
		group: "规则配置",
		level: "primary",
		menuOwner: "/governance/rules",
		path: "/governance/rules/config/tables",
	},
	{
		key: "rule-by-template",
		label: "按模板配置",
		group: "规则配置",
		level: "primary",
		menuOwner: "/governance/rules",
		path: "/governance/rules/config/templates",
	},
	{
		key: "monitor",
		label: "运行策略",
		group: "质量运维",
		level: "primary",
		menuOwner: "/governance/rules",
		path: "/governance/rules/monitors",
	},
	{
		key: "run-records",
		label: "运行记录",
		group: "质量运维",
		level: "primary",
		menuOwner: "/governance/rules",
		path: "/governance/rules/runs",
	},
	{
		key: "report",
		label: "质量报告",
		group: "质量分析",
		level: "primary",
		menuOwner: "/governance/quality",
		path: "/governance/quality",
	},
	{
		key: "rule-detail",
		label: "规则详情",
		group: "质量资产",
		level: "secondary",
		menuOwner: "/governance/rules",
		path: "/governance/rules/catalog/:ruleId",
	},
	{
		key: "rule-editor",
		label: "新建质量规则",
		group: "质量资产",
		level: "secondary",
		menuOwner: "/governance/rules",
		path: "/governance/rules/catalog/new",
	},
	{
		key: "template-detail",
		label: "模板详情",
		group: "质量资产",
		level: "secondary",
		menuOwner: "/governance/rules",
		path: "/governance/rules/templates/:templateId",
	},
	{
		key: "table-detail",
		label: "表质量详情",
		group: "规则配置",
		level: "secondary",
		menuOwner: "/governance/rules",
		path: "/governance/rules/config/tables/:datasetId",
	},
	{
		key: "batch-wizard",
		label: "批量配置规则",
		group: "规则配置",
		level: "secondary",
		menuOwner: "/governance/rules",
		path: "/governance/rules/config/batch",
	},
	{
		key: "monitor-detail",
		label: "运行策略详情",
		group: "质量运维",
		level: "secondary",
		menuOwner: "/governance/rules",
		path: "/governance/rules/monitors/:taskId",
	},
	{
		key: "monitor-editor",
		label: "配置运行策略",
		group: "质量运维",
		level: "secondary",
		menuOwner: "/governance/rules",
		path: "/governance/rules/monitors/new",
	},
	{
		key: "run-detail",
		label: "运行详情",
		group: "质量运维",
		level: "secondary",
		menuOwner: "/governance/rules",
		path: "/governance/rules/runs/:runId",
	},
	{
		key: "noise",
		label: "去噪管理",
		group: "质量运维",
		level: "secondary",
		menuOwner: "/governance/rules",
		path: "/governance/rules/noise",
	},
	{
		key: "report-editor",
		label: "报告模板编辑",
		group: "质量分析",
		level: "secondary",
		menuOwner: "/governance/quality",
		path: "/governance/quality/reports/new",
	},
	{
		key: "report-preview",
		label: "质量报告预览",
		group: "质量分析",
		level: "secondary",
		menuOwner: "/governance/quality",
		path: "/governance/quality/preview",
	},
];

export const qualityPrimaryRoutesForOwner = (menuOwner: QualityRouteSpec["menuOwner"]) =>
	QUALITY_ROUTE_SPECS.filter((route) => route.level === "primary" && route.menuOwner === menuOwner);

const ROUTE_BY_KEY = new Map(QUALITY_ROUTE_SPECS.map((route) => [route.key, route]));

export const qualityPath = (key: QualityRouteKey, params: Record<string, string> = {}) => {
	const route = ROUTE_BY_KEY.get(key);
	if (!route) return "/governance/rules";
	return route.path.replace(/:([A-Za-z]+)/g, (match, name: string) =>
		params[name] ? encodeURIComponent(params[name]) : match,
	);
};

const appendQuery = (path: string, params: URLSearchParams) => {
	const query = params.toString();
	return `${path}${query ? `?${query}` : ""}`;
};

export const legacyQualityRedirect = (source: URLSearchParams) => {
	const params = new URLSearchParams(source);
	const tab = params.get("tab");
	const ruleId = params.get("ruleId");
	const runId = params.get("runId");

	if (tab === "report") {
		params.delete("tab");
		return appendQuery(qualityPath("report"), params);
	}
	if (tab === "rules") {
		params.delete("tab");
		return appendQuery(qualityPath("rule-list"), params);
	}
	if (tab === "tasks") {
		params.delete("tab");
		return appendQuery(qualityPath("monitor"), params);
	}
	if (tab === "repair") {
		params.delete("tab");
		params.delete("runId");
		return appendQuery(runId ? qualityPath("run-detail", { runId }) : qualityPath("run-records"), params);
	}
	if (ruleId) {
		params.delete("ruleId");
		return appendQuery(qualityPath("rule-detail", { ruleId }), params);
	}
	if (params.get("datasetId")) return appendQuery(qualityPath("rule-list"), params);
	return undefined;
};

export type QualityRuleDraft = {
	name: string;
	type: string;
	datasetId?: string;
	[key: string]: unknown;
};

export type PreservedQualityRuleMetadata = {
	category?: string;
	description?: string;
	owner?: string;
	dataLevel?: string;
	frequencyCron?: string;
	executor?: string;
	template?: boolean;
	enabled?: boolean;
};

export const buildQualityRulePayload = (draft: QualityRuleDraft, existing: PreservedQualityRuleMetadata = {}) => {
	const preserved = Object.fromEntries(Object.entries(existing).filter(([, value]) => value !== undefined));
	return {
		...preserved,
		...draft,
		bindings: draft.datasetId ? [{ datasetId: draft.datasetId, scopeType: "DATASET" as const }] : [],
	};
};

export const UNAVAILABLE_CAPABILITIES = [
	{ key: "atomic-batch", label: "暂未开放", reason: "后端尚无批量规则原子创建与发布合同。" },
	{ key: "noise-management", label: "暂未开放", reason: "后端尚无质量去噪策略的持久化合同。" },
	{ key: "quality-subscription", label: "暂未开放", reason: "后端尚无质量告警订阅和通知渠道合同。" },
	{
		key: "report-template",
		label: "暂未开放",
		reason: "当前仅支持即时质量报告与 Excel 导出，尚无报告模板和定时发送合同。",
	},
] as const;
