import assert from "node:assert/strict";
import test from "node:test";
import { mergeProjectCockpitQueryState } from "./useProjectCockpitQueryState";

test("mergeProjectCockpitQueryState keeps current theme and merges partial filter updates", () => {
	const next = mergeProjectCockpitQueryState(
		{
			theme: "overview",
			majorProjectId: "major-aurora",
			dateFrom: "",
			dateTo: "2026-03-31",
			deptId: "",
			riskLevel: "",
		},
		{ theme: "risk", riskLevel: "高" },
	);

	assert.deepEqual(next, {
		theme: "risk",
		majorProjectId: "major-aurora",
		dateFrom: "",
		dateTo: "2026-03-31",
		deptId: "",
		riskLevel: "高",
	});
});

test("mergeProjectCockpitQueryState updates majorProjectId directly without extra hierarchy reset logic", () => {
	const next = mergeProjectCockpitQueryState(
		{
			theme: "tree",
			majorProjectId: "major-aurora",
			dateFrom: "",
			dateTo: "",
			deptId: "",
			riskLevel: "",
		},
		{ majorProjectId: "major-dragon" },
	);

	assert.equal(next.majorProjectId, "major-dragon");
});
