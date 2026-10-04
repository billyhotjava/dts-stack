import { afterEach, describe, expect, it, vi } from "vitest";
import { LOGIN_ROUTE, resolveCurrentAppPath, resolveLoginHref, resolvePostLoginRedirect } from "./constants";

describe("route constants", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("builds a login href with an encoded safe redirect", () => {
		expect(resolveLoginHref("/bi/screens/42?tab=canvas")).toContain(
			`${LOGIN_ROUTE}?redirect=%2Fbi%2Fscreens%2F42%3Ftab%3Dcanvas`,
		);
	});

	it("drops unsafe absolute redirect targets", () => {
		expect(resolveLoginHref("https://evil.example.com/phish")).toBe(resolveLoginHref());
	});

	it("does not preserve login pages as redirect targets", () => {
		expect(resolveLoginHref(LOGIN_ROUTE)).toBe(resolveLoginHref());
		expect(resolveLoginHref(`${LOGIN_ROUTE}?redirect=%2Fworkbench`)).toBe(resolveLoginHref());
		expect(resolvePostLoginRedirect(LOGIN_ROUTE)).toBe("/workbench");
		expect(resolvePostLoginRedirect(`${LOGIN_ROUTE}?redirect=%2Fbi`)).toBe("/workbench");
	});

	it("preserves a safe business route after login", () => {
		expect(resolvePostLoginRedirect("/bi/screens/42?tab=canvas")).toBe("/bi/screens/42?tab=canvas");
	});

	it("captures the current browser path for post-login return", () => {
		vi.stubGlobal("window", {
			location: {
				pathname: "/bi/screens/42",
				search: "?tab=canvas",
				hash: "#edit",
			},
		});

		expect(resolveCurrentAppPath()).toBe("/bi/screens/42?tab=canvas#edit");
	});
});
