function decodeJwtPayload(token?: string): Record<string, unknown> | null {
	if (!token) return null;
	try {
		const parts = token.split(".");
		if (parts.length < 2) return null;
		let payload = parts[1].replace(/-/g, "+").replace(/_/g, "/");
		while (payload.length % 4 !== 0) payload += "=";
		const json = atob(payload);
		return JSON.parse(json) as Record<string, unknown>;
	} catch {
		return null;
	}
}

export function decodeJwtExp(token?: string): number | null {
	const payload = decodeJwtPayload(token);
	return typeof payload?.exp === "number" ? payload.exp * 1000 : null;
}

export function nextRefreshDelayMs(accessToken?: string): number {
	const minDelayMs = 30_000;
	const defaultDelayMs = 4 * 60 * 1000;
	const skewMs = 60_000;
	const expMs = decodeJwtExp(accessToken);
	if (!expMs) return defaultDelayMs;
	return Math.max(minDelayMs, expMs - Date.now() - skewMs);
}
