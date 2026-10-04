export const PORTAL_SESSION_STORAGE_KEYS = {
	SESSION_ID: "dts.platform.session.id",
	SESSION_USER: "dts.platform.session.user",
	LOGIN_TS: "dts.platform.session.loginTs",
	LOGOUT_TS: "dts.platform.session.logoutTs",
	LAST_ACTIVITY: "dts.platform.session.lastActivity",
	TOKEN_SYNC: "dts.platform.session.tokenSync",
	REFRESH_LEADER: "dts.platform.session.refreshLeader",
} as const;

type PortalSessionStorageKey =
	(typeof PORTAL_SESSION_STORAGE_KEYS)[keyof typeof PORTAL_SESSION_STORAGE_KEYS];

type LogoutMarker = {
	ts: number;
	accessToken?: string;
};

function normalizeAccessToken(accessToken?: string | null): string {
	const raw = String(accessToken || "").trim();
	return raw.startsWith("Bearer ") ? raw.slice(7).trim() : raw;
}

function parseLogoutMarker(raw: string | null): LogoutMarker {
	if (!raw) {
		return { ts: 0 };
	}
	try {
		const parsed = JSON.parse(raw) as Partial<LogoutMarker>;
		const ts = Number(parsed?.ts || 0);
		return {
			ts: Number.isFinite(ts) && ts > 0 ? ts : 0,
			accessToken: normalizeAccessToken(parsed?.accessToken),
		};
	} catch {
		const ts = Number(raw);
		return { ts: Number.isFinite(ts) && ts > 0 ? ts : 0 };
	}
}

export function readPortalSessionTimestamp(key: PortalSessionStorageKey): number {
	try {
		if (key === PORTAL_SESSION_STORAGE_KEYS.LOGOUT_TS) {
			return parseLogoutMarker(localStorage.getItem(key)).ts;
		}
		const value = Number(localStorage.getItem(key) || "0");
		return Number.isFinite(value) && value > 0 ? value : 0;
	} catch {
		return 0;
	}
}

export function markPortalSessionLogin(now = Date.now()) {
	try {
		localStorage.removeItem(PORTAL_SESSION_STORAGE_KEYS.LOGOUT_TS);
		localStorage.removeItem(PORTAL_SESSION_STORAGE_KEYS.SESSION_ID);
		localStorage.removeItem(PORTAL_SESSION_STORAGE_KEYS.SESSION_USER);
		localStorage.removeItem(PORTAL_SESSION_STORAGE_KEYS.TOKEN_SYNC);
		localStorage.removeItem(PORTAL_SESSION_STORAGE_KEYS.REFRESH_LEADER);
		localStorage.setItem(PORTAL_SESSION_STORAGE_KEYS.LOGIN_TS, String(now));
		localStorage.setItem(PORTAL_SESSION_STORAGE_KEYS.LAST_ACTIVITY, String(now));
	} catch {
		// ignore storage failures
	}
}

export function markPortalSessionLogout(now = Date.now(), accessToken?: string | null) {
	try {
		const token = normalizeAccessToken(accessToken);
		const marker: LogoutMarker = token ? { ts: now, accessToken: token } : { ts: now };
		localStorage.setItem(PORTAL_SESSION_STORAGE_KEYS.LOGOUT_TS, JSON.stringify(marker));
	} catch {
		// ignore storage failures
	}
}

export function clearPortalSessionLoginMarkers() {
	try {
		localStorage.removeItem(PORTAL_SESSION_STORAGE_KEYS.LOGIN_TS);
		localStorage.removeItem(PORTAL_SESSION_STORAGE_KEYS.LAST_ACTIVITY);
	} catch {
		// ignore storage failures
	}
}

export function isWithinPortalLoginGrace(graceMs: number, now = Date.now()): boolean {
	const loginTs = readPortalSessionTimestamp(PORTAL_SESSION_STORAGE_KEYS.LOGIN_TS);
	return loginTs > 0 && now - loginTs < graceMs;
}

export function wasPortalLogoutBroadcastRecently(
	windowMs: number,
	now = Date.now(),
	currentAccessToken?: string | null,
): boolean {
	let marker: LogoutMarker;
	try {
		marker = parseLogoutMarker(localStorage.getItem(PORTAL_SESSION_STORAGE_KEYS.LOGOUT_TS));
	} catch {
		marker = { ts: 0 };
	}
	const logoutTs = marker.ts;
	if (logoutTs <= 0 || now - logoutTs >= windowMs) {
		return false;
	}
	const currentToken = normalizeAccessToken(currentAccessToken);
	if (marker.accessToken && currentToken && marker.accessToken !== currentToken) {
		return false;
	}
	const loginTs = readPortalSessionTimestamp(PORTAL_SESSION_STORAGE_KEYS.LOGIN_TS);
	return logoutTs > loginTs;
}
