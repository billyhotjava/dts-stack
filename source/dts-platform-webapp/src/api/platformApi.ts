import api from "@/api/apiClient";
import { withModelingRequestTimeout } from "@/api/modelingRequestTimeout";

export type PortalSessionStatus = {
	authenticated?: boolean;
	username?: string;
	displayName?: string;
	loginIp?: string;
	clientIp?: string;
	roles?: string[];
	permissions?: string[];
	deptCode?: string;
	personnelLevel?: string;
	expiresAt?: string;
	serverNow?: string;
	remainingSeconds?: number | null;
	reason?: "CONCURRENT" | "EXPIRED" | "LOGOUT";
};

// Session status is intentionally public on the backend and reads the HttpOnly
// portal_session cookie. Do not attach Authorization or token headers here.
export const getPortalSessionStatus = () =>
	api.get<PortalSessionStatus>({
		url: "/session/status",
		_skipAuth: true,
	} as any);

// Catalog
export const getCatalogSummary = () => api.get({ url: "/catalog/summary" });
export const getCatalogConfig = () => api.get({ url: "/catalog/config" });
export const listDomains = (page = 0, size = 10, keyword = "") =>
	api.get({ url: "/catalog/domains", params: { page, size, keyword } });
export const createDomain = (data: any) => api.post({ url: "/catalog/domains", data });
export const updateDomain = (id: string, data: any) => api.put({ url: `/catalog/domains/${id}`, data });
export const deleteDomain = (id: string) => api.delete({ url: `/catalog/domains/${id}` });

export const listDatasets = (params: any = {}) => api.get({ url: "/catalog/datasets", params });
export const getDataset = (id: string) => api.get({ url: `/catalog/datasets/${id}` });
export const getDatasetOpenMetadata = (id: string) => api.get({ url: `/catalog/datasets/${id}/openmetadata` });
export const batchDatasetOpenMetadata = (ids: string[]) =>
	api.post({ url: "/catalog/datasets/openmetadata/batch", data: { ids } });
export const getDatasetLineage = (id: string) => api.get({ url: `/catalog/datasets/${id}/lineage` });
export const getDatasetQuality = (id: string) => api.get({ url: `/catalog/datasets/${id}/quality` });
export const getDatasetGovernanceHealth = (id: string) => api.get({ url: `/catalog/datasets/${id}/governance-health` });
export type IndicatorDep = { id: string; name: string; code: string; isDerived: boolean; status: string };
export const getDatasetIndicatorDeps = (datasetId: string) =>
	api.get({ url: `/catalog/datasets/${datasetId}/indicator-deps` });
export const batchDatasetQuality = (ids: string[]) =>
	api.post({ url: "/catalog/quality/batch", data: { ids } });
export const getCatalogReconciliation = (sampleLimit = 20) =>
	api.get({ url: "/catalog/ops/reconciliation", params: { sampleLimit } });
export const getTechMetadataTables = (params?: { keyword?: string; size?: number; sourceId?: string }) =>
	api.get({ url: "/catalog/metadata/tables", params });
export const getTechMetadataTableDetail = (fqn: string) =>
	api.get({ url: "/catalog/metadata/tables/detail", params: { fqn } });
export type CatalogAssetV2Query = {
	keyword?: string;
	service?: string;
	type?: string;
	database?: string;
	schema?: string;
	syncStatus?: string;
	classification?: string;
	warehouseLayer?: string;
	ownerDept?: string;
	governanceStatus?: string;
	matchStatus?: string;
	domainId?: string;
	domainUnassigned?: boolean;
	page?: number;
	size?: number;
};
export const listCatalogAssetsV2 = (params: CatalogAssetV2Query = {}) =>
	api.get({ url: "/catalog/assets-v2", params });
export const getCatalogAssetsOverview = (params: { domainId?: string; domainUnassigned?: boolean } = {}) =>
	api.get({ url: "/catalog/assets-v2/overview", params });
export const getCatalogAssetV2 = (id: string) => api.get({ url: `/catalog/assets-v2/${id}` });
export const getCatalogAssetV2Contract = (id: string) =>
	api.get<Record<string, any>>({ url: `/catalog/assets-v2/${id}/contract` });
export const getCatalogAssetV2SchemaContract = (id: string) =>
	api.get<Record<string, any>>({ url: `/catalog/assets-v2/${id}/schema-contract` });
export const getCatalogAssetsV2GovernanceGaps = (params: CatalogAssetV2Query = {}) =>
	api.get<Record<string, any>>({ url: "/catalog/assets-v2/governance-gaps", params });
export const getCatalogAssetsV2LineageFailures = (params: CatalogAssetV2Query = {}) =>
	api.get<Record<string, any>>({ url: "/catalog/assets-v2/lineage-failures", params });
export const getCatalogAssetsV2MigrationDryRun = () =>
	api.get<Record<string, any>>({ url: "/catalog/assets-v2/migration/dry-run" });
export const updateCatalogAssetV2Governance = (id: string, data: any) =>
	api.patch({ url: `/catalog/assets-v2/${id}/governance`, data });
export const getCatalogAssetV2Lineage = (id: string) => api.get({ url: `/catalog/assets-v2/${id}/lineage` });
export const syncCatalogAssetV2Lineage = (id: string, params?: { upstreamDepth?: number; downstreamDepth?: number }) =>
	api.post({ url: `/catalog/assets-v2/${id}/lineage/sync`, params });
export const getCatalogAssetsV2Diagnostics = () => api.get({ url: "/catalog/assets-v2/diagnostics" });
export const listCatalogAssetResolutionFailures = (params: { since?: string; limit?: number } = {}) =>
	api.get<Array<{
		id?: string;
		ref?: string;
		requestedAt?: string;
		caller?: string;
		typeHintGuess?: string;
		reason?: string;
	}>>({ url: "/catalog/assets-v2/resolution-failures", params });
export const syncCatalogAssetsV2 = (limit?: number) =>
	api.post({ url: "/catalog/assets-v2/sync", params: limit ? { limit } : undefined });
export type SchemaDriftEvent = {
	id: string;
	runId?: string;
	integration?: string;
	datasetId?: string;
	datasetName?: string;
	hiveDatabase?: string;
	hiveTable?: string;
	addedCount?: number;
	removedCount?: number;
	changedCount?: number;
	policyMode?: "REVIEW" | "AUTO_APPLY" | "BLOCK" | string;
	ticketStatus?: "OPEN" | "IN_REVIEW" | "RESOLVED" | "IGNORED" | "REJECTED" | string;
	ticketAssignee?: string;
	workflowNote?: string;
	handledBy?: string;
	handledAt?: string;
	createdDate?: string;
	detailsJson?: string;
};
export const listSchemaDriftEvents = (params?: {
	policyMode?: string;
	ticketStatus?: string;
	limit?: number;
	includeDetails?: boolean;
}) => api.get<SchemaDriftEvent[]>({ url: "/catalog/schema-drift", params });
export const updateSchemaDriftPolicy = (id: string, data: { policyMode: string; note?: string }) =>
	api.post<SchemaDriftEvent>({ url: `/catalog/schema-drift/${id}/policy`, data });
export const updateSchemaDriftTicket = (id: string, data: { ticketStatus: string; assignee?: string; note?: string }) =>
	api.post<SchemaDriftEvent>({ url: `/catalog/schema-drift/${id}/ticket`, data });
export const searchCatalog = (params: {
	keyword: string;
	types?: string;
	domainId?: string;
	sourceId?: string;
	classification?: string;
	ownerDept?: string;
	warehouseLayer?: string;
	exposedBy?: string;
	datasetType?: string;
	enabledOnly?: boolean;
	limit?: number;
}) =>
	api.get({ url: "/catalog/search", params });
export const getDbtConfig = () => api.get(withModelingRequestTimeout({ url: "/etl/dbt/config" }));
export const updateDbtConfig = (data: any) => api.put({ url: "/etl/dbt/config", data });

