export type SessionLeaderLease = {
	tabId: string;
	expiresAt: number;
};

export function parseSessionLeaderLease(raw: string | null | undefined): SessionLeaderLease | null {
	if (!raw) return null;
	try {
		const parsed = JSON.parse(raw);
		if (
			parsed &&
			typeof parsed === "object" &&
			typeof parsed.tabId === "string" &&
			parsed.tabId &&
			typeof parsed.expiresAt === "number" &&
			Number.isFinite(parsed.expiresAt)
		) {
			return { tabId: parsed.tabId, expiresAt: parsed.expiresAt };
		}
	} catch {}
	return null;
}

export function buildSessionLeaderLease(tabId: string, now: number, leaseMs: number): SessionLeaderLease {
	return {
		tabId,
		expiresAt: now + Math.max(1, leaseMs),
	};
}

export function shouldAcquireSessionLeadership(
	lease: SessionLeaderLease | null | undefined,
	tabId: string,
	now: number,
): boolean {
	if (!tabId) return false;
	if (!lease) return true;
	if (lease.tabId === tabId) return true;
	return lease.expiresAt <= now;
}

export function isSessionLeaderActive(lease: SessionLeaderLease | null | undefined, now: number): boolean {
	if (!lease) return false;
	return lease.expiresAt > now;
}
