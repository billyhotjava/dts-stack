import { type ClassificationLevel, normalizeClassification } from "@/utils/classification";
import api from "./apiClient";

export type IngestionRevisionState = "DRAFT" | "ACTIVE" | "SUPERSEDED" | "LEGACY_UNSEALED";

export const normalizeIngestionRevisionState = (value: unknown): IngestionRevisionState | undefined => {
	const normalized = typeof value === "string" ? value.trim().toUpperCase() : "";
	return normalized === "DRAFT" ||
		normalized === "ACTIVE" ||
		normalized === "SUPERSEDED" ||
		normalized === "LEGACY_UNSEALED"
		? normalized
		: undefined;
};

export type ClassificationSealReference = {
	sealId: string;
	subjectType: string;
	subjectKey: string;
	assetType?: string | null;
	effectiveLevel: ClassificationLevel | string;
	snapshotVersion: number;
	checksum: string;
	sealedAt: string;
	propagationStatus?: string;
	fileFloor?: ClassificationLevel | string;
};

export interface IngestionTaskDTO {
	id?: number;
	name: string;
	description?: string;
	sourceType: string;
	sourceConfig: Record<string, any>;
	sourceDataSourceId?: string;
	destinationType?: string;
	destinationConfig?: Record<string, any>;
	targetDatasetId?: string;
	syncMode: string;
	syncSchedule?: string;
	syncPrefix?: string;
	syncConfig?: Record<string, any>;
	graphDsl?: Record<string, any>;
	classificationSeal?: ClassificationSealReference;
	fieldClassifications?: Record<string, ClassificationLevel | string>;
	tableMapping?: Array<{ source: string; target: string }>;
	addaxJobPath?: string;
	addaxConfig?: Record<string, any>;
	airflowEnabled?: boolean;
	airflowDagId?: string;
	dbtModelSelector?: string;
	dbtDagSelector?: string;
	qualityPreCheckEnabled?: boolean;
	stagingTableName?: string;
	preCheckStatus?: string;
	status?: string;
	lastExecutedAt?: string;
	lastExecutionStatus?: string;
	revisionNumber?: number;
	revisionState?: IngestionRevisionState;
	effectiveConfig?: Record<string, unknown>;
	effectiveConfigChecksum?: string;
	defaultPolicyVersion?: number;
	defaultPolicyChecksum?: string;
	qualityPolicyRef?: string;
	createdBy?: string;
	createdDate?: string;
	lastModifiedBy?: string;
	lastModifiedDate?: string;
}

export interface IngestionExecutionDTO {
	id: number;
	taskId: number;
	taskName?: string;
	executionId?: string;
	status: string;
	startTime?: string;
	endTime?: string;
	rowsRead?: number;
	rowsWritten?: number;
	errorMessage?: string;
	failureCategory?: string;
	failureAdvice?: string;
	logPath?: string;
	replaceMode?: string;
	triggerMode?: "MANUAL" | "FAILED_ONLY" | "FULL_RERUN" | string;
	backfillWindowStart?: string;
	backfillWindowEnd?: string;
	backfillColumn?: string;
	droppedTables?: string;
	sourceTables?: Record<string, any>[];
	targetTables?: Record<string, any>[];
	queueWaitSeconds?: number;
	parentExecutionId?: number;
	retryCount?: number;
	maxRetries?: number;
	revisionNumber?: number;
	effectiveConfigChecksum?: string;
	qualityPolicyRef?: string;
	targetDatasetId?: string;
	qualityRunId?: string;
	qualityWorkflowId?: string;
	qualityWorkflowStatus?: "TRIGGERING" | "TRIGGERED" | "RETRY_WAIT" | "EXHAUSTED" | string;
	qualityWorkflowAttemptCount?: number;
	qualityWorkflowNextRetryAt?: string;
	qualityWorkflowError?: string;
	createdAt?: string;
	qualityEvidence?: IngestionQualityEvidence;
}

export type IngestionQualityEvidence = {
	datasetId?: string;
	assetKey?: string;
	assetName?: string;
	qualityBindingCount?: number;
	qualityConfigured?: boolean;
	workflowId?: string;
	triggerRef?: string;
	evidenceState: "MISSING" | "PENDING" | "CURRENT" | "STALE" | "TRIGGER_FAILED" | string;
	qualityStatus: "UNKNOWN" | "RUNNING" | "PASSED" | "FAILED" | string;
	consumptionEligibility: string;
	eligibilityReasons?: string[];
	assetQualityStatus?: string;
	trustedUsable: boolean;
};

export interface IngestionAccessDefaultPolicyDTO {
	policyKey: string;
	version: number;
	status: "ACTIVE" | "RETIRED" | string;
	defaults: Record<string, unknown>;
	checksum: string;
	activatedAt?: string;
}

export interface IngestionTaskRevisionDTO {
	id?: number;
	taskId?: number;
	revisionNumber: number;
	revisionState: IngestionRevisionState;
	sourceKind: "database" | "api" | "file" | string;
	effectiveConfigChecksum: string;
	defaultPolicyVersion?: number;
	defaultPolicyChecksum?: string;
	qualityPolicyRef?: string;
	createdAt?: string;
	activatedAt?: string;
}

