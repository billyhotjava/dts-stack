import assert from "node:assert/strict";
import test from "node:test";
import {
	resolveAxisStyleConfig,
	resolveLegendStyleConfig,
	resolveSeriesColors,
	readPositivePaddingOverride,
} from "./chartStyleConfig";

test("resolveAxisStyleConfig prefers nested axis editor values over legacy flat fields", () => {
	const result = resolveAxisStyleConfig({
		axisFontSize: 11,
		axisLabelColor: "#999",
		xAxis: {
			labelFontSize: 16,
			labelColor: " #00f ",
			show: false,
			splitLineShow: true,
			splitLineColor: " #123 ",
			min: " 0 ",
			max: 100,
			type: " value ",
		},
		yAxis: {
			labelRotate: 30,
		},
	});

	assert.equal(result.axisFontSize, 16);
	assert.equal(result.axisLabelColor, "#00f");
	assert.equal(result.yAxisLabelRotate, 30);
	assert.deepEqual(result.axisOverrides.x, {
		show: false,
		splitLineShow: true,
		splitLineColor: "#123",
		min: "0",
		max: 100,
		type: "value",
	});
});

test("resolveLegendStyleConfig normalizes nested legend style overrides", () => {
	const result = resolveLegendStyleConfig({
		legendFontSize: 12,
		legend: {
			show: false,
			position: "right",
			color: " #fff ",
			fontSize: 18,
			reserveSize: 80,
			itemGap: 6,
		},
	});

	assert.equal(result.legendDisplayOverride, "hide");
	assert.equal(result.legendPositionOverride, "right");
	assert.equal(result.legendColorOverride, "#fff");
	assert.equal(result.legendFontSize, 18);
	assert.equal(result.legendReserveOverrideFromNested, 80);
	assert.equal(result.legendItemGapOverrideFromNested, 6);
});

test("chart style helpers keep only usable values", () => {
	assert.deepEqual(resolveSeriesColors({ seriesColors: ["#111", "", " #222 ", 42] }), ["#111", " #222 "]);
	assert.equal(readPositivePaddingOverride({ chartPaddingLeft: 24.4 }, "chartPaddingLeft"), 24);
	assert.equal(readPositivePaddingOverride({ chartPaddingLeft: 0 }, "chartPaddingLeft"), undefined);
});
