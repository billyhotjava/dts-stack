import { describe, expect, it } from "vitest";
import { resolveModelingOverviewTarget } from "./overviewNavigation";

describe("modeling overview navigation", () => {
	it("opens warehouse-planning summaries through their canonical menu owner", () => {
		expect(resolveModelingOverviewTarget("planning/business-categories")).toBe(
			"/data-architecture?view=business-domains",
		);
	});

	it("keeps modeling-owned summaries inside the modeling workspace", () => {
		expect(resolveModelingOverviewTarget("dimensions/workbench")).toBe("/data-modeling/dimensions/workbench");
		expect(resolveModelingOverviewTarget("standards/fields")).toBe("/data-modeling/standards/fields");
		expect(resolveModelingOverviewTarget("metrics/atomic")).toBe("/data-modeling/metrics/atomic");
	});
});