export interface IngestionEffectiveConfigDTO {
	taskId: number;
	revisionNumber: number;
	revisionState: IngestionRevisionState;
	sourceKind: "database" | "api" | "file" | string;
	effectiveConfig: Record<string, unknown>;
	effectiveConfigChecksum: string;
	defaultPolicyVersion?: number;
	defaultPolicyChecksum?: string;
	qualityPolicyRef?: string;
}

export interface IngestionExecutionLog {
	taskId?: number;
	executionId?: number;
	dagId?: string;
	dagRunId?: string;
	taskInstanceId?: string;
	tryNumber?: number;
	scope?: "single" | "all" | string;
	keyword?: string;
	taskStates?: Record<string, string>;
	failureCategory?: string;
	failureAdvice?: string;
	errorMessage?: string;
	log?: string;
	message?: string;
}

export interface IngestionExecutionObservabilityFailureTopItem {
	category: string;
	count: number;
}

export interface IngestionExecutionObservabilityTrendItem {
	day: string;
	total: number;
	success: number;
	failed: number;
	timeout: number;
}

export interface IngestionExecutionObservabilityDTO {
	taskId?: number;
	sourceType?: string;
	sourceDataSourceId?: string;
	windowStart?: string;
	windowEnd?: string;
	windowDays?: number;
	timeoutMinutes?: number;
	total: number;
	success: number;
	failed: number;
	running: number;
	terminal: number;
	timeout: number;
	successRate?: number;
	timeoutRate?: number;
	avgDurationSeconds?: number;
	mttrSeconds?: number;
	failureTop: IngestionExecutionObservabilityFailureTopItem[];
	trend: IngestionExecutionObservabilityTrendItem[];
}

export interface IngestionIncrementalStateDTO {
	id: number;
	taskId: number;
	sourceTable: string;
	lastSuccessWatermark?: string;
	lastRunId?: string;
	updatedAt?: string;
	createdAt?: string;
}

export interface IngestionIncrementalAuditSummaryDTO {
	total: number;
	advanced: number;
	unchanged: number;
	advancedRate: number;
}

export interface IngestionIncrementalAuditDTO {
	id: number;
	taskId: number;
	executionId?: number;
	executionRunId?: string;
	sourceTable: string;
	incrementalColumn?: string;
	beforeWatermark?: string;
	afterWatermark?: string;
	advanced?: boolean;
	createdAt?: string;
}

export interface AsyncExecutionSubmitResult {
	taskId: number;
	taskName?: string;
	status: string;
	async?: boolean;
	message?: string;
	pollIntervalMs?: number;
	executionId?: number;
	executionRunId?: string;
	retryExecutionId?: number;
	revisionNumber?: number;
	planChecksum?: string;
	idempotencyProtected?: boolean;
	idempotent?: boolean;
}

export interface BackfillSubmitRequest {
	windowStart: string;
	windowEnd: string;
	column?: string;
}

export interface BackfillSubmitResult {
	taskId: number;
	executionId?: number;
	runId?: string;
	status?: string;
	backfillColumn?: string;
	backfillWindowStart?: string;
	backfillWindowEnd?: string;
	pollIntervalMs?: number;
}

export interface ColumnInfo {
	name: string;
	jdbcType?: number;
	typeName?: string;
	columnSize?: number;
	decimalDigits?: number;
}

export interface TableInfo {
	comment?: string | null;
	schema?: string;
	name: string;
	type?: string;
	columns?: ColumnInfo[];
}

export interface TableDiscoveryFilter {
	schema?: string;
	tablePattern?: string;
	limit?: number;
	includeColumns?: boolean;
}

export interface TableDiscoveryRequest {
	source: {
		dataSourceId: string;
	};
	filter?: TableDiscoveryFilter;
}

export interface PageResult<T> {
	content: T[];
	totalElements: number;
	totalPages: number;
	size: number;
	number: number;
}

export interface IngestionStagingParseResult {
	totalRows: number;
	columns: ColumnInfo[];
	stagingTableName: string;
	builtInErrorCount: number;
}

export interface IngestionStagingPreCheckResult {
	status: "PASSED" | "FAILED" | string;
	totalRules: number;
	passedRules: number;
	failedRules: number;
	failedRuleNames: string[];
	totalRows: number;
	passedRows: number;
	failedRows: number;
}

export type IngestionStagingRow = Record<string, unknown> & {
	_row_num?: number;
	_status?: string;
	_errors?: unknown;
};

export interface IngestionStagingCellUpdateResult {
	rowNum: number;
	column: string;
	value: unknown;
}

type StagingAPIResponse<T> = T | { status?: number | string; data?: T };

export interface IngestionChangeLogDTO {
	id?: number;
	taskId: number;
	taskName?: string;
	objType?: string;
	changeType: string;
	summary: string;
	detail?: string;
	riskLevel?: string;
	status?: string;
	assignee?: string;
	approvalComment?: string;
	handledAt?: string;
	handledBy?: string;
	createdBy?: string;
	createdDate?: string;
}

