import assert from "node:assert/strict";
import test from "node:test";
import type { AnalysisQuerySpec } from "../api/analysisApi";
import {
	CANONICAL_VISUALIZATIONS,
	analysisQueryFingerprint,
	placeFieldOnShelf,
	removeFieldFromAnalysis,
	setVisualizationSetting,
} from "./analysisWorkspaceModel.ts";

function spec(): AnalysisQuerySpec {
	return {
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
	};
}

test("dimension and metric drops update the canonical spec and stay idempotent", () => {
	let value = placeFieldOnShelf(spec(), { kind: "dimension", code: "department" }, "x");
	value = placeFieldOnShelf(value, { kind: "dimension", code: "department" }, "x");
	value = placeFieldOnShelf(value, { kind: "metric", code: "project_count" }, "y");

	assert.deepEqual(value.dimensions, [{ field: "department", alias: null }]);
	assert.deepEqual(value.metrics, [{ code: "project_count", alias: null }]);
	assert.deepEqual(value.visualization.settings["graph.dimensions"], ["department"]);
	assert.deepEqual(value.visualization.settings["graph.metrics"], ["project_count"]);
});

test("removing a field clears query and visualization references", () => {
	const value: AnalysisQuerySpec = {
		...spec(),
		dimensions: [{ field: "department", alias: null }],
		filters: [{ field: "department", op: "EQ", values: ["D1"] }],
		orderBy: [{ field: "department", direction: "ASC" }],
		timeRange: { field: "department", grain: "DAY", start: null, end: null },
		visualization: {
			type: "bar",
			settings: {
				"graph.dimensions": ["department"],
				"dts.encoding.color": "department",
				"dts.tooltip.fields": ["department"],
			},
		},
	};

	const next = removeFieldFromAnalysis(value, "department");
	assert.deepEqual(next.dimensions, []);
	assert.deepEqual(next.filters, []);
	assert.deepEqual(next.orderBy, []);
	assert.equal(next.timeRange, null);
	assert.deepEqual(next.visualization.settings["graph.dimensions"], []);
	assert.equal(next.visualization.settings["dts.encoding.color"], undefined);
});

test("style updates are immutable and canonical visualizations exclude fake renderers", () => {
	const original = spec();
	const next = setVisualizationSetting(original, "graph.show_values", true);

	assert.notEqual(next, original);
	assert.equal(original.visualization.settings["graph.show_values"], undefined);
	assert.equal(next.visualization.settings["graph.show_values"], true);
	assert.deepEqual(CANONICAL_VISUALIZATIONS.map((item) => item.value), [
		"table",
		"bar",
		"line",
		"area",
		"pie",
		"number",
	]);
});

test("automatic preview ignores style-only edits but reacts to query edits", () => {
	const original = spec();
	const styled = setVisualizationSetting(original, "graph.show_values", true);
	const filtered = { ...original, filters: [{ field: "department", op: "EQ", values: ["D1"] }] };

	assert.equal(analysisQueryFingerprint(styled), analysisQueryFingerprint(original));
	assert.notEqual(analysisQueryFingerprint(filtered), analysisQueryFingerprint(original));
});
