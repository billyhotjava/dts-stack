export type CurrentUser = {
	id?: number | string;
	email?: string;
	first_name?: string;
	last_name?: string;
	common_name?: string;
};

export type CollectionListItem = {
	id: number | "root";
	name?: string;
	description?: string | null;
	archived?: boolean;
	location?: string | null;
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
};

export type DashboardDetail = DashboardListItem & {
	dashcards?: unknown[];
	parameters?: unknown[];
	ordered_cards?: DashboardCard[];
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
	data?: {
		rows?: unknown[];
		cols?: Array<Record<string, unknown>>;
		native_form?: { query?: string };
		results_timezone?: string;
		results_metadata?: { columns?: unknown[] };
	};
};

export type DashboardQueryResponse = CardQueryResponse;

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
	publishedVersionNo?: number | null;
	publishedAt?: string | null;
	canRead?: boolean;
	canEdit?: boolean;
	canPublish?: boolean;
	canManage?: boolean;
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
	queryRecommendations?: Array<{
		id?: string;
		purpose?: string;
		mode?: string;
		domain?: string;
		timeRange?: string;
		granularity?: string;
		dimensions?: string[];
		metrics?: string[];
		filters?: string[];
		sqlHint?: string;
	}>;
	vizRecommendations?: Array<{
		queryId?: string;
		componentType?: string;
		title?: string;
	}>;
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
		globalVariables?: Array<{ key: string; label?: string; type?: string; defaultValue?: string; description?: string }>;
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
	icon?: string;
	baseType?: string;
	defaultWidth?: number;
	defaultHeight?: number;
	defaultConfig?: Record<string, unknown>;
	propertySchema?: Record<string, unknown>;
	dataContract?: Record<string, unknown>;
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
	signatureRequired?: boolean;
	components?: ScreenPluginComponent[];
	dataSources?: ScreenPluginDataSource[];
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
	perm: "READ" | "EDIT" | "PUBLISH" | "MANAGE";
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
		globalVariables?: Array<{ key: string; label?: string; type?: string; defaultValue?: string; description?: string }>;
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

import { getPlatformTokens, refreshPlatformAccessToken } from "./platformSession";

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

export class AuthError extends HttpError { }

