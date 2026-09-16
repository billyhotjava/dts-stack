import { describe, expect, it } from "vitest";
import {
	buildJourneyParamClearUrl,
	extractJourneyContextParams,
	JOURNEY_CONTEXT_PARAM_KEYS,
	parseDataProductJourneyContext,
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
	it("renders only for an explicitly active journey context", () => {
		expect(resolveJourneyBarMode(true)).toBe("journey");
		expect(resolveJourneyBarMode(false)).toBe("hidden");
	});
});

describe("journey next steps follow the implementation sequence", () => {
	it("moves from planning to standards, integration and modeling", () => {
		const search = new URLSearchParams("journey=e2e-data-product");
		const next = (stage: "planning" | "standards" | "integration") =>
			new URL(parseDataProductJourneyContext(search, stage).nextUrl, "http://dts.local").pathname;

		expect(next("planning")).toBe("/governance/standards/elements");
		expect(next("standards")).toBe("/foundation/data-sources");
		expect(next("integration")).toBe("/data-modeling/dimensions/workbench");
		expect(parseDataProductJourneyContext(search, "standards").nextLabel).toBe("继续到数据集成");
		expect(parseDataProductJourneyContext(search, "integration").nextLabel).toBe("继续到维度建模");
	});
});
