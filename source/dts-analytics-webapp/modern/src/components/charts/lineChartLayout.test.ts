import assert from "node:assert/strict";
import test from "node:test";
import { buildLineChartLayout } from "./lineChartLayout";

test("buildLineChartLayout computes rotated axis spacing from chart labels", () => {
	const layout = buildLineChartLayout(["2026-01-05周", "2026-01-12周", "2026-01-19周"], 36);
	assert.equal(layout.axisLabelLayout.rotate, 36);
	assert.equal(layout.axisLabelLayout.step, 1);
	assert.equal(layout.padding.bottom, 78);
});

test("buildLineChartLayout samples dense labels when rotation is not requested", () => {
	const layout = buildLineChartLayout(
		Array.from({ length: 14 }, (_, index) => `2026-0${Math.floor(index / 4) + 1}-${String((index % 4) * 7 + 1).padStart(2, "0")}周`),
		0,
	);
	assert.ok(layout.axisLabelLayout.step > 1);
	assert.equal(layout.padding.bottom, 40);
});
