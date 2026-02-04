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

const getOrigin = () => {
	if (typeof window === "undefined") return "";
	return window.location.origin;
};

const toAbsolute = (path: string) => {
	const origin = getOrigin();
	if (!origin) return path;
	if (!path.startsWith("/")) return `${origin}/${path}`;
	return `${origin}${path}`;
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
			return preferAbsolute ? toAbsolute(path || "/") : (path || "/");
		}
		return trimmed;
	} catch {
		return trimmed;
	}
};

export const normalizeBiLinkForSave = (raw?: string | null, engine?: string | null) => {
	const text = String(raw || "").trim();
	if (!text) return "";
	const normalizedEngine = String(engine || "").trim().toUpperCase();
	if (normalizedEngine !== "HETU") return text;
	return normalizeHetuUrl(text, true);
};

export const resolveBiLinkForOpen = (raw?: string | null, engine?: string | null) => {
	const text = String(raw || "").trim();
	if (!text) return "";
	const normalizedEngine = String(engine || "").trim().toUpperCase();
	if (normalizedEngine !== "HETU") {
		return text.startsWith("/") ? toAbsolute(text) : text;
	}
	const resolved = normalizeHetuUrl(text, true);
	return resolved.startsWith("/") ? toAbsolute(resolved) : resolved;
};
