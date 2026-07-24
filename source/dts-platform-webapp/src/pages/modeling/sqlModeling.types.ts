export type DbtConfigView = {
	enabled?: boolean;
	config?: {
		enabled?: boolean;
		projectDir?: string;
		profilesDir?: string;
		profileName?: string;
		targetName?: string;
		targetDataSourceId?: string;
		database?: string;
		schema?: string;
		vars?: Record<string, any>;
	};
	profileStatus?: { generated?: boolean; message?: string; profilePath?: string };
	workspaceStatus?: { ok?: boolean; message?: string; detail?: Record<string, any> };
	target?: { id?: string; name?: string; type?: string };
};

export type DbtSyncArtifactStatus = {
	lastSyncAt?: string;
	lastModifiedAt?: number;
	synced?: boolean;
	message?: string;
};

export type DbtSyncStats = {
	lastSyncAt?: string;
	datasetsCreated?: number;
	datasetsUpdated?: number;
	odsUpdated?: number;
	columnsUpdated?: number;
	lineageCreated?: number;
	lineageRemoved?: number;
	message?: string;
};

export type DbtRunFailure = {
	uniqueId?: string;
	name?: string;
	resourceType?: string;
	path?: string;
	status?: string;
	message?: string;
	executionTime?: number;
};

export type DbtRunSummary = {
	present?: boolean;
	projectDir?: string;
	runResultsPath?: string;
	manifestPath?: string;
	invocationId?: string;
	generatedAt?: string;
	command?: string;
	status?: string;
	total?: number;
	success?: number;
	failed?: number;
	skipped?: number;
	failures?: DbtRunFailure[];
	dagRunId?: string;
	dagId?: string;
};

export type DbtSyncStatus = {
	manifest?: DbtSyncArtifactStatus | null;
	runResults?: DbtSyncArtifactStatus | null;
	stats?: DbtSyncStats | null;
	latestRun?: DbtRunSummary | null;
};

export type DbtOutputRelation = {
	modelId?: string;
	modelName?: string;
	selector?: string;
	database?: string;
	schema?: string;
	identifier?: string;
	qualifiedName?: string;
	materialized?: string;
	relationType?: string;
	exists?: boolean;
	truncateAllowed?: boolean;
	downstreamRefCount?: number;
	message?: string;
	checkSkipped?: boolean;
	checkMessage?: string;
};

export type DbtModelRelationStats = {
	schema?: string;
	identifier?: string;
	relationName?: string;
	exists?: boolean;
	rowCount?: number | null;
	error?: string | null;
};

export type DbtModelDependencyDiagnostic = {
	dependency?: {
		uniqueId?: string;
		name?: string;
		resourceType?: string;
		path?: string;
		relationName?: string;
	};
	stats?: DbtModelRelationStats | null;
};

export type DbtModelRuntimeRunInfo = {
	present?: boolean;
	invocationId?: string;
	status?: string;
	command?: string;
	generatedAt?: string;
};

export type DbtModelAirflowRunInfo = {
	present?: boolean;
	dagRunId?: string;
	state?: string;
	logicalDate?: string;
	taskId?: string;
	logSnippet?: string;
};

export type DbtModelRuntimeDiagnostics = {
	dbtRun?: DbtModelRuntimeRunInfo | null;
	airflowRun?: DbtModelAirflowRunInfo | null;
};

export type DbtModelDiagnostics = {
	enabled?: boolean;
	success?: boolean;
	model?: string;
	uniqueId?: string;
	resourceType?: string;
	path?: string;
	relationName?: string;
	current?: DbtModelRelationStats | null;
	upstreams?: DbtModelDependencyDiagnostic[];
	runtime?: DbtModelRuntimeDiagnostics | null;
	findings?: string[];
	recommendedQueries?: string[];
	message?: string | null;
};

export type SqlModel = {
	id?: string;
	modelSpecId?: string;
	planId?: string;
	planName?: string;
	name?: string;
	alias?: string;
	layer?: string;
	sourceDataSourceId?: string;
	sourceDataSourceName?: string;
	sourceSystem?: string;
	dagSelector?: string;
	tags?: string;
	materialized?: string;
	schemaName?: string;
	description?: string;
	sql?: string;
	enabled?: boolean;
	modelPath?: string;
	ownerDept?: string;
	status?: string;
	semanticContract?: string;
	contractVersion?: string;
	contractUpdatedAt?: string;
	metricCount?: number;
	dimensionCount?: number;
	createdDate?: string;
	lastModifiedDate?: string;
};

export type ProjectSpace = {
	id?: string;
	name?: string;
	domain?: string;
	scope?: string;
	status?: string;
	version?: string;
	versionNotes?: string;
	owner?: string;
	ownerDept?: string;
	tags?: string;
	content?: string;
	createdDate?: string;
	lastModifiedDate?: string;
};

export type DagRun = {
	dag_id?: string;
	dag_run_id?: string;
	state?: string;
	execution_date?: string;
	start_date?: string;
	end_date?: string;
	conf?: {
		models?: string;
		target?: string;
		operation?: string;
		[key: string]: any;
	};
};