export type PlatformEventDto = {
	id?: string;
	eventId?: string;
	eventType?: string;
	domain?: string;
	sourceApp?: string;
	aggregateType?: string;
	aggregateId?: string;
	aggregateName?: string;
	action?: string;
	severity?: string;
	status?: string;
	occurredAt?: string;
	actor?: string;
	correlationId?: string;
	traceId?: string;
	auditActionCode?: string;
	policyRef?: string;
	payload?: Record<string, any>;
	dispatchStatus?: string;
	dispatchAttempts?: number;
	dispatchedAt?: string;
	dispatchError?: string;
	createdBy?: string;
	createdDate?: string;
};

export type PlatformEventSummary = {
	total: number;
	pending: number;
	sent: number;
	failed: number;
	skipped: number;
	kafkaEnabled?: boolean;
	kafkaTopic?: string;
	byDomain?: Record<string, number>;
	byStatus?: Record<string, number>;
	bySeverity?: Record<string, number>;
};

export type PlatformEventPage = {
	content: PlatformEventDto[];
	total: number;
	page: number;
	size: number;
	totalPages: number;
};

export type Sprint27SourceStatus = {
	status: "READY" | "EMPTY" | "ERROR" | string;
	source?: string;
	message?: string;
	checkedAt?: string;
};

export type Sprint27EltConsole = {
	sources?: Record<string, Sprint27SourceStatus>;
	observability?: Record<string, any>;
	governance?: Record<string, any>;
	stages?: any[];
	chainItems?: any[];
};

export type Sprint27MetricOperations = {
	sources?: Record<string, Sprint27SourceStatus>;
	overview?: Record<string, any>;
	trendRows?: any[];
	domains?: any[];
	objects?: any[];
	metrics?: any[];
	models?: any[];
	runs?: any[];
};

export type Sprint27EventsConsole = {
	summary?: PlatformEventSummary;
	page?: PlatformEventPage;
};

export type Sprint27AuditEvidence = {
	sources?: Record<string, Sprint27SourceStatus>;
	events?: PlatformEventDto[];
	rows?: any[];
	summary?: Record<string, any>;
};

export type Sprint27ReleaseGovernance = {
	sources?: Record<string, Sprint27SourceStatus>;
	readyForRelease?: boolean;
	blockerFailed?: number;
	eventFailed?: number;
	ingestionFailed?: number;
	indicatorFailed?: number;
	dbtBlocked?: boolean;
	checkedAt?: string;
	governanceGate?: Record<string, any>;
	indicatorOverview?: Record<string, any>;
	ingestionOverview?: Record<string, any>;
	eventSummary?: PlatformEventSummary;
	dbtGate?: any;
	checks?: any[];
};

export const getPlatformEventSummary = () =>
	api.get<PlatformEventSummary>({ url: "/platform/events/summary" });
export const listPlatformEvents = (params: any = {}) =>
	api.get<PlatformEventPage>({ url: "/platform/events", params });
export const getSprint27EltConsole = (params: any = {}) =>
	api.get<Sprint27EltConsole>({ url: "/platform/sprint27/elt-console", params });
export const getSprint27MetricOperations = (params: any = {}) =>
	api.get<Sprint27MetricOperations>({ url: "/platform/sprint27/metric-operations", params });
export const getSprint27EventsConsole = (params: any = {}) =>
	api.get<Sprint27EventsConsole>({ url: "/platform/sprint27/events-console", params });
export const getSprint27AuditEvidence = () =>
	api.get<Sprint27AuditEvidence>({ url: "/platform/sprint27/audit-evidence" });
export const getSprint27ReleaseGovernance = (params: any = {}) =>
	api.get<Sprint27ReleaseGovernance>({ url: "/platform/sprint27/release-governance", params });

// dbt project file management
export const getDbtFileTree = () => api.get({ url: "/etl/dbt/files/tree" });
export const getDbtFileContent = (path: string) =>
	api.get({ url: "/etl/dbt/files/content", params: { path } });
export const saveDbtFileContent = (data: { path: string; content: string }) =>
	api.put({ url: "/etl/dbt/files/content", data });
export const createDbtFile = (data: { path: string; type: "file" | "directory"; content?: string }) =>
	api.post({ url: "/etl/dbt/files", data });
export const deleteDbtFile = (path: string) =>
	api.delete({ url: "/etl/dbt/files", params: { path } });
export const renameDbtFile = (data: { oldPath: string; newPath: string }) =>
	api.put({ url: "/etl/dbt/files/rename", data });
export const uploadDbtArchive = (data: FormData, clean = false) =>
	api.post<{ extracted: string[]; skipped: string[]; cleaned: string[]; cleanBeforeExtract: boolean }>(
		withModelingRequestTimeout({ url: "/etl/dbt/files/upload-archive", params: { clean }, data }),
	);
export const listDbtModels = () => api.get({ url: "/etl/dbt/models" });
export const syncDbtModels = () => api.post(withModelingRequestTimeout({ url: "/etl/dbt/models/sync" }));
export const getDbtSyncStatus = (params?: { models?: string }) =>
	api.get(withModelingRequestTimeout({ url: "/etl/dbt/sync/status", params }));
export const checkDagReady = (params?: { selector?: string }) =>
	api.get(withModelingRequestTimeout({ url: "/etl/dbt/dag/ready", params }));
export const listDbtRuns = (limit = 20, params?: { dagId?: string; selector?: string }) =>
	api.get(withModelingRequestTimeout({ url: "/etl/dbt/runs", params: { limit, ...(params || {}) } }));
export const triggerDbtRun = (data: any) => api.post(withModelingRequestTimeout({ url: "/etl/dbt/run", data }));
export const triggerDbtCompile = (data?: any) => api.post(withModelingRequestTimeout({ url: "/etl/dbt/compile", data }));
export const triggerDbtTest = (data?: any) => api.post(withModelingRequestTimeout({ url: "/etl/dbt/test", data }));
export const triggerDbtDocs = (data?: any) => api.post(withModelingRequestTimeout({ url: "/etl/dbt/docs", data }));
export const checkDbtQualityGate = (data?: any) =>
	api.post(withModelingRequestTimeout({ url: "/etl/dbt/quality-gate/check", data }));
export const checkDbtReleaseGate = (data?: any) =>
	api.post(withModelingRequestTimeout({ url: "/etl/dbt/release-gate/check", data }));
export const submitDbtRelease = (data: any) =>
	api.post(withModelingRequestTimeout({ url: "/etl/dbt/release/submit", data }));

// dbt execution log (from Airflow)
export const getDbtRunLog = (dagRunId: string, params?: { dagId?: string; taskId?: string; tryNumber?: number }) =>
	api.get(withModelingRequestTimeout({ url: `/etl/dbt/runs/${encodeURIComponent(dagRunId)}/logs`, params }));

// Airflow generic task log (non-dbt DAGs or explicit taskId)
export const getAirflowTaskLog = (
	dagId: string,
	dagRunId: string,
	taskId: string,
	tryNumber = 1,
) =>
	api.get<{ dagId: string; dagRunId: string; taskId: string; tryNumber: number; log: string }>({
		url: `/etl/airflow/jobs/${encodeURIComponent(dagId)}/runs/${encodeURIComponent(dagRunId)}/task-logs`,
		params: { taskId, tryNumber },
	});

// Airflow task instances for a DAG run
export const listAirflowTaskInstances = (dagId: string, dagRunId: string) =>
	api.get<{ task_instances: AirflowTaskInstance[] }>({
		url: `/etl/airflow/jobs/${encodeURIComponent(dagId)}/runs/${encodeURIComponent(dagRunId)}/tasks`,
	});

export type AirflowTaskInstance = {
	task_id: string;
	dag_id: string;
	dag_run_id: string;
	state?: string;
	start_date?: string;
	end_date?: string;
	duration?: number;
	try_number?: number;
	operator?: string;
};

// dbt data preview
export const previewDbtModel = (model: string, limit = 100) =>
	api.get(withModelingRequestTimeout({ url: "/etl/dbt/preview", params: { model, limit } }));
export const getDbtModelDiagnostics = (model: string) =>
	api.get(withModelingRequestTimeout({ url: `/etl/dbt/models/${encodeURIComponent(model)}/diagnostics` }));
