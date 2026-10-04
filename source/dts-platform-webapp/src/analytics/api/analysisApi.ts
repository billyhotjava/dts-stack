import { fetchWithPlatformAuth } from "./analyticsApi";

const ANALYSIS_API = "/bi/api/analysis";

export type AnalysisDatasetRef = {
	id: string;
	version: number;
	contractVersion: string;
	checksum: string;
};

export type AnalysisDimensionSelection = {
	field: string;
	alias?: string | null;
};

export type AnalysisMetricSelection = {
	code: string;
	alias?: string | null;
};

export type AnalysisDerivedMetric = {
	code: string;
	expression: string;
	format?: string | null;
};

export type AnalysisFilterSelection = {
	field: string;
	op: string;
	values: unknown[];
	required?: boolean;
};

export type AnalysisTimeRange = {
	field: string;
	start?: string | null;
	end?: string | null;
	grain: string;
};

export type AnalysisOrderSelection = {
	field: string;
	direction: "ASC" | "DESC";
};

export type AnalysisVisualization = {
	type: "table" | "bar" | "line" | "area" | "pie" | "number" | "scatter";
	settings: Record<string, unknown>;
};

export type AnalysisQuerySpec = {
	apiVersion: "dts.analysis/v1";
	dataset: AnalysisDatasetRef;
	dimensions: AnalysisDimensionSelection[];
	metrics: AnalysisMetricSelection[];
	derivedMetrics: AnalysisDerivedMetric[];
	filters: AnalysisFilterSelection[];
	timeRange?: AnalysisTimeRange | null;
	orderBy: AnalysisOrderSelection[];
	limit: number;
	visualization: AnalysisVisualization;
};

export type AnalysisPermissions = {
	read: boolean;
	write: boolean;
	publish: boolean;
	export?: boolean;
};

export type Analysis = {
	id: number;
	name: string;
	description?: string | null;
	lifecycleStatus: "DRAFT" | "PUBLISHED" | "ARCHIVED";
	versionNo: number;
	publishedRevisionId?: number | null;
	queryDatasetId: string;
	queryDatasetVersion: number;
	contractVersion: string;
	visualization: AnalysisVisualization;
	querySpec: AnalysisQuerySpec;
	createdBy: string;
	updatedAt?: string | null;
	permissions: AnalysisPermissions;
};

export type AnalysisPage = {
	items: Analysis[];
	page: number;
	size: number;
	totalElements: number;
	totalPages: number;
};

export type CreateAnalysisPayload = {
	name: string;
	description?: string | null;
	querySpec: AnalysisQuerySpec;
	collectionId?: number | null;
};

export type UpdateAnalysisPayload = {
	name: string;
	description?: string | null;
	querySpec: AnalysisQuerySpec;
	versionNo: number;
};

export type AnalysisQueryResult = {
	queryId: string;
	columns: Array<Record<string, unknown>>;
	rows: unknown[][];
	rowCount: number;
	truncated: boolean;
	cacheHit: boolean;
	durationMs: number;
	contractChecksum: string;
};

export type PublicationAudience = {
	deptCodes: string[];
	roleCodes: string[];
	classification: "DATA_PUBLIC" | "DATA_INTERNAL" | "DATA_CONFIDENTIAL" | "DATA_SENSITIVE" | "DATA_SECRET";
	expiresAt?: string | null;
};

export type PublicationIssue = {
	code: string;
	path: string;
	message: string;
};

export type PublicationValidation = {
	valid: boolean;
	blockers: PublicationIssue[];
	warnings: PublicationIssue[];
	dependencySnapshot: Record<string, unknown>;
};

export type AnalysisPublicationResult = {
	analysisId: number;
	revisionId: number;
	versionNo: number;
	status: "PUBLISHED";
	contractChecksum: string;
	dependencySnapshot: Record<string, unknown>;
	publishedAt: string;
};

export type AnalysisVersion = {
	revisionId: number;
	versionNo: number;
	status: "DRAFT" | "PUBLISHED" | "SUPERSEDED";
	contractChecksum?: string | null;
	dependencySnapshot: Record<string, unknown>;
	publishedBy?: number | null;
	publishedAt?: string | null;
	createdAt: string;
};

export class AnalysisApiError extends Error {
	status: number;
	errorCode?: string;
	field?: string;
	correlationId?: string;

	constructor(
		status: number,
		message: string,
		details?: { errorCode?: string; field?: string; correlationId?: string },
	) {
		super(message);
		this.status = status;
		this.errorCode = details?.errorCode;
		this.field = details?.field;
		this.correlationId = details?.correlationId;
	}
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
	const headers = new Headers(init.headers);
	headers.set("accept", "application/json");
	if (init.body != null) headers.set("content-type", "application/json");
	const url = path.startsWith("/bi/") ? path : `${ANALYSIS_API}${path}`;
	const response = await fetchWithPlatformAuth(url, { ...init, headers });
	if (!response.ok) {
		const payload = (await response.json().catch(() => ({}))) as Record<string, unknown>;
		throw new AnalysisApiError(response.status, String(payload.message ?? `分析服务请求失败（${response.status}）`), {
			errorCode: typeof payload.errorCode === "string" ? payload.errorCode : undefined,
			field: typeof payload.field === "string" ? payload.field : undefined,
			correlationId:
				typeof payload.correlationId === "string"
					? payload.correlationId
					: response.headers.get("x-correlation-id") ?? response.headers.get("x-request-id") ?? undefined,
		});
	}
	if (response.status === 204) return undefined as T;
	return (await response.json()) as T;
}

