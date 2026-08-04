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

export type SqlResultPageResponse = {
	executionId: string;
	resultSetId?: string;
	page: number;
	pageSize: number;
	totalRows: number;
	totalPages: number;
	hasNext: boolean;
	headers: string[];
	rows: Array<Record<string, any>>;
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
	nullable: boolean;
	description?: string;
	defaultValue?: string;
	autoIncrement?: boolean;
	ordinalPosition?: number;
	columnSize?: number;
	decimalDigits?: number;
};

export const fetchCatalogTree = (payload: SqlCatalogRequest = {}) =>
	api.post<SqlCatalogNode>({ url: "/sql/catalog", data: payload });

export const listTables = (datasourceId: string, options?: { keyword?: string; limit?: number }) =>
	api.get<TableInfo[]>({ url: `/sql/tables/${datasourceId}`, params: options });

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

export const listSavedQueries = () => api.get<SavedQueryResponse[]>({ url: "/sql/saved-queries" });

export const getSavedQuery = (id: string) => api.get<SavedQueryResponse>({ url: `/sql/saved-queries/${id}` });

export const deleteSavedQuery = (id: string) => api.delete<boolean>({ url: `/sql/saved-queries/${id}` });

// 审计日志
export const auditCopy = (payload: { rowCount: number; columnCount: number; executionId?: string }) =>
	api.post<boolean>({ url: "/sql/audit/copy", data: payload });

export const validateSql = (payload: SqlValidateRequest) =>
	api.post<SqlValidateResponse>({ url: "/sql/validate", data: payload });

export const submitSql = (payload: SqlSubmitRequest) =>
	api.post<SqlSubmitResponse>({ url: "/sql/submit", data: payload });

export const getSqlStatus = (executionId: string) => api.get<SqlStatusResponse>({ url: `/sql/status/${executionId}` });

export const getSqlResultPage = (executionId: string, page = 1, pageSize = 200) =>
	api.get<SqlResultPageResponse>({ url: `/sql/result-page/${executionId}`, params: { page, pageSize } });

export const cancelSql = (executionId: string) => api.post<boolean>({ url: `/sql/cancel/${executionId}` });

export type QueryDatasetAsset = {
	id: string;
	name: string;
	description?: string | null;
	sourceDatasourceId?: string | null;
	sourceDatasourceName?: string | null;
	ownerDept?: string | null;
	status: string;
	refreshStrategy: string;
	publishedVersion?: number | null;
	latestExecutionId?: string | null;
	latestResultSetId?: string | null;
	enabled?: boolean;
	createdBy?: string | null;
	createdDate?: string | null;
	lastModifiedDate?: string | null;
	semanticContractVersion?: string | null;
	semanticModelCount?: number | null;
	semanticModelNames?: string[] | null;
};

export type QueryDatasetVersion = {
	id: string;
	datasetId: string;
	versionNo: number;
	status: string;
	sqlText: string;
	changeSummary?: string | null;
	resultSetId?: string | null;
	executionId?: string | null;
	publishedAt?: string | null;
	createdBy?: string | null;
	createdDate?: string | null;
};

export type CreateQueryDatasetFromExecutionRequest = {
	name?: string;
	description?: string;
	refreshStrategy?: string;
	changeSummary?: string;
};

export type CreateQueryDatasetVersionRequest = {
	sqlText: string;
	changeSummary?: string;
	status?: string;
};

export type PublishQueryDatasetRequest = {
	versionNo?: number;
	changeSummary?: string;
};

export const listQueryDatasets = () => api.get<QueryDatasetAsset[]>({ url: "/sql/query-datasets" });

export const listQueryDatasetVersions = (datasetId: string) =>
	api.get<QueryDatasetVersion[]>({ url: `/sql/query-datasets/${datasetId}/versions` });

export const createQueryDatasetFromExecution = (
	executionId: string,
	payload?: CreateQueryDatasetFromExecutionRequest,
) => api.post<QueryDatasetAsset>({ url: `/sql/query-datasets/from-execution/${executionId}`, data: payload ?? {} });

export const createQueryDatasetVersion = (datasetId: string, payload: CreateQueryDatasetVersionRequest) =>
	api.post<QueryDatasetVersion>({ url: `/sql/query-datasets/${datasetId}/versions`, data: payload });

export const publishQueryDataset = (datasetId: string, payload?: PublishQueryDatasetRequest) =>
	api.post<QueryDatasetVersion>({ url: `/sql/query-datasets/${datasetId}/publish`, data: payload ?? {} });

export const archiveQueryDataset = (datasetId: string) =>
	api.post<QueryDatasetAsset>({ url: `/sql/query-datasets/${datasetId}/archive`, data: {} });
