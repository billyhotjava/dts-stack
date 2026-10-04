import { afterEach, describe, expect, it, vi } from "vitest";
import { buildExternalRedirectPath, resolveSafeExternalRedirectTarget } from "./externalRedirect";

describe("externalRedirect", () => {
	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it("builds a relay path that stays inside the platform router", () => {
		expect(buildExternalRedirectPath("https://meta.example.com/explore")).toBe(
			"/external-redirect?target=https%3A%2F%2Fmeta.example.com%2Fexplore",
		);
	});

	it("allows same-origin redirect targets", () => {
		expect(resolveSafeExternalRedirectTarget("/bi/screens/42/edit", "https://bi.example.com")).toBe(
			"https://bi.example.com/bi/screens/42/edit",
		);
	});

	it("allows configured cross-domain targets from runtime config", () => {
		vi.stubGlobal("window", {
			__RUNTIME_CONFIG__: {
			allowedExternalRedirectHosts: ["meta.example.com", "flow.example.com"],
			},
		});

		expect(resolveSafeExternalRedirectTarget("https://meta.example.com/explore/tables", "https://bi.example.com")).toBe(
			"https://meta.example.com/explore/tables",
		);
	});

	it("rejects untrusted hosts", () => {
		vi.stubGlobal("window", {
			__RUNTIME_CONFIG__: {
			allowedExternalRedirectHosts: ["meta.example.com"],
			},
		});

		expect(resolveSafeExternalRedirectTarget("https://evil.example.com/phish", "https://bi.example.com")).toBeNull();
	});
});
