// @vitest-environment jsdom
import { beforeEach, describe, expect, it } from "vitest";
import {
	PORTAL_SESSION_STORAGE_KEYS,
	isWithinPortalLoginGrace,
	markPortalSessionLogin,
	markPortalSessionLogout,
	wasPortalLogoutBroadcastRecently,
} from "./portalSessionStorage";

describe("portal session storage markers", () => {
	beforeEach(() => {
		localStorage.clear();
	});

	it("clears stale logout broadcasts when a new login is accepted", () => {
		localStorage.setItem(PORTAL_SESSION_STORAGE_KEYS.SESSION_ID, "old-session");
		localStorage.setItem(PORTAL_SESSION_STORAGE_KEYS.REFRESH_LEADER, "old-leader");
		markPortalSessionLogout(1_000, "old-token");

		markPortalSessionLogin(2_000);

		expect(localStorage.getItem(PORTAL_SESSION_STORAGE_KEYS.LOGOUT_TS)).toBeNull();
		expect(localStorage.getItem(PORTAL_SESSION_STORAGE_KEYS.SESSION_ID)).toBeNull();
		expect(localStorage.getItem(PORTAL_SESSION_STORAGE_KEYS.REFRESH_LEADER)).toBeNull();
		expect(localStorage.getItem(PORTAL_SESSION_STORAGE_KEYS.LOGIN_TS)).toBe("2000");
		expect(localStorage.getItem(PORTAL_SESSION_STORAGE_KEYS.LAST_ACTIVITY)).toBe("2000");
		expect(wasPortalLogoutBroadcastRecently(15_000, 2_100, "new-token")).toBe(false);
	});

	it("ignores a logout broadcast that is older than the current login", () => {
		markPortalSessionLogout(1_000, "token-1");
		localStorage.setItem(PORTAL_SESSION_STORAGE_KEYS.LOGIN_TS, "2000");

		expect(wasPortalLogoutBroadcastRecently(15_000, 2_500, "token-1")).toBe(false);
	});

	it("honors a logout broadcast that is newer than the current login", () => {
		localStorage.setItem(PORTAL_SESSION_STORAGE_KEYS.LOGIN_TS, "1000");
		markPortalSessionLogout(2_000, "token-1");

		expect(wasPortalLogoutBroadcastRecently(15_000, 2_500, "token-1")).toBe(true);
	});

	it("ignores a newer logout broadcast from a replaced token", () => {
		localStorage.setItem(PORTAL_SESSION_STORAGE_KEYS.LOGIN_TS, "1000");
		markPortalSessionLogout(2_000, "old-token");

		expect(wasPortalLogoutBroadcastRecently(15_000, 2_500, "new-token")).toBe(false);
	});

	it("reports login grace only inside the configured window", () => {
		markPortalSessionLogin(10_000);

		expect(isWithinPortalLoginGrace(5_000, 14_999)).toBe(true);
		expect(isWithinPortalLoginGrace(5_000, 15_000)).toBe(false);
	});
});
