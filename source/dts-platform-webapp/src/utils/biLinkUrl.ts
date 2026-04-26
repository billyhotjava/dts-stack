import { resolveAppHref } from "@/routes/constants";

const isIpv4 = (host: string) => {
	if (!host) return false;
	const parts = host.split(".");
	if (parts.length !== 4) return false;
	return parts.every((part) => {
		if (!/^\d+$/.test(part)) return false;
		const num = Number(part);
		return num >= 0 && num <= 255;
	});
};

const resolveRuntimePlatformBase = () => {
	if (typeof window === "undefined") return "";
	const rc = (window as any).__RUNTIME_CONFIG__ as { platformBaseUrl?: string } | undefined;
	return String(rc?.platformBaseUrl || "").trim();
};

const normalizeOrigin = (raw: string) => {
	const trimmed = raw.trim().replace(/\/+$/, "");
	if (!trimmed) return "";
	if (trimmed.startsWith("//")) {
		return `${window.location.protocol}${trimmed}`.replace(/\/+$/, "");
	}
	if (/^[a-zA-Z][a-zA-Z\d+.-]*:\/\//.test(trimmed)) {
		try {
			return new URL(trimmed).origin;
		} catch {
			return trimmed;
		}
	}
	return `https://${trimmed}`;
};

const getOrigin = () => {
	if (typeof window === "undefined") return "";
	const runtimeBase = resolveRuntimePlatformBase();
	const normalized = runtimeBase ? normalizeOrigin(runtimeBase) : "";
	return normalized || window.location.origin;
};

const toAbsolute = (path: string) => {
	const origin = getOrigin();
	if (!origin) return path;
	if (!path.startsWith("/")) return `${origin}/${path}`;
	return `${origin}${path}`;
};

const toAppHref = (path: string) => resolveAppHref(path);

const extractPathLike = (raw: string) => {
	const trimmed = raw.trim();
	if (!trimmed) return "";
	if (trimmed.startsWith("/")) return trimmed;
	try {
		const url = new URL(trimmed);
		return `${url.pathname || ""}${url.search || ""}${url.hash || ""}` || "/";
	} catch {
		return trimmed;
	}
};

const isAppRoutePath = (rawPath: string) => {
	const plainPath =
		String(rawPath || "")
			.split("#")[0]
			.split("?")[0] || "";
	const path = plainPath.toLowerCase().replace(/\/+$/, "") || "/";
	return path === "/bi" || path.startsWith("/bi/");
};

const sameRuntimeOrigin = (url: URL) => {
	if (typeof window === "undefined") return false;
	const origin = getOrigin();
	if (!origin) return url.origin === window.location.origin;
	return url.origin === origin || url.origin === window.location.origin;
};

const resolveAppRouteForOpen = (raw: string) => {
	const pathLike = extractPathLike(raw);
	const normalized = pathLike.startsWith("/") ? pathLike : `/${pathLike}`;
	if (isAppRoutePath(normalized)) return toAppHref(normalized);
	return "";
};

const shouldRedirectHetuEntryToAnalytics = (rawPath: string) => {
	const plainPath =
		String(rawPath || "")
			.split("#")[0]
			.split("?")[0] || "";
	const path = plainPath.toLowerCase().replace(/\/+$/, "");
	// Only convert Hetu entry pages to /bi.
	// Keep deep links (e.g. /screen/share/...) untouched.
	return (
		path === "/screen" ||
		path === "/dashboards" ||
		path === "/dashboard/hetu" ||
		path === "/system" ||
		path === "/tdv" ||
		path === "/account" ||
		path === "/hetu"
	);
};

const normalizeHetuUrl = (raw: string, preferAbsolute: boolean) => {
	const trimmed = raw.trim();
	if (!trimmed) return trimmed;
	if (trimmed.startsWith("/")) {
		return preferAbsolute ? toAbsolute(trimmed) : trimmed;
	}
	try {
		const url = new URL(trimmed);
		if (isIpv4(url.hostname)) {
			const path = `${url.pathname || ""}${url.search || ""}${url.hash || ""}`;
			return preferAbsolute ? toAbsolute(path || "/") : path || "/";
		}
		return trimmed;
	} catch {
		return trimmed;
	}
};

export const normalizeBiLinkForSave = (raw?: string | null, engine?: string | null) => {
	const text = String(raw || "").trim();
	if (!text) return "";
	const normalizedEngine = String(engine || "")
		.trim()
		.toUpperCase();
	if (normalizedEngine !== "HETU") return text;
	const resolved = normalizeHetuUrl(text, true);
	if (shouldRedirectHetuEntryToAnalytics(extractPathLike(resolved))) {
		return toAbsolute("/bi");
	}
	return resolved;
};

export const resolveBiLinkForOpen = (raw?: string | null, engine?: string | null) => {
	const text = String(raw || "").trim();
	if (!text) return "";
	const normalizedEngine = String(engine || "")
		.trim()
		.toUpperCase();
	if (normalizedEngine !== "HETU") {
		if (text.startsWith("/")) {
			return resolveAppRouteForOpen(text) || toAbsolute(text);
		}
		const appRoute = resolveAppRouteForOpen(text);
		if (appRoute) return appRoute;
		try {
			const url = new URL(text);
			const path = `${url.pathname || ""}${url.search || ""}${url.hash || ""}`;
			if (sameRuntimeOrigin(url) && isAppRoutePath(path)) return toAppHref(path);
			return text;
		} catch {
			return text;
		}
	}
	const resolved = normalizeHetuUrl(text, true);
	if (shouldRedirectHetuEntryToAnalytics(extractPathLike(resolved))) {
		return toAppHref("/bi");
	}
	if (resolved.startsWith("/")) {
		return resolveAppRouteForOpen(resolved) || toAbsolute(resolved);
	}
	try {
		const url = new URL(resolved);
		const path = `${url.pathname || ""}${url.search || ""}${url.hash || ""}`;
		if (sameRuntimeOrigin(url) && isAppRoutePath(path)) return toAppHref(path);
	} catch {
		// Keep the original resolved value for non-URL HETU deep links.
	}
	return resolved;
};