export interface FileUploadResult {
	hostPath: string;
	containerPath: string;
	fileType: string;
	columns: Array<{ name: string; type: string; label?: string; length?: number; precision?: number; scale?: number }>;
	originalName: string;
	fileId?: string;
	batchCode?: string;
	sheetName?: string;
	sheetIndex?: number;
	fileHash?: string;
	fileSize?: number;
	keyVersion?: string;
	encrypted?: boolean;
	csvPath?: string;
	csvContainerPath?: string;
	errorPath?: string;
	errorContainerPath?: string;
	delimiter?: string;
	preview?: string[][];
	rowCount?: number;
	errorCount?: number;
	sourceFileType?: string;
	sheets?: Array<{ index: number; name: string }>;
	classification?: ClassificationLevel;
	classificationSeal?: ClassificationSealReference;
	fieldClassifications?: Record<string, ClassificationLevel>;
}

export type ManagedFileColumn = {
	name: string;
	type: string;
	label?: string;
	description?: string;
	length?: number;
	precision?: number;
	scale?: number;
	_odsMatched?: boolean;
};

export type ManagedFileClassificationSealReference = ClassificationSealReference & {
	fileId?: string;
	fileSubjectKey?: string;
	fileChecksum?: string;
};

/**
 * File metadata exposed to the access workspace. Server/container paths are
 * deliberately absent: persisted plans refer to the managed fileId and seal.
 */
export interface ManagedFileUploadResult {
	fileId: string;
	fileType: string;
	originalName: string;
	columns: ManagedFileColumn[];
	batchCode?: string;
	sheetName?: string;
	sheetIndex?: number;
	fileHash?: string;
	fileSize?: number;
	keyVersion?: string;
	encrypted?: boolean;
	delimiter?: string;
	preview?: string[][];
	rowCount?: number;
	errorCount?: number;
	sourceFileType?: string;
	sheets?: Array<{ index: number; name: string }>;
	classification?: ClassificationLevel;
	classificationSeal?: ManagedFileClassificationSealReference;
	fieldClassifications?: Record<string, ClassificationLevel>;
}

export interface DefaultDestinationStatus {
	available: boolean;
	writerTypeReady: boolean;
	writerConfigReady: boolean;
	destinationName?: string;
	writerType?: string;
	message?: string;
	dataSourceId?: string;
}

export interface IngestionConnectorCapabilityDTO {
	connectorType: string;
	capabilities: string[];
	connectorVersion?: string;
	constraints?: Record<string, any>;
	enabled?: boolean;
	updatedAt?: string;
}

export interface IngestionTaskTemplateDTO {
	id: string;
	name: string;
	description?: string;
	sourceCategory?: "database" | "file" | string;
	connectorType?: string;
	defaults?: Record<string, any>;
	requiredParams?: string[];
	warnings?: string[];
	version?: string;
}

export interface IngestionTemplateRenderDTO {
	id: string;
	name: string;
	version?: string;
	renderedDefaults?: Record<string, any>;
	requiredParams?: string[];
	warnings?: string[];
	errors?: string[];
	canApply?: boolean;
}

export interface IngestionGovernanceSourceLoadItem {
	sourceDataSourceId?: string;
	sourceType?: string;
	running: number;
	preparing: number;
}

export interface IngestionGovernanceProjectLoadItem {
	projectKey: string;
	running: number;
	preparing: number;
}

export interface IngestionGovernanceOverviewDTO {
	generatedAt?: string;
	running: number;
	preparing: number;
	queueLength: number;
	blockedByPolicy: number;
	avgExecutionSeconds?: number;
	avgQueueWaitSeconds?: number;
	maxQueueWaitSeconds?: number;
	sourceLoads: IngestionGovernanceSourceLoadItem[];
	projectLoads: IngestionGovernanceProjectLoadItem[];
}

export interface IngestionRealtimeStatusDTO {
	taskId: number;
	connectorType: string;
	status: string;
	topicName?: string;
	consumerGroup?: string;
	checkpointToken?: string;
	lagMs?: number;
	throughputRps?: number;
	backlogCount?: number;
	lastHeartbeat?: string;
	updatedAt?: string;
}

export interface ApiAuthProviderFieldDTO {
	name: string;
	label: string;
	type: string;
	required?: boolean;
	sensitive?: boolean;
	description?: string;
	metadata?: Record<string, any>;
}

export interface ApiAuthProviderDescriptorDTO {
	id: string;
	label: string;
	description?: string;
	fields?: ApiAuthProviderFieldDTO[];
	supportsRotation?: boolean;
	enabled?: boolean;
}

export interface ApiConnectorContractDTO {
	contractVersion?: string;
	connectorType?: string;
	sourceTypes?: string[];
	defaultReaderType?: string;
	syncModes?: string[];
	authProviders?: ApiAuthProviderDescriptorDTO[];
}

export interface ApiConnectionTestRequestDTO {
	dataSourceId?: string;
	resource?: Record<string, any>;
	requestPolicy?: Record<string, any>;
	sourceConfig?: Record<string, any>;
	secrets?: Record<string, any>;
}

export interface ManagedApiConnectionTestResourceDTO {
	path: string;
	method: "GET";
	resourceId?: string;
	displayName?: string;
	recordPath?: string;
}

export interface ManagedApiConnectionTestRequestDTO {
	dataSourceId: string;
	resource: ManagedApiConnectionTestResourceDTO;
}

