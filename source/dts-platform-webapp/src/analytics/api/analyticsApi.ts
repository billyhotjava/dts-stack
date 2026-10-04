import { refreshPortalSessionIfPossible } from "@/api/apiClient";
import { resolveCurrentAppPath, resolveLoginHref } from "@/routes/constants";
import userStore from "@/store/userStore";
import { markPortalSessionLogout } from "@/utils/portalSessionStorage";
import type { ScreenWritePayload } from "../pages/screens/contracts";
import { withPlatformAuthorization } from "./platform-auth-header";

export type CollectionListItem = {
	id: number | "root";
	name?: string;
	description?: string | null;
	archived?: boolean;
	location?: string | null;
	parent_id?: number | null;
	can_write?: boolean;
};

export type CollectionItem = {
	id: number;
	model: "dashboard" | "card";
	name?: string;
	description?: string | null;
	archived?: boolean;
	collection_id?: number | null;
	favorite?: boolean;
	created_at?: string;
	updated_at?: string;
};

export type DataPortalContentType = "SCREEN" | "DASHBOARD";

export type DataPortalDirectoryItem = {
	id: number;
	name: string;
	parent_id: number | null;
	sort_order: number;
	version_no?: number;
	created_at?: string;
	updated_at?: string;
};

export type DataPortalBindingItem = {
	id: number;
	directory_id: number;
	content_type: DataPortalContentType;
	content_id: number;
	sort_order: number;
	created_at?: string;
};

export type DataPortalSnapshot = {
	can_write: boolean;
	directories: DataPortalDirectoryItem[];
	items: DataPortalBindingItem[];
};

export type DashboardListItem = {
	id: number;
	name?: string;
	description?: string | null;
	archived?: boolean;
	collection_id?: number | null;
	created_at?: string;
	updated_at?: string;
	favorite?: boolean;
	public_uuid?: string | null;
	lifecycle_status?: "DRAFT" | "PUBLISHED" | "ARCHIVED";
	published_revision_id?: number | null;
	registration_status?: "NOT_REGISTERED" | "PENDING_REGISTRATION" | "AVAILABLE" | "REGISTRATION_FAILED" | "DISABLED";
	version_no?: number;
};

export type DashboardDetail = DashboardListItem & {
	dashcards?: unknown[];
	parameters?: unknown[];
	ordered_cards?: DashboardCard[];
};

export type DashboardPublicationAudience = {
	deptCodes: string[];
	roleCodes: string[];
	classification: "DATA_PUBLIC" | "DATA_INTERNAL" | "DATA_CONFIDENTIAL" | "DATA_SENSITIVE" | "DATA_SECRET";
	expiresAt?: string | null;
};

export type DashboardPublicationIssue = {
	code: string;
	path: string;
	message: string;
};

export type DashboardPublicationValidation = {
	valid: boolean;
	blockers: DashboardPublicationIssue[];
	warnings: DashboardPublicationIssue[];
	dependencySnapshot: Record<string, unknown>;
};

export type DashboardPublicationResult = {
	dashboardId: number;
	revisionId: number;
	versionNo: number;
	lifecycleStatus: "PUBLISHED";
	registrationStatus: "PENDING_REGISTRATION" | "AVAILABLE" | "REGISTRATION_FAILED";
	contractChecksum: string;
	dependencySnapshot: Record<string, unknown>;
	publishedAt: string;
};

export type DashboardVersion = {
	revisionId: number;
	versionNo: number;
	status: "DRAFT" | "PUBLISHED" | "SUPERSEDED";
	contractChecksum?: string | null;
	dependencySnapshot: Record<string, unknown>;
	publishedBy?: number | null;
	publishedAt?: string | null;
	createdAt: string;
};

export type PublicCardDetail = CardDetail & {
	public_uuid?: string | null;
};

export type PublicDashboardDetail = DashboardDetail & {
	public_uuid?: string | null;
};

export type DashboardCard = {
	id: number;
	card_id?: number | null;
	row?: number;
	col?: number;
	size_x?: number;
	size_y?: number;
	parameter_mappings?: unknown[];
	visualization_settings?: unknown;
	card?: CardListItem | null;
};

export type CardListItem = {
	id: number;
	name?: string;
	description?: string | null;
	archived?: boolean;
	collection_id?: number | null;
	display?: string;
	type?: "question" | "model" | "analysis";
	lifecycle_status?: "DRAFT" | "PUBLISHED" | "ARCHIVED";
	published_revision_id?: number | null;
	created_at?: string;
	updated_at?: string;
	favorite?: boolean;
	public_uuid?: string | null;
};

export type CardDetail = CardListItem & {
	dataset_query?: unknown;
	visualization_settings?: unknown;
	result_metadata?: unknown;
};

export type CardQueryResponse = {
	status?: string;
	row_count?: number;
	running_time?: number;
	error?: unknown;
	code?: string;
	requestId?: string;
	data?: {
		rows?: unknown[];
		cols?: Array<Record<string, unknown>>;
		native_form?: { query?: string };
		results_timezone?: string;
		results_metadata?: { columns?: unknown[] };
	};
};

export type DashboardQueryResponse = CardQueryResponse;

