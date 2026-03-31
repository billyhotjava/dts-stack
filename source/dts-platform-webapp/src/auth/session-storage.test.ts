import { describe, expect, it } from "vitest";
import { parsePersistedUserStoreSnapshot, readPersistedTokens } from "@dts-session-core/persisted-store";
import { createSessionStorageKeys, readStorageValue } from "@dts-session-core/storage";

describe("session storage protocol", () => {
	it("namespaces app-specific storage keys", () => {
		expect(createSessionStorageKeys("platform").userStore).toBe("dts.platform.userStore");
		expect(createSessionStorageKeys("admin").userStore).toBe("dts.admin.userStore");
		expect(createSessionStorageKeys("platform").logoutTs).not.toBe(createSessionStorageKeys("admin").logoutTs);
	});

	it("falls back to legacy storage values during migration", () => {
		const storage = new Map<string, string>();
		const mockStorage = {
			getItem: (key: string) => storage.get(key) ?? null,
			setItem: (key: string, value: string) => storage.set(key, value),
			removeItem: (key: string) => storage.delete(key),
		};
		mockStorage.setItem(
			"userStore",
			JSON.stringify({
				state: {
					userInfo: { username: "alice", roles: ["ROLE_OP_ADMIN"] },
					userToken: { accessToken: "legacy-access", refreshToken: "legacy-refresh" },
				},
				version: 0,
			}),
		);

		expect(readStorageValue("dts.platform.userStore", ["userStore"], mockStorage)).toContain("legacy-access");
		expect(readPersistedTokens("dts.platform.userStore", ["userStore"], mockStorage)).toEqual({
			accessToken: "legacy-access",
			refreshToken: "legacy-refresh",
			adminAccessToken: "",
			adminRefreshToken: "",
			adminAccessTokenExpiresAt: "",
			adminRefreshTokenExpiresAt: "",
		});
		expect(parsePersistedUserStoreSnapshot(mockStorage.getItem("userStore"))?.userInfo.username).toBe("alice");
	});
});
