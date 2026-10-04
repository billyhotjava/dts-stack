const EXTERNAL_REDIRECT_ROUTE = "/external-redirect";
const MAX_TARGET_LENGTH = 4096;

type HostRule = {
	hostname: string;
	port: string | null;
};

function normalizeHostRule(raw: string): HostRule | null {
	const trimmed = String(raw || "").trim();
	if (!trimmed) return null;
	const candidate = /^[a-zA-Z][a-zA-Z\d+.-]*:\/\//.test(trimmed) ? trimmed : `https://${trimmed}`;
	try {
		const url = new URL(candidate);
		return {
			hostname: url.hostname.toLowerCase(),
			port: url.port || null,
		};
	} catch {
		return null;
	}
}

function runtimeAllowedHosts(): string[] {
	if (typeof window === "undefined") return [];
	const raw = window.__RUNTIME_CONFIG__?.allowedExternalRedirectHosts;
	if (Array.isArray(raw)) {
		return raw.map((item) => String(item || "").trim()).filter(Boolean);
	}
	if (typeof raw === "string") {
		return raw
			.split(",")
			.map((item) => item.trim())
			.filter(Boolean);
	}
	return [];
}

function envAllowedHosts(): string[] {
	const raw = String(import.meta.env.VITE_ALLOWED_EXTERNAL_REDIRECT_HOSTS || "").trim();
	if (!raw) return [];
	return raw
		.split(",")
		.map((item) => item.trim())
		.filter(Boolean);
}

function allowedHostRules(currentOrigin: string): HostRule[] {
	const current = normalizeHostRule(currentOrigin);
	const extras = [...runtimeAllowedHosts(), ...envAllowedHosts()].map(normalizeHostRule).filter(Boolean) as HostRule[];
	return current ? [current, ...extras] : extras;
}

function effectivePort(url: URL): string {
	if (url.port) return url.port;
	return url.protocol === "http:" ? "80" : "443";
}

function isAllowedUrl(url: URL, currentOrigin: string): boolean {
	const rules = allowedHostRules(currentOrigin);
	const hostname = url.hostname.toLowerCase();
	const port = effectivePort(url);
	return rules.some((rule) => rule.hostname === hostname && (!rule.port || rule.port === port));
}

export function buildExternalRedirectPath(target: string): string {
	return `${EXTERNAL_REDIRECT_ROUTE}?target=${encodeURIComponent(target)}`;
}

export function resolveSafeExternalRedirectTarget(rawTarget: string | null | undefined, currentOrigin?: string): string | null {
	const target = String(rawTarget || "").trim();
	if (!target || target.length > MAX_TARGET_LENGTH) return null;
	const origin = currentOrigin || (typeof window !== "undefined" ? window.location.origin : "");
	if (!origin) return null;
	try {
		const url = target.startsWith("/") ? new URL(target, origin) : new URL(target);
		if (url.protocol !== "https:" && url.protocol !== "http:") {
			return null;
		}
		if (!isAllowedUrl(url, origin)) {
			return null;
		}
		return url.toString();
	} catch {
		return null;
	}
}

export { EXTERNAL_REDIRECT_ROUTE };