export interface ApiConnectionTestResultDTO {
	connected?: boolean;
	httpStatus?: number;
	authOk?: boolean;
	sampleCount?: number;
	recordPathResolved?: boolean;
	sampleRecords?: Record<string, any>[];
	failureCategory?: string;
	advice?: string;
	message?: string;
	errorCode?: string;
	elapsedMs?: number;
}

const ingestionRecord = (value: unknown): Record<string, unknown> =>
	value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : {};

const optionalString = (value: unknown) => (typeof value === "string" && value.trim() ? value.trim() : undefined);

const optionalNumber = (value: unknown) => {
	const number = typeof value === "number" ? value : Number(value);
	return Number.isFinite(number) ? number : undefined;
};

const normalizeClassificationSeal = (value: unknown): ManagedFileClassificationSealReference | undefined => {
	const seal = ingestionRecord(value);
	const sealId = optionalString(seal.sealId);
	const subjectType = optionalString(seal.subjectType);
	const subjectKey = optionalString(seal.subjectKey);
	const effectiveLevel = normalizeClassification(optionalString(seal.effectiveLevel), undefined);
	const snapshotVersion = optionalNumber(seal.snapshotVersion);
	const checksum = optionalString(seal.checksum);
	const sealedAt = optionalString(seal.sealedAt);
	if (
		!sealId ||
		!subjectType ||
		!subjectKey ||
		!effectiveLevel ||
		snapshotVersion === undefined ||
		!checksum ||
		!sealedAt
	) {
		return undefined;
	}
	return {
		sealId,
		subjectType,
		subjectKey,
		assetType: optionalString(seal.assetType),
		effectiveLevel,
		snapshotVersion,
		checksum,
		sealedAt,
		propagationStatus: optionalString(seal.propagationStatus),
		fileFloor: normalizeClassification(optionalString(seal.fileFloor), undefined),
		fileId: optionalString(seal.fileId),
		fileSubjectKey: optionalString(seal.fileSubjectKey),
		fileChecksum: optionalString(seal.fileChecksum),
	};
};

export const normalizeManagedFileUploadResult = (value: unknown): ManagedFileUploadResult => {
	const result = ingestionRecord(value);
	const fileId = optionalString(result.fileId);
	const fileType = optionalString(result.fileType);
	const originalName = optionalString(result.originalName);
	if (!fileId || !fileType || !originalName || !Array.isArray(result.columns)) {
		throw new Error("文件上传响应无效");
	}
	const columns = result.columns.flatMap((item): ManagedFileColumn[] => {
		const column = ingestionRecord(item);
		const name = optionalString(column.name);
		if (!name) return [];
		return [
			{
				name,
				type: optionalString(column.type) || optionalString(column.dataType) || "string",
				label: optionalString(column.label),
				description:
					optionalString(column.description) || optionalString(column.comment) || optionalString(column.remarks),
				length: optionalNumber(column.length),
				precision: optionalNumber(column.precision),
				scale: optionalNumber(column.scale),
				_odsMatched: column._odsMatched === true || undefined,
			},
		];
	});
	const fieldClassifications = Object.fromEntries(
		Object.entries(ingestionRecord(result.fieldClassifications)).flatMap(([name, rawLevel]) => {
			const level = normalizeClassification(optionalString(rawLevel), undefined);
			return name.trim() && level ? [[name.trim(), level]] : [];
		}),
	) as Record<string, ClassificationLevel>;
	const preview = Array.isArray(result.preview)
		? result.preview.filter(Array.isArray).map((row) => row.map((cell) => String(cell ?? "")))
		: undefined;
	const sheets = Array.isArray(result.sheets)
		? result.sheets.flatMap((item) => {
				const sheet = ingestionRecord(item);
				const index = optionalNumber(sheet.index);
				const name = optionalString(sheet.name);
				return index !== undefined && name ? [{ index, name }] : [];
			})
		: undefined;
	return {
		fileId,
		fileType,
		originalName,
		columns,
		batchCode: optionalString(result.batchCode),
		sheetName: optionalString(result.sheetName),
		sheetIndex: optionalNumber(result.sheetIndex),
		fileHash: optionalString(result.fileHash),
		fileSize: optionalNumber(result.fileSize),
		keyVersion: optionalString(result.keyVersion),
		encrypted: typeof result.encrypted === "boolean" ? result.encrypted : undefined,
		delimiter: optionalString(result.delimiter),
		preview,
		rowCount: optionalNumber(result.rowCount),
		errorCount: optionalNumber(result.errorCount),
		sourceFileType: optionalString(result.sourceFileType),
		sheets,
		classification: normalizeClassification(optionalString(result.classification), undefined),
		classificationSeal: normalizeClassificationSeal(result.classificationSeal),
		fieldClassifications: Object.keys(fieldClassifications).length ? fieldClassifications : undefined,
	};
};

export const normalizeIngestionTaskDTO = (value: unknown): IngestionTaskDTO => {
	const task = ingestionRecord(value) as unknown as IngestionTaskDTO;
	return { ...task, revisionState: normalizeIngestionRevisionState(task.revisionState) };
};

