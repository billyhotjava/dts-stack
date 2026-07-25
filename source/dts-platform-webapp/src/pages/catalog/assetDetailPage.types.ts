export type AssetRow = {
	id: string;
	name: string;
	type: string;
	sourceId?: string;
	domainId?: string;
	domain?: string;
	classification?: string;
	ownerDept?: string;
	owner?: string;
	tags?: string;
	description?: string;
	hiveDatabase?: string;
	hiveTable?: string;
	warehouseLayer?: string;
	updatedAt?: string;
	snapshotTime?: string;
	lifecycleStatus?: string;
	status?: string;
	editable?: boolean;
};

export type TableDetail = {
	enabled?: boolean;
	found?: boolean;
	message?: string;
	entity?: Record<string, any>;
};

export type ColumnRow = {
	key: string;
	name: string;
	type: string;
	comment: string;
	status?: string;
};

export type GovernanceImpact = {
	grantsCount: number;
	lineageNodeCount: number;
	lineageEdgeCount: number;
};

export type DatasetSecurityLinkage = {
	classification?: string;
	requiresMasking?: boolean;
	maskingRuleCount?: number;
	conflict?: boolean;
	effectiveRules?: Array<{ id?: string; column?: string; function?: string; args?: string }>;
	suggestions?: string[];
};

export type GovernanceHealth = {
	healthScore?: number;
	healthLevel?: string;
	quality?: {
		totalRuns?: number;
		passRuns?: number;
		failRuns?: number;
		runningRuns?: number;
		latestRunAt?: string;
		latestStatus?: string;
		failureTop?: Array<{ category?: string; count?: number }>;
		trend?: Array<{ date?: string; total?: number; passed?: number; failed?: number }>;
	};
	issues?: {
		total?: number;
		open?: number;
		closed?: number;
		overdue?: number;
		top?: Array<{ id?: string; title?: string; status?: string; priority?: string; severity?: string; dueAt?: string }>;
	};
	links?: {
		qualityRulesPath?: string;
		qualityReportPath?: string;
		issuesPath?: string;
	};
};

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

export const LAYER_OPTIONS = [
	{ label: "全部分层", value: "ALL" },
	{ label: "ODS", value: "ODS" },
	{ label: "STG", value: "STG" },
	{ label: "DWD", value: "DWD" },
	{ label: "DWS", value: "DWS" },
	{ label: "ADS", value: "ADS" },
];

export const DATASET_FILTER_STORAGE_KEY = "catalog.asset-detail.filter.v1";
export const SECURITY_LINKAGE_VERSION_KEY = "catalog.security.linkage.version";
export const SECURITY_LINKAGE_EVENT = "catalog-security-linkage-updated";

export const CLASSIFICATION_LABEL: Record<string, string> = {
	PUBLIC: "公开",
	INTERNAL: "内部",
	SECRET: "秘密",
	CONFIDENTIAL: "机密",
};

export const layerColor = (layer?: string) => {
	const key = String(layer || "").toUpperCase();
	if (key === "ODS") return "default";
	if (key === "DWD") return "blue";
	if (key === "DWS") return "cyan";
	if (key === "ADS") return "green";
	if (key === "DIM") return "purple";
	return "processing";
};

export const buildColumnRows = (detail?: TableDetail | null): ColumnRow[] => {
	if (!detail?.entity) return [];
	const columns = Array.isArray(detail.entity.columns) ? detail.entity.columns : [];
	return columns.map((item: any, idx: number) => ({
		key: String(item?.name || item?.displayName || idx),
		name: String(item?.name || item?.displayName || "-").trim(),
		type: String(item?.dataType || item?.dataTypeDisplay || "-").trim(),
		comment: String(item?.description || item?.comment || "").trim(),
		status: String(item?.status || "").trim(),
	}));
};
