export type LogoutBroadcastReason =
	| "logged_out"
	| "expired"
	| "taken_over";

export type LogoutBroadcastPayload = {
	ts: number;
	reason?: LogoutBroadcastReason;
};

export function serializeLogoutBroadcast(
	reason?: LogoutBroadcastReason,
	ts = Date.now(),
): string {
	return JSON.stringify({
		ts,
		...(reason ? { reason } : {}),
	});
}

export function parseLogoutBroadcast(raw: string | null | undefined): LogoutBroadcastPayload | null {
	if (!raw) return null;
	try {
		const parsed = JSON.parse(raw) as Record<string, unknown> | number;
		if (typeof parsed === "number") {
			const ts = Number(parsed);
			if (!Number.isFinite(ts) || ts <= 0) {
				return null;
			}
			return { ts };
		}
		const ts = Number(parsed.ts ?? 0);
		if (!Number.isFinite(ts) || ts <= 0) {
			return null;
		}
		const reason = typeof parsed.reason === "string" ? parsed.reason.trim() as LogoutBroadcastReason : undefined;
		return reason ? { ts, reason } : { ts };
	} catch {
		const ts = Number(raw);
		if (!Number.isFinite(ts) || ts <= 0) {
			return null;
		}
		return { ts };
	}
}
