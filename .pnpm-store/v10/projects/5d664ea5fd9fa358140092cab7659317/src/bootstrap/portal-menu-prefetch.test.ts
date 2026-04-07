import { describe, expect, it } from "vitest";
import { shouldPrefetchPortalMenus } from "./portal-menu-prefetch";

describe("shouldPrefetchPortalMenus", () => {
	it("returns false on login route without access token", () => {
		expect(shouldPrefetchPortalMenus("/auth/login", "")).toBe(false);
	});

	it("returns false on login route even when an access token exists", () => {
		expect(shouldPrefetchPortalMenus("/admin/auth/login", "token")).toBe(false);
	});

	it("returns false without access token on protected route", () => {
		expect(shouldPrefetchPortalMenus("/admin/users", "")).toBe(false);
	});

	it("returns true with access token away from login route", () => {
		expect(shouldPrefetchPortalMenus("/admin/users", "token")).toBe(true);
	});
});
