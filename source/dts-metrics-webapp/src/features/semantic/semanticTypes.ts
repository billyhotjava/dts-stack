export type LoadState<T> = { state: "loading" } | { state: "loaded"; value: T } | { state: "error"; error: unknown };

export type SemanticQueryBody = {
	base?: string;
	nodes?: Array<{
		id: string;
		role: "BASE" | "JOIN" | "MEASURE" | "DIMENSION" | "PUBLISH";
		warehouseLayer?: WarehouseLayer | string | null;
		assetKey?: string | null;
		grain?: string[];
		primaryKeys?: string[];
		standardCode?: string;
		standardCodes?: string[];
	}>;
	joins?: Array<{ to: string; via?: string; type?: string }>;
	measures?: string[];
	dimensions?: Array<string | { id: string; granularity?: string }>;
	filters?: Array<{ field: string; op: string; value?: unknown; value_to?: unknown }>;
	derived_metrics?: Array<{ id: string; label?: string; expression: string; format?: Record<string, unknown> }>;
	order_by?: Array<{ field: string; direction?: "asc" | "desc" }>;
	limit?: number;
	format?: "json" | "arrow_ipc";
	cache_hint?: string;
};

export type SemanticColumn = {
	id?: string;
	label?: string;
	type?: string;
	format?: Record<string, unknown> | null;
};

export type SemanticModelMeta = {
	id?: string | number;
	label?: string;
	subject_area?: string | null;
	security_level?: string | null;
	grain?: string | null;
	warehouse_layer?: "DWD" | "DWS" | "ADS" | string | null;
	asset_key?: string | null;
	governance_status?: string | null;
	permission_decision?: string | null;
	lineage_status?: string | null;
	database_id?: number;
	schema_name?: string | null;
	table_name?: string | null;
	description?: string | null;
	metrics?: Array<Record<string, unknown>>;
	dimensions?: Array<Record<string, unknown>>;
	joins?: Array<Record<string, unknown>>;
};

export type SemanticMetaResponse = {
	spec_version?: string;
	generated_at?: string;
	models?: SemanticModelMeta[];
};

export type WarehouseLayer = "DWD" | "DWS" | "ADS";

export type MetricLifecycleStatus =
	| "ARTIFACT_GENERATED"
	| "DBT_VALIDATED"
	| "REVIEW_SUBMITTED"
	| "PUBLISH_DRY_RUN_READY"
	| "PUBLISHED"
	| "ROLLED_BACK";

export type MetricContractErrorCode =
	| "invalid_layer"
	| "grain_mismatch"
	| "standard_code_required"
	| "asset_permission_denied"
	| "graph_validation_failed"
	| "dbt_validation_failed"
	| "platform_contract_unavailable"
	| "artifact_required"
	| "dbt_validation_required"
	| "published_version_required"
	| "rollback_target_required"
	| "rollback_target_not_found"
	| "rollback_target_must_differ"
	| "model_lifecycle_state_not_found";

export type ValidationDiagnostic = {
	nodeId?: string | null;
	edgeId?: string | null;
	fieldId?: string | null;
	metricCode?: string | null;
	severity: "ERROR" | "WARNING" | "INFO";
	code: MetricContractErrorCode | string;
	message: string;
};

export type PublishReference = {
	modelId: string;
	modelName?: string;
	status: MetricLifecycleStatus;
	version?: string;
	activeVersion?: string;
	artifactRef?: string;
	platformPublishReference?: string;
	platformRollbackReference?: string;
	releaseDecision?: string;
};

export type ModelVersionHistory = {
	modelId: string;
	modelName?: string;
	status: MetricLifecycleStatus;
	activeVersion?: string;
	rollbackAvailable?: boolean;
	versions: PublishReference[];
	rollbackEvents: PublishReference[];
};

export type VisualAssetSummary = {
	assetId?: string;
	assetKey: string;
	name: string;
	warehouseLayer: WarehouseLayer;
	domainCode?: string;
	businessObjectCode?: string;
	grain?: string[];
	primaryKeys?: string[];
	standardCodes?: string[];
	timeColumns?: string[];
	dimensionColumns?: string[];
	metricColumns?: string[];
	governanceStatus?: string;
	lineageStatus?: string;
	permissionDecision?: string;
	classification?: string;
	ownerDept?: string;
	description?: string;
};

export type VisualAssetsResponse = {
	data: VisualAssetSummary[];
	meta?: {
		page?: number;
		size?: number;
		total?: number;
		layers?: string[];
		source?: string;
	};
};

export type SemanticQueryResponse = {
	status?: string;
	meta?: {
		sql_preview?: string;
		row_count?: number;
		elapsed_ms?: number;
		cache_hit?: boolean;
		security_applied?: string[];
		warnings?: string[];
	};
	diagnostics?: Array<Record<string, unknown>>;
	columns?: SemanticColumn[];
	rows?: unknown[][];
};

export type GraphDraftResponse = {
	id?: string;
	status?: string;
	graph?: SemanticQueryBody;
	diagnostics?: ValidationDiagnostic[];
	meta?: Record<string, unknown>;
};

export type SemanticMetric = {
	id?: string;
	objectId?: string;
	code: string;
	name: string;
	formulaType?: string;
	formulaJson?: string;
	format?: string;
	unit?: string;
	status?: string;
};

export type QueryFilterDraft = { field: string; op: string; value: string };

export type DerivedMetricOperation = "sum" | "count" | "count_distinct" | "avg" | "count_if" | "sum_if" | "ratio" | "case_when" | "date_trunc";

export type DerivedMetricDraft = {
	id: string;
	label: string;
	operation: DerivedMetricOperation;
	leftField: string;
	rightField?: string;
	timeGrain?: "day" | "week" | "month" | "quarter" | "year";
	conditionField?: string;
	conditionOp?: "eq" | "ne" | "gt" | "gte" | "lt" | "lte";
	conditionValue?: string;
	trueValue?: string;
	falseValue?: string;
	expression: string;
};
