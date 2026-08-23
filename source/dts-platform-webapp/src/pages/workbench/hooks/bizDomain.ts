/**
 * Sprint-15 P0-16 / P1-10 — Single source of truth for biz-domain UI labels.
 *
 * The backend uses two synthetic sentinels (`__OTHER__`, `__UNCATEGORIZED__`)
 * to bucket reports/visits that have no real domain code. Without a shared
 * mapping these strings would leak straight into the UI as Tag labels.
 * Components should always go through `humanizeBizDomain` before rendering
 * a domain string.
 */

export const BIZ_DOMAIN_OTHER = "__OTHER__";
export const BIZ_DOMAIN_UNCATEGORIZED = "__UNCATEGORIZED__";

const SENTINEL_LABELS: Record<string, string> = {
	[BIZ_DOMAIN_OTHER]: "其他",
	[BIZ_DOMAIN_UNCATEGORIZED]: "未分类",
};

export function humanizeBizDomain(
	raw: string | null | undefined,
	domainLabels?: Readonly<Record<string, string>>,
): string | null {
	if (raw == null) return null;
	const trimmed = raw.trim();
	if (trimmed.length === 0) return null;
	const sentinelLabel = SENTINEL_LABELS[trimmed];
	if (sentinelLabel) return sentinelLabel;
	if (domainLabels) return domainLabels[trimmed] ?? "未命名业务域";
	return trimmed;
}

export function isBizDomainSentinel(raw: string | null | undefined): boolean {
	return raw === BIZ_DOMAIN_OTHER || raw === BIZ_DOMAIN_UNCATEGORIZED;
}
