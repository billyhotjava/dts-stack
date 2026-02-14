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
};

export type ScreenDetail = ScreenListItem & {
	backgroundColor?: string;
	backgroundImage?: string | null;
	theme?: string;
	components?: ScreenComponentData[];
	globalVariables?: Array<{ key: string; label?: string; type?: string; defaultValue?: string; description?: string }>;
	sourceMode?: "draft" | "published" | string;
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
	constructor(status: number, message: string, bodyText: string, requestId?: string, code?: string) {
		super(message);
		this.status = status;
		this.bodyText = bodyText;
		this.requestId = requestId;
		this.code = code;
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

function buildHttpError(response: Response, bodyText: string): HttpError {
	const requestId = extractRequestId(response);
	const errorCode = extractErrorCode(response, bodyText);
	const baseMsg = "HTTP " + response.status + " " + response.statusText + ": " + bodyText;
	const taggedMsg = errorCode ? baseMsg + " [code=" + errorCode + "]" : baseMsg;
	const msg = requestId ? taggedMsg + " [requestId=" + requestId + "]" : taggedMsg;
	if (response.status === 401 || response.status === 403) {
		return new AuthError(response.status, msg, bodyText, requestId, errorCode);
	}
	return new HttpError(response.status, msg, bodyText, requestId, errorCode);
}

export function isRetryableHttpError(error: unknown): boolean {
	if (!(error instanceof HttpError)) {
		return false;
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
	listScreens: () => fetchJson<ScreenListItem[]>("/analytics/api/screens"),
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
	createScreen: (body: unknown) => sendJson<ScreenDetail>("/analytics/api/screens", body),
	listScreenVersions: (id: string | number) =>
		fetchJson<ScreenVersion[]>(`/analytics/api/screens/${encodeURIComponent(String(id))}/versions`),
	publishScreen: (id: string | number) =>
		sendJson<{ screen: ScreenDetail; version: ScreenVersion }>(
			`/analytics/api/screens/${encodeURIComponent(String(id))}/publish`,
			{},
		),
	rollbackScreenVersion: (id: string | number, versionId: string | number) =>
		sendJson<{ screen: ScreenDetail; version: ScreenVersion }>(
			`/analytics/api/screens/${encodeURIComponent(String(id))}/rollback/${encodeURIComponent(String(versionId))}`,
			{},
		),
	updateScreen: (id: string | number, body: unknown) =>
		requestJson<ScreenDetail>(`/analytics/api/screens/${encodeURIComponent(String(id))}`, "PUT", body),
	deleteScreen: (id: string | number) =>
		requestJson<void>(`/analytics/api/screens/${encodeURIComponent(String(id))}`, "DELETE"),
	createScreenPublicLink: (id: string | number) =>
		sendJson<{ uuid: string }>(`/analytics/api/screens/${encodeURIComponent(String(id))}/public_link`, {}),
	deleteScreenPublicLink: (id: string | number) =>
		requestJson<void>(`/analytics/api/screens/${encodeURIComponent(String(id))}/public_link`, "DELETE"),
	getPublicScreen: (uuid: string) =>
		fetchJson<PublicScreenDetail>(`/analytics/api/public/screen/${encodeURIComponent(uuid)}`),
};
