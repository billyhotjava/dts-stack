import { describe, expect, it } from "vitest";
import { currentRoutePath, buildLoginRedirectHref } from "@dts-session-core/route";
import { decodeJwtExp, nextRefreshDelayMs } from "@dts-session-core/token";
import { createSessionStorageKeys } from "@dts-session-core/storage";

describe("session-core: route", () => {
	it("currentRoutePath reads hash in hash-router mode", () => {
		const loc = { hash: "#/bi/gpmc/drill/execution", pathname: "/", search: "" };
		expect(currentRoutePath("hash", loc)).toBe("/bi/gpmc/drill/execution");
	});

	it("currentRoutePath reads pathname in browser-router mode", () => {
		const loc = { hash: "", pathname: "/admin/dashboard", search: "?tab=1" };
		expect(currentRoutePath("browser", loc)).toBe("/admin/dashboard?tab=1");
	});

	it("currentRoutePath returns / for empty hash", () => {
		const loc = { hash: "", pathname: "/", search: "" };
		expect(currentRoutePath("hash", loc)).toBe("/");
	});

	it("buildLoginRedirectHref appends ?redirect= with encoded path", () => {
		const href = buildLoginRedirectHref("/#/auth/login", "/bi/gpmc/drill/execution");
		expect(href).toBe("/#/auth/login?redirect=%2Fbi%2Fgpmc%2Fdrill%2Fexecution");
	});

	it("buildLoginRedirectHref uses & if login href already has params", () => {
		const href = buildLoginRedirectHref("/#/auth/login?mode=admin", "/workbench");
		expect(href).toBe("/#/auth/login?mode=admin&redirect=%2Fworkbench");
	});
});

describe("session-core: token", () => {
	function makeJwt(exp: number): string {
		const header = btoa(JSON.stringify({ alg: "RS256" }));
		const payload = btoa(JSON.stringify({ exp }));
		return `${header}.${payload}.signature`;
	}

	it("decodeJwtExp extracts exp claim in milliseconds", () => {
		const expSec = 1700000000;
		expect(decodeJwtExp(makeJwt(expSec))).toBe(expSec * 1000);
	});

	it("decodeJwtExp returns null for non-JWT", () => {
		expect(decodeJwtExp("dev-access-token")).toBe(null);
		expect(decodeJwtExp(undefined)).toBe(null);
		expect(decodeJwtExp("")).toBe(null);
	});

	it("nextRefreshDelayMs returns at least 30s", () => {
		// Token expiring in 10 seconds → would compute negative, clamped to 30s
		const exp = Math.floor(Date.now() / 1000) + 10;
		expect(nextRefreshDelayMs(makeJwt(exp))).toBe(30_000);
	});

	it("nextRefreshDelayMs returns exp-now-60s for future token", () => {
		const exp = Math.floor(Date.now() / 1000) + 300; // 5 min from now
		const delay = nextRefreshDelayMs(makeJwt(exp));
		// Should be roughly 240s (300 - 60), allow 2s tolerance
		expect(delay).toBeGreaterThan(238_000);
		expect(delay).toBeLessThan(242_000);
	});

	it("nextRefreshDelayMs returns 4min default for non-JWT", () => {
		expect(nextRefreshDelayMs("opaque-token")).toBe(4 * 60 * 1000);
		expect(nextRefreshDelayMs(undefined)).toBe(4 * 60 * 1000);
	});
});

describe("session-core: storage isolation", () => {
	it("platform and admin keys do not overlap", () => {
		const platform = createSessionStorageKeys("platform");
		const admin = createSessionStorageKeys("admin");

		expect(platform.sessionId).toBe("dts.platform.session.id");
		expect(admin.sessionId).toBe("dts.admin.session.id");

		// No key should appear in both namespaces
		const platformValues = Object.values(platform);
		const adminValues = new Set(Object.values(admin));
		for (const key of platformValues) {
			expect(adminValues.has(key)).toBe(false);
		}
	});

	it("logoutTs broadcast in platform does not affect admin", () => {
		const platform = createSessionStorageKeys("platform");
		const admin = createSessionStorageKeys("admin");
		expect(platform.logoutTs).toBe("dts.platform.session.logoutTs");
		expect(admin.logoutTs).toBe("dts.admin.session.logoutTs");
		expect(platform.logoutTs).not.toBe(admin.logoutTs);
	});
});
