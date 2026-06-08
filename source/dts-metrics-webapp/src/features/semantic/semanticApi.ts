import { fetchJson } from "../../api";
import type { GraphDraftResponse, SemanticQueryBody, SemanticQueryResponse, VisualAssetsResponse } from "./semanticTypes";

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
	getVisualAssets: (params?: { layers?: string; keyword?: string; includeDrilldown?: boolean }) =>
		fetchJson<VisualAssetsResponse>(
			withQuery("/api/metrics/visual-assets", {
				layers: params?.layers ?? "DWS,ADS",
				keyword: params?.keyword,
				includeDrilldown: params?.includeDrilldown,
			}),
		),
	previewSemanticSql: (body: SemanticQueryBody) =>
		sendJson<SemanticQueryResponse>("/api/metrics/graphs/draft/preflight", "POST", body),
	runSemanticQuery: (body: SemanticQueryBody) => sendJson<SemanticQueryResponse>("/api/metrics/graphs/draft/preflight", "POST", body),
	createGraphDraft: (body: SemanticQueryBody) => sendJson<GraphDraftResponse>("/api/metrics/graphs", "POST", body),
};
