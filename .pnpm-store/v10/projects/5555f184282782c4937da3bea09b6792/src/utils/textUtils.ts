/**
 * Shared text-normalization and time-formatting helpers.
 *
 * Extracted from ~25 page-level local definitions to a single source of truth.
 */

/** Trim whitespace, coerce null/undefined to empty string. */
export function normalizeText(value?: string | null): string {
	return String(value ?? "").trim();
}

/**
 * Format a timestamp string for display (zh-CN, no seconds).
 * Returns "-" for falsy / unparseable input.
 */
export function formatTime(value?: string | number | null): string {
	if (!value) return "-";
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) return String(value);
	return date.toLocaleString("zh-CN", {
		year: "numeric",
		month: "2-digit",
		day: "2-digit",
		hour: "2-digit",
		minute: "2-digit",
		second: "2-digit",
		hour12: false,
	});
}

/**
 * Format a timestamp string for display (zh-CN, with seconds).
 * Returns "-" for falsy / unparseable input.
 */
export function formatDateTime(value?: string | number | null): string {
	if (!value) return "-";
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) return String(value);
	return date.toLocaleString("zh-CN", {
		year: "numeric",
		month: "2-digit",
		day: "2-digit",
		hour: "2-digit",
		minute: "2-digit",
		second: "2-digit",
		hour12: false,
	});
}
