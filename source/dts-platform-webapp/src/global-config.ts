import packageJson from "../package.json";
import { DEFAULT_PORTAL_ROUTE } from "./constants/portal-navigation";

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
	/** History strategy: browser (HTML5) or hash (#) */
	routerHistory: "browser" | "hash";
	/** Enable admin UI for managing portal (client) menus */
	enablePortalMenuMgmt: boolean;
    /** Enable experimental SQL workbench experience */
    enableSqlWorkbench: boolean;
    /** Allowed roles to sign in; empty means allow all authenticated users */
    allowedLoginRoles: string[];
    /** Base URL for Admin APIs (used for Keycloak localization, etc.) */
    adminApiBaseUrl: string;
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

const removeTrailingSlash = (path: string) => {
	if (path === "/") {
		return "/";
	}

	const trimmed = path.replace(/\/+$/, "");
	return trimmed || "/";
};

const resolveDefaultRoute = () => {
	const env = import.meta.env as Record<string, string | undefined>;
	const routerMode = (env.VITE_APP_ROUTER_MODE || "backend").trim().toLowerCase();
	const backendFallback = DEFAULT_PORTAL_ROUTE;
	const frontendFallback = DEFAULT_PORTAL_ROUTE;

	const fallback = routerMode === "backend" ? backendFallback : frontendFallback;
	const rawDefaultRoute = env.VITE_APP_DEFAULT_ROUTE;
	const normalizedRoute = removeTrailingSlash(ensureLeadingSlash(rawDefaultRoute ?? "", fallback));

	if (routerMode !== "backend" && normalizedRoute === backendFallback) {
		if (import.meta.env.DEV) {
			console.warn(
				`[global-config] "${normalizedRoute}" is reserved for backend routing. Falling back to "${frontendFallback}" in frontend mode.`,
			);
		}
		return frontendFallback;
	}

	return normalizedRoute;
};

const resolvePublicPath = () => {
	const env = import.meta.env as Record<string, string | undefined>;
	const rawPublicPath = env.VITE_PUBLIC_PATH || env.VITE_APP_PUBLIC_PATH || env.BASE_URL || "/";

	if (isAbsoluteUrl(rawPublicPath)) {
		return removeTrailingSlash(rawPublicPath);
	}

	const withLeadingSlash = ensureLeadingSlash(rawPublicPath, "/");
	return removeTrailingSlash(withLeadingSlash);
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

const resolveAdminApiBaseUrl = () => {
    const raw = (import.meta.env.VITE_ADMIN_API_BASE_URL || "") as string;
    const normalized = String(raw).trim();
    if (!normalized) {
        // Fallback to a conventional reverse-proxy path if configured (may not work in dev unless proxied)
        return "/admin/api";
    }
    return normalized.replace(/\/+$/, "");
};

const resolveAllowedLoginRoles = (): string[] => {
    // Default: allow all authenticated users (empty list).
    // If you want to restrict, set VITE_ALLOWED_LOGIN_ROLES to a comma-separated list, e.g.
    // "DEPT_DATA_VIEWER,DEPT_DATA_DEV,DEPT_DATA_OWNER,INST_DATA_VIEWER,INST_DATA_DEV,INST_DATA_OWNER,ROLE_OP_ADMIN".
    const defaultValue = "";
    const raw = (import.meta.env.VITE_ALLOWED_LOGIN_ROLES || defaultValue) as string;
    return String(raw)
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
};


export const GLOBAL_CONFIG: GlobalConfig = {
	appName: import.meta.env.VITE_APP_NAME || "数据管理平台",
	appVersion: packageJson.version,
	defaultRoute: resolveDefaultRoute(),
	publicPath: resolvePublicPath(),
	apiBaseUrl: resolveApiBaseUrl(),
	routerMode: ((import.meta.env.VITE_APP_ROUTER_MODE || "backend") as string).trim().toLowerCase() as
		| "frontend"
		| "backend",
	routerHistory: ((import.meta.env.VITE_APP_ROUTER_HISTORY || "browser") as string).trim().toLowerCase() as
		| "browser"
		| "hash",
    enablePortalMenuMgmt: String(import.meta.env.VITE_ENABLE_PORTAL_MENU_MGMT || "true").toLowerCase() === "true",
    enableSqlWorkbench: String(import.meta.env.VITE_ENABLE_SQL_WORKBENCH || "false").toLowerCase() === "true",
    allowedLoginRoles: resolveAllowedLoginRoles(),
    adminApiBaseUrl: resolveAdminApiBaseUrl(),
};
