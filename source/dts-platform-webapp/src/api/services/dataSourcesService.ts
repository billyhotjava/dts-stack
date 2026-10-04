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
	capabilities?: string[];
	selectable?: boolean;
	defaultSource?: boolean;
	recommended?: boolean;
	recommendationReason?: string;
};

export type DataSourceSelectionItem = Pick<InfraDataSource, "id" | "name" | "type"> & {
	connectorKey?: string;
	connectorName?: string;
	connectorCategory?: string;
	defaultEngine?: string;
	status?: string;
	heartbeatStatus?: string;
	capabilities?: string[];
	selectable?: boolean;
	defaultSource?: boolean;
	recommended?: boolean;
	recommendationReason?: string;
};

export type DataSourceSelectionResponse = {
	capability: string;
	defaultDataSourceId?: string;
	defaultSource?: string;
	message?: string;
	items: DataSourceSelectionItem[];
};

export type DataSourceUpsertPayload = {
	name: string;
	type: string;
	connectorKey?: string;
	jdbcUrl?: string;
	username?: string;
	description?: string;
	ownerDept?: string;
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
	classification: string;
	sealId: string;
	sealVersion: number;
	sealChecksum: string;
	sealedAt: string;
};

export type ExcelColumnSpec = {
	name: string;
	dataType?: string;
	label?: string;
	classification?: string;
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
	fieldClassifications?: Record<string, string>;
	sealClassification?: boolean;
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

export default {
	list: () => apiClient.get<InfraDataSource[]>({ url: "/infra/data-sources" }),
	selections: (params?: { capability?: string }) =>
		apiClient.get<DataSourceSelectionResponse>({ url: "/infra/data-source-selections", params }),
	detail: (id: string) => apiClient.get<InfraDataSource>({ url: `/infra/data-sources/${id}` }),
	create: (payload: DataSourceUpsertPayload) =>
		apiClient.post<InfraDataSource>({ url: "/infra/data-sources", data: payload }),
	update: (id: string, payload: DataSourceUpsertPayload) =>
		apiClient.put<InfraDataSource>({ url: `/infra/data-sources/${id}`, data: payload }),
	updateWithImpact: (id: string, payload: DataSourceUpsertPayload) =>
		apiClient.put<DataSourceUpdateImpact>({ url: `/infra/data-sources/${id}/impact`, data: payload }),
	remove: (id: string) => apiClient.delete<void>({ url: `/infra/data-sources/${id}` }),
	test: (id: string) => apiClient.post<ConnectionTestResult>({ url: `/infra/data-sources/${id}/test` }),
	excelPrepare: (file: File, classification: string) => {
		const formData = new FormData();
		formData.append("file", file);
		formData.append("classification", classification);
		return apiClient.post<ExcelImportPrepareResponse>({ url: "/infra/excel-import/prepare", data: formData });
	},
	excelParse: (payload: ExcelImportParseRequest) =>
		apiClient.post<ExcelImportParseResponse>({ url: "/infra/excel-import/parse", data: payload }),
	excelErrors: (params: { fileId: string; limit?: number }) =>
		apiClient.get<ExcelImportErrorPreviewResponse>({ url: "/infra/excel-import/errors", params }),
};
