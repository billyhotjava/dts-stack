/**
 * Sprint-15 F5 — Shared relative-time helper.
 *
 * Renders a human-readable "x 分钟前 / x 小时前 / x 天前" string.
 * Future-dated values and NaN inputs fall back to the raw string to avoid
 * negative "前" phrasing.
 */
export function relativeTime(value?: string | null): string {
	if (!value) return "";
	const diff = Date.now() - new Date(value).getTime();
	if (diff < 0 || Number.isNaN(diff)) return value;
	const mins = Math.floor(diff / 60_000);
	if (mins < 1) return "刚刚";
	if (mins < 60) return `${mins} 分钟前`;
	const hours = Math.floor(mins / 60);
	if (hours < 24) return `${hours} 小时前`;
	const days = Math.floor(hours / 24);
	return `${days} 天前`;
}
