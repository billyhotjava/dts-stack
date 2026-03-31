import type { UserInfo, UserToken } from "#/entity";
import { parsePersistedUserStoreSnapshot as parsePersistedSnapshot } from "@dts-session-core/persisted-store";

type AnyRecord = Record<string, unknown>;

export type PersistedSessionSnapshot = {
	userInfo: Partial<UserInfo>;
	userToken: UserToken;
};

function asRecord(value: unknown): AnyRecord | null {
	return value && typeof value === "object" ? (value as AnyRecord) : null;
}

export function parsePersistedUserStoreSnapshot(raw: string | null | undefined): PersistedSessionSnapshot | null {
	const snapshot = parsePersistedSnapshot(raw);
	if (!snapshot) return null;
	return {
		userInfo: (asRecord(snapshot.userInfo) ?? {}) as Partial<UserInfo>,
		userToken: (asRecord(snapshot.userToken) ?? {}) as UserToken,
	};
}

function normalizeTokenValue(value: unknown): string {
	return typeof value === "string" ? value.trim() : "";
}

export function hasPersistedSessionChanged(
	current: PersistedSessionSnapshot | null | undefined,
	next: PersistedSessionSnapshot | null | undefined,
): boolean {
	if (!next) return false;
	const currentToken = current?.userToken ?? {};
	const nextToken = next.userToken ?? {};
	if (
		normalizeTokenValue(currentToken.accessToken) !== normalizeTokenValue(nextToken.accessToken) ||
		normalizeTokenValue(currentToken.refreshToken) !== normalizeTokenValue(nextToken.refreshToken) ||
		normalizeTokenValue(currentToken.adminAccessToken) !== normalizeTokenValue(nextToken.adminAccessToken) ||
		normalizeTokenValue(currentToken.adminRefreshToken) !== normalizeTokenValue(nextToken.adminRefreshToken) ||
		normalizeTokenValue(currentToken.adminAccessTokenExpiresAt) !== normalizeTokenValue(nextToken.adminAccessTokenExpiresAt) ||
		normalizeTokenValue(currentToken.adminRefreshTokenExpiresAt) !== normalizeTokenValue(nextToken.adminRefreshTokenExpiresAt)
	) {
		return true;
	}
	const currentUser = current?.userInfo ?? {};
	const nextUser = next.userInfo ?? {};
	return normalizeTokenValue(currentUser.username) !== normalizeTokenValue(nextUser.username);
}
