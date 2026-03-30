import { getPlatformTokens } from "../api/platformSession";

const PUBLIC_ANALYTICS_PATH_PREFIXES = [
	"/public/card/",
	"/public/dashboard/",
	"/public/screen/",
] as const;

export function isPublicAnalyticsPath(pathname: string): boolean {
	const normalized = pathname.startsWith("/") ? pathname : `/${pathname}`;
	return PUBLIC_ANALYTICS_PATH_PREFIXES.some((prefix) => normalized.startsWith(prefix));
}

export function requiresPlatformSession(pathname: string): boolean {
	return !isPublicAnalyticsPath(pathname);
}

export function resolvePlatformBaseOrigin(runtimePlatformBase: string, currentOrigin: string): string {
	const runtimeBase = String(runtimePlatformBase || "").trim().replace(/\/+$/, "");
	if (!runtimeBase) {
		return currentOrigin;
	}
	try {
		return new URL(runtimeBase).origin;
	} catch {
		if (runtimeBase.startsWith("//")) {
			return new URL(`${window.location.protocol}${runtimeBase}`).origin;
		}
		return currentOrigin;
	}
}

export function buildPlatformLoginHref(
	pathname: string,
	search: string = "",
	runtimePlatformBase: string = "",
	currentOrigin: string = window.location.origin,
): string {
	const normalizedPath = pathname.startsWith("/") ? pathname : `/${pathname}`;
	const origin = resolvePlatformBaseOrigin(runtimePlatformBase, currentOrigin);
	const redirect = encodeURIComponent(`${normalizedPath}${search || ""}`);
	return `${origin}/#/auth/login?redirect=${redirect}`;
}

export function hasPlatformSessionAccessToken(): boolean {
	return Boolean(getPlatformTokens().accessToken);
}

export function readRuntimePlatformBase(): string {
	if (typeof window === "undefined") return "";
	const rc = (window as unknown as { __RUNTIME_CONFIG__?: { platformBaseUrl?: string } }).__RUNTIME_CONFIG__;
	return String(rc?.platformBaseUrl || "").trim();
}
