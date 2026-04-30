import apiClient from "../apiClient";

export type ConnectionTestResult = {
	success: boolean;
	message?: string;
	elapsedMillis?: number;
	engineVersion?: string;
	driverVersion?: string;
	warnings?: string[];
	errorType?: string;
	suggestion?: string;
};

export type InfraDataSource = {
	id: string;
	name: string;
	type: string;
	connectorKey?: string;
	connectorName?: string;
	connectorCategory?: string;
	defaultEngine?: string;
	jdbcUrl?: string;
	username?: string;
	description?: string;
	ownerDept?: string;
	props?: Record<string, any>;
	createdAt?: string;
	lastUpdatedAt?: string;
	lastVerifiedAt?: string;
	status?: string;
	hasSecrets?: boolean;
	engineVersion?: string;
	driverVersion?: string;
	lastTestElapsedMillis?: number;
	lastHeartbeatAt?: string;
	heartbeatStatus?: string;
	heartbeatFailureCount?: number;
	lastError?: string;
};

export type DataSourceUpsertPayload = {
	name: string;
	type: string;
	connectorKey?: string;
	jdbcUrl?: string;
	username?: string;
	description?: string;
	props?: Record<string, any>;
	secrets?: Record<string, any>;
};

export type DataSourceUpdateImpact = {
	dataSource: InfraDataSource;
	connectionChanged: boolean;
	affectedTasks: number;
	changeLogCreated: number;
	taskIds: number[];
};

export type ExcelSheetInfo = {
	index: number;
	name: string;
};

export type ExcelImportPrepareResponse = {
	fileId: string;
	fileName: string;
	batchCode: string;
	sheets: ExcelSheetInfo[];
};

export type ExcelColumnSpec = {
	name: string;
	dataType?: string;
	label?: string;
};

export type ExcelImportParseRequest = {
	fileId: string;
	sheetName?: string;
	sheetIndex?: number;
	headerRow?: number;
	dataStartRow?: number;
	delimiter?: string;
	previewLimit?: number;
	skipErrors?: boolean;
	fillMerged?: boolean;
	dateFormat?: string;
};

export type ExcelImportParseResponse = {
	fileId: string;
	batchCode: string;
	sheetName?: string;
	csvPath: string;
	csvContainerPath: string;
	errorPath?: string;
	errorContainerPath?: string;
	delimiter?: string;
	columns: ExcelColumnSpec[];
	preview: string[][];
	rowCount: number;
	errorCount: number;
};

export type ExcelImportErrorRow = {
	rowIndex?: number;
	message?: string;
};

export type ExcelImportErrorPreviewResponse = {
	fileId: string;
	errorCount: number;
	limit: number;
	rows: ExcelImportErrorRow[];
};

export type SchemaDiscoverColumn = {
	name: string;
	dataType?: string;
	nativeType?: string;
	nullable?: boolean;
	defaultValue?: string;
	comment?: string;
	ordinalPosition?: number;
	primaryKey?: boolean;
	indexed?: boolean;
	incrementalCandidate?: boolean;
};

export type SchemaDiscoverIndex = {
	name: string;
	unique?: boolean;
	columns?: string[];
};

export type SchemaDiscoverTable = {
	schema?: string;
	name: string;
	type?: string;
	comment?: string;
	view?: boolean;
	columns?: SchemaDiscoverColumn[];
	primaryKeys?: string[];
	indexes?: SchemaDiscoverIndex[];
	incrementalCandidates?: string[];
	sampleRows?: Record<string, any>[];
};

export type SchemaDiscoverResponse = {
	dataSourceId: string;
	dataSourceName?: string;
	connectorKey?: string;
	databaseProduct?: string;
	databaseVersion?: string;
	schemas?: string[];
	tables?: SchemaDiscoverTable[];
	elapsedMs?: number;
	status?: string;
	error?: string;
	discoveredAt?: string;
	cached?: boolean;
	cacheKey?: string;
	drift?: {
		addedTables?: number;
		removedTables?: number;
		changedTables?: number;
		detailsJson?: string;
	};
};

export type SchemaDiscoverRequest = {
	schema?: string;
	tablePattern?: string;
	maxTables?: number;
	sampleLimit?: number;
	includeColumns?: boolean;
	includeIndexes?: boolean;
	includeSample?: boolean;
	useCache?: boolean;
	forceRefresh?: boolean;
};

export type OdsSourceColumnRequest = {
	name: string;
	include?: boolean;
	targetName?: string;
	dataType?: string;
	nativeType?: string;
	targetDataType?: string;
	nullable?: boolean;
	comment?: string;
	primaryKey?: boolean;
	indexed?: boolean;
	incrementalCandidate?: boolean;
};

export type OdsSourceTableRequest = {
	schema?: string;
	name: string;
	comment?: string;
	primaryKeys?: string[];
	incrementalCandidates?: string[];
	columns?: OdsSourceColumnRequest[];
};

export type OdsGenerationRequest = {
	odsSchema?: string;
	systemCode?: string;
	bizCode?: string;
	entityCode?: string;
	includeTechnicalColumns?: boolean;
	includeRawJson?: boolean;
	syncMode?: string;
	tables: OdsSourceTableRequest[];
};