export type SemanticQueryBody = {
	indicatorRefs?: Array<{ id: string; version: string }>;
	timeRange?: { fieldRef: string; start: string; endExclusive: string; timezone: string };
	base?: string;
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
	id?: string;
	label?: string;
	subject_area?: string | null;
	security_level?: string | null;
	grain?: string | null;
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

export type SemanticGraphResponse = {
	nodes?: Array<Record<string, unknown>>;
	edges?: Array<Record<string, unknown>>;
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
	columns?: SemanticColumn[];
	rows?: unknown[][];
};

export type SemanticVirtualDataset = {
	id?: number;
	name?: string;
	description?: string | null;
	owner_id?: number;
	workspace_id?: number | null;
	base_model?: string | null;
	archived?: boolean;
	state?: SemanticQueryBody | Record<string, unknown>;
	created_at?: string;
	updated_at?: string;
};

export type SemanticPromoteResult = {
	source_virtual_dataset_id?: number;
	model_name?: string;
	sql?: string;
	schema_yml?: string;
	state?: Record<string, unknown>;
};

export type Nl2SqlEvalCaseItem = {
	id?: number | string;
	name?: string;
	domain?: string | null;
	promptText?: string;
	expected?: Record<string, unknown>;
	notes?: string | null;
	enabled?: boolean;
	createdAt?: string;
	updatedAt?: string;
};

export type Nl2SqlEvalRunRow = {
	id?: number | string;
	name?: string;
	passed?: boolean;
	score?: number;
	totalChecks?: number;
	passedChecks?: number;
	checks?: Array<Record<string, unknown>>;
	generated?: Record<string, unknown>;
};

export type Nl2SqlEvalRunSummary = {
	executedAt?: string;
	total?: number;
	passed?: number;
	failed?: number;
	passRate?: number;
	averageScore?: number;
	rows?: Nl2SqlEvalRunRow[];
};

export type Nl2SqlEvalRunRecord = {
	id?: number | string;
	label?: string | null;
	modelVersion?: string | null;
	promptVersion?: string | null;
	dictionaryVersion?: string | null;
	caseCount?: number;
	passCount?: number;
	failCount?: number;
	passRate?: number;
	averageScore?: number;
	blockedRate?: number;
	gatePassed?: boolean | null;
	gate?: Record<string, unknown>;
	createdAt?: string;
};

export type Nl2SqlEvalGateRunResponse = {
	runId?: number | string;
	executedAt?: string;
	version?: {
		label?: string | null;
		modelVersion?: string | null;
		promptVersion?: string | null;
		dictionaryVersion?: string | null;
	};
	summary?: Nl2SqlEvalRunSummary;
	gate?: {
		passed?: boolean;
		checks?: Array<Record<string, unknown>>;
		reasons?: string[];
		baseline?: Record<string, unknown> | null;
		config?: Record<string, unknown>;
	};
};

export type Nl2SqlEvalCompareResponse = {
	baseline?: Nl2SqlEvalRunRecord;
	candidate?: Nl2SqlEvalRunRecord;
	metrics?: {
		passRateDelta?: number;
		averageScoreDelta?: number;
		failedDelta?: number;
		blockedRateDelta?: number;
	};
	changes?: {
		regressionCount?: number;
		improvementCount?: number;
		unchangedCount?: number;
		totalCompared?: number;
		rows?: Array<Record<string, unknown>>;
	};
};

export type ExplainabilityResponse = {
	cardId?: number | string;
	cardName?: string;
	componentId?: string | null;
	generatedAt?: string;
	explainCard?: {
		metricDefinition?: Record<string, unknown>;
		filterContext?: Record<string, unknown>;
		dataLineage?: Record<string, unknown>;
		querySummary?: Record<string, unknown>;
		nextActions?: string[];
		trace?: Record<string, unknown>;
	};
	copyJson?: string | null;
};

export type ExploreSessionItem = {
	id?: number | string;
	title?: string;
	question?: string | null;
	steps?: Array<Record<string, unknown>>;
	stepCount?: number;
	conclusion?: string | null;
	tags?: string[];
	projectKey?: string | null;
	dept?: string | null;
	creatorId?: number | string;
	archived?: boolean;
	publicUuid?: string | null;
	createdAt?: string;
	updatedAt?: string;
};

export type ReportTemplateItem = {
	id?: number | string;
	name?: string;
	description?: string | null;
	spec?: Record<string, unknown>;
	versionNo?: number;
	published?: boolean;
	archived?: boolean;
	creatorId?: number | string;
	createdAt?: string;
	updatedAt?: string;
};

export type ReportRunItem = {
	id?: number | string;
	templateId?: number | string | null;
	sourceType?: string | null;
	sourceId?: number | string | null;
	status?: string;
	outputFormat?: string;
	summary?: Record<string, unknown>;
	distribution?: Record<string, unknown>;
	creatorId?: number | string;
	createdAt?: string;
	updatedAt?: string;
};

export type MetricLensSummary = {
	metricId?: number | string;
	name?: string;
	owner?: number | string;
	aggregation?: string | null;
	timeGrain?: string | null;
	aclScope?: string | null;
	latestVersion?: string | null;
};

export type MetricLensDetail = {
	metricId?: number | string;
	name?: string;
	definition?: Record<string, unknown>;
	aggregation?: string | null;
	timeGrain?: string | null;
	owner?: number | string;
	version?: string | null;
	versions?: string[];
	aclScope?: string | null;
	lineage?: Record<string, unknown>;
	conflicts?: Array<Record<string, unknown>>;
};

export type MetricLensCompare = {
	metricId?: number | string;
	leftVersion?: Record<string, unknown>;
	rightVersion?: Record<string, unknown>;
	delta?: Record<string, unknown>;
};

export type QueryTraceFailureSummary = {
	since?: string;
	windowDays?: number;
	chain?: string | null;
	total?: number;
	success?: number;
	failed?: number;
	failureRate?: number;
	topErrorCodes?: Array<{
		code?: string;
		count?: number;
		retryableHint?: boolean;
		category?: string;
	}>;
	topErrorCategories?: Array<{
		category?: string;
		count?: number;
	}>;
};

export type SearchItem = {
	model: "dashboard" | "card" | "collection" | string;
	id: number;
	name?: string;
	description?: string | null;
	archived?: boolean;
};

export type SearchResponse = {
	data: SearchItem[];
	total: number;
};

export type TrashItem = {
	model: "dashboard" | "card" | string;
	id: number;
	name?: string;
	description?: string | null;
	collection_id?: number | null;
	updated_at?: string;
	created_at?: string;
};

export type TrashResponse = {
	dashboards: TrashItem[];
	cards: TrashItem[];
};

export type DatabaseListItem = {
	id: number;
	name?: string;
	engine?: string;
	is_system?: boolean;
};

export type CurrentUser = {
	id: number;
	email?: string;
	first_name?: string;
	last_name?: string;
	common_name?: string;
	is_superuser?: boolean;
	is_data_admin?: boolean;
	platform_username?: string;
};

export type MyUploadItem = {
	id: number;
	name: string;
	display_name?: string;
	schema?: string;
	created_at?: string;
};

export type DatabaseListResponse = {
	data: DatabaseListItem[];
	total: number;
};

export type DatabaseMetadataResponse = Record<string, unknown>;

export type DatasetCacheStats = {
	size?: number;
	hit_count?: number;
	miss_count?: number;
	hit_rate?: number;
	eviction_count?: number;
};

export type DatasetCachePolicy = {
	databaseId?: number;
	enabled?: boolean;
	ttlSeconds?: number;
	cacheNativeQueries?: boolean;
};

export type DatabaseValidateResponse = Record<string, unknown>;
export type DatabaseCreateResponse = Record<string, unknown>;

export type PlatformSourceWithDbId = {
	platformId: string;
	name?: string;
	type?: string;
	jdbcUrl?: string;
	description?: string | null;
	analyticsDbId?: number | null;
};

export type PlatformDataSourceItem = {
	id: string;
	name?: string;
	type?: string;
	jdbcUrl?: string;
	description?: string | null;
	ownerDept?: string | null;
	status?: string | null;
	driverVersion?: string | null;
	lastUpdatedAt?: string | null;
};

export type TableSummary = {
	id: number;
	db_id?: number;
	schema?: string | null;
	name?: string;
	display_name?: string;
	description?: string | null;
};

export type TableDetail = TableSummary & {
	fields?: Array<{
		id: number;
		name?: string;
		display_name?: string;
		base_type?: string;
		semantic_type?: string | null;
	}>;
};

export type FieldDetail = {
	id: number;
	name?: string;
	display_name?: string;
	description?: string | null;
	table_id?: number;
	db_id?: number;
	base_type?: string;
	effective_type?: string;
	semantic_type?: string | null;
	active?: boolean;
	visibility_type?: string;
	fingerprint?: unknown;
	created_at?: string;
	updated_at?: string;
};

export type FieldValuesResponse = {
	field_id: number;
	values: unknown[];
	has_more_values?: boolean;
	error?: unknown;
};

export type Metric = {
	id: number;
	name?: string;
	description?: string | null;
	archived?: boolean;
	creator_id?: number;
	table_id?: number | null;
	definition?: unknown;
	// Structured metric fields
	baseTableId?: number | null;
	aggregation?: string | null;
	expressionField?: string | null;
	filterJson?: string | null;
	timeDimension?: string | null;
	timeGrain?: string | null;
	displayName?: string | null;
	unit?: string | null;
	tags?: string | null;
	visibility?: string | null;
};

export type PlatformMetric = {
	id: string | number;
	name?: string;
	description?: string | null;
	dept?: string;
	classification?: string;
};

export type VisibleTable = {
	tableId: number;
	dbId?: number;
	schema?: string | null;
	name?: string | null;
};

export type PublicScreenDetail = ScreenDetail & {
	public_uuid?: string | null;
};

// Screen Designer Types
export type ScreenListItem = {
	id: number | string;
	name?: string;
	description?: string | null;
	width?: number;
	height?: number;
	createdAt?: string;
	updatedAt?: string;
	creatorId?: number | string | null;
	creatorName?: string | null;
	publishedVersionNo?: number | null;
	publishedAt?: string | null;
	canRead?: boolean;
	canEdit?: boolean;
	canPublish?: boolean;
	canManage?: boolean;
	canDelete?: boolean;
	isOwner?: boolean;
	// Sprint-24 F2：后端 toListResponse 已经回吐 classification，
	// 列表卡片密级 Tag 直接消费。null 表示历史未设密级的大屏。
	classification?: string | null;
	manualClassificationFloor?: string | null;
	classificationSnapshotId?: string | null;
	classificationSnapshotVersion?: number | null;
	classificationDerivedAt?: string | null;
	classificationEvidence?: Record<string, unknown> | null;
	domainId?: string | null;
	ownerDeptCode?: string | null;
};

export type ScreenDetail = ScreenListItem & {
	schemaVersion?: number;
	backgroundColor?: string;
	backgroundImage?: string | null;
	theme?: string;
	components?: ScreenComponentData[];
	globalVariables?: Array<{ key: string; label?: string; type?: string; defaultValue?: string; description?: string }>;
	sourceMode?: "draft" | "published" | string;
};

export type ScreenWarmupSummary = {
	totalDatabaseSources?: number;
	warmed?: number;
	skipped?: number;
	failed?: number;
	items?: Array<Record<string, unknown>>;
};

export type ScreenAiGenerationRequest = {
	prompt: string;
	width?: number;
	height?: number;
};

export type ScreenAiRevisionRequest = {
	prompt: string;
	screenSpec: Record<string, unknown>;
	context?: string[];
	mode?: "apply" | "suggest";
};

export type ScreenAiGenerationResponse = {
	engine?: string;
	prompt?: string;
	contextCount?: number;
	usedContextCount?: number;
	applyMode?: "apply" | "suggest" | string;
	applied?: boolean;
	intent?: {
		domain?: string;
		timeRange?: string;
		granularity?: string;
		metrics?: string[];
		dimensions?: string[];
		filters?: string[];
	};
	semanticModelHints?: {
		domain?: string;
		factTable?: string;
		timeField?: string;
		dimensions?: string[];
		metricMappings?: Array<{
			name?: string;
			expression?: string;
		}>;
	};
	queryRecommendations?: Array<{
		id?: string;
		purpose?: string;
		mode?: string;
		semanticLayer?: string;
		domain?: string;
		factTable?: string;
		timeField?: string;
		timeRange?: string;
		granularity?: string;
		dimensions?: string[];
		metrics?: string[];
		filters?: string[];
		sqlHint?: string;
	}>;
	sqlBlueprints?: Array<{
		queryId?: string;
		purpose?: string;
		sql?: string;
		factTable?: string;
		timeField?: string;
	}>;
	vizRecommendations?: Array<{
		queryId?: string;
		componentType?: string;
		title?: string;
	}>;
	metricLensReferences?: Array<Record<string, unknown>>;
	semanticRecall?: {
		schemaCandidates?: Array<Record<string, unknown>>;
		synonymHits?: Array<Record<string, unknown>>;
		fewShotExamples?: Array<Record<string, unknown>>;
		promptHints?: Array<string>;
		trace?: Record<string, unknown>;
	};
	nl2sqlDiagnostics?: {
		stage?: string;
		domain?: string;
		factTable?: string;
		timeField?: string;
		queryRecommendationCount?: number;
		sqlBlueprintCount?: number;
		safeCount?: number;
		executableBlueprintCount?: number;
		needsParamsCount?: number;
		blockedCount?: number;
		status?: string;
		executionReadiness?: "ready" | "needs-params" | "blocked" | string;
		requiredVariableCount?: number;
		pendingVariableCount?: number;
		requiredVariables?: string[];
		pendingVariables?: string[];
		autoInjectedVariableCount?: number;
		autoInjectedVariables?: string[];
		blockedQueryIds?: string[];
		needsParamsQueryIds?: string[];
		safeQueryIds?: string[];
		semanticRecallEnabled?: boolean;
		blueprintChecks?: Array<{
			queryId?: string;
			purpose?: string;
			status?: string;
			hasTemplateVariables?: boolean;
			templateVariables?: string[];
			reasons?: string[];
			[key: string]: unknown;
		}>;
	};
	generatedBy?: number | string;
	generatedAt?: string;
	actions?: string[];
	quality?: {
		score?: number;
		warnings?: string[];
		suggestions?: string[];
	};
	screenSpec?: {
		schemaVersion?: number;
		name?: string;
		description?: string | null;
		width?: number;
		height?: number;
		backgroundColor?: string;
		backgroundImage?: string | null;
		theme?: string;
		components?: ScreenComponentData[];
		globalVariables?: Array<{
			key: string;
			label?: string;
			type?: string;
			defaultValue?: string;
			description?: string;
		}>;
	};
};

export type ScreenSpecValidationResponse = {
	valid?: boolean;
	warnings?: string[];
};

export type ScreenTemplateItem = {
	id: number | string;
	schemaVersion?: number;
	name?: string;
	description?: string | null;
	category?: string;
	thumbnail?: string | null;
	tags?: string[];
	theme?: string | null;
	width?: number;
	height?: number;
	backgroundColor?: string;
	backgroundImage?: string | null;
	components?: ScreenComponentData[];
	globalVariables?: Array<{ key: string; label?: string; type?: string; defaultValue?: string; description?: string }>;
	creatorId?: number | string;
	visibilityScope?: "personal" | "team" | "global" | string;
	ownerDept?: string | null;
	listed?: boolean;
	themePack?: Record<string, unknown>;
	sourceScreenId?: number | string;
	sourceTemplateId?: number | string;
	templateVersion?: number;
	createdAt?: string;
	updatedAt?: string;
};

export type ScreenTemplateVersionItem = {
	id?: number | string;
	templateId?: number | string;
	versionNo?: number;
	action?: string;
	actorId?: number | string;
	createdAt?: string;
	restoredFromVersion?: number | null;
	snapshot?: Record<string, unknown>;
};

export type ScreenPluginComponent = {
	id: string;
	name?: string;
	description?: string;
	icon?: string;
	baseType?: string;
	defaultWidth?: number;
	defaultHeight?: number;
	defaultConfig?: Record<string, unknown>;
	propertySchema?: Record<string, unknown>;
	dataContract?: Record<string, unknown>;
	installed?: boolean;
};

export type ScreenPluginDataSource = {
	id: string;
	name?: string;
	type?: string;
	sdkVersion?: string;
};

export type ScreenPluginManifest = {
	id: string;
	name?: string;
	version?: string;
	enabled?: boolean;
	installed?: boolean;
	origin?: string;
	signatureRequired?: boolean;
	components?: ScreenPluginComponent[];
	dataSources?: ScreenPluginDataSource[];
};

export type MarketplaceCatalogItem = {
	id: string;
	name?: string;
	description?: string;
	author?: string;
	version?: string;
	category?: string;
	tags?: string[];
	thumbnailUrl?: string;
	downloads?: number;
	createdAt?: string;
	updatedAt?: string;
	installed?: boolean;
	sourceTemplateId?: number | string;
};

export type ScreenPluginValidationResult = {
	valid?: boolean;
	errors?: string[];
};

export type ScreenIndustryPack = {
	packageType?: string;
	specVersion?: string;
	exportedAt?: string;
	exportedBy?: string;
	metadata?: Record<string, unknown>;
	templates?: Array<Record<string, unknown>>;
	summary?: Record<string, unknown>;
};

export type ScreenIndustryPackImportResult = {
	imported?: number;
	failed?: number;
	items?: Array<Record<string, unknown>>;
};

export type ScreenIndustryPackPresets = {
	industries?: Array<Record<string, unknown>>;
	hardwareProfiles?: Array<Record<string, unknown>>;
	connectorTemplates?: Array<Record<string, unknown>>;
	deploymentModes?: string[];
};

export type ScreenIndustryPackValidationResult = {
	valid?: boolean;
	errors?: string[];
	warnings?: string[];
	recommendations?: string[];
};

export type ScreenIndustryPackAuditRow = {
	id?: number | string;
	assetType?: string;
	assetId?: number | string | null;
	action?: string;
	actorId?: number | string | null;
	source?: string;
	result?: string;
	requestId?: string;
	createdAt?: string;
	details?: Record<string, unknown> | string | null;
};

export type ScreenIndustryConnectorPlan = {
	generatedAt?: string;
	templateCount?: number;
	jobCount?: number;
	items?: Array<Record<string, unknown>>;
};

export type ScreenIndustryConnectorProbe = {
	generatedAt?: string;
	summary?: Record<string, unknown>;
	rows?: Array<Record<string, unknown>>;
};

export type ScreenIndustryOpsHealth = {
	generatedAt?: string;
	summary?: ScreenIndustryOpsHealthSummary;
	checks?: ScreenIndustryOpsHealthCheck[];
};

export type ScreenIndustryRuntimeProbe = {
	generatedAt?: string;
	summary?: ScreenIndustryRuntimeProbeSummary;
	rows?: ScreenIndustryRuntimeProbeRow[];
};

export type ScreenIndustryOpsHealthSummary = {
	score?: number;
	deploymentMode?: string;
	templateCount?: number;
	listedCount?: number;
	auditSamples?: number;
	failedAudits?: number;
};

export type ScreenIndustryOpsHealthCheck = {
	id?: string;
	name?: string;
	status?: "pass" | "warn" | "fail" | string;
	message?: string;
	details?: Record<string, unknown>;
};

export type ScreenIndustryRuntimeProbeSummary = {
	total?: number;
	pass?: number;
	warn?: number;
	fail?: number;
	timeoutMs?: number;
};

export type ScreenIndustryRuntimeProbeRow = {
	id?: string;
	name?: string;
	host?: string;
	port?: number;
	required?: boolean;
	protocol?: "tcp" | "http" | "https" | "mqtt" | string;
	path?: string | null;
	expectedBodyContains?: string | null;
	url?: string | null;
	httpStatus?: number | null;
	bodyMatched?: boolean | null;
	bodyPreview?: string | null;
	status?: "pass" | "warn" | "fail" | string;
	message?: string;
	latencyMs?: number;
};

export type ScreenCompliancePolicy = {
	maskingEnabled?: boolean;
	watermarkEnabled?: boolean;
	watermarkText?: string;
	exportApprovalRequired?: boolean;
	auditRetentionDays?: number;
	updatedBy?: string;
	updatedAt?: string;
};

export type ScreenComplianceReport = {
	generatedAt?: string;
	scope?: string;
	screenId?: number | string;
	days?: number;
	limit?: number;
	policy?: ScreenCompliancePolicy;
	summary?: Record<string, unknown>;
	rows?: Array<Record<string, unknown>>;
};

export type ScreenComplianceReportQuery = {
	screenId?: number | string;
	days?: number;
	limit?: number;
};

export type ScreenVersion = {
	id: number | string;
	screenId?: number | string;
	versionNo?: number;
	status?: string;
	name?: string;
	description?: string | null;
	currentPublished?: boolean;
	publishedAt?: string | null;
	createdAt?: string;
	creatorId?: number | string;
};

export type ScreenVersionDiff = {
	from?: ScreenVersion;
	to?: ScreenVersion;
	summary?: {
		componentCountFrom?: number;
		componentCountTo?: number;
		addedComponents?: number;
		removedComponents?: number;
		addedComponentTypes?: number;
		removedComponentTypes?: number;
		changedTypeComponents?: number;
		addedVariables?: number;
		removedVariables?: number;
	};
	details?: {
		addedComponentIds?: string[];
		removedComponentIds?: string[];
		addedComponentTypes?: string[];
		removedComponentTypes?: string[];
		changedTypeComponents?: Array<{
			id?: string;
			fromType?: string;
			toType?: string;
		}>;
		addedVariableKeys?: string[];
		removedVariableKeys?: string[];
	};
};

export type ScreenAclEntry = {
	id?: number | string;
	screenId?: number | string;
	subjectType: "USER" | "ROLE";
	subjectId: string;
	subjectName?: string;
	subjectUsername?: string;
	perm: "READ" | "MANAGE" | "OWNER";
	/**
	 * 大屏密级越级共享。仅对 perm=READ（后端 VIEWER）有意义；
	 * 表示授予被分享人在自身密级低于大屏密级时仍可访问的权限。
	 */
	levelOverride?: boolean;
	creatorId?: number | string;
	createdAt?: string;
	updatedAt?: string;
};

export type ScreenAuditEntry = {
	id: number | string;
	screenId?: number | string;
	actorId?: number | string;
	action?: string;
	requestId?: string;
	createdAt?: string;
	before?: Record<string, unknown>;
	after?: Record<string, unknown>;
};

export type ScreenComment = {
	id: number | string;
	screenId?: number | string;
	componentId?: string | null;
	message?: string;
	anchor?: Record<string, unknown> | null;
	mentions?: Array<Record<string, unknown>>;
	createdBy?: number | string;
	createdAt?: string;
	status?: "open" | "resolved" | string;
	resolvedBy?: number | string | null;
	resolvedAt?: string | null;
	resolutionNote?: string | null;
	requestId?: string | null;
};

export type ScreenCommentChanges = {
	cursor?: number;
	sinceId?: number;
	fullReload?: boolean;
	waitMs?: number;
	rows?: ScreenComment[];
};

export type ScreenCollaborationPresenceRow = {
	sessionId?: string;
	userId?: number | string | null;
	displayName?: string;
	componentId?: string | null;
	typing?: boolean;
	clientType?: string | null;
	selectedCount?: number | null;
	selectionPreview?: string | null;
	lastSeenAt?: string | null;
	idleSeconds?: number;
	mine?: boolean;
};

export type ScreenCollaborationPresence = {
	generatedAt?: string;
	ttlSeconds?: number;
	meSessionId?: string | null;
	activeCount?: number;
	rows?: ScreenCollaborationPresenceRow[];
};

export type ScreenEditLock = {
	active?: boolean;
	screenId?: number | string | null;
	ownerId?: number | string | null;
	ownerName?: string | null;
	mine?: boolean;
	requestId?: string | null;
	acquiredAt?: string | null;
	heartbeatAt?: string | null;
	expireAt?: string | null;
	ttlSeconds?: number;
};

export type ScreenPublicLinkPolicy = {
	uuid?: string | null;
	expireAt?: string | null;
	hasPassword?: boolean;
	ipAllowlist?: string | null;
	disabled?: boolean;
};

export type ScreenHealthStats = {
	componentCount?: number;
	dataBoundComponentCount?: number;
	refreshableComponentCount?: number;
	interactiveComponentCount?: number;
	heavyComponentCount?: number;
	warmupEligibleDatabaseSources?: number;
	uniqueComponentTypes?: number;
	estimatedComplexity?: number;
	pass?: boolean;
	recommendations?: string[];
};

export type ScreenHealthReport = {
	screenId?: number | string;
	generatedAt?: string;
	requestId?: string;
	baselineTargetComponents?: number;
	draft?: ScreenHealthStats;
	published?: ScreenHealthStats;
	publishedVersionNo?: number | null;
	publishedAt?: string | null;
};

export type ScreenExportPrepareRequest = {
	format?: "png" | "pdf" | "json" | string;
	mode?: "draft" | "published" | "preview" | string;
	device?: "pc" | "tablet" | "mobile" | string;
	includeScreenSpec?: boolean;
};

export type ScreenExportPrepareResult = {
	allowed?: boolean;
	screenId?: number | string;
	classification?: string;
	classificationSnapshotId?: string;
	classificationSnapshotVersion?: number;
	fileSubjectKey?: string;
	format?: string;
	mode?: string;
	requestedMode?: string;
	resolvedMode?: string;
	device?: string | null;
	requestId?: string;
	previewUrl?: string;
	specDigest?: string | null;
	publishedVersionNo?: number | null;
	publishedAt?: string | null;
	screenSpec?: {
		schemaVersion?: number;
		name?: string;
		description?: string | null;
		width?: number;
		height?: number;
		backgroundColor?: string;
		backgroundImage?: string | null;
		theme?: string;
		components?: ScreenComponentData[];
		globalVariables?: Array<{
			key: string;
			label?: string;
			type?: string;
			defaultValue?: string;
			description?: string;
		}>;
	};
	policy?: {
		policyVersion?: number;
		exportApprovalRequired?: boolean;
		watermarkEnabled?: boolean;
		watermarkText?: string;
	};
};

export type ScreenExportReportRequest = {
	status: "success" | "failed" | "fallback" | string;
	format?: "png" | "pdf" | "json" | string;
	mode?: "draft" | "published" | "preview" | string;
	resolvedMode?: "draft" | "published" | "preview" | string;
	device?: "pc" | "tablet" | "mobile" | string;
	requestId?: string;
	specDigest?: string;
	message?: string;
};

export type ScreenExportReportResult = {
	accepted?: boolean;
	status?: string;
	screenId?: number | string;
	format?: string;
	mode?: string;
	device?: string | null;
	clientRequestId?: string | null;
	message?: string | null;
	specDigest?: string | null;
	requestId?: string;
	reportedAt?: string;
};

export type ScreenExportRenderRequest = {
	format?: "png" | "pdf" | string;
	mode?: "draft" | "published" | "preview" | string;
	device?: "pc" | "tablet" | "mobile" | string;
	pixelRatio?: number;
	screenSpec?: Record<string, unknown>;
};

export type ScreenExportRenderResult = {
	blob: Blob;
	contentType?: string;
	fileName?: string;
	requestId?: string;
	specDigest?: string;
	resolvedMode?: string;
	renderEngine?: string;
	pixelRatio?: number;
	deviceMode?: string;
	hiddenByDevice?: number;
};

export type ScreenComponentData = {
	id: string;
	type: string;
	name: string;
	x: number;
	y: number;
	width: number;
	height: number;
	zIndex: number;
	locked: boolean;
	visible: boolean;
	config: Record<string, unknown>;
	dataSource?: Record<string, unknown>;
	interaction?: Record<string, unknown>;
};

// ── Auth coordination ──
// Token refresh and login redirect are delegated to the shared session-auth module.
// This ensures analyticsApi, apiClient, and SessionManager all share one single-flight
// lock and one redirect guard — eliminating the dual-timer race that was the root cause
// of the "drill page kicked back to workbench" bug.

/** @deprecated No longer needed after session-auth removal. No-op for backwards compatibility. */
export const resetAnalyticsAuthRedirectFlag = () => {};

export class HttpError extends Error {
	status: number;
	bodyText: string;
	requestId?: string;
	code?: string;
	retryable?: boolean;
	constructor(
		status: number,
		message: string,
		bodyText: string,
		requestId?: string,
		code?: string,
		retryable?: boolean,
	) {
		super(message);
		this.status = status;
		this.bodyText = bodyText;
		this.requestId = requestId;
		this.code = code;
		this.retryable = retryable;
	}
}

export class AuthError extends HttpError {}

// Dashboard shares require login; other public endpoints retain their own error UI.
function isPublicAnalyticsUrl(url: string): boolean {
	if (/\/api\/public\/(?:pivot\/)?dashboard\//.test(url)) return false;
	return (
		url.includes("/api/public/") ||
		url.includes("/bi/api/public/") ||
		url.includes("/api/explore-session/public/") ||
		url.includes("/bi/api/explore-session/public/")
	);
}

function redirectToLoginWithCurrentPath() {
	userStore.getState().actions.clearUserInfoAndToken();
	markPortalSessionLogout(Date.now());
	window.location.replace(resolveLoginHref(resolveCurrentAppPath()));
}

async function apiFetch(url: string, init: RequestInit, allowRedirect: boolean): Promise<Response> {
	const headers = withPlatformAuthorization(init.headers);
	if (!headers.has("accept")) headers.set("accept", "application/json");

	const response = await fetch(url, { ...init, credentials: "include", headers });
	if (response.status !== 401 || !allowRedirect || isPublicAnalyticsUrl(url)) {
		return response;
	}
	const refreshed = await refreshPortalSessionIfPossible().catch(() => null);
	if (refreshed?.authenticated) {
		return await fetch(url, { ...init, credentials: "include", headers });
	}
	// Browser auth is cookie-only; redirect only after the shared platform refresh path also fails.
	redirectToLoginWithCurrentPath();
	return response;
}

export async function fetchWithPlatformAuth(
	url: string,
	init: RequestInit = {},
	allowRefresh: boolean = true,
): Promise<Response> {
	return await apiFetch(url, init, allowRefresh);
}

async function readErrorText(response: Response): Promise<string> {
	return await response.text().catch(() => "");
}

function extractRequestId(response: Response): string | undefined {
	const headers = ["x-request-id", "x-requestid", "x-correlation-id"];
	for (const header of headers) {
		const value = response.headers.get(header);
		if (value && value.trim().length > 0) {
			return value.trim();
		}
	}
	return undefined;
}

function extractErrorCode(response: Response, bodyText: string): string | undefined {
	const headerCode = response.headers.get("x-error-code");
	if (headerCode && headerCode.trim().length > 0) {
		return headerCode.trim();
	}
	if (!bodyText) {
		return undefined;
	}
	try {
		const payload = JSON.parse(bodyText) as { code?: unknown };
		if (typeof payload.code === "string" && payload.code.trim().length > 0) {
			return payload.code.trim();
		}
	} catch {
		// ignore non-JSON error bodies
	}
	return undefined;
}

function extractErrorRetryable(response: Response, bodyText: string): boolean | undefined {
	const headerValue = response.headers.get("x-error-retryable");
	if (headerValue != null && headerValue.trim().length > 0) {
		const normalized = headerValue.trim().toLowerCase();
		if (normalized === "true" || normalized === "1" || normalized === "yes") return true;
		if (normalized === "false" || normalized === "0" || normalized === "no") return false;
	}
	if (!bodyText) {
		return undefined;
	}
	try {
		const payload = JSON.parse(bodyText) as { retryable?: unknown };
		if (typeof payload.retryable === "boolean") {
			return payload.retryable;
		}
	} catch {
		// ignore non-JSON error bodies
	}
	return undefined;
}

function buildHttpError(response: Response, bodyText: string): HttpError {
	const requestId = extractRequestId(response);
	const errorCode = extractErrorCode(response, bodyText);
	const retryable = extractErrorRetryable(response, bodyText);
	const baseMsg = "HTTP " + response.status + " " + response.statusText + ": " + bodyText;
	const taggedMsg = errorCode ? baseMsg + " [code=" + errorCode + "]" : baseMsg;
	const retryableTag = typeof retryable === "boolean" ? " [retryable=" + String(retryable) + "]" : "";
	const requestTag = requestId ? " [requestId=" + requestId + "]" : "";
	const msg = taggedMsg + retryableTag + requestTag;
	if (response.status === 401 || response.status === 403) {
		return new AuthError(response.status, msg, bodyText, requestId, errorCode, retryable);
	}
	return new HttpError(response.status, msg, bodyText, requestId, errorCode, retryable);
}

export function isRetryableHttpError(error: unknown): boolean {
	if (!(error instanceof HttpError)) {
		return false;
	}
	if (typeof error.retryable === "boolean") {
		return error.retryable;
	}
	return [408, 429, 502, 503, 504].includes(error.status);
}

async function fetchJson<T>(url: string): Promise<T> {
	const response = await apiFetch(url, { method: "GET" }, true);
	if (!response.ok) {
		const text = await readErrorText(response);
		throw buildHttpError(response, text);
	}
	return (await response.json()) as T;
}

async function sendJson<T>(url: string, body: unknown): Promise<T> {
	return await requestJson<T>(url, "POST", body);
}

async function requestJson<T>(url: string, method: "POST" | "PUT" | "PATCH" | "DELETE", body?: unknown): Promise<T> {
	const init: RequestInit = {
		method,
		headers: {
			accept: "application/json",
			"content-type": "application/json",
		},
	};
	if (method !== "DELETE") {
		init.body = JSON.stringify(body ?? {});
	}
	const response = await apiFetch(url, init, true);
	if (!response.ok) {
		const text = await readErrorText(response);
		throw buildHttpError(response, text);
	}
	if (response.status === 204) {
		return undefined as T;
	}
	const contentType = response.headers.get("content-type") ?? "";
	if (!contentType.includes("application/json")) {
		return (await response.text()) as unknown as T;
	}
	return (await response.json()) as T;
}

function parseContentDispositionFilename(headerValue: string | null): string | undefined {
	if (!headerValue) return undefined;
	const utf8Match = /filename\*=UTF-8''([^;]+)/i.exec(headerValue);
	if (utf8Match && utf8Match[1]) {
		try {
			return decodeURIComponent(utf8Match[1].trim());
		} catch {
			return utf8Match[1].trim();
		}
	}
	const plainMatch = /filename="?([^\";]+)"?/i.exec(headerValue);
	if (plainMatch && plainMatch[1]) {
		return plainMatch[1].trim();
	}
	return undefined;
}

async function requestBinary(url: string, method: "POST" | "PUT", body?: unknown): Promise<ScreenExportRenderResult> {
	const response = await apiFetch(
		url,
		{
			method,
			headers: {
				accept: "application/octet-stream",
				"content-type": "application/json",
			},
			body: JSON.stringify(body ?? {}),
		},
		true,
	);
	if (!response.ok) {
		const text = await readErrorText(response);
		throw buildHttpError(response, text);
	}
	const blob = await response.blob();
	return {
		blob,
		contentType: response.headers.get("content-type") ?? undefined,
		fileName: parseContentDispositionFilename(response.headers.get("content-disposition")),
		requestId: response.headers.get("x-request-id") ?? undefined,
		specDigest: response.headers.get("x-screen-spec-digest") ?? undefined,
		resolvedMode: response.headers.get("x-screen-resolved-mode") ?? undefined,
		renderEngine: response.headers.get("x-screen-render-engine") ?? undefined,
		pixelRatio: (() => {
			const raw = response.headers.get("x-screen-render-pixel-ratio");
			if (!raw) return undefined;
			const value = Number.parseFloat(raw);
			return Number.isFinite(value) ? value : undefined;
		})(),
		deviceMode: response.headers.get("x-screen-device-mode") ?? undefined,
		hiddenByDevice: (() => {
			const raw = response.headers.get("x-screen-hidden-by-device");
			if (!raw) return undefined;
			const value = Number.parseInt(raw, 10);
			return Number.isFinite(value) ? value : undefined;
		})(),
	};
}

export type ProjectCockpitFilterQuery = {
	majorProjectId?: string;
	dateFrom?: string;
	dateTo?: string;
	deptId?: string;
	riskLevel?: string;
};

export type ProjectCockpitSettingsResponse = {
	periodStart?: string;
	periodEnd?: string;
	updatedBy?: string;
	updatedAt?: string;
	canPublish?: boolean;
};

export type ProjectCockpitOption = {
	value?: string;
	label?: string;
};

export type ProjectCockpitKpi = {
	key?: string;
	label?: string;
	value?: string;
	unit?: string;
};

export type ProjectCockpitRankingItem = {
	majorProjectId?: string;
	majorProjectName?: string;
	subprojectCount?: number;
	healthScore?: number;
	completionRate?: string;
	highRiskCount?: number;
	overdueCount?: number;
	topDelayReason?: string;
};

export type ProjectCockpitAlertItem = {
	title?: string;
	majorProjectName?: string;
	subprojectName?: string;
	riskLevel?: string;
	delayDays?: number;
	reason?: string;
};

export type ProjectCockpitSummaryResponse = {
	dataState?: {
		warehouseEnabled?: boolean;
		ready?: boolean;
		message?: string;
		batchId?: string;
		status?: string;
	};
	hero?: {
		title?: string;
		subtitle?: string;
		updatedAt?: string;
		scope?: string;
	};
	filters?: {
		majorProjects?: ProjectCockpitOption[];
		depts?: ProjectCockpitOption[];
		riskLevels?: ProjectCockpitOption[];
		current?: ProjectCockpitFilterQuery;
	};
	kpis?: ProjectCockpitKpi[];
	ranking?: ProjectCockpitRankingItem[];
	alerts?: ProjectCockpitAlertItem[];
	spotlight?: {
		majorProjectId?: string;
		majorProjectName?: string;
		summary?: string;
		highRiskCount?: number;
		delayCount?: number;
		nextMilestone?: string;
	};
};

export type ProjectCockpitTrendPoint = {
	weekLabel?: string;
	completionRate?: number;
	delayedNodes?: number;
	highRiskNodes?: number;
	milestoneCompletionRate?: number;
	label?: string;
	value?: number;
};

export type ProjectCockpitTrendSeries = {
	majorProjectId?: string;
	name?: string;
	points?: ProjectCockpitTrendPoint[];
};

export type ProjectCockpitTrendsResponse = {
	dataState?: {
		warehouseEnabled?: boolean;
		ready?: boolean;
		message?: string;
		batchId?: string;
		status?: string;
	};
	weekly?: ProjectCockpitTrendPoint[];
	majorProjectSeries?: ProjectCockpitTrendSeries[];
};

export type ProjectCockpitExecutionTask = {
	id?: string;
	name?: string;
	type?: string;
	planDate?: string;
	actualDate?: string;
	isCompleted?: boolean;
	isOverdue?: boolean;
	isIncomplete?: boolean;
	delayDays?: number;
	riskLevel?: string;
	owner?: string;
	majorProjectName?: string;
	subprojectName?: string;
	status?: string;
};

export type ProjectCockpitExecutionResponse = {
	dataState?: {
		warehouseEnabled?: boolean;
		ready?: boolean;
		message?: string;
		batchId?: string;
		status?: string;
	};
	ganttTasks?: ProjectCockpitExecutionTask[];
	milestones?: Array<Record<string, unknown>>;
	dueList?: Array<Record<string, unknown>>;
	workload?: Array<Record<string, unknown>>;
	stageBuckets?: Array<Record<string, unknown>>;
};

export type ProjectCockpitRiskAttributionResponse = {
	dataState?: {
		warehouseEnabled?: boolean;
		ready?: boolean;
		message?: string;
		batchId?: string;
		status?: string;
	};
	riskBreakdown?: Array<Record<string, unknown>>;
	delayReasonBreakdown?: Array<Record<string, unknown>>;
	delayReasonMatrix?: Array<Record<string, unknown>>;
	weeklyDelayTrend?: Array<Record<string, unknown>>;
	delayedProjects?: Array<Record<string, unknown>>;
};

export type ProjectCockpitTreeNode = {
	id?: string;
	parentId?: string;
	level?: "major" | "subproject" | "node";
	name?: string;
	status?: string;
	progressRate?: number;
	riskLevel?: string;
	delayDays?: number;
	ownerDept?: string;
	ownerUser?: string;
	milestoneCount?: number;
	incompleteCount?: number;
	highRiskCount?: number;
	planDate?: string;
	actualDate?: string;
	reason?: string;
	children?: ProjectCockpitTreeNode[];
};

export type ProjectCockpitTreeResponse = {
	selectedMajorProjectId?: string;
	dataState?: {
		warehouseEnabled?: boolean;
		ready?: boolean;
		message?: string;
		batchId?: string;
		status?: string;
	};
	tree?: ProjectCockpitTreeNode[];
	summary?: {
		selectedMajorProjectId?: string;
		totalNodes?: number;
		completedNodes?: number;
		highRiskNodes?: number;
		delayNodes?: number;
		avgHealthScore?: number;
	};
};

export type ProjectCockpitDataSupportResponse = {
	dataState?: {
		warehouseEnabled?: boolean;
		ready?: boolean;
		message?: string;
		batchId?: string;
		status?: string;
	};
	lastUpdatedAt?: string;
	batch?: {
		batchId?: string;
		sourceFileName?: string;
		uploadedAt?: string;
		refreshedAt?: string;
		status?: string;
		totalRows?: number;
		validRows?: number;
		issueRows?: number;
		issueCount?: number;
		coverageRate?: number;
	};
	quality?: {
		issueCount?: number;
		issueRows?: number;
		unknownDelayReasonCount?: number;
		unmappedSubprojectCount?: number;
		highRiskNodeCount?: number;
		filteredNodeCount?: number;
	};
	coverage?: Array<Record<string, unknown>>;
	glossary?: Array<Record<string, unknown>>;
	missingChecklist?: Array<Record<string, unknown>>;
	dataSources?: Array<Record<string, unknown>>;
};

export type ProjectCockpitDrillItem = {
	id?: string;
	name?: string;
	majorProjectName?: string;
	subprojectName?: string;
	riskLevel?: string;
	status?: string;
	progressRate?: number;
	delayDays?: number;
	planDate?: string;
	actualDate?: string;
	reason?: string;
	ownerDept?: string;
	ownerUser?: string;
	nodeType?: string;
};

export type ProjectCockpitDrillDetailResponse = {
	target?: string;
	title?: string;
	items?: ProjectCockpitDrillItem[];
	total?: number;
};

function buildProjectCockpitQuery(params?: ProjectCockpitFilterQuery): string {
	const qs = new URLSearchParams();
	if (params?.majorProjectId) qs.set("majorProjectId", params.majorProjectId);
	if (params?.dateFrom) qs.set("dateFrom", params.dateFrom);
	if (params?.dateTo) qs.set("dateTo", params.dateTo);
	if (params?.deptId) qs.set("deptId", params.deptId);
	if (params?.riskLevel) qs.set("riskLevel", params.riskLevel);
	const query = qs.toString();
	return query.length > 0 ? `?${query}` : "";
}

export type UserSearchItem = {
	id: number | string;
	email?: string;
	common_name?: string;
};

export type PlatformUser = {
	id?: string;
	username: string;
	displayName?: string;
	deptCode?: string;
	deptName?: string;
};

export type PlatformRole = {
	id?: string;
	name: string;
	description?: string;
	scope?: string;
	operations?: string[];
	source?: "builtin" | "custom" | "assignment" | string;
};

export type PlatformOrgNode = {
	id: number | string;
	name: string;
	deptCode?: string;
	parentId?: number | string;
	children?: PlatformOrgNode[];
	isRoot?: boolean;
};

export const analyticsApi = {
	listIndicatorCards: (id: string, version: string) =>
		fetchJson<Array<{ id: number; name: string }>>(
			`/bi/api/card?indicatorId=${encodeURIComponent(id)}&indicatorVersion=${encodeURIComponent(version)}`,
		),
	getCurrentUser: () => fetchJson<CurrentUser>("/bi/api/user/current"),
	getUser: (id: number | string) =>
		fetchJson<{ id: number; email?: string; common_name?: string; first_name?: string; last_name?: string }>(
			`/bi/api/user/${encodeURIComponent(String(id))}`,
		),
	searchUsers: (query: string) => fetchJson<UserSearchItem[]>("/bi/api/user/search?q=" + encodeURIComponent(query)),
	listPlatformUsers: async (keyword?: string): Promise<PlatformUser[]> => {
		const params = keyword ? `?keyword=${encodeURIComponent(keyword)}` : "";
		const response = await apiFetch(`/api/directory/users${params}`, { method: "GET" }, true);
		if (!response.ok) return [];
		const body = await response.json();
		// Platform returns { data: [...] } or raw array
		const list = Array.isArray(body) ? body : Array.isArray(body?.data) ? body.data : [];
		return list as PlatformUser[];
	},
	listPlatformRoles: async (): Promise<PlatformRole[]> => {
		const response = await apiFetch("/api/directory/roles", { method: "GET" }, true);
		if (!response.ok) return [];
		const body = await response.json();
		// Platform returns { data: [...] } or raw array
		return Array.isArray(body) ? body : Array.isArray(body?.data) ? body.data : [];
	},
	listPlatformOrgs: async (): Promise<PlatformOrgNode[]> => {
		const response = await apiFetch("/api/directory/orgs", { method: "GET" }, true);
		if (!response.ok) return [];
		const body = await response.json();
		return Array.isArray(body) ? body : Array.isArray(body?.data) ? body.data : [];
	},
	getHealth: () => fetchJson<{ status?: string }>("/bi/api/health"),
	getProjectCockpitSettings: () => fetchJson<ProjectCockpitSettingsResponse>("/bi/api/project-cockpit/settings"),
	updateProjectCockpitSettings: (body: { periodStart: string; periodEnd: string }) =>
		requestJson<void>("/bi/api/project-cockpit/settings", "PUT", body),
	getProjectCockpitSummary: (params?: ProjectCockpitFilterQuery) =>
		fetchJson<ProjectCockpitSummaryResponse>("/bi/api/project-cockpit/summary" + buildProjectCockpitQuery(params)),
	getProjectCockpitTrends: (params?: ProjectCockpitFilterQuery) =>
		fetchJson<ProjectCockpitTrendsResponse>("/bi/api/project-cockpit/trends" + buildProjectCockpitQuery(params)),
	getProjectCockpitExecution: (params?: ProjectCockpitFilterQuery) =>
		fetchJson<ProjectCockpitExecutionResponse>("/bi/api/project-cockpit/execution" + buildProjectCockpitQuery(params)),
	getProjectCockpitRiskAttribution: (params?: ProjectCockpitFilterQuery) =>
		fetchJson<ProjectCockpitRiskAttributionResponse>(
			"/bi/api/project-cockpit/risk-attribution" + buildProjectCockpitQuery(params),
		),
	getProjectCockpitTree: (params?: ProjectCockpitFilterQuery) =>
		fetchJson<ProjectCockpitTreeResponse>(
			"/bi/api/project-cockpit/major-project-tree" + buildProjectCockpitQuery(params),
		),
	getProjectCockpitDataSupport: (params?: ProjectCockpitFilterQuery) =>
		fetchJson<ProjectCockpitDataSupportResponse>(
			"/bi/api/project-cockpit/data-support" + buildProjectCockpitQuery(params),
		),
	getProjectCockpitDrillDetail: (target: string, params?: ProjectCockpitFilterQuery & Record<string, string>) => {
		const qs = new URLSearchParams();
		if (params) {
			for (const [key, val] of Object.entries(params)) {
				if (val) qs.set(key, val);
			}
		}
		const query = qs.toString();
		return fetchJson<ProjectCockpitDrillDetailResponse>(
			`/bi/api/project-cockpit/drill/${encodeURIComponent(target)}${query ? `?${query}` : ""}`,
		);
	},
	listDatabases: () => fetchJson<DatabaseListResponse>("/bi/api/database"),
	listPlatformDataSources: () => fetchJson<PlatformDataSourceItem[]>("/bi/api/platform/data-sources"),
	/** 从 platform 数据源列表获取（附带已注册的 analytics DB ID） */
	listPlatformSources: () => fetchJson<PlatformSourceWithDbId[]>("/bi/api/database/platform-sources"),
	/** 按需注册 platform 数据源到 analytics，返回 analytics database 信息 */
	ensureFromPlatform: (platformDataSourceId: string) =>
		sendJson<DatabaseCreateResponse>(
			`/bi/api/database/ensure-from-platform/${encodeURIComponent(platformDataSourceId)}`,
			{},
		),
	listTables: (dbId: string | number) =>
		fetchJson<TableSummary[]>(`/bi/api/table?db_id=${encodeURIComponent(String(dbId))}`),
	getTable: (tableId: string | number) =>
		fetchJson<TableDetail>(`/bi/api/table/${encodeURIComponent(String(tableId))}`),
	getTableFks: (tableId: string | number) =>
		fetchJson<
			Array<{
				origin_id: number;
				origin: { id: number; name: string; table_id: number };
				destination_id: number;
				destination: { id: number; name: string; table_id: number };
			}>
		>(`/bi/api/table/${encodeURIComponent(String(tableId))}/fks`),
	getField: (fieldId: string | number) =>
		fetchJson<FieldDetail>(`/bi/api/field/${encodeURIComponent(String(fieldId))}`),
	getFieldValues: (fieldId: string | number) =>
		fetchJson<FieldValuesResponse>(`/bi/api/field/${encodeURIComponent(String(fieldId))}/values`),
	validateDatabase: (body: unknown) => sendJson<DatabaseValidateResponse>("/bi/api/database/validate", body),
	createDatabase: (body: unknown) => sendJson<DatabaseCreateResponse>("/bi/api/database", body),
	syncDatabaseSchema: (dbId: string | number) =>
		sendJson<Record<string, unknown>>(`/bi/api/database/${encodeURIComponent(String(dbId))}/sync_schema`, {}),
	updateDatabase: (id: string | number, body: unknown) =>
		requestJson<DatabaseCreateResponse>(`/bi/api/database/${encodeURIComponent(String(id))}`, "PUT", body),
	deleteDatabase: (id: string | number) =>
		requestJson<void>(`/bi/api/database/${encodeURIComponent(String(id))}`, "DELETE"),
	getDatabaseMetadata: (dbId: string | number) =>
		fetchJson<DatabaseMetadataResponse>(`/bi/api/database/${encodeURIComponent(String(dbId))}/metadata`),
	listCollections: () => fetchJson<CollectionListItem[]>("/bi/api/collection"),
	getCollectionItems: (id: string | number) =>
		fetchJson<CollectionItem[]>(`/bi/api/collection/${encodeURIComponent(String(id))}/items`),
	createCollection: (body: { name: string; parent_id?: number | null; description?: string | null }) =>
		sendJson<CollectionListItem>("/bi/api/collection", body),
	updateCollection: (id: number, body: { name?: string; parent_id?: number | null; description?: string | null }) =>
		requestJson<CollectionListItem>(`/bi/api/collection/${encodeURIComponent(String(id))}`, "PUT", body),
	deleteCollection: (id: number) => requestJson<void>(`/bi/api/collection/${encodeURIComponent(String(id))}`, "DELETE"),
	getDataPortal: () => fetchJson<DataPortalSnapshot>("/bi/api/data-portal"),
	createDataPortalDirectory: (body: { name: string; parent_id: number | null }) =>
		sendJson<DataPortalDirectoryItem>("/bi/api/data-portal/directories", body),
	updateDataPortalDirectory: (id: number, body: { name: string; parent_id: number | null }) =>
		requestJson<DataPortalDirectoryItem>(
			`/bi/api/data-portal/directories/${encodeURIComponent(String(id))}`,
			"PUT",
			body,
		),
	deleteDataPortalDirectory: (id: number) =>
		requestJson<void>(`/bi/api/data-portal/directories/${encodeURIComponent(String(id))}`, "DELETE"),
	createDataPortalBinding: (body: {
		directory_id: number;
		content_type: DataPortalContentType;
		content_id: number;
	}) => sendJson<DataPortalBindingItem>("/bi/api/data-portal/items", body),
	deleteDataPortalBinding: (id: number) =>
		requestJson<void>(`/bi/api/data-portal/items/${encodeURIComponent(String(id))}`, "DELETE"),
	listDashboards: () => fetchJson<DashboardListItem[]>("/bi/api/dashboard"),
	getDashboard: (id: string | number) =>
		fetchJson<DashboardDetail>(`/bi/api/dashboard/${encodeURIComponent(String(id))}`),
	createDashboard: (body: unknown) => sendJson<DashboardDetail>("/bi/api/dashboard", body),
	saveDashboard: (body: unknown) => sendJson<DashboardDetail>("/bi/api/dashboard/save", body),
	validateDashboardPublication: (id: string | number, audience: DashboardPublicationAudience) =>
		sendJson<DashboardPublicationValidation>(
			`/bi/api/dashboard/${encodeURIComponent(String(id))}/validate`,
			audience,
		),
	publishDashboard: (id: string | number, audience: DashboardPublicationAudience) =>
		sendJson<DashboardPublicationResult>(
			`/bi/api/dashboard/${encodeURIComponent(String(id))}/publish`,
			audience,
		),
	listDashboardVersions: (id: string | number) =>
		fetchJson<DashboardVersion[]>(`/bi/api/dashboard/${encodeURIComponent(String(id))}/versions`),
	createDashboardDraftFromVersion: (id: string | number, revisionId: string | number) =>
		sendJson<DashboardDetail>(
			`/bi/api/dashboard/${encodeURIComponent(String(id))}/versions/${encodeURIComponent(String(revisionId))}/draft`,
			{},
		),
	retryDashboardRegistration: (id: string | number) =>
		sendJson<{ queued: boolean }>(
			`/bi/api/dashboard/${encodeURIComponent(String(id))}/registration/retry`,
			{},
		),
	listDashboardParamValues: (dashId: string | number, paramId: string) =>
		fetchJson<string[]>(
			`/bi/api/dashboard/${encodeURIComponent(String(dashId))}/params/${encodeURIComponent(String(paramId))}/values`,
		),
	searchDashboardParamValues: (dashId: string | number, paramId: string, query: string) =>
		fetchJson<string[]>(
			`/bi/api/dashboard/${encodeURIComponent(String(dashId))}/params/${encodeURIComponent(String(paramId))}/search/${encodeURIComponent(String(query))}`,
		),
	listCards: (type?: string) =>
		fetchJson<CardListItem[]>(type ? `/bi/api/card?type=${encodeURIComponent(type)}` : "/bi/api/card"),
	listModels: () => fetchJson<CardListItem[]>("/bi/api/card?type=model"),
	getCard: (id: string | number) => fetchJson<CardDetail>(`/bi/api/card/${encodeURIComponent(String(id))}`),
	createCard: (body: unknown) => sendJson<CardDetail>("/bi/api/card", body),
	updateCard: (id: string | number, body: unknown) =>
		requestJson<CardDetail>(`/bi/api/card/${encodeURIComponent(String(id))}`, "PUT", body),
	deleteCard: (id: string | number) => requestJson<void>(`/bi/api/card/${encodeURIComponent(String(id))}`, "DELETE"),
	deleteDashboard: (id: string | number) =>
		requestJson<void>(`/bi/api/dashboard/${encodeURIComponent(String(id))}`, "DELETE"),
	updateDashboard: (id: string | number, body: unknown) =>
		requestJson<DashboardDetail>(`/bi/api/dashboard/${encodeURIComponent(String(id))}`, "PUT", body),
	queryCard: (id: string | number, body?: unknown) =>
		sendJson<CardQueryResponse>(`/bi/api/card/${encodeURIComponent(String(id))}/query`, body ?? {}),
	runDatasetQuery: (body: unknown) => sendJson<CardQueryResponse>("/bi/api/dataset", body),
	getSemanticMeta: (params?: {
		subjectArea?: string;
		exposedToModeler?: boolean;
		includeClassificationAbove?: string;
	}) => {
		const qs = new URLSearchParams();
		if (params?.subjectArea) {
			qs.set("subject_area", params.subjectArea);
		}
		if (params?.exposedToModeler !== undefined) {
			qs.set("exposed_to_modeler", String(params.exposedToModeler));
		}
		if (params?.includeClassificationAbove) {
			qs.set("include_classification_above", params.includeClassificationAbove);
		}
		const suffix = qs.toString() ? `?${qs.toString()}` : "";
		return fetchJson<SemanticMetaResponse>("/bi/api/semantic/meta" + suffix);
	},
	getSemanticGraph: () => fetchJson<SemanticGraphResponse>("/bi/api/semantic/graph"),
	previewSemanticSql: (body: SemanticQueryBody) =>
		sendJson<SemanticQueryResponse>("/bi/api/semantic/query/preview-sql", body),
	runSemanticQuery: (body: SemanticQueryBody) => sendJson<SemanticQueryResponse>("/bi/api/semantic/query", body),
	listSemanticVirtualDatasets: (params?: { owner?: string; workspace?: number }) => {
		const qs = new URLSearchParams();
		if (params?.owner) {
			qs.set("owner", params.owner);
		}
		if (params?.workspace !== undefined) {
			qs.set("workspace", String(params.workspace));
		}
		const suffix = qs.toString() ? `?${qs.toString()}` : "";
		return fetchJson<SemanticVirtualDataset[]>("/bi/api/semantic/virtual-datasets" + suffix);
	},
	getSemanticVirtualDataset: (id: string | number) =>
		fetchJson<SemanticVirtualDataset>("/bi/api/semantic/virtual-datasets/" + encodeURIComponent(String(id))),
	createSemanticVirtualDataset: (body: {
		name: string;
		description?: string | null;
		workspace_id?: number | null;
		state: SemanticQueryBody;
	}) => sendJson<SemanticVirtualDataset>("/bi/api/semantic/virtual-datasets", body),
	updateSemanticVirtualDataset: (
		id: string | number,
		body: { name?: string; description?: string | null; workspace_id?: number | null; state: SemanticQueryBody },
	) =>
		requestJson<SemanticVirtualDataset>(
			"/bi/api/semantic/virtual-datasets/" + encodeURIComponent(String(id)),
			"PUT",
			body,
		),
	deleteSemanticVirtualDataset: (id: string | number) =>
		requestJson<void>("/bi/api/semantic/virtual-datasets/" + encodeURIComponent(String(id)), "DELETE"),
	promoteSemanticVirtualDataset: (id: string | number) =>
		sendJson<SemanticPromoteResult>(
			"/bi/api/semantic/virtual-datasets/" + encodeURIComponent(String(id)) + "/promote",
			{},
		),
	getDatasetCacheStats: () => fetchJson<DatasetCacheStats>("/bi/api/dataset/cache/stats"),
	getDatasetCachePolicy: (databaseId: string | number) =>
		fetchJson<DatasetCachePolicy>("/bi/api/dataset/cache/policy/" + encodeURIComponent(String(databaseId))),
	setDatasetCachePolicy: (databaseId: string | number, body: unknown) =>
		sendJson<DatasetCachePolicy>("/bi/api/dataset/cache/policy/" + encodeURIComponent(String(databaseId)), body),
	warmupDatasetCache: (body: unknown) => sendJson<Record<string, unknown>>("/bi/api/dataset/cache/warmup", body),
	queryDashcard: (dashboardId: string | number, dashcardId: string | number, cardId: string | number, body?: unknown) =>
		sendJson<DashboardQueryResponse>(
			`/bi/api/dashboard/${encodeURIComponent(String(dashboardId))}/dashcard/${encodeURIComponent(String(dashcardId))}/card/${encodeURIComponent(String(cardId))}/query`,
			body ?? {},
		),
	search: (q: string) =>
		fetchJson<SearchResponse>(`/bi/api/search?q=${encodeURIComponent(String(q ?? ""))}&limit=25&offset=0`),
	listMetrics: () => fetchJson<Metric[]>("/bi/api/metric"),
	getMetric: (id: string | number) => fetchJson<Metric>("/bi/api/metric/" + encodeURIComponent(String(id))),
	createMetric: (body: Partial<Metric>) => sendJson<Metric>("/bi/api/metric", body),
	updateMetric: (id: string | number, body: Partial<Metric>) =>
		requestJson<Metric>("/bi/api/metric/" + encodeURIComponent(String(id)), "PUT", body),
	deleteMetric: (id: string | number) =>
		requestJson<void>("/bi/api/metric/" + encodeURIComponent(String(id)), "DELETE"),
	listMetricVersions: (metricId: string | number) =>
		fetchJson<string[]>("/bi/api/query-trace/metric/" + encodeURIComponent(String(metricId)) + "/versions"),
	getQueryTraceFailureSummary: (days = 7, topN = 10, chain?: string) => {
		const qs = new URLSearchParams();
		qs.set("days", String(days));
		qs.set("topN", String(topN));
		if (chain && chain.trim().length > 0) {
			qs.set("chain", chain.trim());
		}
		return fetchJson<QueryTraceFailureSummary>("/bi/api/query-trace/failure-summary?" + qs.toString());
	},
	explainCard: (cardId: string | number, body?: unknown) =>
		sendJson<ExplainabilityResponse>("/bi/api/explain/card/" + encodeURIComponent(String(cardId)), body ?? {}),
	listExploreSessions: (params?: { includeArchived?: boolean; dept?: string; projectKey?: string; limit?: number }) => {
		const qs = new URLSearchParams();
		qs.set("includeArchived", String(Boolean(params?.includeArchived)));
		if (params?.dept && params.dept.trim()) {
			qs.set("dept", params.dept.trim());
		}
		if (params?.projectKey && params.projectKey.trim()) {
			qs.set("projectKey", params.projectKey.trim());
		}
		qs.set("limit", String(params?.limit ?? 100));
		return fetchJson<ExploreSessionItem[]>("/bi/api/explore-session?" + qs.toString());
	},
	getExploreSession: (id: string | number) =>
		fetchJson<ExploreSessionItem>("/bi/api/explore-session/" + encodeURIComponent(String(id))),
	createExploreSession: (body: unknown) => sendJson<ExploreSessionItem>("/bi/api/explore-session", body ?? {}),
	updateExploreSession: (id: string | number, body: unknown) =>
		requestJson<ExploreSessionItem>("/bi/api/explore-session/" + encodeURIComponent(String(id)), "PUT", body),
	appendExploreSessionStep: (id: string | number, body: unknown) =>
		sendJson<ExploreSessionItem>("/bi/api/explore-session/" + encodeURIComponent(String(id)) + "/steps", body ?? {}),
	replayExploreSessionStep: (id: string | number, stepIndex: number) =>
		sendJson<Record<string, unknown>>("/bi/api/explore-session/" + encodeURIComponent(String(id)) + "/replay", {
			stepIndex,
		}),
	archiveExploreSession: (id: string | number) =>
		sendJson<ExploreSessionItem>("/bi/api/explore-session/" + encodeURIComponent(String(id)) + "/archive", {}),
	cloneExploreSession: (id: string | number) =>
		sendJson<ExploreSessionItem>("/bi/api/explore-session/" + encodeURIComponent(String(id)) + "/clone", {}),
	createExploreSessionPublicLink: (id: string | number) =>
		sendJson<{ uuid: string }>("/bi/api/explore-session/" + encodeURIComponent(String(id)) + "/public_link", {}),
	deleteExploreSessionPublicLink: (id: string | number) =>
		requestJson<void>("/bi/api/explore-session/" + encodeURIComponent(String(id)) + "/public_link", "DELETE"),
	getPublicExploreSession: (uuid: string) =>
		fetchJson<ExploreSessionItem>("/bi/api/explore-session/public/" + encodeURIComponent(String(uuid))),
	listReportTemplates: (limit = 100) =>
		fetchJson<ReportTemplateItem[]>("/bi/api/report-factory/templates?limit=" + encodeURIComponent(String(limit))),
	createReportTemplate: (body: unknown) => sendJson<ReportTemplateItem>("/bi/api/report-factory/templates", body ?? {}),
	updateReportTemplate: (id: string | number, body: unknown) =>
		requestJson<ReportTemplateItem>("/bi/api/report-factory/templates/" + encodeURIComponent(String(id)), "PUT", body),
	deleteReportTemplate: (id: string | number) =>
		requestJson<void>("/bi/api/report-factory/templates/" + encodeURIComponent(String(id)), "DELETE"),
	generateReportRun: (body: unknown) => sendJson<ReportRunItem>("/bi/api/report-factory/generate", body ?? {}),
	listReportRuns: (limit = 100) =>
		fetchJson<ReportRunItem[]>("/bi/api/report-factory/runs?limit=" + encodeURIComponent(String(limit))),
	getReportRun: (id: string | number) =>
		fetchJson<ReportRunItem>("/bi/api/report-factory/runs/" + encodeURIComponent(String(id))),
	getReportRunExportUrl: (id: string | number, format: "html" | "markdown" = "html") =>
		"/bi/api/report-factory/runs/" +
		encodeURIComponent(String(id)) +
		"/export?format=" +
		encodeURIComponent(String(format)),
	listMetricLens: () => fetchJson<MetricLensSummary[]>("/bi/api/metric-lens"),
	getMetricLens: (metricId: string | number) =>
		fetchJson<MetricLensDetail>("/bi/api/metric-lens/" + encodeURIComponent(String(metricId))),
	compareMetricLensVersions: (metricId: string | number, leftVersion: string, rightVersion: string) =>
		fetchJson<MetricLensCompare>(
			"/bi/api/metric-lens/" +
				encodeURIComponent(String(metricId)) +
				"/compare?leftVersion=" +
				encodeURIComponent(String(leftVersion)) +
				"&rightVersion=" +
				encodeURIComponent(String(rightVersion)),
		),
	getMetricLensConflicts: () => fetchJson<Array<Record<string, unknown>>>("/bi/api/metric-lens/conflicts"),
	listNl2SqlEvalCases: (enabledOnly = false, limit = 200) =>
		fetchJson<Nl2SqlEvalCaseItem[]>(
			"/bi/api/nl2sql-eval/cases?enabledOnly=" +
				encodeURIComponent(String(enabledOnly)) +
				"&limit=" +
				encodeURIComponent(String(limit)),
		),
	createNl2SqlEvalCase: (body: unknown) => sendJson<Nl2SqlEvalCaseItem>("/bi/api/nl2sql-eval/cases", body ?? {}),
	updateNl2SqlEvalCase: (id: string | number, body: unknown) =>
		requestJson<Nl2SqlEvalCaseItem>("/bi/api/nl2sql-eval/cases/" + encodeURIComponent(String(id)), "PUT", body),
	deleteNl2SqlEvalCase: (id: string | number) =>
		requestJson<void>("/bi/api/nl2sql-eval/cases/" + encodeURIComponent(String(id)), "DELETE"),
	runNl2SqlEvaluation: (body?: unknown) => sendJson<Nl2SqlEvalRunSummary>("/bi/api/nl2sql-eval/run", body ?? {}),
	runNl2SqlEvaluationWithGate: (body?: unknown) =>
		sendJson<Nl2SqlEvalGateRunResponse>("/bi/api/nl2sql-eval/run-gated", body ?? {}),
	listNl2SqlEvalRuns: (limit = 20) =>
		fetchJson<Nl2SqlEvalRunRecord[]>("/bi/api/nl2sql-eval/runs?limit=" + encodeURIComponent(String(limit))),
	compareNl2SqlEvalRuns: (baselineRunId: string | number, candidateRunId: string | number) =>
		fetchJson<Nl2SqlEvalCompareResponse>(
			"/bi/api/nl2sql-eval/compare?baselineRunId=" +
				encodeURIComponent(String(baselineRunId)) +
				"&candidateRunId=" +
				encodeURIComponent(String(candidateRunId)),
		),
	listPlatformMetrics: () => fetchJson<PlatformMetric[]>("/bi/api/platform/metrics"),
	listVisibleTables: () => fetchJson<Array<number | VisibleTable>>("/bi/api/platform/visible-tables"),
	getTrash: () => fetchJson<TrashResponse>("/bi/api/trash"),
	createCardPublicLink: (id: string | number) =>
		sendJson<{ uuid: string }>(`/bi/api/card/${encodeURIComponent(String(id))}/public_link`, {}),
	deleteCardPublicLink: (id: string | number) =>
		requestJson<void>(`/bi/api/card/${encodeURIComponent(String(id))}/public_link`, "DELETE"),
	createDashboardPublicLink: (id: string | number) =>
		sendJson<{ uuid: string }>(`/bi/api/dashboard/${encodeURIComponent(String(id))}/public_link`, {}),
	deleteDashboardPublicLink: (id: string | number) =>
		requestJson<void>(`/bi/api/dashboard/${encodeURIComponent(String(id))}/public_link`, "DELETE"),
	getPublicCard: (uuid: string) => fetchJson<PublicCardDetail>(`/bi/api/public/card/${encodeURIComponent(uuid)}`),
	queryPublicCard: (uuid: string, body?: unknown) =>
		sendJson<CardQueryResponse>(`/bi/api/public/card/${encodeURIComponent(uuid)}/query`, body ?? {}),
	getPublicDashboard: (uuid: string) =>
		fetchJson<PublicDashboardDetail>(`/bi/api/public/dashboard/${encodeURIComponent(uuid)}`),
	queryPublicDashboardDashcard: (uuid: string, dashcardId: string | number, cardId: string | number, body?: unknown) =>
		sendJson<DashboardQueryResponse>(
			`/bi/api/public/dashboard/${encodeURIComponent(uuid)}/dashcard/${encodeURIComponent(String(dashcardId))}/card/${encodeURIComponent(String(cardId))}/query`,
			body ?? {},
		),

	// Screen Designer API
	listScreenPlugins: () => fetchJson<ScreenPluginManifest[]>("/bi/api/screen-plugins"),
	validateScreenPlugin: (body: unknown) =>
		sendJson<ScreenPluginValidationResult>("/bi/api/screen-plugins/validate", body),
	exportScreenIndustryPack: (body?: unknown) => sendJson<ScreenIndustryPack>("/bi/api/screen-packs/export", body ?? {}),
	importScreenIndustryPack: (body: unknown) =>
		sendJson<ScreenIndustryPackImportResult>("/bi/api/screen-packs/import", body),
	getScreenIndustryPackPresets: () => fetchJson<ScreenIndustryPackPresets>("/bi/api/screen-packs/presets"),
	validateScreenIndustryPack: (body: unknown) =>
		sendJson<ScreenIndustryPackValidationResult>("/bi/api/screen-packs/validate", body),
	listScreenIndustryPackAudit: (limit = 100) =>
		fetchJson<ScreenIndustryPackAuditRow[]>("/bi/api/screen-packs/audit?limit=" + encodeURIComponent(String(limit))),
	generateScreenIndustryConnectorPlan: (body?: unknown) =>
		sendJson<ScreenIndustryConnectorPlan>("/bi/api/screen-packs/connectors/plan", body ?? {}),
	probeScreenIndustryConnectors: (body?: unknown) =>
		sendJson<ScreenIndustryConnectorProbe>("/bi/api/screen-packs/connectors/probe", body ?? {}),
	getScreenIndustryOpsHealth: (deploymentMode?: string, includeRuntime = false) => {
		const qs = new URLSearchParams();
		if (deploymentMode && String(deploymentMode).trim().length > 0) {
			qs.set("deploymentMode", String(deploymentMode));
		}
		if (includeRuntime) {
			qs.set("includeRuntime", "true");
		}
		const query = qs.toString();
		const suffix = query.length > 0 ? `?${query}` : "";
		return fetchJson<ScreenIndustryOpsHealth>("/bi/api/screen-packs/ops/health" + suffix);
	},
	probeScreenIndustryRuntime: (body?: unknown) =>
		sendJson<ScreenIndustryRuntimeProbe>("/bi/api/screen-packs/ops/runtime-probe", body ?? {}),
	getScreenCompliancePolicy: () => fetchJson<ScreenCompliancePolicy>("/bi/api/screen-compliance/policy"),
	updateScreenCompliancePolicy: (body: unknown) =>
		requestJson<ScreenCompliancePolicy>("/bi/api/screen-compliance/policy", "PUT", body),
	getScreenComplianceReport: (query?: ScreenComplianceReportQuery) => {
		const qs = new URLSearchParams();
		if (query?.screenId !== undefined && query?.screenId !== null && String(query.screenId).trim() !== "") {
			qs.set("screenId", String(query.screenId));
		}
		if (query?.days !== undefined) {
			qs.set("days", String(query.days));
		}
		if (query?.limit !== undefined) {
			qs.set("limit", String(query.limit));
		}
		const suffix = qs.toString();
		const url = suffix.length > 0 ? "/bi/api/screen-compliance/report?" + suffix : "/bi/api/screen-compliance/report";
		return fetchJson<ScreenComplianceReport>(url);
	},
	generateScreenSpec: (body: ScreenAiGenerationRequest) =>
		sendJson<ScreenAiGenerationResponse>("/bi/api/screens/ai/generate", body),
	reviseScreenSpec: (body: ScreenAiRevisionRequest) =>
		sendJson<ScreenAiGenerationResponse>("/bi/api/screens/ai/revise", body),
	listScreens: (params?: { domainId?: string; domainUnassigned?: boolean; publishedOnly?: boolean }) => {
		const qs = new URLSearchParams();
		if (params?.domainId) qs.set("domainId", params.domainId);
		if (params?.domainUnassigned) qs.set("domainUnassigned", "true");
		if (params?.publishedOnly) qs.set("publishedOnly", "true");
		const query = qs.toString();
		return fetchJson<ScreenListItem[]>(query ? "/bi/api/screens?" + query : "/bi/api/screens");
	},
	listScreenTemplates: (params?: {
		q?: string;
		category?: string;
		tag?: string;
		visibility?: string;
		listed?: boolean;
	}) => {
		const qs = new URLSearchParams();
		if (params?.q) qs.set("q", String(params.q));
		if (params?.category) qs.set("category", String(params.category));
		if (params?.tag) qs.set("tag", String(params.tag));
		if (params?.visibility) qs.set("visibility", String(params.visibility));
		if (typeof params?.listed === "boolean") qs.set("listed", String(params.listed));
		const query = qs.toString();
		const url = query.length > 0 ? "/bi/api/screen-templates?" + query : "/bi/api/screen-templates";
		return fetchJson<ScreenTemplateItem[]>(url);
	},
	getScreenTemplate: (id: string | number) =>
		fetchJson<ScreenTemplateItem>("/bi/api/screen-templates/" + encodeURIComponent(String(id))),
	createScreenTemplate: (body: unknown) => sendJson<ScreenTemplateItem>("/bi/api/screen-templates", body),
	createScreenTemplateFromScreen: (screenId: string | number, body?: unknown) =>
		sendJson<ScreenTemplateItem>(
			"/bi/api/screen-templates/from-screen/" + encodeURIComponent(String(screenId)),
			body ?? {},
		),
	updateScreenTemplate: (id: string | number, body: unknown) =>
		requestJson<ScreenTemplateItem>("/bi/api/screen-templates/" + encodeURIComponent(String(id)), "PUT", body),
	updateScreenTemplateListing: (id: string | number, listed: boolean) =>
		requestJson<ScreenTemplateItem>("/bi/api/screen-templates/" + encodeURIComponent(String(id)) + "/listing", "PUT", {
			listed,
		}),
	listScreenTemplateVersions: (id: string | number, limit = 50) =>
		fetchJson<ScreenTemplateVersionItem[]>(
			"/bi/api/screen-templates/" +
				encodeURIComponent(String(id)) +
				"/versions?limit=" +
				encodeURIComponent(String(limit)),
		),
	restoreScreenTemplateVersion: (id: string | number, versionNo: number) =>
		sendJson<ScreenTemplateItem>(
			"/bi/api/screen-templates/" +
				encodeURIComponent(String(id)) +
				"/restore/" +
				encodeURIComponent(String(versionNo)),
			{},
		),
	deleteScreenTemplate: (id: string | number) =>
		requestJson<void>("/bi/api/screen-templates/" + encodeURIComponent(String(id)), "DELETE"),
	createScreenFromTemplate: (id: string | number, body?: unknown) =>
		sendJson<ScreenDetail>("/bi/api/screen-templates/" + encodeURIComponent(String(id)) + "/create-screen", body ?? {}),
	getScreen: (
		id: string | number,
		options?: { mode?: "draft" | "published" | "preview" | string; fallbackDraft?: boolean },
	) => {
		const params = new URLSearchParams();
		if (options?.mode) params.set("mode", String(options.mode));
		if (options?.fallbackDraft !== undefined) params.set("fallbackDraft", String(options.fallbackDraft));
		const qs = params.toString();
		const base = `/bi/api/screens/${encodeURIComponent(String(id))}`;
		return fetchJson<ScreenDetail>(qs ? `${base}?${qs}` : base);
	},
	getScreenHealth: (id: string | number) =>
		fetchJson<ScreenHealthReport>(`/bi/api/screens/${encodeURIComponent(String(id))}/health`),
	prepareScreenExport: (id: string | number, body?: ScreenExportPrepareRequest) =>
		sendJson<ScreenExportPrepareResult>(`/bi/api/screens/${encodeURIComponent(String(id))}/export-prepare`, body ?? {}),
	reportScreenExport: (id: string | number, body: ScreenExportReportRequest) =>
		sendJson<ScreenExportReportResult>(`/bi/api/screens/${encodeURIComponent(String(id))}/export-report`, body),
	renderScreenExport: (id: string | number, body?: ScreenExportRenderRequest) =>
		requestBinary(`/bi/api/screens/${encodeURIComponent(String(id))}/export-render`, "POST", body ?? {}),
	validateScreenSpec: (body: unknown) => sendJson<ScreenSpecValidationResponse>("/bi/api/screens/validate-spec", body),
	createScreen: (body: ScreenWritePayload) => sendJson<ScreenDetail>("/bi/api/screens", body),
	listScreenVersions: (id: string | number) =>
		fetchJson<ScreenVersion[]>(`/bi/api/screens/${encodeURIComponent(String(id))}/versions`),
	compareScreenVersions: (id: string | number, fromVersionId: string | number, toVersionId: string | number) =>
		fetchJson<ScreenVersionDiff>(
			`/bi/api/screens/${encodeURIComponent(String(id))}/versions/compare` +
				`?fromVersionId=${encodeURIComponent(String(fromVersionId))}` +
				`&toVersionId=${encodeURIComponent(String(toVersionId))}`,
		),
	publishScreen: (id: string | number) =>
		sendJson<{ screen: ScreenDetail; version: ScreenVersion; warmup?: ScreenWarmupSummary }>(
			`/bi/api/screens/${encodeURIComponent(String(id))}/publish`,
			{},
		),
	rollbackScreenVersion: (id: string | number, versionId: string | number) =>
		sendJson<{ screen: ScreenDetail; version: ScreenVersion; warmup?: ScreenWarmupSummary }>(
			`/bi/api/screens/${encodeURIComponent(String(id))}/rollback/${encodeURIComponent(String(versionId))}`,
			{},
		),
	updateScreen: (id: string | number, body: ScreenWritePayload) =>
		requestJson<ScreenDetail>(`/bi/api/screens/${encodeURIComponent(String(id))}`, "PUT", body),
	updateScreenDomain: (id: string | number, domainId?: string | null) =>
		requestJson<ScreenDetail>(`/bi/api/screens/${encodeURIComponent(String(id))}`, "PUT", { domainId: domainId || null }),
	deleteScreen: (id: string | number) =>
		requestJson<void>(`/bi/api/screens/${encodeURIComponent(String(id))}`, "DELETE"),
	getScreenAcl: async (id: string | number): Promise<ScreenAclEntry[]> => {
		type PlatformGrant = {
			id?: number;
			granteeType?: string;
			granteeId?: string;
			granteeName?: string;
			granteeUsername?: string;
			granteeLabel?: string;
			displayName?: string;
			permission?: string;
			levelOverride?: boolean;
			grantedBy?: string;
			grantedAt?: string;
			createdDate?: string;
			lastModifiedDate?: string;
		};
		const grants = await fetchJson<PlatformGrant[]>(`/bi/api/screens/${encodeURIComponent(String(id))}/grants`);
		return (grants || []).map((g) => ({
			id: g.id,
			screenId: id,
			subjectType: (g.granteeType === "ROLE" ? "ROLE" : "USER") as ScreenAclEntry["subjectType"],
			subjectId: g.granteeId || "",
			subjectName: g.granteeName || g.displayName || g.granteeLabel,
			subjectUsername: g.granteeUsername,
			perm: (g.permission === "OWNER"
				? "OWNER"
				: g.permission === "MANAGER" || g.permission === "EDIT"
					? "MANAGE"
					: "READ") as ScreenAclEntry["perm"],
			levelOverride: g.levelOverride === true,
			createdAt: g.createdDate || g.grantedAt,
			updatedAt: g.lastModifiedDate,
		}));
	},
	addScreenGrant: (
		id: string | number,
		body: { granteeType: string; granteeId: string; permission: string; levelOverride?: boolean },
	) => requestJson<Record<string, unknown>>(`/bi/api/screens/${encodeURIComponent(String(id))}/grants`, "PUT", body),
	/**
	 * 设置大屏人工密级下限。有效密级由所有展示数据的最高密级与人工下限共同派生，
	 * 只能升高，不能降低。
	 */
	updateScreenClassification: (id: string | number, classification: string) =>
		requestJson<{ classification: string; manualClassificationFloor?: string; changed: boolean }>(
			`/bi/api/screens/${encodeURIComponent(String(id))}/classification`,
			"PATCH",
			{ classification },
		),
	revokeScreenGrant: (screenId: string | number, grantId: string | number) =>
		requestJson<void>(
			`/bi/api/screens/${encodeURIComponent(String(screenId))}/grants/${encodeURIComponent(String(grantId))}`,
			"DELETE",
		),
	/**
	 * Sprint-24 F4：大屏密级合规盘点。返回所有 archived=false 且 classification
	 * 为 null/空的大屏。允许角色：superuser / OP_ADMIN / 所级或部门数据管理员 /
	 * 所级或部门领导（与后端 SCREEN_AUDITOR_ROLES 对齐），其它角色 403。
	 * 端点本身写一条 screen.compliance.audit_unclassified 审计到 dts-admin。
	 */
	listUnclassifiedScreens: () =>
		fetchJson<{
			count: number;
			items: Array<{
				id: number | string;
				name?: string | null;
				creatorId?: number | null;
				creatorEmail?: string | null;
				creatorPlatformUsername?: string | null;
				createdAt?: string | null;
			}>;
		}>("/bi/api/screens/admin/unclassified"),
	getScreenEditLock: (id: string | number) =>
		fetchJson<ScreenEditLock>(`/bi/api/screens/${encodeURIComponent(String(id))}/edit-lock`),
	acquireScreenEditLock: (id: string | number, body?: { ttlSeconds?: number; forceTakeover?: boolean }) =>
		sendJson<ScreenEditLock>(`/bi/api/screens/${encodeURIComponent(String(id))}/edit-lock/acquire`, body ?? {}),
	heartbeatScreenEditLock: (id: string | number, body?: { ttlSeconds?: number }) =>
		sendJson<ScreenEditLock>(`/bi/api/screens/${encodeURIComponent(String(id))}/edit-lock/heartbeat`, body ?? {}),
	releaseScreenEditLock: (id: string | number) =>
		sendJson<ScreenEditLock>(`/bi/api/screens/${encodeURIComponent(String(id))}/edit-lock/release`, {}),
	getScreenAuditLogs: (id: string | number, limit = 200) =>
		fetchJson<ScreenAuditEntry[]>(
			`/bi/api/screens/${encodeURIComponent(String(id))}/audit?limit=${encodeURIComponent(String(limit))}`,
		),
	listScreenComments: (id: string | number, limit = 200) =>
		fetchJson<ScreenComment[]>(
			`/bi/api/screens/${encodeURIComponent(String(id))}/comments?limit=${encodeURIComponent(String(limit))}`,
		),
	listScreenCommentChanges: (id: string | number, sinceId = 0, limit = 200) =>
		fetchJson<ScreenCommentChanges>(
			`/bi/api/screens/${encodeURIComponent(String(id))}/comments/changes` +
				`?sinceId=${encodeURIComponent(String(sinceId))}` +
				`&limit=${encodeURIComponent(String(limit))}`,
		),
	listScreenCommentChangesLive: (id: string | number, sinceId = 0, limit = 200, waitMs = 12000) =>
		fetchJson<ScreenCommentChanges>(
			`/bi/api/screens/${encodeURIComponent(String(id))}/comments/live` +
				`?sinceId=${encodeURIComponent(String(sinceId))}` +
				`&limit=${encodeURIComponent(String(limit))}` +
				`&waitMs=${encodeURIComponent(String(waitMs))}`,
		),
	getScreenCollaborationPresence: (id: string | number, ttlSeconds = 45, sessionId?: string) =>
		fetchJson<ScreenCollaborationPresence>(
			`/bi/api/screens/${encodeURIComponent(String(id))}/collaboration/presence` +
				`?ttlSeconds=${encodeURIComponent(String(ttlSeconds))}` +
				`${sessionId ? `&sessionId=${encodeURIComponent(sessionId)}` : ""}`,
		),
	heartbeatScreenCollaborationPresence: (
		id: string | number,
		body: {
			sessionId?: string;
			componentId?: string | null;
			typing?: boolean;
			clientType?: string;
			selectedIds?: string[];
		},
		ttlSeconds = 45,
	) =>
		sendJson<ScreenCollaborationPresence>(
			`/bi/api/screens/${encodeURIComponent(String(id))}/collaboration/presence/heartbeat` +
				`?ttlSeconds=${encodeURIComponent(String(ttlSeconds))}`,
			body ?? {},
		),
	leaveScreenCollaborationPresence: (
		id: string | number,
		body?: {
			sessionId?: string;
		},
		ttlSeconds = 45,
	) =>
		sendJson<ScreenCollaborationPresence>(
			`/bi/api/screens/${encodeURIComponent(String(id))}/collaboration/presence/leave` +
				`?ttlSeconds=${encodeURIComponent(String(ttlSeconds))}`,
			body ?? {},
		),
	createScreenComment: (
		id: string | number,
		body: {
			message: string;
			componentId?: string | null;
			anchor?: Record<string, unknown>;
			mentions?: Array<Record<string, unknown>>;
		},
	) => sendJson<ScreenComment>(`/bi/api/screens/${encodeURIComponent(String(id))}/comments`, body),
	resolveScreenComment: (id: string | number, commentId: string | number, body?: { note?: string }) =>
		sendJson<ScreenComment>(
			`/bi/api/screens/${encodeURIComponent(String(id))}/comments/${encodeURIComponent(String(commentId))}/resolve`,
			body ?? {},
		),
	reopenScreenComment: (id: string | number, commentId: string | number, body?: { note?: string }) =>
		sendJson<ScreenComment>(
			`/bi/api/screens/${encodeURIComponent(String(id))}/comments/${encodeURIComponent(String(commentId))}/reopen`,
			body ?? {},
		),
	createScreenPublicLink: (id: string | number, body?: unknown) =>
		sendJson<ScreenPublicLinkPolicy>(`/bi/api/screens/${encodeURIComponent(String(id))}/public_link`, body ?? {}),
	updateScreenPublicLinkPolicy: (id: string | number, body: unknown) =>
		requestJson<ScreenPublicLinkPolicy>(
			`/bi/api/screens/${encodeURIComponent(String(id))}/public_link/policy`,
			"PUT",
			body,
		),
	deleteScreenPublicLink: (id: string | number) =>
		requestJson<void>(`/bi/api/screens/${encodeURIComponent(String(id))}/public_link`, "DELETE"),
	getPublicScreen: (uuid: string) => fetchJson<PublicScreenDetail>(`/bi/api/public/screen/${encodeURIComponent(uuid)}`),
	// Marketplace API
	listMarketplaceComponents: (params?: { search?: string; category?: string }) => {
		const qs = new URLSearchParams();
		if (params?.search) qs.set("search", params.search);
		if (params?.category) qs.set("category", params.category);
		const suffix = qs.toString() ? `?${qs.toString()}` : "";
		return fetchJson<MarketplaceCatalogItem[]>(`/bi/api/marketplace/components${suffix}`);
	},
	listMarketplaceTemplates: (params?: { search?: string; category?: string }) => {
		const qs = new URLSearchParams();
		if (params?.search) qs.set("search", params.search);
		if (params?.category) qs.set("category", params.category);
		const suffix = qs.toString() ? `?${qs.toString()}` : "";
		return fetchJson<MarketplaceCatalogItem[]>(`/bi/api/marketplace/templates${suffix}`);
	},
	installMarketplaceComponent: (id: string) =>
		sendJson<MarketplaceCatalogItem>(`/bi/api/marketplace/components/${encodeURIComponent(id)}/install`, {}),
	installMarketplaceTemplate: (id: string) =>
		sendJson<MarketplaceCatalogItem>(`/bi/api/marketplace/templates/${encodeURIComponent(id)}/install`, {}),
	// Upload table data
	uploadTable: (
		dbId: number | string,
		body: {
			tableName: string;
			columns: Array<{ name: string; displayName: string; type: string }>;
			rows: unknown[][];
		},
	) => sendJson<{ tableName: string; schema: string; rowCount: number }>(`/bi/api/database/${dbId}/upload-table`, body),
	listMyUploads: (dbId: number | string) =>
		fetchJson<MyUploadItem[]>(`/bi/api/database/${encodeURIComponent(String(dbId))}/my-uploads`),
	deleteUploadTable: (dbId: number | string, tableName: string) =>
		requestJson<void>(
			`/bi/api/database/${encodeURIComponent(String(dbId))}/upload-table/${encodeURIComponent(tableName)}`,
			"DELETE",
		),
};