export const normalizeIngestionTaskRevisionDTO = (value: unknown): IngestionTaskRevisionDTO | undefined => {
	const revision = ingestionRecord(value);
	const revisionNumber = optionalNumber(revision.revisionNumber);
	const revisionState = normalizeIngestionRevisionState(revision.revisionState);
	if (!Number.isInteger(revisionNumber) || !revisionState) return undefined;
	return {
		...(revision as unknown as IngestionTaskRevisionDTO),
		revisionNumber: revisionNumber as number,
		revisionState,
	};
};

export const normalizeIngestionEffectiveConfigDTO = (value: unknown): IngestionEffectiveConfigDTO => {
	const config = ingestionRecord(value);
	const revisionState = normalizeIngestionRevisionState(config.revisionState);
	if (!revisionState) throw new Error("接入任务 Revision 状态无效");
	return { ...(config as unknown as IngestionEffectiveConfigDTO), revisionState };
};

const DEFAULT_EXECUTION_POLL_INTERVAL_MS = (() => {
	const raw = Number((import.meta as any)?.env?.VITE_INGESTION_EXECUTION_POLL_MS ?? 5000);
	if (!Number.isFinite(raw)) return 5000;
	return Math.min(30000, Math.max(1000, Math.floor(raw)));
})();

export const resolveExecutionPollIntervalMs = (hint?: number): number => {
	const picked = Number(hint);
	if (!Number.isFinite(picked)) {
		return DEFAULT_EXECUTION_POLL_INTERVAL_MS;
	}
	return Math.min(30000, Math.max(1000, Math.floor(picked)));
};

/**
 * 数据入湖任务API
 */
class IngestionTaskAPI {
	private resolveWrappedResponse<T>(payload: T | { status?: number | string; data?: T }): T {
		if (payload && typeof payload === "object" && "status" in payload && "data" in payload) {
			return (payload as { data?: T }).data as T;
		}
		return payload as T;
	}

	/**
	 * 创建入湖任务
	 */
	async createTask(data: IngestionTaskDTO): Promise<IngestionTaskDTO> {
		return api.post({ url: "/ingestion/tasks", data });
	}

	/**
	 * 获取任务列表
	 */
	async getTasks(params?: {
		status?: string;
		sourceKind?: "database" | "api" | "file";
		query?: string;
		health?: "healthy" | "running" | "attention" | "not_evaluated";
		sourceDataSourceId?: string;
		page?: number;
		size?: number;
		sort?: string;
	}): Promise<PageResult<IngestionTaskDTO>> {
		const payload: any = await api.get({ url: "/ingestion/tasks/list", params });
		const page = this.resolveWrappedResponse<PageResult<unknown>>(payload);
		return {
			...page,
			content: Array.isArray(page?.content) ? page.content.map(normalizeIngestionTaskDTO) : [],
		};
	}

	/**
	 * 获取任务详情
	 */
	async getTask(id: number): Promise<IngestionTaskDTO> {
		const payload: unknown = await api.get({ url: `/ingestion/tasks/${id}` });
		return normalizeIngestionTaskDTO(this.resolveWrappedResponse(payload));
	}

	async getAccessDefaultPolicy(): Promise<IngestionAccessDefaultPolicyDTO> {
		return api.get({ url: "/ingestion/access/default-policy" });
	}

	async getTaskRevisions(id: number): Promise<IngestionTaskRevisionDTO[]> {
		const payload: unknown = await api.get({ url: `/ingestion/tasks/${id}/revisions` });
		const revisions = this.resolveWrappedResponse<unknown>(payload);
		return Array.isArray(revisions)
			? revisions.flatMap((revision) => {
					const normalized = normalizeIngestionTaskRevisionDTO(revision);
					return normalized ? [normalized] : [];
				})
			: [];
	}

	async getEffectiveConfig(id: number): Promise<IngestionEffectiveConfigDTO> {
		const payload: unknown = await api.get({ url: `/ingestion/tasks/${id}/effective-config` });
		return normalizeIngestionEffectiveConfigDTO(this.resolveWrappedResponse(payload));
	}

	/**
	 * 获取默认数据湖写入器状态
	 */
	async getDefaultDestinationStatus(): Promise<DefaultDestinationStatus> {
		return api.get({ url: "/ingestion/default-destination" });
	}

	/**
	 * 更新任务
	 */
	async updateTask(id: number, data: IngestionTaskDTO): Promise<IngestionTaskDTO> {
		return api.put({ url: `/ingestion/tasks/${id}`, data });
	}

	async admitTask(id: number, planChecksum?: string): Promise<IngestionTaskDTO> {
		const payload: any = await api.post({
			url: `/ingestion/tasks/${id}/admit`,
			headers: planChecksum ? { "X-Expected-Plan-Checksum": planChecksum } : undefined,
		});
		return this.resolveWrappedResponse<IngestionTaskDTO>(payload);
	}

	async parseStagingFile(taskId: number): Promise<IngestionStagingParseResult> {
		const payload = await api.post<StagingAPIResponse<IngestionStagingParseResult>>({
			url: `/ingestion/tasks/${taskId}/parse`,
		});
		return this.resolveWrappedResponse<IngestionStagingParseResult>(payload);
	}

