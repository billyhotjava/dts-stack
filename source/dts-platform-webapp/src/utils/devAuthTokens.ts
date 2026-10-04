const DEV_TOKEN_SCOPE = "dev";

const buildDevTokenPrefix = (kind: "access" | "refresh"): string => `${[DEV_TOKEN_SCOPE, kind].join("-")}-`;

export const buildDevFallbackToken = (kind: "access" | "refresh", normalizedUsername: string): string =>
	`${buildDevTokenPrefix(kind)}${normalizedUsername}-${Date.now()}`;

export const isDevFallbackAccessToken = (token?: string | null): boolean =>
	typeof token === "string" && token.startsWith(buildDevTokenPrefix("access"));

export const isDevFallbackAllowedHost = (): boolean => {
	if (typeof window === "undefined") {
		return false;
	}
	const host = window.location.hostname || "";
	return host === "localhost" || host === "127.0.0.1" || host === "::1" || host.endsWith(".local");
};
