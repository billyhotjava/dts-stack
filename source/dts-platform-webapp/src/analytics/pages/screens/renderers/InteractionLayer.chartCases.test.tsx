// @vitest-environment node
import { describe, expect, it } from "vitest";
import { normalizeDrillLevel, resolveNextDrillEntry } from "../drillRuntime";
import {
	buildTableRowActionParams,
	normalizeDataPointClickPayload,
	resolveActionMappingValues,
} from "./shared/actionUtils";

describe("generic ECharts data-item click normalization", () => {
	const chartCases = [
		{ id: "UI-01", label: "bar/stacked segment", seriesType: "bar" },
		{ id: "UI-02", label: "pie/donut sector", seriesType: "pie" },
		{ id: "UI-03", label: "line/area point", seriesType: "line" },
		{ id: "UI-04", label: "scatter point", seriesType: "scatter" },
		{ id: "UI-04", label: "funnel stage", seriesType: "funnel" },
		{ id: "UI-04", label: "radar series", seriesType: "radar" },
		{ id: "UI-05", label: "combo bar series", seriesType: "bar" },
		{ id: "UI-05", label: "combo line series", seriesType: "line" },
		{ id: "UI-06", label: "treemap node", seriesType: "treemap" },
		{ id: "UI-06", label: "sunburst node", seriesType: "sunburst" },
		{ id: "UI-07", label: "map region", seriesType: "map" },
		{ id: "UI-09", label: "gauge data item", seriesType: "gauge" },
	];
	const mappings = [{ sourcePath: "data.key", variableKey: "selectedKey", transform: "string" as const }];
	const level = normalizeDrillLevel({
		label: "Neutral detail",
		dataSource: { type: "api", apiConfig: { url: "/example", method: "GET" } },
		mappings,
	});

	for (const chartCase of chartCases) {
		it(`${chartCase.id} accepts one ${chartCase.label} click through one mapping protocol`, () => {
			const payload = {
				componentType: "series",
				seriesType: chartCase.seriesType,
				dataIndex: 0,
				name: "A-01",
				value: 12,
				data: { key: "A-01", value: 12 },
			};
			const normalizedPayload = normalizeDataPointClickPayload(payload);
			expect(normalizedPayload).toEqual(payload);
			expect(resolveActionMappingValues(normalizedPayload ?? {}, mappings)).toEqual({ selectedKey: "A-01" });
			expect(level && resolveNextDrillEntry(level, normalizedPayload ?? {})).toEqual({
				label: "Neutral detail: A-01",
				parameters: { selectedKey: "A-01" },
			});
		});
	}

	it("UI-08 maps ordinary and scrolling table rows by column and full row", () => {
		const payload = buildTableRowActionParams(["key", "amount"], ["A-01", 12]);
		const tableMappings = [{ sourcePath: "key", variableKey: "selectedKey", transform: "string" as const }];
		const tableLevel = normalizeDrillLevel({
			label: "Neutral detail",
			dataSource: { type: "dataset", datasetConfig: { queryBody: {} } },
			mappings: tableMappings,
		});

		expect(payload.row).toEqual(["A-01", 12]);
		expect(payload["row[0]"]).toBe("A-01");
		expect(resolveActionMappingValues(payload, tableMappings)).toEqual({ selectedKey: "A-01" });
		expect(tableLevel && resolveNextDrillEntry(tableLevel, payload)?.parameters).toEqual({ selectedKey: "A-01" });
	});

	it("UI-09 maps KPI, number-card, and stat-card scalar payloads once", () => {
		const payload = { name: "Neutral KPI", value: 12, data: { key: "A-01", value: 12 } };
		expect(resolveActionMappingValues(payload, mappings)).toEqual({ selectedKey: "A-01" });
		expect(level && resolveNextDrillEntry(level, payload)?.parameters).toEqual({ selectedKey: "A-01" });
	});

	it("UI-01/UI-02/UI-03/UI-07 ignores legend, axis, geo roam, controls, and blank canvas", () => {
		expect(normalizeDataPointClickPayload({ componentType: "legend", name: "A-01" })).toBeNull();
		expect(normalizeDataPointClickPayload({ componentType: "xAxis", value: "A-01" })).toBeNull();
		expect(normalizeDataPointClickPayload({ componentType: "yAxis", value: "A-01" })).toBeNull();
		expect(normalizeDataPointClickPayload({ componentType: "geo", type: "georoam" })).toBeNull();
		expect(normalizeDataPointClickPayload({ componentType: "timeline", value: "next" })).toBeNull();
		expect(normalizeDataPointClickPayload({ componentType: "series" })).toBeNull();
		expect(normalizeDataPointClickPayload({})).toBeNull();
	});
});
