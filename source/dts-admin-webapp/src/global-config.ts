import packageJson from "../package.json";

/**
 * Global application configuration type definition
 */
export type GlobalConfig = {
	/** Application name */
	appName: string;
	/** Application version number */
	appVersion: string;
	/** Default route path for the application */
	defaultRoute: string;
	/** Public path for static assets */
	publicPath: string;
	/** Base URL for API endpoints */
	apiBaseUrl: string;
	/** Routing mode: frontend routing or backend routing */
	routerMode: "frontend" | "backend";
};

/**
 * Global configuration constants
 * Reads configuration from environment variables and package.json
 *
 * @warning
 * Please don't use the import.meta.env to get the configuration, use the GLOBAL_CONFIG instead
 */
const isAbsoluteUrl = (value: string) => {
	const normalized = value.trim();
	if (!normalized) return false;
	return normalized.startsWith("//") || /^[a-zA-Z][a-zA-Z\d+.-]*:\/\//.test(normalized);
};

const ensureLeadingSlash = (path: string, fallback: string) => {
	const normalized = path.trim();
	if (!normalized) return fallback;
	if (isAbsoluteUrl(normalized)) return normalized;
	return normalized.startsWith("/") ? normalized : `/${normalized}`;
};

const resolveDefaultRoute = () => {
	const rawDefaultRoute = import.meta.env.VITE_APP_DEFAULT_ROUTE || "/workbench";
	return ensureLeadingSlash(rawDefaultRoute, "/workbench");
};

const resolveApiBaseUrl = () => {
	const rawApiBaseUrl = import.meta.env.VITE_API_BASE_URL || "/api";
	const normalized = rawApiBaseUrl.trim();
	if (!normalized) return "/api";
	if (isAbsoluteUrl(normalized)) {
		return normalized.replace(/\/+$/, "");
	}
	return ensureLeadingSlash(normalized, "/api");
};

export const GLOBAL_CONFIG: GlobalConfig = {
	appName: import.meta.env.VITE_APP_NAME || "数据管理平台",
	appVersion: packageJson.version,
	defaultRoute: resolveDefaultRoute(),
	publicPath: import.meta.env.VITE_PUBLIC_PATH || "/",
	apiBaseUrl: resolveApiBaseUrl(),
	routerMode: import.meta.env.VITE_APP_ROUTER_MODE || "frontend",
};
