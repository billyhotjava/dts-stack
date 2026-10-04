// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from "vitest";

const config = vi.hoisted(() => ({
	routerHistory: "hash" as "browser" | "hash",
	publicPath: "/",
}));

vi.mock("@/global-config", () => ({
	GLOBAL_CONFIG: config,
}));

import { normalizeBiLinkForSave, resolveBiLinkForOpen } from "./biLinkUrl";

describe("resolveBiLinkForOpen", () => {
	beforeEach(() => {
		config.routerHistory = "hash";
		config.publicPath = "/";
		(window as unknown as { __RUNTIME_CONFIG__?: unknown }).__RUNTIME_CONFIG__ = undefined;
	});

	it("uses_hash_router_href_for_internal_bi_screen_paths", () => {
		expect(resolveBiLinkForOpen("/bi/screens/42/preview", "DTS_BI")).toBe("/#/bi/screens/42/preview");
	});

	it("uses_hash_router_href_for_internal_bi_screen_paths_without_leading_slash", () => {
		expect(resolveBiLinkForOpen("bi/screens/42/preview", "DTS_BI")).toBe("/#/bi/screens/42/preview");
	});

	it("uses_hash_router_href_for_same_origin_internal_urls", () => {
		expect(resolveBiLinkForOpen(`${window.location.origin}/bi/screens/42/preview`, "DTS_BI")).toBe(
			"/#/bi/screens/42/preview",
		);
	});

	it("keeps_external_urls_unchanged", () => {
		expect(resolveBiLinkForOpen("https://example.com/report/42", "TABLEAU")).toBe("https://example.com/report/42");
	});

	it("redirects_hetu_entry_pages_to_internal_bi_route", () => {
		expect(resolveBiLinkForOpen("/screen", "HETU")).toBe("/#/bi");
	});
});

describe("normalizeBiLinkForSave", () => {
	beforeEach(() => {
		config.routerHistory = "hash";
		config.publicPath = "/";
		(window as unknown as { __RUNTIME_CONFIG__?: unknown }).__RUNTIME_CONFIG__ = undefined;
	});

	it("passes_builtin_bi_links_through", () => {
		expect(normalizeBiLinkForSave("/bi/screens/42/preview", "DTS_BI")).toBe("/bi/screens/42/preview");
	});

	it("rewrites_legacy_hetu_entry_pages_to_absolute_bi_url", () => {
		expect(normalizeBiLinkForSave("/screen", "HETU")).toBe(`${window.location.origin}/bi`);
		expect(normalizeBiLinkForSave("/dashboard/hetu", "HETU")).toBe(`${window.location.origin}/bi`);
	});
});
