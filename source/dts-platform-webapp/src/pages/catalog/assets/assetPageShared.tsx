import type { ReactNode } from "react";

// DatasetsPage（资产地图/台账）拆分出的共享层：类型、常量、纯工具与指标卡片。
// 视图组件与页面容器均从此处取用，保持单一事实源。

export type AssetRow = {
	id: string;
	name: string;
	type: string;
	domainId?: string;
	domain?: string;
	classification?: string;
	warehouseLayer?: string;
	status?: string;
	lifecycleStatus?: string;
	owner?: string;
	ownerDept?: string;
	governanceStatus?: string;
	matchStatus?: string;
	metadataSource?: string;
	legacyDatasetId?: string;
	description?: string;
	hiveDatabase?: string;
	hiveTable?: string;
	updatedAt?: string;
	snapshotTime?: string;
};

export type DomainNode = { id?: string; name?: string; code?: string; children?: DomainNode[] };

export const TYPE_OPTIONS = [
	{ label: "全部类型", value: "ALL" },
	{ label: "Hive", value: "HIVE" },
	{ label: "JDBC", value: "JDBC" },
	{ label: "文件", value: "FILE" },
];

export const CLASSIFICATION_OPTIONS = [
	{ label: "全部密级", value: "ALL" },
	{ label: "公开", value: "PUBLIC" },
	{ label: "内部", value: "INTERNAL" },
	{ label: "秘密", value: "SECRET" },
	{ label: "机密", value: "CONFIDENTIAL" },
];

export const GOVERNANCE_OPTIONS = [
	{ label: "全部治理状态", value: "ALL" },
	{ label: "已治理", value: "GOVERNED" },
	{ label: "待认领", value: "PENDING_CLAIM" },
	{ label: "待定级", value: "PENDING_CLASSIFICATION" },
	{ label: "待归域", value: "PENDING_DOMAIN" },
	{ label: "停用", value: "DISABLED" },
];

export const MATCH_OPTIONS = [
	{ label: "全部映射", value: "ALL" },
	{ label: "已映射", value: "MATCHED" },
	{ label: "未匹配", value: "UNMATCHED" },
	{ label: "人工确认", value: "MANUAL_REVIEW" },
];

export const DATASET_FILTER_STORAGE_KEY = "catalog.asset.filter.v2";
export const ASSET_PORTAL_V2_ENABLED = import.meta.env.VITE_CATALOG_ASSET_PORTAL_V2 !== "false";
export const UNASSIGNED_DOMAIN_KEY = "__UNASSIGNED__";

export const CLASSIFICATION_LABEL: Record<string, string> = {
	PUBLIC: "公开",
	INTERNAL: "内部",
	SECRET: "秘密",
	CONFIDENTIAL: "机密",
};

export const LAYER_META: Record<string, { label: string; color: string; tone: string }> = {
	SOURCE: { label: "来源", color: "magenta", tone: "border-pink-200 bg-pink-50/60" },
	ODS: { label: "ODS", color: "default", tone: "border-slate-200 bg-slate-50/70" },
	STG: { label: "STG", color: "geekblue", tone: "border-indigo-200 bg-indigo-50/60" },
	DWD: { label: "DWD", color: "blue", tone: "border-blue-200 bg-blue-50/60" },
	DIM: { label: "DIM", color: "purple", tone: "border-purple-200 bg-purple-50/60" },
	DWS: { label: "DWS", color: "cyan", tone: "border-cyan-200 bg-cyan-50/60" },
	ADS: { label: "ADS", color: "green", tone: "border-green-200 bg-green-50/60" },
	OTHER: { label: "未分层", color: "default", tone: "border-slate-200 bg-white" },
};

export const LAYER_ORDER = ["SOURCE", "ODS", "STG", "DWD", "DIM", "DWS", "ADS", "OTHER"];
// 台账遵循全局分页约定：默认 10 条/页；地图保持原有卡片档位
export const LEDGER_PAGE_SIZE = 10;
export const MAP_PAGE_SIZE = 18;
export const ASSET_ACTION_COLUMN_WIDTH = 320;
export const ASSET_TABLE_SCROLL_X = 1440;

export type ReconciliationAssertion = {
	code?: string;
	name?: string;
	passed?: boolean;
	severity?: string;
	detail?: string;
	suggestion?: string;
};

export type ReconciliationResult = {
	generatedAt?: string;
	assertionCount?: number;
	failedCount?: number;
	errorCount?: number;
	warningCount?: number;
	assertions?: ReconciliationAssertion[];
	regressionChecklist?: Array<{ code?: string; name?: string; route?: string; description?: string }>;
};

export type ResolutionFailureRow = {
	id?: string;
	ref?: string;
	requestedAt?: string;
	caller?: string;
	typeHintGuess?: string;
	reason?: string;
};

export type GovernanceGapRow = {
	id?: string;
	displayName?: string;
	fqn?: string;
	assetKey?: string;
	grantAssetType?: string;
	grantAssetId?: string;
	lifecycleStatus?: string;
	governanceStatus?: string;
	severity?: string;
	blockingGaps?: string[];
	warningGaps?: string[];
	metadataSource?: string;
};

export type LineageFailureRow = GovernanceGapRow & {
	blocking?: boolean;
	reason?: string;
	evidenceSource?: string;
	nextAction?: string;
};

export const normalizeLayer = (value?: string) => {
	const normalized = String(value || "").trim().toUpperCase();
	return normalized && LAYER_META[normalized] ? normalized : "OTHER";
};

export const classificationText = (value?: string) => {
	const normalized = String(value || "").trim().toUpperCase();
	return normalized ? CLASSIFICATION_LABEL[normalized] || normalized : "未设定";
};

export const formatTime = (value?: string) => {
	if (!value) return "-";
	try {
		return new Date(value).toLocaleString();
	} catch {
		return value;
	}
};

export const buildTreeNodes = (nodes: DomainNode[], prefix = "domain"): any[] =>
	nodes.map((node, index) => ({
		key: node.id || `fallback-${prefix}-${index}`,
		title: node.name ?? node.code ?? "未命名",
		children: node.children?.length ? buildTreeNodes(node.children, `${prefix}-${index}`) : undefined,
	}));

export const MetricTile = ({
	icon,
	label,
	value,
	footnote,
	tone = "text-slate-700",
}: {
	icon: ReactNode;
	label: string;
	value: ReactNode;
	footnote?: string;
	tone?: string;
}) => (
	<div className="rounded-lg border border-slate-200 bg-white px-4 py-3">
		<div className="flex items-center justify-between gap-3">
			<div className="text-xs text-slate-500">{label}</div>
			<div className={`text-lg ${tone}`}>{icon}</div>
		</div>
		<div className="mt-2 text-2xl font-semibold leading-none text-slate-900">{value}</div>
		{footnote ? <div className="mt-2 truncate text-xs text-slate-500">{footnote}</div> : null}
	</div>
);
