import { fetchJson } from "../../api";
import type { SemanticMetaResponse, SemanticMetric, SemanticQueryBody, SemanticQueryResponse } from "./semanticTypes";

function withQuery(path: string, params?: Record<string, string | number | boolean | undefined>) {
	const query = new URLSearchParams();
	for (const [key, value] of Object.entries(params ?? {})) {
		if (value !== undefined && value !== "") {
			query.set(key, String(value));
		}
	}
	const suffix = query.toString();
	return suffix ? `${path}?${suffix}` : path;
}

function sendJson<T>(url: string, method: "POST" | "PUT", body: unknown): Promise<T> {
	return fetchJson<T>(url, {
		method,
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify(body ?? {}),
	});
}

export const semanticApi = {
	getSemanticMeta: (params?: { exposedToModeler?: boolean; subjectArea?: string }) =>
		fetchJson<SemanticMetaResponse>(
			withQuery("/bi/api/semantic/meta", {
				exposed_to_modeler: params?.exposedToModeler,
				subject_area: params?.subjectArea,
			}),
		),
	previewSemanticSql: (body: SemanticQueryBody) =>
		sendJson<SemanticQueryResponse>("/bi/api/semantic/query/preview-sql", "POST", body),
	runSemanticQuery: (body: SemanticQueryBody) => sendJson<SemanticQueryResponse>("/bi/api/semantic/query", "POST", body),
	listSemanticMetrics: (params?: { objectId?: string }) =>
		fetchJson<SemanticMetric[]>(
			withQuery("/api/semantic/metrics", {
				objectId: params?.objectId,
			}),
		),
	createSemanticMetric: (data: Partial<SemanticMetric> & { formulaJson?: string; unit?: string }) =>
		sendJson<SemanticMetric>("/api/semantic/metrics", "POST", data),
};
