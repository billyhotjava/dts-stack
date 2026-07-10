import { describe, expect, it } from "vitest";
import {
	buildJourneyParamClearUrl,
	extractJourneyContextParams,
	journeyJoinDismissStorageKey,
	resolveJourneyBarMode,
} from "./journeyContext";

describe("journey context params utilities", () => {
	it("extracts only journey params from the search string", () => {
		const params = extractJourneyContextParams(
			new URLSearchParams("journey=e2e-data-product&modelId=m-1&sourceId=ds-1&foo=bar"),
		);

		expect(params).toEqual({ modelId: "m-1", sourceId: "ds-1" });
	});

	it("builds a clear url that drops the target param but keeps the journey and the rest", () => {
		const url = buildJourneyParamClearUrl(
			"/workbench",
			new URLSearchParams("journey=e2e-data-product&modelId=bad-id&sourceId=ds-1"),
			"modelId",
		);

		expect(url.startsWith("/workbench?")).toBe(true);
		expect(url).toContain("journey=e2e-data-product");
		expect(url).toContain("sourceId=ds-1");
		expect(url).not.toContain("modelId");
	});
});

describe("journey bar mode", () => {
	it("prefers journey mode, then joinable, then hidden after dismissal", () => {
		expect(resolveJourneyBarMode(true, false)).toBe("journey");
		expect(resolveJourneyBarMode(true, true)).toBe("journey");
		expect(resolveJourneyBarMode(false, false)).toBe("joinable");
		expect(resolveJourneyBarMode(false, true)).toBe("hidden");
	});

	it("scopes the dismiss memory per stage", () => {
		expect(journeyJoinDismissStorageKey("modeling")).not.toBe(journeyJoinDismissStorageKey("metrics"));
	});
});
