import { describe, expect, it } from "vitest";
import {
	buildJourneyParamClearUrl,
	extractJourneyContextParams,
	JOURNEY_CONTEXT_PARAM_KEYS,
	journeyJoinDismissStorageKey,
	resolveJourneyBarMode,
} from "./journeyContext";

describe("journey context params utilities", () => {
	it("extracts only journey params from the search string", () => {
		const params = extractJourneyContextParams(
			new URLSearchParams(
				"journey=e2e-data-product&modelId=m-1&sourceId=ds-1&planningId=p-1&domainId=d-1&warehouseLayer=DWD&modelingMode=dimension&foo=bar",
			),
		);

		expect(params).toEqual({
			modelSpecId: "m-1",
			sourceId: "ds-1",
			planId: "p-1",
			domainId: "d-1",
		});
		expect(JOURNEY_CONTEXT_PARAM_KEYS).not.toContain("planningId");
		expect(JOURNEY_CONTEXT_PARAM_KEYS).not.toContain("processId");
	});

	it("does not carry retired context when building journey routes", () => {
		const params = extractJourneyContextParams(new URLSearchParams("journey=e2e-data-product&processId=node-plan-loop"));
		expect(params).toEqual({});
	});

	it("builds a clear url that drops the target param but keeps the journey and the rest", () => {
		const url = buildJourneyParamClearUrl(
			"/workbench",
			new URLSearchParams("journey=e2e-data-product&modelSpecId=bad-id&sourceId=ds-1"),
			"modelSpecId",
		);

		expect(url.startsWith("/workbench?")).toBe(true);
		expect(url).toContain("journey=e2e-data-product");
		expect(url).toContain("sourceId=ds-1");
		expect(url).not.toContain("modelSpecId");
	});

	it("keeps planning context when clearing an unrelated artifact", () => {
		const url = buildJourneyParamClearUrl(
			"/workbench",
			new URLSearchParams(
				"journey=e2e-data-product&planningId=p-1&domainId=d-1&warehouseLayer=DWD&modelingMode=dimension&modelSpecId=bad-id",
			),
			"modelSpecId",
		);

		expect(url).toContain("planId=p-1");
		expect(url).toContain("domainId=d-1");
		expect(url).not.toContain("warehouseLayer");
		expect(url).not.toContain("modelingMode");
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
