import { describe, expect, it } from "vitest";
import type { AnalysisQuerySpec } from "../api/analysisApi";
import { placeAllDimensionsOnShelf, placeFieldOnShelf } from "./analysisWorkspaceModel";

const spec = (): AnalysisQuerySpec => ({
	apiVersion: "dts.analysis/v1",
	dataset: { id: "dataset-1", version: 1, contractVersion: "v1", checksum: "checksum-1" },
	dimensions: [],
	metrics: [],
	derivedMetrics: [],
	filters: [],
	timeRange: null,
	orderBy: [],
	limit: 5000,
	visualization: { type: "table", settings: {} },
});

describe("S10DC-115 select all dimensions", () => {
	it("puts every dimension on the x shelf in contract order, keeping existing picks once", () => {
		const started = placeFieldOnShelf(spec(), { kind: "dimension", code: "risk_name" }, "x");
		const value = placeAllDimensionsOnShelf(started, ["id", "project_no", "risk_name"]);

		expect(value.visualization.settings["graph.dimensions"]).toEqual(["risk_name", "id", "project_no"]);
		expect(value.dimensions.map((item) => item.field)).toEqual(["risk_name", "id", "project_no"]);
	});

	it("is idempotent and leaves metrics untouched", () => {
		const withMetric = placeFieldOnShelf(spec(), { kind: "metric", code: "risk_count" }, "y");
		const once = placeAllDimensionsOnShelf(withMetric, ["a", "b"]);
		expect(placeAllDimensionsOnShelf(once, ["a", "b"])).toEqual(once);
		expect(once.metrics.map((item) => item.code)).toEqual(["risk_count"]);
	});
});