export const getDbtOutputRelation = (modelId: string) =>
	api.get(withModelingRequestTimeout({ url: "/etl/dbt/output", params: { modelId } }));
export const truncateDbtOutputRelation = (data: { modelId: string; target?: string }) =>
	api.post(withModelingRequestTimeout({ url: "/etl/dbt/output/truncate", data }));
export const rebuildDbtOutputRelation = (data: { modelId: string; target?: string; vars?: Record<string, any> }) =>
	api.post({ url: "/etl/dbt/output/rebuild", data });

// dbt git operations
export const getDbtGitStatus = () => api.get({ url: "/etl/dbt/git/status" });
export const commitDbtChanges = (data: { message: string; authorName?: string; authorEmail?: string }) =>
	api.post({ url: "/etl/dbt/git/commit", data });
export const getDbtGitLog = (limit = 20) => api.get({ url: "/etl/dbt/git/log", params: { limit } });
export const getDbtGitDiff = (path?: string) =>
	api.get({ url: "/etl/dbt/git/diff", params: path ? { path } : {} });
export const revertDbtFile = (path: string) =>
	api.post({ url: "/etl/dbt/git/revert", data: { path } });
export const getDbtFileAtCommit = (path: string, commitHash: string) =>
	api.get({ url: "/etl/dbt/git/file-at-commit", params: { path, commitHash } });
export const listSqlModels = (params?: any) => api.get(withModelingRequestTimeout({ url: "/modeling/sql-models", params }));
export const getSqlModel = (id: string) => api.get(withModelingRequestTimeout({ url: `/modeling/sql-models/${id}` }));
export const listSqlModelColumns = (id: string) =>
	api.get(withModelingRequestTimeout({ url: `/modeling/sql-models/${id}/columns` }));
export const getSqlModelContractImpact = (id: string) =>
	api.get(withModelingRequestTimeout({ url: `/modeling/sql-models/${id}/contract-impact` }));
export const publishSqlModelSemantic = (id: string) =>
	api.post(withModelingRequestTimeout({ url: `/modeling/sql-models/${id}/semantic/publish` }));
export const listSqlModelStandardBindings = (id: string) =>
	api.get(withModelingRequestTimeout({ url: `/modeling/sql-models/${id}/standard-bindings` }));
export const saveSqlModelStandardBindings = (id: string, data: any) =>
	api.put(withModelingRequestTimeout({ url: `/modeling/sql-models/${id}/standard-bindings`, data }));
export const generateSqlModelSchemaYml = (id: string) =>
	api.post(withModelingRequestTimeout({ url: `/modeling/sql-models/${id}/dbt/schema-yml` }));
export const checkSqlModelStandardGate = (id: string) =>
	api.post(withModelingRequestTimeout({ url: `/modeling/sql-models/${id}/standard-gate/check` }));
export const createStandardBindingDraftSnapshot = (data: any) =>
	api.post(withModelingRequestTimeout({ url: "/modeling/standard-binding-drafts", data }));
export const getStandardBindingDraftSnapshot = (id: string) =>
	api.get(withModelingRequestTimeout({ url: `/modeling/standard-binding-drafts/${id}` }));
export const createSqlModel = (data: any) => api.post(withModelingRequestTimeout({ url: "/modeling/sql-models", data }));
export const updateSqlModel = (id: string, data: any) =>
	api.put(withModelingRequestTimeout({ url: `/modeling/sql-models/${id}`, data }));
export const deleteSqlModel = (id: string) => api.delete(withModelingRequestTimeout({ url: `/modeling/sql-models/${id}` }));
export const batchDeleteSqlModels = (data: { modelIds: string[] }) =>
	api.post(withModelingRequestTimeout({ url: "/modeling/sql-models/batch-delete", data }));
export const importSqlModel = (data: FormData) => api.post(withModelingRequestTimeout({ url: "/modeling/sql-models/import", data }));
export const batchImportSqlModels = (data: FormData) =>
	api.post(withModelingRequestTimeout({ url: "/modeling/sql-models/batch-import", data }));
export const generateSqlModelsFromOds = (data: any) =>
	api.post(withModelingRequestTimeout({ url: "/modeling/sql-models/generate-from-ods", data }));
export const previewSqlModelGovernance = (data: any) =>
	api.post(withModelingRequestTimeout({ url: "/modeling/sql-models/governance/preview", data }));
export const executeSqlModelGovernance = (data: any) =>
	api.post(withModelingRequestTimeout({ url: "/modeling/sql-models/governance/execute", data }));
export const listDbtSources = (params?: { keyword?: string; sourceDataSourceId?: string }) =>
	api.get(withModelingRequestTimeout({ url: "/modeling/sql-models/dbt/sources", params }));
export const listDbtRefs = (params?: { keyword?: string; layer?: string }) =>
	api.get(withModelingRequestTimeout({ url: "/modeling/sql-models/dbt/refs", params }));
export const listAirflowJobs = (limit = 50) => api.get({ url: "/etl/airflow/jobs", params: { limit } });
export const listAirflowJobRuns = (dagId: string, limit = 20) =>
	api.get({ url: `/etl/airflow/jobs/${dagId}/runs`, params: { limit } });
export const triggerAirflowJob = (dagId: string, data?: any) => api.post({ url: `/etl/airflow/jobs/${dagId}/trigger`, data });
export const createDataset = (data: any) => api.post({ url: "/catalog/datasets", data });
export const updateDataset = (id: string, data: any) => api.put({ url: `/catalog/datasets/${id}`, data });
export const deleteDataset = (id: string) => api.delete({ url: `/catalog/datasets/${id}` });
export const listDatasetGrants = (datasetId: string) => api.get({ url: `/catalog/datasets/${datasetId}/grants` });
export const createDatasetGrant = (datasetId: string, data: any) =>
	api.post({ url: `/catalog/datasets/${datasetId}/grants`, data });
export const deleteDatasetGrant = (datasetId: string, grantId: string) =>
	api.delete({ url: `/catalog/datasets/${datasetId}/grants/${grantId}` });

export const getDomainTree = () => api.get({ url: "/catalog/domains/tree" });
export const moveDomain = (id: string, data: { newParentId?: string | null }) =>
	api.post({ url: `/catalog/domains/${id}/move`, data });
export const getDomainAssetStats = (domainId: string): Promise<{
	datasetCount: number;
	indicatorCount: number | null;
	qualityRuleCount: number | null;
}> =>
	api.get({ url: `/catalog/domains/${domainId}/asset-stats` }).then((r: any) => r.data?.data);

export type DatasetField = {
	name: string;
	dataType: string;
	comment?: string;
	nullable?: boolean;
	tableName?: string;
};

export const getDatasetFields = (datasetId: string): Promise<DatasetField[]> =>
	api.get({ url: `/catalog/datasets/${datasetId}/fields` }).then((r: any) => r.data?.data ?? []);

// Asset extras (tasks)
export const syncDatasetSchema = (datasetId: string, data?: any) =>
    api.post({ url: `/datasets/${datasetId}/sync-schema`, data });
export const previewDataset = (datasetId: string, rows = 50) =>
    api.get({ url: `/datasets/${datasetId}/preview`, params: { rows } });
export const getDatasetJob = (jobId: string) => api.get({ url: `/dataset-jobs/${jobId}` });
export const listDatasetJobs = (datasetId: string) => api.get({ url: `/datasets/${datasetId}/jobs` });

// Dataset data-access approval (query/preview)
export type DatasetAccessRequestCreatePayload = {
	datasetId: string;
	targetUserId?: string;
	targetUsername: string;
	targetName?: string;
	targetDept?: string;
	canQuery?: boolean;
	canPreview?: boolean;
	validFrom?: string;
	validTo?: string;
	reason?: string;
};

export const createDatasetAccessRequest = (data: DatasetAccessRequestCreatePayload) =>
	api.post({ url: "/catalog/access/requests", data });

export const listMyDatasetAccessRequests = (
	params: { status?: string; keyword?: string; datasetId?: string; page?: number; size?: number } = {},
) => api.get({ url: "/catalog/access/requests/mine", params });
export const getDatasetAccessRequestDetail = (requestId: string) =>
	api.get({ url: `/catalog/access/requests/${requestId}` });
