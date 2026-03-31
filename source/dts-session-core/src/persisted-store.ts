import { readStorageValue, type StorageLike } from "./storage";

type AnyRecord = Record<string, unknown>;

export type PersistedUserStoreSnapshot = {
	userInfo: AnyRecord;
	userToken: AnyRecord;
};

function asRecord(value: unknown): AnyRecord | null {
	return value && typeof value === "object" ? (value as AnyRecord) : null;
}

function pickString(obj: AnyRecord | null | undefined, keys: string[]): string {
	if (!obj) return "";
	for (const key of keys) {
		const value = obj[key];
		if (typeof value === "string" && value.trim()) {
			return value.trim();
		}
	}
	return "";
}

export function parsePersistedUserStoreSnapshot(raw: string | null | undefined): PersistedUserStoreSnapshot | null {
	if (!raw || !raw.trim()) return null;
	try {
		const parsed = JSON.parse(raw) as unknown;
		const root = asRecord(parsed);
		const state = asRecord(root?.state);
		const userInfo = asRecord(state?.userInfo) ?? {};
		const userToken = asRecord(state?.userToken) ?? {};
		if (!Object.keys(userInfo).length && !Object.keys(userToken).length) {
			return null;
		}
		return { userInfo, userToken };
	} catch {
		return null;
	}
}

export function readPersistedUserStoreSnapshot(
	primaryStoreKey: string,
	legacyStoreKeys: string[] = [],
	storage: StorageLike | null | undefined = globalThis.localStorage,
): PersistedUserStoreSnapshot | null {
	return parsePersistedUserStoreSnapshot(readStorageValue(primaryStoreKey, legacyStoreKeys, storage));
}

export function readPersistedTokens(
	primaryStoreKey: string,
	legacyStoreKeys: string[] = [],
	storage: StorageLike | null | undefined = globalThis.localStorage,
) {
	const snapshot = readPersistedUserStoreSnapshot(primaryStoreKey, legacyStoreKeys, storage);
	const userToken = asRecord(snapshot?.userToken) ?? {};
	return {
		accessToken: pickString(userToken, ["accessToken", "access_token", "token"]),
		refreshToken: pickString(userToken, ["refreshToken", "refresh_token"]),
		adminAccessToken: pickString(userToken, ["adminAccessToken", "admin_access_token"]),
		adminRefreshToken: pickString(userToken, ["adminRefreshToken", "admin_refresh_token"]),
		adminAccessTokenExpiresAt: pickString(userToken, ["adminAccessTokenExpiresAt"]),
		adminRefreshTokenExpiresAt: pickString(userToken, ["adminRefreshTokenExpiresAt"]),
	};
}

export function readPersistedRoles(
	primaryStoreKey: string,
	legacyStoreKeys: string[] = [],
	storage: StorageLike | null | undefined = globalThis.localStorage,
): string[] {
	const snapshot = readPersistedUserStoreSnapshot(primaryStoreKey, legacyStoreKeys, storage);
	const userInfo = asRecord(snapshot?.userInfo) ?? {};
	const roles = userInfo.roles;
	if (!Array.isArray(roles)) return [];
	return roles.map((role) => String(role || "").trim()).filter(Boolean);
}
