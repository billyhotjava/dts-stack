import api from "@/api/apiClient";

export type SqlCatalogNodeType = "CATALOG" | "SCHEMA" | "TABLE" | "COLUMN";

export type SqlCatalogNode = {
	id: string;
	label: string;
	type: SqlCatalogNodeType;
	children?: SqlCatalogNode[];
};

export type SqlCatalogRequest = {
	datasource?: string;
	catalog?: string;
	schema?: string;
	search?: string;
};

export type SqlViolation = {
	code: string;
	message: string;
	blocking: boolean;
};

export type SqlPlanCost = {
	outputRows?: number;
	cpuMillis?: number;
	wallMillis?: number;
};

export type SqlPlanSnippet = {
	text?: string;
	cost?: SqlPlanCost;
};

export type SqlLimitInfo = {
	enforced: boolean;
	limit?: number;
	reason?: string;
};

export type SqlColumnMeta = {
	name: string;
	type?: string;
};

export type SqlTableRef = {
	catalog?: string;
	schema?: string;
	name: string;
};

export type SqlSummary = {
	tables: SqlTableRef[];
	limit?: number | null;
	columns: SqlColumnMeta[];
};

export type SqlValidateRequest = {
	sqlText: string;
	datasource?: string;
	catalog?: string;
	schema?: string;
	clientRequestId?: string;
};

export type SqlValidateResponse = {
	executable: boolean;
	rewrittenSql: string;
	summary: SqlSummary;
	violations: SqlViolation[];
	warnings: string[];
	plan?: SqlPlanSnippet | null;
	limitInfo?: SqlLimitInfo | null;
};

export type SqlSubmitRequest = {
	sqlText: string;
	datasource?: string;
	catalog?: string;
	schema?: string;
	clientRequestId?: string;
	fetchSize?: number;
	dryRun?: boolean;
};

export type SqlSubmitResponse = {
	executionId: string;
	trinoQueryId?: string | null;
	queued: boolean;
};

export type SqlStatusResponse = {
	executionId: string;
	status: string;
	elapsedMs?: number;
	rows?: number;
	bytes?: number;
	queuePosition?: number;
	errorMessage?: string;
	resultSetId?: string;
	plan?: SqlPlanSnippet | null;
};

export const fetchCatalogTree = (payload: SqlCatalogRequest = {}) =>
	api.post<SqlCatalogNode>({ url: "/sql/catalog", data: payload });

export const validateSql = (payload: SqlValidateRequest) =>
	api.post<SqlValidateResponse>({ url: "/sql/validate", data: payload });

export const submitSql = (payload: SqlSubmitRequest) =>
	api.post<SqlSubmitResponse>({ url: "/sql/submit", data: payload });

export const getSqlStatus = (executionId: string) =>
	api.get<SqlStatusResponse>({ url: `/sql/status/${executionId}` });

export const cancelSql = (executionId: string) =>
	api.post<boolean>({ url: `/sql/cancel/${executionId}` });
