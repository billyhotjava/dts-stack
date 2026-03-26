import assert from "node:assert/strict";
import test from "node:test";
import { resolveGanttBaselineRange, resolveGanttSideTextStyle } from "./projectGanttBoard.helpers";

test("resolveGanttBaselineRange prefers explicit baseline dates and normalizes reversed ranges", () => {
	const range = resolveGanttBaselineRange({
		planDate: "2026-03-08",
		baselineStartDate: "2026-03-12",
		baselineEndDate: "2026-03-06",
	});

	assert.deepEqual(range, {
		startDate: "2026-03-06",
		endDate: "2026-03-12",
	});
});

test("resolveGanttBaselineRange falls back to plan date when baseline dates are absent", () => {
	const range = resolveGanttBaselineRange({
		planDate: "2026-03-08",
	});

	assert.deepEqual(range, {
		startDate: "2026-03-08",
		endDate: "2026-03-08",
	});
});

test("resolveGanttSideTextStyle returns inline color when template provides an explicit side text color", () => {
	assert.deepEqual(resolveGanttSideTextStyle("#ffffff"), {
		color: "#ffffff",
	});
});
