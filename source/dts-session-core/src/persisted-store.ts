import { readStorageValue, type StorageLike } from "./storage";

type AnyRecord = Record<string, unknown>;

export type PersistedUserStoreSnapshot = {
	userInfo: AnyRecord;
	session: AnyRecord;
};

function asRecord(value: unknown): AnyRecord | null {
	return value && typeof value === "object" ? (value as AnyRecord) : null;
}

export function parsePersistedUserStoreSnapshot(raw: string | null | undefined): PersistedUserStoreSnapshot | null {
	if (!raw || !raw.trim()) return null;
	try {
		const parsed = JSON.parse(raw) as unknown;
		const root = asRecord(parsed);
		const state = asRecord(root?.state);
		const userInfo = asRecord(state?.userInfo) ?? {};
		const session = asRecord(state?.session) ?? {};
		if (!Object.keys(userInfo).length && !Object.keys(session).length) {
			return null;
		}
		return { userInfo, session };
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
