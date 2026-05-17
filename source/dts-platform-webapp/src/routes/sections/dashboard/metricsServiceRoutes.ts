const LEGACY_METRICS_PREFIX = "/bi-apps/metrics";
const METRICS_PREFIX = "/metrics";
const LEGACY_SEMANTIC_CENTER_PREFIX = "/modeling/semantic-center";

const stripTrailingSlash = (pathname: string) => {
	const normalized = (pathname || "/").replace(/\/{2,}/g, "/");
	if (normalized.length > 1 && normalized.endsWith("/")) {
		return normalized.slice(0, -1);
	}
	return normalized || "/";
};

const normalizeMetricsSuffix = (suffix: string) => {
	const normalized = suffix.replace(/^\/+/, "").replace(/\/+$/, "");
	if (!normalized || normalized === "center") {
		return "center";
	}
	if (normalized === "assets") {
		return "dictionary";
	}
	if (normalized === "publish") {
		return "semantic/publish";
	}
	return normalized;
};

export const metricsServicePathFromPlatformPath = (pathname: string) => {
	const normalized = stripTrailingSlash(pathname);

	if (normalized === LEGACY_METRICS_PREFIX) {
		return `${METRICS_PREFIX}/center`;
	}
	if (normalized.startsWith(`${LEGACY_METRICS_PREFIX}/`)) {
		const suffix = normalized.slice(LEGACY_METRICS_PREFIX.length + 1);
		return `${METRICS_PREFIX}/${normalizeMetricsSuffix(suffix)}`;
	}
	if (normalized === METRICS_PREFIX) {
		return `${METRICS_PREFIX}/center`;
	}
	if (normalized === `${METRICS_PREFIX}/publish`) {
		return `${METRICS_PREFIX}/semantic/publish`;
	}
	if (normalized.startsWith(`${METRICS_PREFIX}/`)) {
		return normalized;
	}
	if (normalized === LEGACY_SEMANTIC_CENTER_PREFIX) {
		return `${METRICS_PREFIX}/semantic`;
	}
	if (normalized.startsWith(`${LEGACY_SEMANTIC_CENTER_PREFIX}/`)) {
		const suffix = normalized.slice(LEGACY_SEMANTIC_CENTER_PREFIX.length + 1);
		return `${METRICS_PREFIX}/semantic/${suffix}`;
	}
	if (normalized === "/bi/semantic-modeling") {
		return `${METRICS_PREFIX}/semantic`;
	}

	return normalized;
};

export const metricsServiceHrefFromPlatformLocation = (
	pathname: string,
	search = "",
	hash = "",
) => `${metricsServicePathFromPlatformPath(pathname)}${search || ""}${hash || ""}`;