export type OdsColumnPlan = {
	sourceName: string;
	targetName: string;
	sourceType?: string;
	odsType?: string;
	comment?: string;
	nullable?: boolean;
	primaryKey?: boolean;
	indexed?: boolean;
	incrementalCandidate?: boolean;
	overrideRequired?: boolean;
};

export type OdsTechnicalColumn = {
	name: string;
	dataType: string;
	comment?: string;
};

export type OdsTablePlan = {
	sourceSchema?: string;
	sourceTable: string;
	odsSchema: string;
	odsTable: string;
	systemCode?: string;
	bizCode?: string;
	entityCode?: string;
	primaryKeys?: string[];
	incrementalCandidates?: string[];
	columns?: OdsColumnPlan[];
	technicalColumns?: OdsTechnicalColumn[];
	createTableSql?: string;
	dbtSourceYaml?: string;
	addaxJobDraft?: Record<string, any>;
	airflowDagDraft?: Record<string, any>;
	warnings?: string[];
};

export type OdsGenerationPreviewResponse = {
	dataSourceId: string;
	dataSourceName?: string;
	odsSchema?: string;
	tables?: OdsTablePlan[];
	dbtSourceYaml?: string;
	warnings?: string[];
};

export type OdsGenerationApplyResult = {
	dataSourceId: string;
	dataSourceName?: string;
	mappingsUpserted?: number;
	columnsUpserted?: number;
	lineageCreated?: number;
	lineageUpdated?: number;
	lineageSkipped?: number;
	dbtMessage?: string;
	tables?: OdsTablePlan[];
	warnings?: string[];
};

export type OdsSyncTaskDraftResponse = {
	dataSourceId: string;
	dataSourceName?: string;
	taskName?: string;
	payload?: Record<string, any>;
	tables?: OdsTablePlan[];
	warnings?: string[];
};

export type OdsPrecheckRuleResult = {
	code: string;
	level: "INFO" | "WARN" | "ERROR" | string;
	status: "PASS" | "WARN" | "FAIL" | string;
	target?: string;
	message?: string;
	suggestion?: string;
};

export type OdsPrecheckResponse = {
	dataSourceId: string;
	dataSourceName?: string;
	status: "PASS" | "WARN" | "FAIL" | string;
	totalRules: number;
	passedRules: number;
	warningRules: number;
	failedRules: number;
	rules?: OdsPrecheckRuleResult[];
	tables?: OdsTablePlan[];
	warnings?: string[];
};

export default {
	list: () => apiClient.get<InfraDataSource[]>({ url: "/infra/data-sources" }),
	detail: (id: string) => apiClient.get<InfraDataSource>({ url: `/infra/data-sources/${id}` }),
	create: (payload: DataSourceUpsertPayload) =>
		apiClient.post<InfraDataSource>({ url: "/infra/data-sources", data: payload }),
	update: (id: string, payload: DataSourceUpsertPayload) =>
		apiClient.put<InfraDataSource>({ url: `/infra/data-sources/${id}`, data: payload }),
	updateWithImpact: (id: string, payload: DataSourceUpsertPayload) =>
		apiClient.put<DataSourceUpdateImpact>({ url: `/infra/data-sources/${id}/impact`, data: payload }),
	remove: (id: string) => apiClient.delete<void>({ url: `/infra/data-sources/${id}` }),
	test: (id: string) => apiClient.post<ConnectionTestResult>({ url: `/infra/data-sources/${id}/test` }),
	excelPrepare: (file: File) => {
		const formData = new FormData();
		formData.append("file", file);
		return apiClient.post<ExcelImportPrepareResponse>({ url: "/infra/excel-import/prepare", data: formData });
	},
	excelParse: (payload: ExcelImportParseRequest) =>
		apiClient.post<ExcelImportParseResponse>({ url: "/infra/excel-import/parse", data: payload }),
	excelErrors: (params: { fileId: string; limit?: number }) =>
		apiClient.get<ExcelImportErrorPreviewResponse>({ url: "/infra/excel-import/errors", params }),
	schemaDiscover: (id: string, payload?: SchemaDiscoverRequest) =>
		apiClient.post<SchemaDiscoverResponse>({ url: `/infra/data-sources/${id}/schema-discover`, data: payload || {} }),
	odsPreview: (id: string, payload: OdsGenerationRequest) =>
		apiClient.post<OdsGenerationPreviewResponse>({ url: `/infra/data-sources/${id}/ods-preview`, data: payload }),
	odsApply: (id: string, payload: OdsGenerationRequest) =>
		apiClient.post<OdsGenerationApplyResult>({ url: `/infra/data-sources/${id}/ods-apply`, data: payload }),
	odsPrecheck: (id: string, payload: OdsGenerationRequest) =>
		apiClient.post<OdsPrecheckResponse>({ url: `/infra/data-sources/${id}/ods-precheck`, data: payload }),
	syncTaskDraft: (id: string, payload: OdsGenerationRequest) =>
		apiClient.post<OdsSyncTaskDraftResponse>({ url: `/infra/data-sources/${id}/sync-task-draft`, data: payload }),
};