	async preCheckStaging(taskId: number): Promise<IngestionStagingPreCheckResult> {
		const payload = await api.post<StagingAPIResponse<IngestionStagingPreCheckResult>>({
			url: `/ingestion/tasks/${taskId}/pre-check`,
		});
		return this.resolveWrappedResponse<IngestionStagingPreCheckResult>(payload);
	}

	async reCheckStaging(taskId: number): Promise<IngestionStagingPreCheckResult> {
		const payload = await api.post<StagingAPIResponse<IngestionStagingPreCheckResult>>({
			url: `/ingestion/tasks/${taskId}/re-check`,
		});
		return this.resolveWrappedResponse<IngestionStagingPreCheckResult>(payload);
	}

	async updateStagingCell(
		taskId: number,
		rowNum: number,
		data: { column: string; value: unknown },
	): Promise<IngestionStagingCellUpdateResult> {
		const payload = await api.put<StagingAPIResponse<IngestionStagingCellUpdateResult>>({
			url: `/ingestion/tasks/${taskId}/staging/${rowNum}`,
			data,
		});
		return this.resolveWrappedResponse<IngestionStagingCellUpdateResult>(payload);
	}

	async getStagingRows(
		taskId: number,
		params?: { errorsOnly?: boolean; page?: number; size?: number; sort?: string },
	): Promise<PageResult<IngestionStagingRow>> {
		const payload = await api.get<StagingAPIResponse<PageResult<IngestionStagingRow>>>({
			url: `/ingestion/tasks/${taskId}/staging`,
			params,
		});
		return this.resolveWrappedResponse<PageResult<IngestionStagingRow>>(payload);
	}

	async dropStaging(taskId: number): Promise<void> {
		await api.delete({ url: `/ingestion/tasks/${taskId}/staging` });
	}

	/**
	 * 删除任务（软删除）
	 */
	async deleteTask(id: number): Promise<void> {
		return api.delete({ url: `/ingestion/tasks/${id}` });
	}

	/**
	 * 执行任务
	 */
	async executeTask(id: number): Promise<IngestionExecutionDTO> {
		return api.post({ url: `/ingestion/tasks/${id}/execute` });
	}

	async executeTaskAsync(id: number, idempotencyKey?: string): Promise<AsyncExecutionSubmitResult> {
		return api.post({
			url: `/ingestion/tasks/${id}/execute/async`,
			headers: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined,
			_skipErrorToast: true,
		} as any);
	}

	async backfillTask(id: number, data: BackfillSubmitRequest): Promise<BackfillSubmitResult> {
		return api.post({ url: `/ingestion/tasks/${id}/backfill`, data, _skipErrorToast: true } as any);
	}

	/**
	 * 强制重建 DAG
	 */
	async rebuildDag(id: number): Promise<IngestionTaskDTO> {
		return api.post({ url: `/ingestion/tasks/${id}/dag/rebuild` });
	}

	/**
	 * 获取任务执行历史
	 */
	async getExecutions(
		taskId: number,
		params?: {
			page?: number;
			size?: number;
			sort?: string;
			status?: string;
			failureCategory?: string;
			revisionNumber?: number;
		},
	): Promise<PageResult<IngestionExecutionDTO>> {
		return api.get({ url: `/ingestion/tasks/${taskId}/executions`, params });
	}

	/**
	 * 获取最新执行记录
	 */
	async getLatestExecution(taskId: number): Promise<IngestionExecutionDTO | null> {
		try {
			const response = await api.get<
				IngestionExecutionDTO | { status: number | string; data: IngestionExecutionDTO | null }
			>({
				url: `/ingestion/tasks/${taskId}/executions/latest`,
				_acceptedEnvelopeStatuses: [404],
				_returnEnvelope: true,
				_skipErrorToast: true,
			} as any);
			if (response && typeof response === "object" && "data" in response && "status" in response) {
				return Number(response.status) === 404 ? null : response.data;
			}
			return response;
		} catch (error: any) {
			if (error.response?.status === 404) {
				return null;
			}
			throw error;
		}
	}

	async getExecution(taskId: number, executionId: number): Promise<IngestionExecutionDTO> {
		const payload: unknown = await api.get({ url: `/ingestion/tasks/${taskId}/executions/${executionId}` });
		return this.resolveWrappedResponse<IngestionExecutionDTO>(payload as IngestionExecutionDTO);
	}

	async cancelExecution(taskId: number, executionId: number): Promise<IngestionExecutionDTO> {
		const payload: unknown = await api.post({
			url: `/ingestion/tasks/${taskId}/executions/${executionId}/cancel`,
		});
		return this.resolveWrappedResponse<IngestionExecutionDTO>(payload as IngestionExecutionDTO);
	}

	/**
	 * 获取执行日志
	 */
	async getExecutionLog(
		taskId: number,
		executionId: number,
		params?: { tryNumber?: number; keyword?: string; scope?: "single" | "all" },
	): Promise<IngestionExecutionLog> {
		return api.get({ url: `/ingestion/tasks/${taskId}/executions/${executionId}/logs`, params });
	}

