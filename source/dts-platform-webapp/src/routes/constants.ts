import { GLOBAL_CONFIG } from "@/global-config";
import { urlJoin } from "@/utils";

const ensureLeadingSlash = (path: string) => (path.startsWith("/") ? path : `/${path}`);
const MAX_REDIRECT_LENGTH = 4096;

export const LOGIN_ROUTE = "/auth/login";

export const resolveAppHref = (route: string) => {
	const normalizedRoute = ensureLeadingSlash(route);
	if (GLOBAL_CONFIG.routerHistory === "hash") {
		const base = GLOBAL_CONFIG.publicPath === "/" ? "/" : GLOBAL_CONFIG.publicPath;
		return `${base}#${normalizedRoute}`;
	}
	return urlJoin(GLOBAL_CONFIG.publicPath, normalizedRoute);
};

const stripPublicPath = (pathname: string) => {
	const publicPath = GLOBAL_CONFIG.publicPath;
	if (!publicPath || publicPath === "/") {
		return pathname || "/";
	}
	if (pathname === publicPath) {
		return "/";
	}
	if (pathname.startsWith(`${publicPath}/`)) {
		return pathname.slice(publicPath.length) || "/";
	}
	return pathname || "/";
};

const sanitizeRedirectPath = (redirect?: string | null) => {
	const normalized = String(redirect || "").trim();
	if (!normalized) return null;
	if (!normalized.startsWith("/")) return null;
	if (normalized.startsWith("//")) return null;
	if (normalized.includes("://")) return null;
	if (normalized.length > MAX_REDIRECT_LENGTH) return null;
	return normalized;
};

const isLoginRedirectTarget = (redirect: string) => {
	try {
		const pathname = new URL(redirect, "http://dts.local").pathname.replace(/\/+$/, "") || "/";
		return pathname === LOGIN_ROUTE || pathname === "/login";
	} catch {
		const pathname = redirect.split(/[?#]/)[0].replace(/\/+$/, "") || "/";
		return pathname === LOGIN_ROUTE || pathname === "/login";
	}
};

export const resolveCurrentAppPath = () => {
	if (typeof window === "undefined") return null;
	if (GLOBAL_CONFIG.routerHistory === "hash") {
		const hashPath = window.location.hash.replace(/^#/, "");
		return sanitizeRedirectPath(hashPath || "/");
	}
	const currentPath = `${stripPublicPath(window.location.pathname)}${window.location.search}${window.location.hash}`;
	return sanitizeRedirectPath(currentPath);
};

export const resolveLoginHref = (redirect?: string | null) => {
	const safeRedirect = sanitizeRedirectPath(redirect);
	if (!safeRedirect || isLoginRedirectTarget(safeRedirect)) {
		return resolveAppHref(LOGIN_ROUTE);
	}
	return resolveAppHref(`${LOGIN_ROUTE}?redirect=${encodeURIComponent(safeRedirect)}`);
};

export const resolvePostLoginRedirect = (
	redirect?: string | null,
	fallback = GLOBAL_CONFIG.defaultRoute || "/workbench",
) => {
	const safeRedirect = sanitizeRedirectPath(redirect);
	if (safeRedirect && !isLoginRedirectTarget(safeRedirect)) {
		return safeRedirect;
	}

	const safeFallback = sanitizeRedirectPath(fallback);
	if (safeFallback && !isLoginRedirectTarget(safeFallback)) {
		return safeFallback;
	}

	return "/workbench";
};

export const isLoginRouteActive = () => {
	if (typeof window === "undefined") return false;
	const normalized = ensureLeadingSlash(LOGIN_ROUTE);
	if (GLOBAL_CONFIG.routerHistory === "hash") {
		const hash = window.location.hash.replace(/^#/, "").split("?")[0];
		return hash === normalized;
	}
	return window.location.pathname.endsWith(normalized);
};