export const cancelDatasetAccessRequest = (requestId: string, notes?: string) =>
	api.post({ url: `/catalog/access/requests/${requestId}/cancel`, data: notes ? { notes } : {} });

export const listPendingDatasetAccessTasks = (
	params: { keyword?: string; datasetId?: string; page?: number; size?: number } = {},
) => api.get({ url: "/catalog/access/tasks/pending", params });

export const listDoneDatasetAccessTasks = (
	params: { keyword?: string; datasetId?: string; status?: string; page?: number; size?: number } = {},
) => api.get({ url: "/catalog/access/tasks/done", params });

export const getDatasetAccessWorkflowPreview = (datasetId: string) =>
	api.get({ url: "/catalog/access/workflow/preview", params: { datasetId } });

export const listDatasetAccessRequestSteps = (requestId: string) => api.get({ url: `/catalog/access/requests/${requestId}/steps` });

export const approveDatasetAccessTask = (taskId: string, notes?: string) =>
	api.post({ url: `/catalog/access/tasks/${taskId}/approve`, data: notes ? { notes } : {} });

export const rejectDatasetAccessTask = (taskId: string, notes?: string) =>
	api.post({ url: `/catalog/access/tasks/${taskId}/reject`, data: notes ? { notes } : {} });
export const decideDatasetAccessTask = (taskId: string, approved: boolean, notes?: string) =>
	api.post({ url: `/catalog/access/tasks/${taskId}/decide`, data: { approved, ...(notes ? { notes } : {}) } });
export const decideDatasetAccessTaskBatch = (taskIds: string[], approved: boolean, notes?: string) =>
	api.post({ url: "/catalog/access/tasks/decide/batch", data: { taskIds, approved, ...(notes ? { notes } : {}) } });

export const listMaskingRules = () => api.get<any[]>({ url: "/catalog/masking-rules" });
export const createMaskingRule = (data: any) => api.post({ url: "/catalog/masking-rules", data });
export const updateMaskingRule = (id: string, data: any) => api.put({ url: `/catalog/masking-rules/${id}`, data });
export const deleteMaskingRule = (id: string) => api.delete({ url: `/catalog/masking-rules/${id}` });
export const previewMasking = (data: any) => api.post({ url: "/catalog/masking-rules/preview", data });

export const getClassificationMapping = () => api.get({ url: "/catalog/classification-mapping" });
export const replaceClassificationMapping = (data: any[]) => api.put({ url: "/catalog/classification-mapping", data });
export const validateClassificationMapping = (data: any[]) =>
	api.post({ url: "/catalog/classification-mapping/validate", data });
export const importClassificationMapping = (data: any[]) =>
	api.post({ url: "/catalog/classification-mapping/import", data });
export const exportClassificationMapping = () => api.get({ url: "/catalog/classification-mapping/export" });
export const getClassificationMaskingLinkage = (datasetId?: string) =>
	api.get({ url: "/catalog/classification-masking/linkage", params: datasetId ? { datasetId } : undefined });

export const getDatasetSecurityMapping = (datasetId: string) =>
    api.get({ url: `/catalog/datasets/${datasetId}/security-mapping` });
export const upsertDatasetSecurityMapping = (datasetId: string, data: any) =>
    api.put({ url: `/catalog/datasets/${datasetId}/security-mapping`, data });

// Infra external links (ETL entry)
export const listExternalLinks = () => api.get({ url: "/infra/external-links" });
export const getExternalLink = (entryKey: string) => api.get({ url: `/infra/external-links/${entryKey}` });
export const checkExternalLink = (entryKey: string) => api.get({ url: `/infra/external-links/${entryKey}/check` });
export const getExternalLinkStatus = (entryKey: string) => api.get({ url: `/infra/external-links/${entryKey}/status` });
export const visitExternalLink = (entryKey: string, data?: any) => api.post({ url: `/infra/external-links/${entryKey}/visit`, data });
export const upsertExternalLink = (entryKey: string, data: any) => api.put({ url: `/infra/external-links/${entryKey}`, data });
export const deleteExternalLink = (entryKey: string) => api.delete({ url: `/infra/external-links/${entryKey}` });

// Addax (data lake ingestion)
export const createIngestionTask = (data: any) =>
	api.post({ url: "/ingestion/tasks", data, timeout: 180000 });

// External exchange files (data ingestion ledger)
export const listExchangeFiles = (params?: any) => api.get({ url: "/infra/exchange-files", params });
export const createExchangeFile = (data: any) => api.post({ url: "/infra/exchange-files", data });
export const updateExchangeFile = (id: string, data: any) => api.put({ url: `/infra/exchange-files/${id}`, data });
export const deleteExchangeFile = (id: string) => api.delete({ url: `/infra/exchange-files/${id}` });

// Security audit logs (proxy to dts-admin)
export const listAuditLogs = (params: any = {}) => api.get({ url: "/security/audit-logs", params });
export const getAuditLog = (id: string) => api.get({ url: `/security/audit-logs/${id}` });

// Modeling
export const listStandards = (params: any = {}) => api.get({ url: "/modeling/standards", params });
export const getStandard = (id: string) => api.get({ url: `/modeling/standards/${id}` });
export const createStandard = (data: any) => api.post({ url: "/modeling/standards", data });
export const updateStandard = (id: string, data: any) => api.put({ url: `/modeling/standards/${id}`, data });
export const deleteStandard = (id: string) => api.delete({ url: `/modeling/standards/${id}` });
export const archiveStandard = (id: string) => api.post({ url: `/modeling/standards/${id}/archive` });
export const listStandardVersions = (id: string) => api.get({ url: `/modeling/standards/${id}/versions` });
export const listStandardAttachments = (id: string) => api.get({ url: `/modeling/standards/${id}/attachments` });
export const importStandards = (formData: FormData) =>
    api.post({ url: "/modeling/standards/import", data: formData });
export const uploadStandardAttachment = (id: string, formData: FormData) =>
    api.post({
        url: `/modeling/standards/${id}/attachments`,
        data: formData,
    });
export const deleteStandardAttachment = (standardId: string, attachmentId: string) =>
	api.delete({ url: `/modeling/standards/${standardId}/attachments/${attachmentId}` });
export const getStandardSettings = () => api.get({ url: "/modeling/standards/settings" });
export const updateStandardSettings = (data: any) => api.put({ url: "/modeling/standards/settings", data });
export const getStandardHealth = () => api.get({ url: "/modeling/standards/health" });

// Metadata standards
export const listMetadataStandards = (params: any = {}) => api.get({ url: "/modeling/metadata-standards", params });
export const getMetadataStandard = (id: string) => api.get({ url: `/modeling/metadata-standards/${id}` });
export const createMetadataStandard = (data: any) => api.post({ url: "/modeling/metadata-standards", data });
export const updateMetadataStandard = (id: string, data: any) => api.put({ url: `/modeling/metadata-standards/${id}`, data });
export const deleteMetadataStandard = (id: string) => api.delete({ url: `/modeling/metadata-standards/${id}` });
export const getMetadataStandardReferences = (id: string) =>
	api.get({ url: `/modeling/metadata-standards/${id}/references` });
export const downloadDataStandardPackageTemplate = () =>
	api.get<Blob>({ url: "/modeling/metadata-standards/template", responseType: "blob" });
export const importMetadataStandards = (formData: FormData) =>
    api.post({ url: "/modeling/metadata-standards/import", data: formData });

// Standard packages (数据元+码表+术语 打包导入管道)
export const previewStandardPackageImport = (formData: FormData) =>
	api.post({ url: "/modeling/standard-packages/import/preview", data: formData });
export const applyStandardPackageImport = (runId: string) =>
	api.post({ url: "/modeling/standard-packages/import/apply", data: { runId } });
export const listStandardPackageRuns = (params: any = {}) =>
	api.get({ url: "/modeling/standard-packages/runs", params });
export const getStandardPackageRun = (runId: string) =>
	api.get({ url: `/modeling/standard-packages/runs/${runId}` });
export const rollbackStandardPackageRun = (runId: string) =>
	api.post({ url: `/modeling/standard-packages/runs/${runId}/rollback` });
