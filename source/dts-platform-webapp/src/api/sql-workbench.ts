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
	resultSetId?: string | null;
	preview?: SqlResultPreview | null;
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
	preview?: SqlResultPreview | null;
};

export type SqlResultPreview = {
	headers: string[];
	rows: Array<Record<string, any>>;
	rowCount?: number;
	truncated?: boolean;
};

export type TableInfo = {
	schema: string;
	name: string;
	type: string;
	rowCount?: number;
};

export type ColumnInfo = {
	name: string;
	type: string;
	nullable: string;
};

export const fetchCatalogTree = (payload: SqlCatalogRequest = {}) =>
	api.post<SqlCatalogNode>({ url: "/sql/catalog", data: payload });

export const listTables = (datasourceId: string) =>
	api.get<TableInfo[]>({ url: `/sql/tables/${datasourceId}` });

export const listColumns = (datasourceId: string, schema: string, table: string) =>
	api.get<ColumnInfo[]>({ url: `/sql/columns/${datasourceId}`, params: { schema, table } });

// 保存的查询
export type SavedQueryRequest = {
	name: string;
	description?: string;
	sqlText: string;
	datasourceId?: string;
	datasourceName?: string;
};

export type SavedQueryResponse = {
	id: string;
	name: string;
	description?: string;
	sqlText: string;
	datasourceId?: string;
	datasourceName?: string;
	createdBy: string;
	createdAt: string;
	updatedAt: string;
};

export const saveQuery = (payload: SavedQueryRequest) =>
	api.post<SavedQueryResponse>({ url: "/sql/saved-queries", data: payload });

export const updateSavedQuery = (id: string, payload: SavedQueryRequest) =>
	api.put<SavedQueryResponse>({ url: `/sql/saved-queries/${id}`, data: payload });

export const listSavedQueries = () =>
	api.get<SavedQueryResponse[]>({ url: "/sql/saved-queries" });

export const getSavedQuery = (id: string) =>
	api.get<SavedQueryResponse>({ url: `/sql/saved-queries/${id}` });

export const deleteSavedQuery = (id: string) =>
	api.delete<boolean>({ url: `/sql/saved-queries/${id}` });

// 审计日志
export const auditCopy = (payload: { rowCount: number; columnCount: number; executionId?: string }) =>
	api.post<boolean>({ url: "/sql/audit/copy", data: payload });

export const validateSql = (payload: SqlValidateRequest) =>
	api.post<SqlValidateResponse>({ url: "/sql/validate", data: payload });

export const submitSql = (payload: SqlSubmitRequest) =>
	api.post<SqlSubmitResponse>({ url: "/sql/submit", data: payload });

export const getSqlStatus = (executionId: string) =>
	api.get<SqlStatusResponse>({ url: `/sql/status/${executionId}` });

export const cancelSql = (executionId: string) =>
	api.post<boolean>({ url: `/sql/cancel/${executionId}` });
