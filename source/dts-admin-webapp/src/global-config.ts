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
	/** Allowed roles to sign in; empty means allow all authenticated users */
	allowedLoginRoles: string[];
	/** Hide vendor branding in UI texts */
	hideKeycloakBranding: boolean;
	/** Hide built-in IAM roles (default-roles-*, offline_access, uma_authorization, realm-management, etc.) in selectors */
	hideBuiltinRoles: boolean;
	/** Hide default-roles-* from role catalogs/tables */
	hideDefaultRoles: boolean;
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
	const fallback = "/admin/my-changes";
	const rawDefaultRoute = import.meta.env.VITE_APP_DEFAULT_ROUTE || fallback;
	return ensureLeadingSlash(rawDefaultRoute, fallback);
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

const resolvePublicPath = () => {
	const rawPublicPath = import.meta.env.VITE_PUBLIC_PATH || import.meta.env.VITE_APP_PUBLIC_PATH || "/";
	if (isAbsoluteUrl(rawPublicPath)) {
		return removeTrailingSlash(rawPublicPath);
	}
	const withLeadingSlash = ensureLeadingSlash(rawPublicPath, "/");
	return removeTrailingSlash(withLeadingSlash);
};

const resolveAllowedLoginRoles = (): string[] => {
	const raw = (import.meta.env.VITE_ALLOWED_LOGIN_ROLES ||
		// default to admin-console super roles only
		"ROLE_SYS_ADMIN,ROLE_AUTH_ADMIN,ROLE_SECURITY_AUDITOR") as string;
	return String(raw)
		.split(",")
		.map((s) => s.trim())
		.filter(Boolean);
};

export const GLOBAL_CONFIG: GlobalConfig = {
	appName: import.meta.env.VITE_APP_NAME || "BI数智平台(机密)",
	appVersion: packageJson.version,
	defaultRoute: resolveDefaultRoute(),
	publicPath: resolvePublicPath(),
	apiBaseUrl: resolveApiBaseUrl(),
	routerMode: import.meta.env.VITE_APP_ROUTER_MODE || "frontend",
	allowedLoginRoles: resolveAllowedLoginRoles(),
	hideKeycloakBranding: String(import.meta.env.VITE_HIDE_KEYCLOAK_BRANDING ?? "true").toLowerCase() === "true",
	hideBuiltinRoles: String(import.meta.env.VITE_HIDE_BUILTIN_ROLES ?? "true").toLowerCase() === "true",
	hideDefaultRoles: String(import.meta.env.VITE_HIDE_DEFAULT_ROLES ?? "true").toLowerCase() === "true",
};