	async getExecutionsObservability(params?: {
		taskId?: number;
		sourceType?: string;
		sourceDataSourceId?: string;
		from?: string;
		to?: string;
		days?: number;
		timeoutMinutes?: number;
	}): Promise<IngestionExecutionObservabilityDTO> {
		return api.get({ url: "/ingestion/tasks/executions/observability", params });
	}

	async getGovernanceOverview(params?: { hours?: number }): Promise<IngestionGovernanceOverviewDTO> {
		return api.get({ url: "/ingestion/tasks/executions/governance-overview", params });
	}

	async retryExecution(
		taskId: number,
		executionId: number,
		params?: { mode?: "FAILED_ONLY" | "FULL_RERUN" },
	): Promise<IngestionExecutionDTO> {
		return api.post({ url: `/ingestion/tasks/${taskId}/executions/${executionId}/retry`, params });
	}

	async retryExecutionAsync(
		taskId: number,
		executionId: number,
		params?: { mode?: "FAILED_ONLY" | "FULL_RERUN" },
		idempotencyKey?: string,
	): Promise<any> {
		return api.post({
			url: `/ingestion/tasks/${taskId}/executions/${executionId}/retry/async`,
			params,
			headers: idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined,
			_skipErrorToast: true,
		} as any);
	}

	async getIncrementalStates(taskId: number): Promise<IngestionIncrementalStateDTO[]> {
		return api.get({ url: `/ingestion/tasks/${taskId}/incremental-states` });
	}

	async getIncrementalAudits(
		taskId: number,
		params?: { executionId?: number },
	): Promise<IngestionIncrementalAuditDTO[]> {
		return api.get({ url: `/ingestion/tasks/${taskId}/incremental-audits`, params });
	}

	async getIncrementalAuditsPage(
		taskId: number,
		params?: {
			executionId?: number;
			executionIds?: number[];
			from?: string;
			to?: string;
			tableName?: string;
			status?: "advanced" | "unchanged";
			page?: number;
			size?: number;
			sort?: string;
		},
	): Promise<PageResult<IngestionIncrementalAuditDTO>> {
		return api.get({ url: `/ingestion/tasks/${taskId}/incremental-audits/page`, params });
	}

	async getIncrementalAuditsSummary(
		taskId: number,
		params?: {
			executionId?: number;
			executionIds?: number[];
			from?: string;
			to?: string;
			tableName?: string;
			status?: "advanced" | "unchanged";
		},
	): Promise<IngestionIncrementalAuditSummaryDTO> {
		return api.get({ url: `/ingestion/tasks/${taskId}/incremental-audits/summary`, params });
	}

	async uploadAndParseFile(
		file: File,
		options: {
			classification: ClassificationLevel;
			previewLimit?: number;
			sheetIndex?: number;
			sheetName?: string;
		},
	): Promise<ManagedFileUploadResult> {
		const formData = new FormData();
		formData.append("file", file);
		formData.append("classification", options.classification);
		if (options?.previewLimit != null) {
			formData.append("previewLimit", String(options.previewLimit));
		}
		if (options?.sheetIndex != null) {
			formData.append("sheetIndex", String(options.sheetIndex));
		}
		if (options?.sheetName) {
			formData.append("sheetName", options.sheetName);
		}
		const payload: any = await api.post({
			url: "/ingestion/files/upload-and-parse",
			data: formData,
			headers: { "Content-Type": "multipart/form-data" },
		});
		return normalizeManagedFileUploadResult(this.resolveWrappedResponse<unknown>(payload));
	}

	async getConnectorCapabilities(): Promise<IngestionConnectorCapabilityDTO[]> {
		const payload: any = await api.get({ url: "/ingestion/connectors/capabilities" });
		if (Array.isArray(payload)) return payload as IngestionConnectorCapabilityDTO[];
		if (payload && typeof payload === "object" && Array.isArray((payload as any).data)) {
			return (payload as any).data as IngestionConnectorCapabilityDTO[];
		}
		return [];
	}

	async getTaskTemplates(): Promise<IngestionTaskTemplateDTO[]> {
		const payload: any = await api.get({ url: "/ingestion/templates" });
		if (Array.isArray(payload)) return payload as IngestionTaskTemplateDTO[];
		if (payload && typeof payload === "object" && Array.isArray((payload as any).data)) {
			return (payload as any).data as IngestionTaskTemplateDTO[];
		}
		return [];
	}

	async renderTaskTemplate(
		templateId: string,
		data?: { params?: Record<string, any>; strictRequired?: boolean },
	): Promise<IngestionTemplateRenderDTO | null> {
		if (!templateId) return null;
		const payload: any = await api.post({
			url: `/ingestion/templates/${encodeURIComponent(templateId)}/render`,
			data,
		});
		if (!payload) return null;
		if (payload && typeof payload === "object" && "renderedDefaults" in payload) {
			return payload as IngestionTemplateRenderDTO;
		}
		if (payload && typeof payload === "object" && (payload as any).data) {
			return (payload as any).data as IngestionTemplateRenderDTO;
		}
		return null;
	}

