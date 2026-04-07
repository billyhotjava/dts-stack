import { GLOBAL_CONFIG } from "@/global-config";
import { urlJoin } from "@/utils";

const ensureLeadingSlash = (path: string) => (path.startsWith("/") ? path : `/${path}`);

export const LOGIN_ROUTE = "/auth/login";

export const resolveAppHref = (route: string) => {
	const normalizedRoute = ensureLeadingSlash(route);
	if (GLOBAL_CONFIG.routerHistory === "hash") {
		const base = GLOBAL_CONFIG.publicPath === "/" ? "/" : GLOBAL_CONFIG.publicPath;
		return `${base}#${normalizedRoute}`;
	}
	return urlJoin(GLOBAL_CONFIG.publicPath, normalizedRoute);
};

export const resolveLoginHref = () => resolveAppHref(LOGIN_ROUTE);

export const isLoginRouteActive = () => {
	if (typeof window === "undefined") return false;
	const normalized = ensureLeadingSlash(LOGIN_ROUTE);
	if (GLOBAL_CONFIG.routerHistory === "hash") {
		const hash = window.location.hash.replace(/^#/, "").split("?")[0];
		return hash === normalized;
	}
	return window.location.pathname.endsWith(normalized);
};
