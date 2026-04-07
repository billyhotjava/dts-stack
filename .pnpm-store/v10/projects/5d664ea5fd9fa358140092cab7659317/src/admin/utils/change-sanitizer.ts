const ROLE_RESOURCE_TYPES = new Set(["ROLE", "CUSTOM_ROLE"]);

const HIDDEN_CHANGE_FIELDS = new Set([
	"operations",
	"permissions",
	"maxrows",
	"allowdesensitizejson",
	"allowdesensitize",
	"allowdesensitizeflag",
]);

function normalizeKey(key?: string | null): string {
	return key ? key.trim().toLowerCase() : "";
}

export function isFieldHiddenForResource(field?: string, resourceType?: string | null): boolean {
	if (!ROLE_RESOURCE_TYPES.has(String(resourceType ?? "").toUpperCase())) {
		return false;
	}
	const normalized = normalizeKey(field);
	return normalized !== "" && HIDDEN_CHANGE_FIELDS.has(normalized);
}

function sanitizeValueInternal(value: unknown, resourceType?: string | null): unknown {
	if (value == null) {
		return value;
	}
	if (Array.isArray(value)) {
		return value.map((entry) => sanitizeValueInternal(entry, resourceType));
	}
	if (typeof value === "object") {
		const result: Record<string, unknown> = {};
		for (const [key, entry] of Object.entries(value as Record<string, unknown>)) {
			if (HIDDEN_CHANGE_FIELDS.has(normalizeKey(key))) {
				continue;
			}
			result[key] = sanitizeValueInternal(entry, resourceType);
		}
		return result;
	}
	return value;
}

export function sanitizeChangePayload<T>(value: T, resourceType?: string | null): T {
	if (value == null || typeof value !== "object") {
		return value;
	}
	const normalizedType = String(resourceType ?? "").toUpperCase();
	if (!ROLE_RESOURCE_TYPES.has(normalizedType)) {
		return value;
	}
	return sanitizeValueInternal(value, resourceType) as T;
}