async function apiFetch(url: string, init: RequestInit, allowRefresh: boolean): Promise<Response> {
	const tokens = getPlatformTokens();
	const headers = new Headers(init.headers ?? {});
	if (!headers.has("accept")) headers.set("accept", "application/json");
	if (tokens.accessToken && !headers.has("authorization")) {
		headers.set("authorization", `Bearer ${tokens.accessToken}`);
	}

	const response = await fetch(url, { ...init, credentials: "include", headers });
	if (response.status !== 401 || !allowRefresh) {
		return response;
	}

	if (!tokens.refreshToken) {
		return response;
	}

	const refreshed = await refreshPlatformAccessToken(tokens.refreshToken);
	if (!refreshed?.accessToken) {
		return response;
	}

	const retryHeaders = new Headers(init.headers ?? {});
	if (!retryHeaders.has("accept")) retryHeaders.set("accept", "application/json");
	retryHeaders.set("authorization", `Bearer ${refreshed.accessToken}`);
	return await fetch(url, { ...init, credentials: "include", headers: retryHeaders });
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

async function requestJson<T>(url: string, method: "POST" | "PUT" | "DELETE", body?: unknown): Promise<T> {
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

export const analyticsApi = {
	getCurrentUser: () => fetchJson<CurrentUser>("/analytics/api/user/current"),
	getHealth: () => fetchJson<{ status?: string }>("/analytics/api/health"),
	listDatabases: () => fetchJson<DatabaseListResponse>("/analytics/api/database"),
	listPlatformDataSources: () => fetchJson<PlatformDataSourceItem[]>("/analytics/api/platform/data-sources"),
	listTables: (dbId: string | number) =>
		fetchJson<TableSummary[]>(`/analytics/api/table?db_id=${encodeURIComponent(String(dbId))}`),
	getTable: (tableId: string | number) =>
		fetchJson<TableDetail>(`/analytics/api/table/${encodeURIComponent(String(tableId))}`),
	getField: (fieldId: string | number) =>
		fetchJson<FieldDetail>(`/analytics/api/field/${encodeURIComponent(String(fieldId))}`),
	getFieldValues: (fieldId: string | number) =>
		fetchJson<FieldValuesResponse>(`/analytics/api/field/${encodeURIComponent(String(fieldId))}/values`),
	validateDatabase: (body: unknown) => sendJson<DatabaseValidateResponse>("/analytics/api/database/validate", body),
	createDatabase: (body: unknown) => sendJson<DatabaseCreateResponse>("/analytics/api/database", body),
	syncDatabaseSchema: (dbId: string | number) =>
		sendJson<Record<string, unknown>>(`/analytics/api/database/${encodeURIComponent(String(dbId))}/sync_schema`, {}),
	updateDatabase: (id: string | number, body: unknown) =>
		requestJson<DatabaseCreateResponse>(`/analytics/api/database/${encodeURIComponent(String(id))}`, "PUT", body),
	deleteDatabase: (id: string | number) =>
		requestJson<void>(`/analytics/api/database/${encodeURIComponent(String(id))}`, "DELETE"),
	getDatabaseMetadata: (dbId: string | number) =>
		fetchJson<DatabaseMetadataResponse>(`/analytics/api/database/${encodeURIComponent(String(dbId))}/metadata`),
	listCollections: () => fetchJson<CollectionListItem[]>("/analytics/api/collection"),
	getCollectionItems: (id: string | number) =>
		fetchJson<CollectionItem[]>(`/analytics/api/collection/${encodeURIComponent(String(id))}/items`),
	listDashboards: () => fetchJson<DashboardListItem[]>("/analytics/api/dashboard"),
	getDashboard: (id: string | number) => fetchJson<DashboardDetail>(`/analytics/api/dashboard/${encodeURIComponent(String(id))}`),
	createDashboard: (body: unknown) => sendJson<DashboardDetail>("/analytics/api/dashboard", body),
	saveDashboard: (body: unknown) => sendJson<DashboardDetail>("/analytics/api/dashboard/save", body),
	listDashboardParamValues: (dashId: string | number, paramId: string) =>
		fetchJson<string[]>(
			`/analytics/api/dashboard/${encodeURIComponent(String(dashId))}/params/${encodeURIComponent(String(paramId))}/values`,
		),
	searchDashboardParamValues: (dashId: string | number, paramId: string, query: string) =>
		fetchJson<string[]>(
			`/analytics/api/dashboard/${encodeURIComponent(String(dashId))}/params/${encodeURIComponent(String(paramId))}/search/${encodeURIComponent(String(query))}`,
		),
	listCards: () => fetchJson<CardListItem[]>("/analytics/api/card"),
	getCard: (id: string | number) => fetchJson<CardDetail>(`/analytics/api/card/${encodeURIComponent(String(id))}`),
	createCard: (body: unknown) => sendJson<CardDetail>("/analytics/api/card", body),
	updateCard: (id: string | number, body: unknown) =>
		requestJson<CardDetail>(`/analytics/api/card/${encodeURIComponent(String(id))}`, "PUT", body),
	queryCard: (id: string | number, body?: unknown) =>
		sendJson<CardQueryResponse>(`/analytics/api/card/${encodeURIComponent(String(id))}/query`, body ?? {}),
	runDatasetQuery: (body: unknown) => sendJson<CardQueryResponse>("/analytics/api/dataset", body),
	getDatasetCacheStats: () => fetchJson<DatasetCacheStats>("/analytics/api/dataset/cache/stats"),
	getDatasetCachePolicy: (databaseId: string | number) =>
		fetchJson<DatasetCachePolicy>("/analytics/api/dataset/cache/policy/" + encodeURIComponent(String(databaseId))),
	setDatasetCachePolicy: (databaseId: string | number, body: unknown) =>
		sendJson<DatasetCachePolicy>("/analytics/api/dataset/cache/policy/" + encodeURIComponent(String(databaseId)), body),
	warmupDatasetCache: (body: unknown) => sendJson<Record<string, unknown>>("/analytics/api/dataset/cache/warmup", body),
	queryDashcard: (dashboardId: string | number, dashcardId: string | number, cardId: string | number, body?: unknown) =>
		sendJson<DashboardQueryResponse>(
			`/analytics/api/dashboard/${encodeURIComponent(String(dashboardId))}/dashcard/${encodeURIComponent(String(dashcardId))}/card/${encodeURIComponent(String(cardId))}/query`,
			body ?? {},
		),
	search: (q: string) =>
		fetchJson<SearchResponse>(`/analytics/api/search?q=${encodeURIComponent(String(q ?? ""))}&limit=25&offset=0`),
	listMetrics: () => fetchJson<Metric[]>("/analytics/api/metric"),
	listMetricVersions: (metricId: string | number) =>
		fetchJson<string[]>("/analytics/api/query-trace/metric/" + encodeURIComponent(String(metricId)) + "/versions"),
	listPlatformMetrics: () => fetchJson<PlatformMetric[]>("/analytics/api/platform/metrics"),
	listVisibleTables: () => fetchJson<Array<number | VisibleTable>>("/analytics/api/platform/visible-tables"),
	getTrash: () => fetchJson<TrashResponse>("/analytics/api/trash"),
	createCardPublicLink: (id: string | number) =>
		sendJson<{ uuid: string }>(`/analytics/api/card/${encodeURIComponent(String(id))}/public_link`, {}),
	deleteCardPublicLink: (id: string | number) =>
		requestJson<void>(`/analytics/api/card/${encodeURIComponent(String(id))}/public_link`, "DELETE"),
	createDashboardPublicLink: (id: string | number) =>
		sendJson<{ uuid: string }>(`/analytics/api/dashboard/${encodeURIComponent(String(id))}/public_link`, {}),
	deleteDashboardPublicLink: (id: string | number) =>
		requestJson<void>(`/analytics/api/dashboard/${encodeURIComponent(String(id))}/public_link`, "DELETE"),
	getPublicCard: (uuid: string) => fetchJson<PublicCardDetail>(`/analytics/api/public/card/${encodeURIComponent(uuid)}`),
	queryPublicCard: (uuid: string, body?: unknown) =>
		sendJson<CardQueryResponse>(`/analytics/api/public/card/${encodeURIComponent(uuid)}/query`, body ?? {}),
	getPublicDashboard: (uuid: string) =>
		fetchJson<PublicDashboardDetail>(`/analytics/api/public/dashboard/${encodeURIComponent(uuid)}`),
	queryPublicDashboardDashcard: (uuid: string, dashcardId: string | number, cardId: string | number, body?: unknown) =>
		sendJson<DashboardQueryResponse>(
			`/analytics/api/public/dashboard/${encodeURIComponent(uuid)}/dashcard/${encodeURIComponent(String(dashcardId))}/card/${encodeURIComponent(String(cardId))}/query`,
			body ?? {},
		),

	// Screen Designer API
	listScreenPlugins: () => fetchJson<ScreenPluginManifest[]>("/analytics/api/screen-plugins"),
	validateScreenPlugin: (body: unknown) =>
		sendJson<ScreenPluginValidationResult>("/analytics/api/screen-plugins/validate", body),
	exportScreenIndustryPack: (body?: unknown) =>
		sendJson<ScreenIndustryPack>("/analytics/api/screen-packs/export", body ?? {}),
	importScreenIndustryPack: (body: unknown) =>
		sendJson<ScreenIndustryPackImportResult>("/analytics/api/screen-packs/import", body),
	getScreenIndustryPackPresets: () =>
		fetchJson<ScreenIndustryPackPresets>("/analytics/api/screen-packs/presets"),
	validateScreenIndustryPack: (body: unknown) =>
		sendJson<ScreenIndustryPackValidationResult>("/analytics/api/screen-packs/validate", body),
	listScreenIndustryPackAudit: (limit = 100) =>
		fetchJson<ScreenIndustryPackAuditRow[]>(
			"/analytics/api/screen-packs/audit?limit=" + encodeURIComponent(String(limit)),
		),
	generateScreenIndustryConnectorPlan: (body?: unknown) =>
		sendJson<ScreenIndustryConnectorPlan>("/analytics/api/screen-packs/connectors/plan", body ?? {}),
	probeScreenIndustryConnectors: (body?: unknown) =>
		sendJson<ScreenIndustryConnectorProbe>("/analytics/api/screen-packs/connectors/probe", body ?? {}),
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
		return fetchJson<ScreenIndustryOpsHealth>("/analytics/api/screen-packs/ops/health" + suffix);
	},
	probeScreenIndustryRuntime: (body?: unknown) =>
		sendJson<ScreenIndustryRuntimeProbe>("/analytics/api/screen-packs/ops/runtime-probe", body ?? {}),
	getScreenCompliancePolicy: () =>
		fetchJson<ScreenCompliancePolicy>("/analytics/api/screen-compliance/policy"),
	updateScreenCompliancePolicy: (body: unknown) =>
		requestJson<ScreenCompliancePolicy>("/analytics/api/screen-compliance/policy", "PUT", body),
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
		const url = suffix.length > 0
			? "/analytics/api/screen-compliance/report?" + suffix
			: "/analytics/api/screen-compliance/report";
		return fetchJson<ScreenComplianceReport>(url);
	},
	generateScreenSpec: (body: ScreenAiGenerationRequest) =>
		sendJson<ScreenAiGenerationResponse>("/analytics/api/screens/ai/generate", body),
	reviseScreenSpec: (body: ScreenAiRevisionRequest) =>
		sendJson<ScreenAiGenerationResponse>("/analytics/api/screens/ai/revise", body),
	listScreens: () => fetchJson<ScreenListItem[]>("/analytics/api/screens"),
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
		const url = query.length > 0 ? "/analytics/api/screen-templates?" + query : "/analytics/api/screen-templates";
		return fetchJson<ScreenTemplateItem[]>(url);
	},
	getScreenTemplate: (id: string | number) =>
		fetchJson<ScreenTemplateItem>("/analytics/api/screen-templates/" + encodeURIComponent(String(id))),
	createScreenTemplate: (body: unknown) =>
		sendJson<ScreenTemplateItem>("/analytics/api/screen-templates", body),
	createScreenTemplateFromScreen: (screenId: string | number, body?: unknown) =>
		sendJson<ScreenTemplateItem>("/analytics/api/screen-templates/from-screen/" + encodeURIComponent(String(screenId)), body ?? {}),
	updateScreenTemplate: (id: string | number, body: unknown) =>
		requestJson<ScreenTemplateItem>("/analytics/api/screen-templates/" + encodeURIComponent(String(id)), "PUT", body),
	updateScreenTemplateListing: (id: string | number, listed: boolean) =>
		requestJson<ScreenTemplateItem>(
			"/analytics/api/screen-templates/" + encodeURIComponent(String(id)) + "/listing",
			"PUT",
			{ listed },
		),
	listScreenTemplateVersions: (id: string | number, limit = 50) =>
		fetchJson<ScreenTemplateVersionItem[]>(
			"/analytics/api/screen-templates/" + encodeURIComponent(String(id)) + "/versions?limit=" + encodeURIComponent(String(limit)),
		),
	restoreScreenTemplateVersion: (id: string | number, versionNo: number) =>
		sendJson<ScreenTemplateItem>(
			"/analytics/api/screen-templates/"
				+ encodeURIComponent(String(id))
				+ "/restore/"
				+ encodeURIComponent(String(versionNo)),
			{},
		),
	deleteScreenTemplate: (id: string | number) =>
		requestJson<void>("/analytics/api/screen-templates/" + encodeURIComponent(String(id)), "DELETE"),
	createScreenFromTemplate: (id: string | number, body?: unknown) =>
		sendJson<ScreenDetail>("/analytics/api/screen-templates/" + encodeURIComponent(String(id)) + "/create-screen", body ?? {}),
	getScreen: (
		id: string | number,
		options?: { mode?: "draft" | "published" | "preview" | string; fallbackDraft?: boolean },
	) => {
		const params = new URLSearchParams();
		if (options?.mode) params.set("mode", String(options.mode));
		if (options?.fallbackDraft !== undefined) params.set("fallbackDraft", String(options.fallbackDraft));
		const qs = params.toString();
		const base = `/analytics/api/screens/${encodeURIComponent(String(id))}`;
		return fetchJson<ScreenDetail>(qs ? `${base}?${qs}` : base);
	},
	getScreenHealth: (id: string | number) =>
		fetchJson<ScreenHealthReport>(`/analytics/api/screens/${encodeURIComponent(String(id))}/health`),
	prepareScreenExport: (id: string | number, body?: ScreenExportPrepareRequest) =>
		sendJson<ScreenExportPrepareResult>(`/analytics/api/screens/${encodeURIComponent(String(id))}/export-prepare`, body ?? {}),
	reportScreenExport: (id: string | number, body: ScreenExportReportRequest) =>
		sendJson<ScreenExportReportResult>(`/analytics/api/screens/${encodeURIComponent(String(id))}/export-report`, body),
	validateScreenSpec: (body: unknown) =>
		sendJson<ScreenSpecValidationResponse>("/analytics/api/screens/validate-spec", body),
	createScreen: (body: unknown) => sendJson<ScreenDetail>("/analytics/api/screens", body),
	listScreenVersions: (id: string | number) =>
		fetchJson<ScreenVersion[]>(`/analytics/api/screens/${encodeURIComponent(String(id))}/versions`),
	compareScreenVersions: (id: string | number, fromVersionId: string | number, toVersionId: string | number) =>
		fetchJson<ScreenVersionDiff>(
			`/analytics/api/screens/${encodeURIComponent(String(id))}/versions/compare`
			+ `?fromVersionId=${encodeURIComponent(String(fromVersionId))}`
			+ `&toVersionId=${encodeURIComponent(String(toVersionId))}`,
		),
	publishScreen: (id: string | number) =>
		sendJson<{ screen: ScreenDetail; version: ScreenVersion; warmup?: ScreenWarmupSummary }>(
			`/analytics/api/screens/${encodeURIComponent(String(id))}/publish`,
			{},
		),
	rollbackScreenVersion: (id: string | number, versionId: string | number) =>
		sendJson<{ screen: ScreenDetail; version: ScreenVersion; warmup?: ScreenWarmupSummary }>(
			`/analytics/api/screens/${encodeURIComponent(String(id))}/rollback/${encodeURIComponent(String(versionId))}`,
			{},
		),
	updateScreen: (id: string | number, body: unknown) =>
		requestJson<ScreenDetail>(`/analytics/api/screens/${encodeURIComponent(String(id))}`, "PUT", body),
	deleteScreen: (id: string | number) =>
		requestJson<void>(`/analytics/api/screens/${encodeURIComponent(String(id))}`, "DELETE"),
	getScreenAcl: (id: string | number) =>
		fetchJson<ScreenAclEntry[]>(`/analytics/api/screens/${encodeURIComponent(String(id))}/acl`),
	updateScreenAcl: (id: string | number, body: { entries: ScreenAclEntry[] }) =>
		requestJson<ScreenAclEntry[]>(`/analytics/api/screens/${encodeURIComponent(String(id))}/acl`, "PUT", body),
	getScreenEditLock: (id: string | number) =>
		fetchJson<ScreenEditLock>(`/analytics/api/screens/${encodeURIComponent(String(id))}/edit-lock`),
	acquireScreenEditLock: (id: string | number, body?: { ttlSeconds?: number; forceTakeover?: boolean }) =>
		sendJson<ScreenEditLock>(`/analytics/api/screens/${encodeURIComponent(String(id))}/edit-lock/acquire`, body ?? {}),
	heartbeatScreenEditLock: (id: string | number, body?: { ttlSeconds?: number }) =>
		sendJson<ScreenEditLock>(`/analytics/api/screens/${encodeURIComponent(String(id))}/edit-lock/heartbeat`, body ?? {}),
	releaseScreenEditLock: (id: string | number) =>
		sendJson<ScreenEditLock>(`/analytics/api/screens/${encodeURIComponent(String(id))}/edit-lock/release`, {}),
	getScreenAuditLogs: (id: string | number, limit = 200) =>
		fetchJson<ScreenAuditEntry[]>(
			`/analytics/api/screens/${encodeURIComponent(String(id))}/audit?limit=${encodeURIComponent(String(limit))}`,
		),
	listScreenComments: (id: string | number, limit = 200) =>
		fetchJson<ScreenComment[]>(
			`/analytics/api/screens/${encodeURIComponent(String(id))}/comments?limit=${encodeURIComponent(String(limit))}`,
		),
	listScreenCommentChanges: (id: string | number, sinceId = 0, limit = 200) =>
		fetchJson<ScreenCommentChanges>(
			`/analytics/api/screens/${encodeURIComponent(String(id))}/comments/changes`
			+ `?sinceId=${encodeURIComponent(String(sinceId))}`
			+ `&limit=${encodeURIComponent(String(limit))}`,
		),
	listScreenCommentChangesLive: (id: string | number, sinceId = 0, limit = 200, waitMs = 12000) =>
		fetchJson<ScreenCommentChanges>(
			`/analytics/api/screens/${encodeURIComponent(String(id))}/comments/live`
			+ `?sinceId=${encodeURIComponent(String(sinceId))}`
			+ `&limit=${encodeURIComponent(String(limit))}`
			+ `&waitMs=${encodeURIComponent(String(waitMs))}`,
		),
	getScreenCollaborationPresence: (id: string | number, ttlSeconds = 45, sessionId?: string) =>
		fetchJson<ScreenCollaborationPresence>(
			`/analytics/api/screens/${encodeURIComponent(String(id))}/collaboration/presence`
			+ `?ttlSeconds=${encodeURIComponent(String(ttlSeconds))}`
			+ `${sessionId ? `&sessionId=${encodeURIComponent(sessionId)}` : ''}`,
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
			`/analytics/api/screens/${encodeURIComponent(String(id))}/collaboration/presence/heartbeat`
			+ `?ttlSeconds=${encodeURIComponent(String(ttlSeconds))}`,
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
			`/analytics/api/screens/${encodeURIComponent(String(id))}/collaboration/presence/leave`
			+ `?ttlSeconds=${encodeURIComponent(String(ttlSeconds))}`,
			body ?? {},
		),
	createScreenComment: (id: string | number, body: {
		message: string;
		componentId?: string | null;
		anchor?: Record<string, unknown>;
		mentions?: Array<Record<string, unknown>>;
	}) =>
		sendJson<ScreenComment>(`/analytics/api/screens/${encodeURIComponent(String(id))}/comments`, body),
	resolveScreenComment: (id: string | number, commentId: string | number, body?: { note?: string }) =>
		sendJson<ScreenComment>(
			`/analytics/api/screens/${encodeURIComponent(String(id))}/comments/${encodeURIComponent(String(commentId))}/resolve`,
			body ?? {},
		),
	reopenScreenComment: (id: string | number, commentId: string | number, body?: { note?: string }) =>
		sendJson<ScreenComment>(
			`/analytics/api/screens/${encodeURIComponent(String(id))}/comments/${encodeURIComponent(String(commentId))}/reopen`,
			body ?? {},
		),
	createScreenPublicLink: (id: string | number, body?: unknown) =>
		sendJson<ScreenPublicLinkPolicy>(`/analytics/api/screens/${encodeURIComponent(String(id))}/public_link`, body ?? {}),
	updateScreenPublicLinkPolicy: (id: string | number, body: unknown) =>
		requestJson<ScreenPublicLinkPolicy>(`/analytics/api/screens/${encodeURIComponent(String(id))}/public_link/policy`, "PUT", body),
	deleteScreenPublicLink: (id: string | number) =>
		requestJson<void>(`/analytics/api/screens/${encodeURIComponent(String(id))}/public_link`, "DELETE"),
	getPublicScreen: (uuid: string) =>
		fetchJson<PublicScreenDetail>(`/analytics/api/public/screen/${encodeURIComponent(uuid)}`),
};
