import type { UserInfo, UserToken } from "#/entity";

type AnyRecord = Record<string, unknown>;

export type PersistedSessionSnapshot = {
	userInfo: Partial<UserInfo>;
	userToken: UserToken;
};

function asRecord(value: unknown): AnyRecord | null {
	return value && typeof value === "object" ? (value as AnyRecord) : null;
}

export function parsePersistedUserStoreSnapshot(raw: string | null | undefined): PersistedSessionSnapshot | null {
	if (!raw || !raw.trim()) return null;
	try {
		const parsed = JSON.parse(raw) as unknown;
		const root = asRecord(parsed);
		const state = asRecord(root?.state);
		const userInfo = (asRecord(state?.userInfo) ?? {}) as Partial<UserInfo>;
		const userToken = (asRecord(state?.userToken) ?? {}) as UserToken;
		if (!Object.keys(userInfo).length && !Object.keys(userToken).length) {
			return null;
		}
		return { userInfo, userToken };
	} catch {
		return null;
	}
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
