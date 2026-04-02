import { describe, expect, it } from "vitest";
import { parsePersistedUserStoreSnapshot, readPersistedUserStoreSnapshot } from "@dts-session-core/persisted-store";
import { parseLogoutBroadcast, serializeLogoutBroadcast } from "@dts-session-core/logout-broadcast";
import { createSessionStorageKeys, readStorageValue, writeLoginActivityMarkers } from "@dts-session-core/storage";

describe("session storage protocol", () => {
	it("namespaces app-specific storage keys", () => {
		expect(createSessionStorageKeys("platform").userStore).toBe("dts.platform.userStore");
		expect(createSessionStorageKeys("admin").userStore).toBe("dts.admin.userStore");
		expect(createSessionStorageKeys("platform").logoutTs).not.toBe(createSessionStorageKeys("admin").logoutTs);
	});

	it("reads user info and session from the persisted zustand store", () => {
		const storage = new Map<string, string>();
		const mockStorage = {
			getItem: (key: string) => storage.get(key) ?? null,
			setItem: (key: string, value: string) => storage.set(key, value),
			removeItem: (key: string) => storage.delete(key),
		};
		mockStorage.setItem(
			"dts.platform.userStore",
			JSON.stringify({
				state: {
					userInfo: { username: "alice", roles: ["ROLE_OP_ADMIN"] },
					session: { initialized: true, authenticated: true, browserId: "browser-1" },
				},
				version: 0,
			}),
		);

		expect(readPersistedUserStoreSnapshot("dts.platform.userStore", ["userStore"], mockStorage)).toEqual({
			userInfo: { username: "alice", roles: ["ROLE_OP_ADMIN"] },
			session: { initialized: true, authenticated: true, browserId: "browser-1" },
		});
		expect(parsePersistedUserStoreSnapshot(mockStorage.getItem("dts.platform.userStore"))).toEqual({
			userInfo: { username: "alice", roles: ["ROLE_OP_ADMIN"] },
			session: { initialized: true, authenticated: true, browserId: "browser-1" },
		});
	});

	it("still falls back to legacy store keys for user info during migration", () => {
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
					userInfo: { username: "legacy-admin" },
				},
				version: 0,
			}),
		);

		expect(readStorageValue("dts.platform.userStore", ["userStore"], mockStorage)).toContain("legacy-admin");
		expect(readPersistedUserStoreSnapshot("dts.platform.userStore", ["userStore"], mockStorage)).toEqual({
			userInfo: { username: "legacy-admin" },
			session: {},
		});
	});

	it("serializes and parses logout broadcasts with reasons", () => {
		const raw = serializeLogoutBroadcast("taken_over", 123456);

		expect(parseLogoutBroadcast(raw)).toEqual({
			ts: 123456,
			reason: "taken_over",
		});
	});

	it("parses legacy numeric logout broadcasts without a reason", () => {
		expect(parseLogoutBroadcast("123456")).toEqual({
			ts: 123456,
			reason: undefined,
		});
	});

	it("records both login and activity timestamps after a successful login", () => {
		const storage = new Map<string, string>();
		const mockStorage = {
			getItem: (key: string) => storage.get(key) ?? null,
			setItem: (key: string, value: string) => storage.set(key, value),
			removeItem: (key: string) => storage.delete(key),
		};
		const keys = createSessionStorageKeys("platform");

		writeLoginActivityMarkers(keys, 123456, mockStorage);

		expect(mockStorage.getItem(keys.loginTs)).toBe("123456");
		expect(mockStorage.getItem(keys.lastActivity)).toBe("123456");
	});
});