export const listBuiltinStandardPackages = () => api.get({ url: "/modeling/standard-packages/builtin" });
export const installBuiltinStandardPackage = (code: string) =>
	api.post({ url: `/modeling/standard-packages/builtin/${code}/install` });

// Reference codes (public code tables)
export const listReferenceCodes = (params: any = {}) => api.get({ url: "/governance/reference-codes", params });
export const getReferenceCode = (id: string) => api.get({ url: `/governance/reference-codes/${id}` });
export const createReferenceCode = (data: any) => api.post({ url: "/governance/reference-codes", data });
export const updateReferenceCode = (id: string, data: any) =>
	api.put({ url: `/governance/reference-codes/${id}`, data });
export const deleteReferenceCode = (id: string) => api.delete({ url: `/governance/reference-codes/${id}` });
export const getReferenceCodeReferences = (id: string) =>
	api.get({ url: `/governance/reference-codes/${id}/references` });
export const getReferenceCodeImportOpsOverview = (params: { hours?: number } = {}) =>
	api.get({ url: "/governance/reference-codes/ops/import-overview", params });
export const listReferenceCodeItems = (id: string) =>
	api.get({ url: `/governance/reference-codes/${id}/items` });
export const createReferenceCodeItem = (id: string, data: any) =>
	api.post({ url: `/governance/reference-codes/${id}/items`, data });
export const batchReferenceCodeItems = (id: string, data: { raw: string }) =>
	api.post({ url: `/governance/reference-codes/${id}/items/batch`, data });
export const previewStructuredReferenceCodeImport = (
	id: string,
	data: { conflictPolicy?: string; rows: Array<Record<string, any>> },
) => api.post({ url: `/governance/reference-codes/${id}/items/import/preview`, data });
export const applyStructuredReferenceCodeImport = (
	id: string,
	data: { conflictPolicy?: string; rows: Array<Record<string, any>> },
) => api.post({ url: `/governance/reference-codes/${id}/items/import/apply`, data });
export const rollbackStructuredReferenceCodeImport = (id: string, runId: string) =>
	api.post({ url: `/governance/reference-codes/${id}/items/import/${runId}/rollback` });
export const listStructuredReferenceCodeImportRuns = (id: string) =>
	api.get({ url: `/governance/reference-codes/${id}/items/import/runs` });
export const getStructuredReferenceCodeImportRun = (id: string, runId: string) =>
	api.get({ url: `/governance/reference-codes/${id}/items/import/${runId}` });
export const updateReferenceCodeItem = (id: string, itemId: string | number, data: any) =>
	api.put({ url: `/governance/reference-codes/${id}/items/${itemId}`, data });
export const deleteReferenceCodeItem = (id: string, itemId: string | number) =>
	api.delete({ url: `/governance/reference-codes/${id}/items/${itemId}` });
export const listReferenceCodeMappings = (id: string) =>
	api.get({ url: `/governance/reference-codes/${id}/mappings` });
export const createReferenceCodeMapping = (id: string, data: any) =>
	api.post({ url: `/governance/reference-codes/${id}/mappings`, data });
export const updateReferenceCodeMapping = (id: string, mapId: string | number, data: any) =>
	api.put({ url: `/governance/reference-codes/${id}/mappings/${mapId}`, data });
export const deleteReferenceCodeMapping = (id: string, mapId: string | number) =>
	api.delete({ url: `/governance/reference-codes/${id}/mappings/${mapId}` });
export const syncReferenceCodeSeeds = () => api.post({ url: "/governance/reference-codes/seeds" });

// Modeling (planning / glossary / templates)
export const listModelingPlans = (params: any = {}) =>
	api.get<any[]>(withModelingRequestTimeout({ url: "/modeling/plans", params }));
export const getModelingPlan = (id: string) => api.get({ url: `/modeling/plans/${id}` });
export const createModelingPlan = (data: any) => api.post({ url: "/modeling/plans", data });
export const updateModelingPlan = (id: string, data: any) => api.put({ url: `/modeling/plans/${id}`, data });
export const deleteModelingPlan = (id: string) => api.delete({ url: `/modeling/plans/${id}` });
export const publishModelingPlan = (id: string, data?: { version?: string; changeSummary?: string }) =>
	api.post({ url: `/modeling/plans/${id}/publish`, data });
export const archiveModelingPlan = (id: string, data?: { notes?: string }) => api.post({ url: `/modeling/plans/${id}/archive`, data });
export const restoreModelingPlan = (id: string) => api.post({ url: `/modeling/plans/${id}/restore` });

export const listGlossaryTerms = (params: any = {}) => api.get<any[]>({ url: "/modeling/glossary/terms", params });
export const createGlossaryTerm = (data: any) => api.post({ url: "/modeling/glossary/terms", data });
export const updateGlossaryTerm = (id: string, data: any) => api.put({ url: `/modeling/glossary/terms/${id}`, data });
export const deleteGlossaryTerm = (id: string) => api.delete({ url: `/modeling/glossary/terms/${id}` });
export const listGlossaryTermVersions = (id: string) =>
	api.get<any[]>({ url: `/modeling/glossary/terms/${id}/versions` });
export const listGlossaryTermReviews = (id: string) =>
	api.get<any[]>({ url: `/modeling/glossary/terms/${id}/reviews` });
export const getGlossaryTermReferences = (id: string) =>
	api.get({ url: `/modeling/glossary/terms/${id}/references` });

export const listModelTemplates = () => api.get<any[]>({ url: "/modeling/templates" });
export const listTemplateLayers = () =>
	api.get<{ layer: string; name: string; description: string }[]>(
		withModelingRequestTimeout({ url: "/modeling/templates/layers" }),
	);
export const getModelTemplate = (id: string) => api.get({ url: `/modeling/templates/${id}` });
export const createModelTemplate = (data: any) => api.post({ url: "/modeling/templates", data });
export const updateModelTemplate = (id: string, data: any) => api.put({ url: `/modeling/templates/${id}`, data });
export const deleteModelTemplate = (id: string) => api.delete({ url: `/modeling/templates/${id}` });
export const getModelTemplateReferences = (id: string) =>
	api.get({ url: `/modeling/templates/${id}/references` });
export const validateModelTemplate = (id: string, tableId: string) =>
	api.get({ url: `/modeling/templates/${id}/validate`, params: { tableId } });

// Quality dashboard / score / history
export interface QualityDashboard {
	ruleCount: number;
	coveredDatasets: number;
	totalDatasets: number;
	todayPassed: number;
	todayFailed: number;
	pendingFixRows: number;
	trend7d: { date: string; passRate: number }[];
	topFailingDatasets: { name: string; failingRows: number }[];
	recentFailedRuns: { ruleName: string; dataset: string; time: string; status: string }[];
}

export interface QualityScoreResult {
	overall: number;
	overallDelta: number | null;
	dimensions: { type: string; score: number; delta: number | null }[];
	trend: { date: string; overall: number }[];
}

export interface RuleRunHistory {
	runId: string;
	time: string;
	status: string;
	passRate: number;
	failingRows: number;
}

export const getQualityDashboard = () =>
	api.get<QualityDashboard>({ url: "/governance/quality/dashboard" });

export const getQualityScore = (datasetId: string, periodDays?: number) =>
	api.get<QualityScoreResult>({ url: "/governance/quality/score", params: { datasetId, periodDays } });

export const getRuleHistory = (ruleId: string, limit?: number) =>
	api.get<RuleRunHistory[]>({ url: `/governance/quality/rules/${ruleId}/history`, params: { limit } });

// Batch cleansing preview / execute
export interface CleansingPreview {
	affectedRows: number;
	unresolvableRows: number;
	samples: { rowId: number; before: string; after: string }[];
}

export const previewCleansing = (data: { runId: string; functionId: string; limit?: number }) =>
	api.post<CleansingPreview>({ url: "/governance/quality/cleansing/preview", data });

export const executeCleansing = (data: { runId: string; functionId: string }) =>
	api.post<{ affectedRows: number }>({ url: "/governance/quality/cleansing/execute", data });