export type ModelColumn = {
	name?: string;
	dataType?: string;
	comment?: string;
	status?: string;
};

export type SqlModelStandardBinding = {
	columnName?: string;
	standardId?: string;
	standardCode?: string;
	standardName?: string;
	standardVersion?: string;
	dataType?: string;
	nullable?: boolean;
	codeSet?: string;
	securityLevel?: string;
	bindingSource?: string;
	status?: string;
	driftReason?: string;
};

export type SqlModelStandardBindingResult = {
	modelId?: string;
	modelName?: string;
	totalColumns?: number;
	mappedColumns?: number;
	missingColumns?: number;
	bindings?: SqlModelStandardBinding[];
};

export type SqlModelStandardGateResult = {
	modelId?: string;
	modelName?: string;
	blocking?: boolean;
	blockers?: string[];
	warnings?: string[];
	totalColumns?: number;
	mappedColumns?: number;
	missingColumns?: number;
};

export type SqlModelSchemaYmlResult = {
	modelId?: string;
	modelName?: string;
	path?: string;
	schemaYml?: string;
};

export type DbtSourceItem = {
	id?: string;
	schema?: string;
	table?: string;
	description?: string;
	systemCode?: string;
	bizCode?: string;
	entityCode?: string;
	sourceDataSourceId?: string;
	sourceDataSourceName?: string;
	sourceSnippet?: string;
};

export type DbtRefItem = {
	id?: string;
	name?: string;
	layer?: string;
	description?: string;
	tags?: string;
	sourceSystem?: string;
	refSnippet?: string;
};

export type SqlModelOdsGenerateResult = {
	mappingsTotal?: number;
	modelsCreated?: number;
	modelsUpdated?: number;
	createdModels?: string[];
	updatedModels?: string[];
	skipped?: string[];
	qualityTemplatesGenerated?: number;
	qualitySkipped?: string[];
};

export type DbtQualityGateResult = {
	selector?: string;
	selectedModels?: string[];
	blocking?: boolean;
	warning?: boolean;
	latestStatus?: string;
	latestCommand?: string;
	latestGeneratedAt?: string;
	latestFailedCount?: number;
	blockers?: string[];
	warnings?: string[];
};

export type DbtReleaseGateResult = {
	selector?: string;
	strictMode?: boolean;
	gitRef?: string;
	commitSha?: string;
	decision?: string;
	blocking?: boolean;
	warning?: boolean;
	blockers?: string[];
	warnings?: string[];
	buildEvidence?: {
		invocationId?: string;
		command?: string;
		status?: string;
		generatedAt?: string;
		runResultsPath?: string;
	};
};

export type DbtReleaseSubmitResult = {
	selector?: string;
	status?: "SUBMITTED" | "WARNING" | "BLOCKED" | string;
	blocking?: boolean;
	warning?: boolean;
	blockers?: string[];
	warnings?: string[];
	dagId?: string;
	dagRunId?: string;
	qualityGate?: DbtQualityGateResult | null;
	releaseGate?: DbtReleaseGateResult | null;
	buildEvidence?: DbtReleaseGateResult["buildEvidence"] | null;
};

export type SqlModelContractImpact = {
	modelId?: string;
	modelName?: string;
	contractVersion?: string;
	contractUpdatedAt?: string;
	metricCount?: number;
	dimensionCount?: number;
	fieldCount?: number;
	impactedDatasetCount?: number;
	impactedReportCount?: number;
	impactedDatasets?: Array<{
		id?: string;
		name?: string;
		status?: string;
		publishedVersion?: number;
	}>;
	impactedReports?: Array<{
		id?: string;
		title?: string;
		code?: string;
		queryDatasetId?: string;
		enabled?: boolean;
	}>;
};

export type SqlModelGovernancePreviewItem = {
	modelId?: string;
	planId?: string;
	planName?: string;
	name?: string;
	layer?: string;
	status?: string;
	modelPath?: string;
	ruleHits?: string[];
	downstreamRefCount?: number;
	datasetBindingCount?: number;
	reportBindingCount?: number;
	fileDeleteSafe?: boolean;
	suggestedAction?: string;
};

export type SqlModelGovernancePreviewResult = {
	total?: number;
	items?: SqlModelGovernancePreviewItem[];
};

export type SqlModelGovernanceExecuteResult = {
	requested?: number;
	deleted?: number;
	skipped?: number;
	failed?: number;
	items?: Array<{
		modelId?: string;
		name?: string;
		result?: string;
		message?: string;
	}>;
};

export type SqlModelBatchDeleteResult = {
	requested?: number;
	deleted?: number;
	failed?: number;
	failures?: Array<{
		modelId?: string;
		message?: string;
	}>;
};

export type OdsSkippedSeverity = "error" | "warn" | "info";

export type OdsSkippedEntry = {
	raw: string;
	reason: string;
	severity: OdsSkippedSeverity;
};
