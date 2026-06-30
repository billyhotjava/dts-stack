const LEGACY_METRICS_PREFIX = "/bi-apps/metrics";
const LEGACY_SEMANTIC_CENTER_PREFIX = "/modeling/semantic-center";
const METRIC_WORKBENCH_PATH = "/modeling/metric-workbench";
const SEMANTIC_PREFIX = "/modeling/semantic";

const SEMANTIC_PAGE_BY_SUFFIX: Record<string, string> = {
	"semantic": "models",
	"semantic/subjects": "subjects",
	"semantic/objects": "objects",
	"semantic/metrics": "metrics",
	"semantic/models": "models",
	"semantic/publish": "publish",
	"semantic/runs": "runs",
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
		return `${SEMANTIC_PREFIX}/metrics`;
	}
	if (normalized === "operations" || normalized === "f5-security-it" || normalized === "runs") {
		return `${SEMANTIC_PREFIX}/runs`;
	}
	if (normalized === "publish") {
		return `${SEMANTIC_PREFIX}/publish`;
	}

	const semanticPage = SEMANTIC_PAGE_BY_SUFFIX[normalized];
	if (semanticPage) {
		return `${SEMANTIC_PREFIX}/${semanticPage}`;
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
		return `${SEMANTIC_PREFIX}/models`;
	}
	if (normalized.startsWith(`${LEGACY_SEMANTIC_CENTER_PREFIX}/`)) {
		const suffix = normalized.slice(LEGACY_SEMANTIC_CENTER_PREFIX.length + 1);
		return platformPathFromMetricsSuffix(`semantic/${suffix}`);
	}
	if (normalized === "/bi/semantic-modeling") {
		return `${SEMANTIC_PREFIX}/models`;
	}

	return normalized;
};

export const metricsServiceHrefFromPlatformLocation = (
	pathname: string,
	search = "",
	hash = "",
) => `${metricsServicePathFromPlatformPath(pathname)}${search || ""}${hash || ""}`;
