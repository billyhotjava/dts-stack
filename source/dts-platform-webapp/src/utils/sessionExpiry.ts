type SessionExpirySource = {
	portalExpiresAt?: unknown;
	portalExpiresIn?: unknown;
	tokenExpiresAt?: unknown;
	expiresIn?: unknown;
};

function readPositiveNumber(value: unknown): number | undefined {
	if (typeof value === "number" && Number.isFinite(value) && value > 0) {
		return value;
	}
	if (typeof value === "string" && value.trim()) {
		const parsed = Number(value);
		if (Number.isFinite(parsed) && parsed > 0) {
			return parsed;
		}
	}
	return undefined;
}

function parseDateLike(value: unknown): number | undefined {
	if (value instanceof Date && Number.isFinite(value.getTime())) {
		return value.getTime();
	}
	const numeric = readPositiveNumber(value);
	if (numeric) {
		return numeric > 1_000_000_000_000 ? numeric : numeric * 1000;
	}
	if (typeof value === "string" && value.trim()) {
		const parsed = Date.parse(value.trim());
		if (Number.isFinite(parsed)) {
			return parsed;
		}
	}
	return undefined;
}

export function resolvePortalTokenExpiresAt(
	source?: SessionExpirySource | null,
	fallback?: number,
): number | undefined {
	const portalExpiresAt = parseDateLike(source?.portalExpiresAt);
	if (portalExpiresAt && portalExpiresAt > 0) {
		return portalExpiresAt;
	}
	const portalExpiresIn = readPositiveNumber(source?.portalExpiresIn);
	if (portalExpiresIn) {
		return Date.now() + portalExpiresIn * 1000;
	}
	const tokenExpiresAt = parseDateLike(source?.tokenExpiresAt);
	if (tokenExpiresAt && tokenExpiresAt > 0) {
		return tokenExpiresAt;
	}
	const expiresIn = readPositiveNumber(source?.expiresIn);
	if (expiresIn) {
		return Date.now() + expiresIn * 1000;
	}
	if (typeof fallback === "number" && Number.isFinite(fallback) && fallback > 0) {
		return fallback;
	}
	return undefined;
}
