import apiClient from "../apiClient";

export type ConnectionTestResult = {
	success: boolean;
	message?: string;
	elapsedMillis?: number;
	engineVersion?: string;
	driverVersion?: string;
	warnings?: string[];
};

export type InfraDataSource = {
	id: string;
	name: string;
	type: string;
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

export type ProjectCockpitBatchLoadRequest = {
	fileId: string;
};

export type ProjectCockpitBatchLoadResponse = {
	batchId: string;
	batchCode: string;
	loadedRowCount: number;
	acceptedRowCount: number;
	rejectedRowCount: number;
	warningRowCount: number;
	issueCount: number;
	status: string;
};

export type ProjectCockpitBatchIssueRow = {
	rowIndex?: number;
	severity?: string;
	issueCode?: string;
	message?: string;
	projectNo?: string;
	subsystem?: string;
	nodeTask?: string;
	planDate?: string;
	completionStatus?: string;
	riskLevel?: string;
};

export type ProjectCockpitBatchIssuePreviewResponse = {
	batchId: string;
	issueRowCount: number;
	limit: number;
	rows: ProjectCockpitBatchIssueRow[];
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
	excelLoadProjectCockpit: (payload: ProjectCockpitBatchLoadRequest) =>
		apiClient.post<ProjectCockpitBatchLoadResponse>({ url: "/infra/excel-import/project-cockpit/load", data: payload }),
	projectCockpitIssues: (params: { batchId: string; severity?: string; limit?: number }) =>
		apiClient.get<ProjectCockpitBatchIssuePreviewResponse>({ url: "/infra/excel-import/project-cockpit/issues", params }),
};