// SQL Repair
export const previewSqlRepair = (data: { sql: string; limit?: number }) =>
	api.post<{ affectedRows: number; samples: { rowId: any; columnValues: Record<string, string>; newValues?: Record<string, string> }[] }>({
		url: "/governance/quality/sql-repair/preview",
		data,
	});

export const executeSqlRepair = (data: { sql: string; runId?: string }) =>
	api.post<{ affectedRows: number; auditLogId: string }>({
		url: "/governance/quality/sql-repair/execute",
		data,
	});

// Governance
export const listQualityRules = () => api.get({ url: "/governance/quality/rules" });
export const listQualityRuleVersions = (id: string) => api.get({ url: `/governance/quality/rules/${id}/versions` });
export const getQualityRuleVersion = (id: string, version: number) =>
	api.get({ url: `/governance/quality/rules/${id}/versions/${version}` });
export const changeQualityRuleVersionStatus = (id: string, version: number, data: { status: string; notes?: string }) =>
	api.post({ url: `/governance/quality/rules/${id}/versions/${version}/status`, data });
export const createQualityRule = (data: any) => api.post({ url: "/governance/quality/rules", data });
export const updateQualityRule = (id: string, data: any) => api.put({ url: `/governance/quality/rules/${id}`, data });
export const deleteQualityRule = (id: string) => api.delete({ url: `/governance/quality/rules/${id}` });
export const toggleQualityRule = (id: string, enabled: boolean) =>
	api.post({ url: `/governance/quality/rules/${id}/toggle`, data: { enabled } });

export const triggerQualityRun = (data: any) => api.post({ url: "/governance/quality/runs", data });
export const triggerQualityDryRun = (data: any) => api.post({ url: "/governance/quality/runs/dry-run", data });
export const listQualityRuns = (params: any = {}) => api.get({ url: "/governance/quality/runs", params });
export const getQualityRun = (id: string) => api.get({ url: `/governance/quality/runs/${id}` });

// Quality templates
export const listQualityTemplates = () => api.get({ url: "/governance/quality/templates" });
export const createQualityTemplate = (data: any) => api.post({ url: "/governance/quality/templates", data });
export const updateQualityTemplate = (id: string, data: any) => api.put({ url: `/governance/quality/templates/${id}`, data });
export const deleteQualityTemplate = (id: string) => api.delete({ url: `/governance/quality/templates/${id}` });
export const previewTemplateSQL = (id: string, params: any) => api.post({ url: `/governance/quality/templates/${id}/preview`, data: params });

// Cleansing functions
export const listCleansingFunctions = () => api.get({ url: "/governance/cleansing/functions" });
export const createCleansingFunction = (data: any) => api.post({ url: "/governance/cleansing/functions", data });
export const updateCleansingFunction = (id: string, data: any) => api.put({ url: `/governance/cleansing/functions/${id}`, data });
export const deleteCleansingFunction = (id: string) => api.delete({ url: `/governance/cleansing/functions/${id}` });

// Quality auto-trigger
export const triggerAutoQuality = (data: any) => api.post({ url: "/governance/quality/auto-trigger", data });

// Failing rows
export const listFailingRows = (runId: string, params: any = {}) => api.get({ url: `/governance/quality/runs/${runId}/failing-rows`, params });

// Data editor (ODS)
export const listOdsTables = () => api.get({ url: "/governance/data-editor/tables" });
export const listOdsColumns = (tableName: string) => api.get({ url: `/governance/data-editor/${tableName}/columns` });
export const listOdsRows = (tableName: string, params: any = {}) => api.get({ url: `/governance/data-editor/${tableName}/rows`, params });
export const updateOdsRow = (tableName: string, rowId: string, data: any) => api.put({ url: `/governance/data-editor/${tableName}/rows/${rowId}`, data });
export const insertOdsRow = (tableName: string, data: any) => api.post({ url: `/governance/data-editor/${tableName}/rows`, data });
export const listEditLogs = (params: any = {}) => api.get({ url: "/governance/data-editor/audit-log", params });

// Quality tasks (巡检计划)
export const listQualityTasks = () => api.get<any[]>({ url: "/governance/quality/tasks" });
export const createQualityTask = (data: any) => api.post({ url: "/governance/quality/tasks", data });
export const updateQualityTask = (id: string, data: any) => api.put({ url: `/governance/quality/tasks/${id}`, data });
export const toggleQualityTask = (id: string, enabled: boolean) =>
	api.post({ url: `/governance/quality/tasks/${id}/toggle`, data: { enabled } });
export const triggerQualityTask = (id: string) => api.post({ url: `/governance/quality/tasks/${id}/trigger` });
export const deleteQualityTask = (id: string) => api.delete({ url: `/governance/quality/tasks/${id}` });

export const createComplianceBatch = (data: any) => api.post({ url: "/governance/compliance/batches", data });
export const listComplianceBatches = (params: any = {}) => api.get({ url: "/governance/compliance/batches", params });
export const getComplianceBatch = (id: string) => api.get({ url: `/governance/compliance/batches/${id}` });
export const updateComplianceItem = (id: string, data: any) => api.put({ url: `/governance/compliance/items/${id}`, data });
export const deleteComplianceBatch = (id: string) => api.delete({ url: `/governance/compliance/batches/${id}` });

export const listIssues = (params: any = {}) => api.get<any[]>({ url: "/governance/issues", params });
export const getIssue = (id: string) => api.get({ url: `/governance/issues/${id}` });
export const createIssue = (data: any) => api.post({ url: "/governance/issues", data });
export const updateIssue = (id: string, data: any) => api.put({ url: `/governance/issues/${id}`, data });
export const closeIssue = (id: string, resolution?: string) =>
	api.post({ url: `/governance/issues/${id}/close`, data: { resolution } });
export const appendIssueAction = (id: string, data: any) => api.post({ url: `/governance/issues/${id}/actions`, data });
export const getIssueSlaMetrics = (params: { days?: number } = {}) =>
	api.get<{ windowDays: number; total: number; open: number; overdue: number; overdueRate: number; avgHandlingHours: number }>({
		url: "/governance/issues/metrics",
		params,
	});
export const getGovernanceOpsOverview = (params: { days?: number } = {}) =>
	api.get({ url: "/governance/ops/overview", params });
export const getGovernanceOpsTrend = (params: { days?: number } = {}) =>
	api.get({ url: "/governance/ops/trend", params });
export const getGovernanceReleaseGate = (params: { days?: number } = {}) =>
	api.get({ url: "/governance/ops/release-gate", params });

// Indicators
export const listIndicators = (params: any = {}) => api.get({ url: "/governance/indicators", params });
export const getIndicator = (id: string) => api.get({ url: `/governance/indicators/${id}` });
export const createIndicator = (data: any) => api.post({ url: "/governance/indicators", data });
export const updateIndicator = (id: string, data: any) => api.put({ url: `/governance/indicators/${id}`, data });
export const deleteIndicator = (id: string) => api.delete({ url: `/governance/indicators/${id}` });
export const publishIndicator = (id: string) => api.post({ url: `/governance/indicators/${id}/publish` });
export const archiveIndicator = (id: string) => api.post({ url: `/governance/indicators/${id}/archive` });
export const validateIndicator = (id: string) => api.post({ url: `/governance/indicators/${id}/validate` });
export const getIndicatorDependencies = (params: any = {}) => api.get({ url: "/governance/indicators/dependencies", params });
export const listIndicatorVersions = (id: string) => api.get({ url: `/governance/indicators/${id}/versions` });
export const getIndicatorVersion = (id: string, version: string) =>
	api.get({ url: `/governance/indicators/${id}/versions/${version}` });
export const diffIndicatorVersions = (id: string, params: { left?: string; right?: string }) =>
	api.get({ url: `/governance/indicators/${id}/versions/diff`, params });
export const rollbackIndicatorVersion = (
	id: string,
	version: string,
	data: { reason?: string; publishAfterRollback?: boolean } = {},
) => api.post({ url: `/governance/indicators/${id}/versions/${version}/rollback`, data });
export const listIndicatorReferences = (id: string) => api.get({ url: `/governance/indicators/${id}/references` });
export const createIndicatorReference = (id: string, data: any) => api.post({ url: `/governance/indicators/${id}/references`, data });
export const updateIndicatorReference = (id: string, refId: string, data: any) =>
	api.put({ url: `/governance/indicators/${id}/references/${refId}`, data });
