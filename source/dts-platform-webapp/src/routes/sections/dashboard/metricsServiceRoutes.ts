const LEGACY_METRICS_PREFIX = "/bi-apps/metrics";
const LEGACY_SEMANTIC_CENTER_PREFIX = "/modeling/semantic-center";
const METRIC_WORKBENCH_PATH = "/modeling/workbench?module=metrics&workspaceView=definitions";
const MODEL_WORKBENCH_PATH = "/modeling/workbench?module=models&workspaceView=model-specs";
const DIMENSION_WORKBENCH_PATH = "/modeling/workbench?module=models&workspaceView=dimensions";

const SEMANTIC_PAGE_BY_SUFFIX: Record<string, string> = {
	semantic: MODEL_WORKBENCH_PATH,
	"semantic/objects": DIMENSION_WORKBENCH_PATH,
	"semantic/metrics": METRIC_WORKBENCH_PATH,
	"semantic/models": MODEL_WORKBENCH_PATH,
	"semantic/publish": `${MODEL_WORKBENCH_PATH}&view=release`,
	"semantic/runs": "/ops/instances",
};

const stripTrailingSlash = (pathname: string) => {
	const normalized = (pathname || "/").replace(/\/{2,}/g, "/");
	if (normalized.length > 1 && normalized.endsWith("/")) {
		return normalized.slice(0, -1);
	}
	return normalized || "/";
};

const platformPathFromMetricsSuffix = (suffix: string) => {
	const normalized = suffix.replace(/^\/+/, "").replace(/\/+$/, "");
	if (!normalized || normalized === "center") {
		return METRIC_WORKBENCH_PATH;
	}
	if (normalized === "assets" || normalized === "dictionary") {
		return METRIC_WORKBENCH_PATH;
	}
	if (normalized === "subjects" || normalized === "semantic/subjects") {
		return "/governance/subjects";
	}
	if (
		normalized === "operations" ||
		normalized === "f5-security-it" ||
		normalized === "runs" ||
		normalized === "semantic/runs"
	) {
		return "/ops/instances";
	}
	if (normalized === "publish") {
		return `${MODEL_WORKBENCH_PATH}&view=release`;
	}

	const semanticPage = SEMANTIC_PAGE_BY_SUFFIX[normalized];
	if (semanticPage) {
		return semanticPage;
	}

	return METRIC_WORKBENCH_PATH;
};

export const metricsServicePathFromPlatformPath = (pathname: string) => {
	const normalized = stripTrailingSlash(pathname);

	if (normalized === LEGACY_METRICS_PREFIX) {
		return METRIC_WORKBENCH_PATH;
	}
	if (normalized.startsWith(`${LEGACY_METRICS_PREFIX}/`)) {
		const suffix = normalized.slice(LEGACY_METRICS_PREFIX.length + 1);
		return platformPathFromMetricsSuffix(suffix);
	}
	if (normalized === "/metrics") {
		return METRIC_WORKBENCH_PATH;
	}
	if (normalized.startsWith("/metrics/")) {
		const suffix = normalized.slice("/metrics/".length);
		return platformPathFromMetricsSuffix(suffix);
	}
	if (normalized === LEGACY_SEMANTIC_CENTER_PREFIX) {
		return MODEL_WORKBENCH_PATH;
	}
	if (normalized.startsWith(`${LEGACY_SEMANTIC_CENTER_PREFIX}/`)) {
		const suffix = normalized.slice(LEGACY_SEMANTIC_CENTER_PREFIX.length + 1);
		return platformPathFromMetricsSuffix(`semantic/${suffix}`);
	}
	if (normalized === "/bi/semantic-modeling") {
		return MODEL_WORKBENCH_PATH;
	}

	return normalized;
};

export const metricsServiceHrefFromPlatformLocation = (pathname: string, search = "", hash = "") => {
	const [targetPath, targetSearch = ""] = metricsServicePathFromPlatformPath(pathname).split("?");
	const params = new URLSearchParams(targetSearch);
	new URLSearchParams(search).forEach((value, key) => {
		if (!params.has(key)) params.set(key, value);
	});
	const query = params.toString();
	return `${targetPath}${query ? `?${query}` : ""}${hash || ""}`;
};
