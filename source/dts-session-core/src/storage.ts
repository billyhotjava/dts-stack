export type SessionStorageKeys = {
	prefix: string;
	userStore: string;
	sessionId: string;
	sessionUser: string;
	logoutTs: string;
	lastActivity: string;
	loginTs: string;
};

export type StorageLike = Pick<Storage, "getItem" | "setItem" | "removeItem">;

function trimKey(key: string): string {
	return String(key || "").trim();
}

export function createSessionStorageKeys(domain: string): SessionStorageKeys {
	const normalizedDomain = trimKey(domain).replace(/\.+/g, ".").replace(/^\.+|\.+$/g, "") || "app";
	const prefix = `dts.${normalizedDomain}`;
	return {
		prefix,
		userStore: `${prefix}.userStore`,
		sessionId: `${prefix}.session.id`,
		sessionUser: `${prefix}.session.user`,
		logoutTs: `${prefix}.session.logoutTs`,
		lastActivity: `${prefix}.session.lastActivity`,
		loginTs: `${prefix}.session.loginTs`,
	};
}

export function readStorageValue(
	primaryKey: string,
	legacyKeys: string[] = [],
	storage: StorageLike | null | undefined = globalThis.localStorage,
): string | null {
	if (!storage) return null;
	const normalizedPrimary = trimKey(primaryKey);
	const candidates = [normalizedPrimary, ...legacyKeys.map(trimKey)].filter(Boolean);
	for (const key of candidates) {
		try {
			const value = storage.getItem(key);
			if (typeof value === "string" && value.length > 0) {
				return value;
			}
		} catch {
			return null;
		}
	}
	return null;
}

export function removeStorageKeys(
	keys: string[],
	storage: StorageLike | null | undefined = globalThis.localStorage,
): void {
	if (!storage) return;
	for (const key of keys.map(trimKey).filter(Boolean)) {
		try {
			storage.removeItem(key);
		} catch {
			return;
		}
	}
}
