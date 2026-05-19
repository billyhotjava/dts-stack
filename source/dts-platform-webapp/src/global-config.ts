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
	/** History strategy: browser (HTML5) or hash (#) */
	routerHistory: "browser" | "hash";
	/** Enable admin UI for managing portal (client) menus */
	enablePortalMenuMgmt: boolean;
    /** Enable experimental SQL workbench experience */
    enableSqlWorkbench: boolean;
    /** Enable Sprint-11 new SQL IDE (replaces QueryWorkbenchPage when true) */
    enableSqlIdeV2: boolean;
    /** Allowed roles to sign in; empty means allow all authenticated users */
    allowedLoginRoles: string[];
    /** Local Koal middleware endpoints, used for PKI login */
    koalPkiEndpoints: string[];
    /** Show the classified login mark: red star + 机密 text */
    showClassifiedLoginBadge: boolean;
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
	const backendFallback = "/workbench";
	const frontendFallback = "/workbench";

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

declare global {
    interface Window {
        __RUNTIME_CONFIG__?: {
            koalPkiEndpoints?: string[];
            enableSqlWorkbench?: string | boolean;
            enableSqlIdeV2?: string | boolean;
            platformBaseUrl?: string;
            allowedExternalRedirectHosts?: string[] | string;
            showClassifiedLoginBadge?: string | boolean;
        };
    }
}

const resolveKoalPkiEndpoints = (): string[] => {
    // 1) Prefer runtime-injected config (unified for admin & platform)
    try {
        const arr = (typeof window !== "undefined" && (window.__RUNTIME_CONFIG__?.koalPkiEndpoints)) || [];
        if (Array.isArray(arr) && arr.length > 0) return arr.map((s) => String(s).trim()).filter(Boolean);
    } catch {}
    // 2) Fall back to build-time env
    const raw = import.meta.env.VITE_KOAL_PKI_ENDPOINTS as any;
    if (typeof raw === "string") {
        return raw
            .split(",")
            .map((endpoint) => endpoint.trim())
            .filter(Boolean);
    }
    if (Array.isArray(raw)) {
        return raw.map((s) => String(s).trim()).filter(Boolean);
    }
    return [];
};

const resolveEnableSqlWorkbench = (): boolean => {
    try {
        const rc = (typeof window !== "undefined" && window.__RUNTIME_CONFIG__) || {};
        if (rc && typeof rc.enableSqlWorkbench !== "undefined") {
            const raw = String(rc.enableSqlWorkbench).trim().toLowerCase();
            if (raw === "true" || raw === "1") return true;
            if (raw === "false" || raw === "0") return false;
        }
    } catch {}
    return String(import.meta.env.VITE_ENABLE_SQL_WORKBENCH || "false").toLowerCase() === "true";
};

const resolveEnableSqlIdeV2 = (): boolean => {
    try {
        const rc = (typeof window !== "undefined" && window.__RUNTIME_CONFIG__) || {};
        if (rc && typeof rc.enableSqlIdeV2 !== "undefined") {
            const raw = String(rc.enableSqlIdeV2).trim().toLowerCase();
            if (raw === "true" || raw === "1") return true;
            if (raw === "false" || raw === "0") return false;
        }
    } catch {}
    return String(import.meta.env.VITE_ENABLE_SQL_IDE_V2 || "true").toLowerCase() === "true";
};

const parseBooleanFlag = (value: unknown): boolean | undefined => {
    if (typeof value === "boolean") return value;
    if (typeof value !== "string") return undefined;
    const normalized = value.trim().toLowerCase();
    if (["true", "1", "yes", "y", "on"].includes(normalized)) return true;
    if (["false", "0", "no", "n", "off"].includes(normalized)) return false;
    return undefined;
};

const resolveShowClassifiedLoginBadge = (): boolean => {
    try {
        const runtimeValue = typeof window !== "undefined" ? window.__RUNTIME_CONFIG__?.showClassifiedLoginBadge : undefined;
        const parsed = parseBooleanFlag(runtimeValue);
        if (typeof parsed === "boolean") return parsed;
    } catch {}
    const env = import.meta.env as Record<string, string | boolean | undefined>;
    return parseBooleanFlag(env.WEBAPP_SHOW_CLASSIFIED_LOGIN_BADGE ?? env.VITE_SHOW_CLASSIFIED_LOGIN_BADGE) ?? true;
};

export const GLOBAL_CONFIG: GlobalConfig = {
	appName: import.meta.env.VITE_APP_NAME || "BI数智平台",
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
    enableSqlWorkbench: resolveEnableSqlWorkbench(),
    enableSqlIdeV2: resolveEnableSqlIdeV2(),
    allowedLoginRoles: resolveAllowedLoginRoles(),
    koalPkiEndpoints: resolveKoalPkiEndpoints(),
    showClassifiedLoginBadge: resolveShowClassifiedLoginBadge(),
};
