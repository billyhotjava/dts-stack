import { afterEach, describe, expect, it, vi } from "vitest";
import { LOGIN_ROUTE, resolveCurrentAppPath, resolveLoginHref } from "./constants";

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