export const listAnalyses = (page = 0, size = 20) =>
	request<AnalysisPage>(`?page=${encodeURIComponent(String(page))}&size=${encodeURIComponent(String(size))}`);

export const getAnalysis = (id: string | number) => request<Analysis>(`/${encodeURIComponent(String(id))}`);

export const createAnalysis = (payload: CreateAnalysisPayload, idempotencyKey: string) =>
	request<Analysis>("", {
		method: "POST",
		headers: { "Idempotency-Key": idempotencyKey },
		body: JSON.stringify(payload),
	});

export const updateAnalysis = (id: string | number, payload: UpdateAnalysisPayload) =>
	request<Analysis>(`/${encodeURIComponent(String(id))}`, {
		method: "PUT",
		body: JSON.stringify(payload),
	});

export const copyAnalysis = (id: string | number) =>
	request<Analysis>(`/${encodeURIComponent(String(id))}/copy`, { method: "POST", body: "{}" });

export const archiveAnalysis = (id: string | number) =>
	request<Analysis>(`/${encodeURIComponent(String(id))}/archive`, { method: "POST", body: "{}" });

export const restoreAnalysis = (id: string | number) =>
	request<Analysis>(`/${encodeURIComponent(String(id))}/restore`, { method: "POST", body: "{}" });

export const validateAnalysisPublication = (id: string | number, audience: PublicationAudience) =>
	request<PublicationValidation>(`/${encodeURIComponent(String(id))}/validate`, {
		method: "POST",
		body: JSON.stringify(audience),
	});

export const publishAnalysis = (id: string | number, audience: PublicationAudience) =>
	request<AnalysisPublicationResult>(`/${encodeURIComponent(String(id))}/publish`, {
		method: "POST",
		body: JSON.stringify(audience),
	});

export const listAnalysisVersions = (id: string | number) =>
	request<AnalysisVersion[]>(`/${encodeURIComponent(String(id))}/versions`);

export const createAnalysisDraftFromVersion = (id: string | number, revisionId: string | number) =>
	request<Analysis>(
		`/${encodeURIComponent(String(id))}/versions/${encodeURIComponent(String(revisionId))}/draft`,
		{ method: "POST", body: "{}" },
	);

export const previewAnalysis = (querySpec: AnalysisQuerySpec, queryId: string, signal?: AbortSignal) =>
	request<AnalysisQueryResult>("/bi/api/analysis/preview", {
		method: "POST",
		headers: { "X-Correlation-Id": queryId },
		body: JSON.stringify(querySpec),
		signal,
	});

export const queryAnalysis = (id: string | number, queryId: string, signal?: AbortSignal) =>
	request<AnalysisQueryResult>(`/${encodeURIComponent(String(id))}/query`, {
		method: "POST",
		headers: { "X-Correlation-Id": queryId },
		body: "{}",
		signal,
	});

export const cancelAnalysisQuery = (queryId: string) =>
	request<{ queryId: string; cancelled: boolean }>(`/queries/${encodeURIComponent(queryId)}/cancel`, {
		method: "POST",
		body: "{}",
	});

function exportFilename(contentDisposition: string | null, fallback: string): string {
	const encoded = contentDisposition?.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
	if (encoded) {
		try {
			return decodeURIComponent(encoded);
		} catch {
			return fallback;
		}
	}
	return contentDisposition?.match(/filename="?([^";]+)"?/i)?.[1] ?? fallback;
}

export async function exportAnalysis(
	id: string | number,
	format: "csv" | "xlsx",
): Promise<{ blob: Blob; filename: string }> {
	const response = await fetchWithPlatformAuth(
		`${ANALYSIS_API}/${encodeURIComponent(String(id))}/query/${format}`,
		{ method: "POST", headers: { accept: format === "csv" ? "text/csv" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" } },
	);
	if (!response.ok) {
		const raw = await response.text();
		let payload: Record<string, unknown> = {};
		try {
			payload = JSON.parse(raw) as Record<string, unknown>;
		} catch {
			payload = {};
		}
		throw new AnalysisApiError(response.status, String(payload.message ?? (raw || `分析导出失败（${response.status}）`)), {
			errorCode: typeof payload.errorCode === "string" ? payload.errorCode : undefined,
			correlationId: response.headers.get("x-correlation-id") ?? response.headers.get("x-request-id") ?? undefined,
		});
	}
	return {
		blob: await response.blob(),
		filename: exportFilename(response.headers.get("content-disposition"), `analysis-${id}.${format}`),
	};
}