export const deleteIndicatorReference = (id: string, refId: string) =>
	api.delete({ url: `/governance/indicators/${id}/references/${refId}` });
export const getIndicatorPublishPreview = (id: string) => api.post({ url: `/governance/indicators/${id}/publish-preview` });
export const previewIndicator = (id: string, params: { limit?: number } = {}) =>
	api.post({ url: `/governance/indicators/${id}/preview`, params });
export const getIndicatorOpsOverview = (params: { hours?: number } = {}) =>
	api.get({ url: "/governance/indicators/ops/overview", params });
export const getIndicatorOpsTrend = (params: { hours?: number; bucketHours?: number } = {}) =>
	api.get({ url: "/governance/indicators/ops/trend", params });

// Indicator Dashboard
export const getIndicatorDashboard = (params?: { domain?: string; days?: number }) =>
	api.get({ url: "/governance/indicators/dashboard", params });
export const getIndicatorDetail = (id: string, params?: { days?: number }) =>
	api.get({ url: `/governance/indicators/${id}/detail`, params });
export const getIndicatorDrilldown = (id: string, params: { dimension: string; period?: string }) =>
	api.get({ url: `/governance/indicators/${id}/drilldown`, params });

// Dimensions
export const listDimensions = (params: any = {}) => api.get({ url: "/governance/dimensions", params });
export const getDimension = (id: string) => api.get({ url: `/governance/dimensions/${id}` });
export const createDimension = (data: any) => api.post({ url: "/governance/dimensions", data });
export const updateDimension = (id: string, data: any) => api.put({ url: `/governance/dimensions/${id}`, data });
export const deleteDimension = (id: string) => api.delete({ url: `/governance/dimensions/${id}` });
export const publishDimension = (id: string) => api.post({ url: `/governance/dimensions/${id}/publish` });
export const archiveDimension = (id: string) => api.post({ url: `/governance/dimensions/${id}/archive` });

// Explore
export const previewQuery = (data: any) => api.post({ url: "/explore/query/preview", data });
export interface SavedQueryCreatePayload {
  name: string;
  sqlText: string;
  datasetId?: string | null;
}

export interface SavedQueryUpdatePayload {
  name?: string;
  sqlText?: string;
  datasetId?: string | null;
}

export const listSavedQueries = () => api.get({ url: "/explore/saved-queries" });
export const createSavedQuery = (data: SavedQueryCreatePayload) => api.post({ url: "/explore/saved-queries", data });
export const deleteSavedQuery = (id: string) => api.delete({ url: `/explore/saved-queries/${id}` });
export const runSavedQuery = (id: string) => api.post({ url: `/explore/saved-queries/${id}/run` });
export const getSavedQuery = (id: string) => api.get({ url: `/explore/saved-queries/${id}` });
export const updateSavedQuery = (id: string, data: SavedQueryUpdatePayload) =>
  api.put({ url: `/explore/saved-queries/${id}`, data });

// Explore (new APIs)
export const executeExplore = (data: any) => api.post({ url: "/explore/execute", data });
export const explainExplore = (data: any) => api.post({ url: "/explore/explain", data });
export const saveExploreResult = (executionId: string, data?: any) =>
  api.post({ url: `/explore/save-result/${executionId}`, data });
export const previewResultSet = (resultSetId: string) => api.get({ url: `/explore/result-preview/${resultSetId}` });
export const deleteResultSet = (id: string) => api.delete({ url: `/explore/result-sets/${id}` });

// Explore (CRUD for generated entities)
export const listSqlConnections = () => api.get({ url: "/sql-connections" });
export const listResultSets = () => api.get({ url: "/explore/result-sets" });
export const cleanupExpiredResultSets = () => api.post({ url: "/explore/result-sets/cleanup" });
export const recordResultSetCopy = (id: string, data?: Record<string, unknown>) =>
	api.post({ url: `/explore/result-sets/${id}/copy-sql`, data });

// Catalog tables & columns
export const listTablesByDataset = (datasetId: string, keyword?: string) =>
  api.get({ url: "/catalog/tables", params: { datasetId, keyword } });
export const listColumnsByTable = (tableId: string, keyword?: string) =>
  api.get({ url: "/catalog/columns", params: { tableId, keyword } });
export const updateTableSchema = (id: string, data: any) => api.put({ url: `/catalog/tables/${id}`, data });
export const updateColumnSchema = (id: string, data: any) => api.put({ url: `/catalog/columns/${id}`, data });
export const validateTableStandardMapping = (tableId: string) =>
  api.get({ url: `/catalog/tables/${tableId}/standard-mapping/validate` });
export const previewAutoMapTableStandardMapping = (
  tableId: string,
  params: { overwrite?: boolean; onlyUnmapped?: boolean } = {},
) => api.get({ url: `/catalog/tables/${tableId}/standard-mapping/auto-map/preview`, params });
export const applyAutoMapTableStandardMapping = (
  tableId: string,
  data: { overwrite?: boolean; onlyUnmapped?: boolean } = {},
) => api.post({ url: `/catalog/tables/${tableId}/standard-mapping/auto-map/apply`, data });

// Catalog lineage
export const getCatalogLineage = (datasetId: string, projectName?: string) =>
  api.get({ url: "/catalog/lineage", params: { datasetId, projectName } });
export const getCatalogLineageImpact = (
  datasetId: string,
  params: {
    direction?: "UPSTREAM" | "DOWNSTREAM" | "BOTH";
    depth?: number;
    projectName?: string;
    layers?: string;
    changedWithinHours?: number;
    sourceId?: string;
    withJobs?: boolean;
    withColumns?: boolean;
    at?: string;
  } = {},
) => api.get({ url: "/catalog/lineage/impact", params: { datasetId, ...params } });
export const getCatalogLineageDiff = (
  datasetId: string,
  params: {
    from: string;
    to: string;
    direction?: "UPSTREAM" | "DOWNSTREAM" | "BOTH";
    depth?: number;
    projectName?: string;
  },
) => api.get({ url: "/catalog/lineage/diff", params: { datasetId, ...params } });
export const createCatalogLineage = (data: any) => api.post({ url: "/catalog/lineage", data });
export const deleteCatalogLineage = (id: string) => api.delete({ url: `/catalog/lineage/${id}` });
export const syncAddaxLineage = () => api.post({ url: "/catalog/lineage/sync-addax" });
export const importDbtManifest = (file: File): Promise<{ created: number; skipped: number; total: number }> => {
	const form = new FormData();
	form.append("file", file);
	return api.post({ url: "/catalog/lineage/import-dbt-manifest", data: form, headers: { "Content-Type": "multipart/form-data" } });
};

// Catalog sync (full scan)
export type CatalogSyncRequest = { includePrimary?: boolean; includeJdbc?: boolean; reason?: string };
export type CatalogSyncConfig = {
	autoSyncEnabled?: boolean;
	autoSyncCron?: string;
	cronRuntimeEditable?: boolean;
	message?: string;
};
export const triggerCatalogSync = (data: CatalogSyncRequest = {}) => api.post({ url: "/catalog/sync", data });
export const getCatalogSyncStatus = () => api.get({ url: "/catalog/sync/status" });
export const getCatalogSyncConfig = () => api.get({ url: "/catalog/sync/config" });
export const updateCatalogSyncConfig = (data: { autoSyncEnabled?: boolean; autoSyncCron?: string }) =>
	api.post({ url: "/catalog/sync/config", data });
export const listCatalogSyncPipelines = () => api.get({ url: "/catalog/sync/pipelines" });
export const listCatalogSyncRuns = (
	params: { integration?: string; limit?: number; includeDetails?: boolean; sourceId?: string } = {},
) =>
	api.get({ url: "/catalog/sync/runs", params });
export const getCatalogSyncRunDiagnostics = (runId: string, params: { sourceId?: string } = {}) =>
	api.get({ url: `/catalog/sync/runs/${runId}/diagnostics`, params });
