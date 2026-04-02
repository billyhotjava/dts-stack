import { describe, expect, it } from "vitest";
import { currentRoutePath, buildLoginRedirectHref } from "@dts/session-core/route";
import { createSessionStorageKeys } from "@dts/session-core/storage";

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

describe("session-core: storage isolation", () => {
	it("platform and admin keys do not overlap", () => {
		const platform = createSessionStorageKeys("platform");
		const admin = createSessionStorageKeys("admin");

		expect(platform.sessionId).toBe("dts.platform.session.id");
		expect(admin.sessionId).toBe("dts.admin.session.id");

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