	async getConnectorCapability(connectorType: string): Promise<IngestionConnectorCapabilityDTO | null> {
		try {
			const payload: any = await api.get({ url: `/ingestion/connectors/capabilities/${connectorType}` });
			if (!payload) return null;
			if (payload && typeof payload === "object" && "connectorType" in payload) {
				return payload as IngestionConnectorCapabilityDTO;
			}
			if (payload && typeof payload === "object" && (payload as any).data) {
				return (payload as any).data as IngestionConnectorCapabilityDTO;
			}
			return null;
		} catch (error: any) {
			if (error?.response?.status === 404) return null;
			throw error;
		}
	}

	async getApiConnectorContract(): Promise<ApiConnectorContractDTO | null> {
		try {
			const payload: any = await api.get({ url: "/ingestion/api/contract" });
			if (!payload) return null;
			if (payload && typeof payload === "object" && "defaultReaderType" in payload) {
				return payload as ApiConnectorContractDTO;
			}
			if (payload && typeof payload === "object" && (payload as any).data) {
				return (payload as any).data as ApiConnectorContractDTO;
			}
			return null;
		} catch (error: any) {
			if (error?.response?.status === 404) return null;
			throw error;
		}
	}

	async getApiAuthProviders(): Promise<ApiAuthProviderDescriptorDTO[]> {
		const payload: any = await api.get({ url: "/ingestion/api/auth-providers" });
		if (Array.isArray(payload)) return payload as ApiAuthProviderDescriptorDTO[];
		if (payload && typeof payload === "object" && Array.isArray((payload as any).data)) {
			return (payload as any).data as ApiAuthProviderDescriptorDTO[];
		}
		return [];
	}

	async testApiConnection(data: ApiConnectionTestRequestDTO): Promise<ApiConnectionTestResultDTO> {
		const payload: any = await api.post({
			url: "/ingestion/api/test-connection",
			data,
			_skipErrorToast: true,
		} as any);
		return this.resolveWrappedResponse<ApiConnectionTestResultDTO>(payload);
	}

	async testManagedApiConnection(data: ManagedApiConnectionTestRequestDTO): Promise<ApiConnectionTestResultDTO> {
		const payload: any = await api.post({
			url: "/ingestion/api/test-connection",
			data,
			_skipErrorToast: true,
		} as any);
		return this.resolveWrappedResponse<ApiConnectionTestResultDTO>(payload);
	}

	async getRealtimeStatus(taskId: number): Promise<IngestionRealtimeStatusDTO | null> {
		try {
			const payload: any = await api.get({ url: `/ingestion/tasks/${taskId}/realtime-status` });
			if (!payload) return null;
			if (payload && typeof payload === "object" && "taskId" in payload) {
				return payload as IngestionRealtimeStatusDTO;
			}
			if (payload && typeof payload === "object" && (payload as any).data) {
				return (payload as any).data as IngestionRealtimeStatusDTO;
			}
			return null;
		} catch (error: any) {
			if (error?.response?.status === 404) return null;
			throw error;
		}
	}

	/**
	 * 源端表发现
	 */
	async discoverTables(data: TableDiscoveryRequest): Promise<TableInfo[]> {
		const payload: any = await api.post({ url: "/ingestion/metadata/tables", data });
		if (Array.isArray(payload)) {
			return payload as TableInfo[];
		}
		if (payload && typeof payload === "object") {
			const inner = (payload as any).data;
			const status = (payload as any).status;
			const message = (payload as any).message;
			if (status && status !== 200 && status !== "200") {
				throw new Error(message || "获取表清单失败");
			}
			if (Array.isArray(inner)) {
				return inner as TableInfo[];
			}
		}
		return [];
	}

	/**
	 * 获取接入变更记录
	 */
	async getChangeLogs(params?: {
		taskId?: number;
		objType?: string;
		changeType?: string;
		status?: string;
		assignee?: string;
		keyword?: string;
		page?: number;
		size?: number;
		sort?: string;
	}): Promise<PageResult<IngestionChangeLogDTO>> {
		return api.get({ url: "/ingestion/tasks/changes", params });
	}

	/**
	 * 登记接入变更
	 */
	async createChangeLog(data: IngestionChangeLogDTO): Promise<IngestionChangeLogDTO> {
		return api.post({ url: "/ingestion/tasks/changes", data });
	}

	async transitionChangeLog(
		id: number,
		data: { action: "SUBMIT" | "APPROVE" | "REJECT"; assignee?: string; approvalComment?: string },
	): Promise<IngestionChangeLogDTO> {
		return api.post({ url: `/ingestion/tasks/changes/${id}/transition`, data });
	}

	async getStagingErrorSummary(taskId: number, limit = 20): Promise<StagingErrorSummary> {
		return api.get({ url: `/ingestion/tasks/${taskId}/staging/errors/summary`, params: { limit } });
	}

	async downloadStagingErrors(taskId: number): Promise<Blob> {
		return api.get({
			url: `/ingestion/tasks/${taskId}/staging/errors/download`,
			responseType: "blob",
		} as any);
	}
}

export interface StagingRuleErrorSummary {
	ruleName: string;
	failCount: number;
}

export interface StagingErrorSummary {
	totalRows: number;
	cleanRows: number;
	errorRows: number;
	errorsByRule: StagingRuleErrorSummary[];
	sampleRows: Record<string, any>[];
}

export const ingestionTaskAPI = new IngestionTaskAPI();
