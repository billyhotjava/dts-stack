import assert from "node:assert/strict";
import test from "node:test";
import { hasPersistedSessionChanged, parsePersistedUserStoreSnapshot } from "./sessionSync.helpers";

test("parsePersistedUserStoreSnapshot returns token and user info from persisted zustand store", () => {
	const snapshot = parsePersistedUserStoreSnapshot(
		JSON.stringify({
			state: {
				userInfo: {
					username: "alice",
					roles: ["USER"],
				},
				userToken: {
					accessToken: "next-access-token",
					refreshToken: "next-refresh-token",
				},
			},
			version: 0,
		}),
	);

	assert.deepEqual(snapshot, {
		userInfo: {
			username: "alice",
			roles: ["USER"],
		},
		userToken: {
			accessToken: "next-access-token",
			refreshToken: "next-refresh-token",
		},
	});
});

test("hasPersistedSessionChanged detects rotated portal tokens from another tab", () => {
	const changed = hasPersistedSessionChanged(
		{
			userInfo: {
				username: "alice",
			},
			userToken: {
				accessToken: "old-access-token",
				refreshToken: "old-refresh-token",
			},
		},
		{
			userInfo: {
				username: "alice",
			},
			userToken: {
				accessToken: "new-access-token",
				refreshToken: "new-refresh-token",
			},
		},
	);

	assert.equal(changed, true);
});