export const triggerJdbcCatalogSync = (sourceId: string, data: { reason?: string } = {}) =>
	api.post({ url: `/catalog/sync/jdbc/${sourceId}/run`, data });

// Visualization
export const getCockpitMetrics = () => api.get({ url: "/vis/cockpit/metrics" });
export const getProjectsSummary = () => api.get({ url: "/vis/projects/summary" });
export const getFinanceSummary = () => api.get({ url: "/vis/finance/summary" });
export const getSupplySummary = () => api.get({ url: "/vis/supply/summary" });
export const getHrSummary = () => api.get({ url: "/vis/hr/summary" });

// Services
export const listMyTokens = () => api.get({ url: "/tokens/me" });
export const createToken = () => api.post({ url: "/tokens" });
export const deleteToken = (id: string) => api.delete({ url: `/tokens/${id}` });

// IAM
export const listClassifications = () => api.get({ url: "/iam/classifications" });
export const createClassification = (data: any) => api.post({ url: "/iam/classifications", data });
export const updateClassification = (id: string, data: any) => api.put({ url: `/iam/classifications/${id}`, data });
export const deleteClassification = (id: string) => api.delete({ url: `/iam/classifications/${id}` });

export const listPermissions = () => api.get({ url: "/iam/permissions" });
export const createPermission = (data: any) => api.post({ url: "/iam/permissions", data });
export const updatePermission = (id: string, data: any) => api.put({ url: `/iam/permissions/${id}`, data });
export const deletePermission = (id: string) => api.delete({ url: `/iam/permissions/${id}` });

export const listRequests = () => api.get({ url: "/iam/requests" });
export const createRequest = (data: any) => api.post({ url: "/iam/requests", data });
export const approveRequest = (id: string) => api.post({ url: `/iam/requests/${id}/approve` });
export const rejectRequest = (id: string) => api.post({ url: `/iam/requests/${id}/reject` });
export const simulateIam = (data: any) => api.post({ url: "/iam/simulate", data });

// API Gateway (tasks)
export const apiTest = (id: string, data?: any) => api.post({ url: `/apis/${id}/test`, data });
export const apiPublish = (id: string, data?: any) => api.post({ url: `/apis/${id}/publish`, data });
export const apiExecute = (id: string, data?: any) => api.post({ url: `/apis/${id}/execute`, data });

// Dashboards & Dev registry (tasks)
export const listDashboards = () => api.get({ url: "/dashboards" });
export const visitDashboard = (data?: Record<string, unknown>) => api.post({ url: "/dashboards/visit", data });
export const submitEtlJob = (jobId: string) => api.post({ url: `/etl-jobs/${jobId}/submit` });
export const getJobRunStatus = (runId: string) => api.get({ url: `/job-runs/${runId}/status` });

// Rollback
export const rollbackAnalyze = (data: {
	level: number;
	scope: string;
	taskId?: number;
	dataSourceId?: string;
	tables?: string[];
	rebuildDbt?: boolean;
}) => api.post({ url: "/rollback/analyze", data });

export const rollbackExecute = (data: {
	level: number;
	scope: string;
	taskId?: number;
	dataSourceId?: string;
	tables?: string[];
	rebuildDbt?: boolean;
}) => api.post({ url: "/rollback/execute", data });

export const getRollbackAuditLog = (params: { taskId?: number; dataSourceId?: string }) =>
	api.get({ url: "/rollback/audit-log", params });

// Asset Ownership
export const listAssetOwnership = (params: {
	assetType?: string;
	ownerDeptCode?: string;
	keyword?: string;
	page?: number;
	size?: number;
}) => api.get({ url: "/asset-ownership", params });
export const updateAssetOwnership = (id: number, data: { ownerDeptCode: string; assignedBy?: string }) =>
	api.put({ url: `/asset-ownership/${id}`, data });
export const batchUpdateAssetOwnership = (data: { ids: number[]; ownerDeptCode: string; assignedBy?: string }) =>
	api.post({ url: "/asset-ownership/batch", data });

// Asset Grant
export const listAssetGrants = (params: { assetType: string; assetId: string }) =>
	api.get({ url: "/asset-grants", params });
export const createAssetGrant = (data: {
	assetType: string;
	assetId: string;
	granteeType: string;
	granteeId: string;
	permission: string;
	validFrom?: string;
	validTo?: string;
	grantReason?: string;
}) => api.post({ url: "/asset-grants", data });
export const deleteAssetGrant = (id: number) => api.delete({ url: `/asset-grants/${id}` });
export const listMyGrants = (params?: { page?: number; size?: number }) =>
	api.get({ url: "/asset-grants/my", params });
export const listGrantedByMe = (params?: { page?: number; size?: number }) =>
	api.get({ url: "/asset-grants/granted-by-me", params });

// Asset Permission Audit
export const listPermissionAudit = (params: {
	action?: string;
	operator?: string;
	targetUser?: string;
	oaReference?: string;
	dateFrom?: string;
	dateTo?: string;
	page?: number;
	size?: number;
}) => api.get({ url: "/asset-permission-audit", params });

// Indicator Templates
export const listIndicatorTemplates = (params?: { domain?: string }) =>
	api.get({ url: "/governance/indicator-templates", params });
export const getIndicatorTemplate = (id: string) =>
	api.get({ url: `/governance/indicator-templates/${id}` });
export const createIndicatorTemplate = (data: any) =>
	api.post({ url: "/governance/indicator-templates", data });
export const updateIndicatorTemplate = (id: string, data: any) =>
	api.put({ url: `/governance/indicator-templates/${id}`, data });
export const deleteIndicatorTemplate = (id: string) =>
	api.delete({ url: `/governance/indicator-templates/${id}` });
export const applyIndicatorTemplate = (templateId: string, data: any) =>
	api.post({ url: `/governance/indicator-templates/${templateId}/apply`, data });

// Indicator Generation
export const previewIndicatorSql = (id: string) =>
	api.post({ url: `/governance/indicators/${id}/preview-sql` });
export const generateIndicators = (data: any) =>
	api.post({ url: "/governance/indicators/generate", data });
export const generateAndRunIndicators = (data: any) =>
	api.post({ url: "/governance/indicators/generate-and-run", data });

// Indicator Subscriptions
export const listSubscriptions = () =>
	api.get({ url: "/governance/indicators/subscriptions" });
export const createSubscription = (data: { indicatorId: string; filterConfig?: string; displayOrder?: number }) =>
	api.post({ url: "/governance/indicators/subscriptions", data });
export const deleteSubscription = (id: string) =>
	api.delete({ url: `/governance/indicators/subscriptions/${id}` });
export const updateSubscription = (id: string, data: { filterConfig?: string; displayOrder?: number }) =>
	api.put({ url: `/governance/indicators/subscriptions/${id}`, data });

// Data Products
export type DataProduct = {
	id?: string;
	name: string;
	code?: string;
	ownerDept?: string;
	description?: string;
	datasetIds?: string;
	indicatorCodes?: string;
	classification?: string;
	freshnessSla?: string;
	lifecycleStatus?: string;
	visibility?: string;
	consumerEntry?: string;
	status?: "DRAFT" | "PUBLISHED" | "OFFLINE";
};

export const listDataProducts = (page = 0, size = 20) =>
	api.get({ url: "/catalog/data-products", params: { page, size } });

export const createDataProduct = (data: Omit<DataProduct, "id">) =>
	api.post({ url: "/catalog/data-products", data });

export const updateDataProduct = (id: string, data: Partial<DataProduct>) =>
	api.put({ url: `/catalog/data-products/${id}`, data });

export const deleteDataProduct = (id: string) =>
	api.delete({ url: `/catalog/data-products/${id}` });

// --- Sprint-8: 指标中心 ---

export const getDomainIndicatorStats = (domainId: string) =>
	api.get({ url: `/catalog/domains/${domainId}/indicator-stats` }).then((r: any) => r.data?.data);

export const importIndicatorTemplates = (file: File) => {
	const form = new FormData();
	form.append("file", file);
	return api.post({ url: "/governance/indicator-templates/import", data: form });
};
