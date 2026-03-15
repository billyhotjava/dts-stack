import assert from "node:assert/strict";
import test from "node:test";
import { mergeProjectCockpitQueryState } from "./useProjectCockpitQueryState";

test("mergeProjectCockpitQueryState keeps current theme and merges partial filter updates", () => {
	const next = mergeProjectCockpitQueryState(
		{
			theme: "overview",
			programId: "program-core",
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
		programId: "program-core",
		majorProjectId: "major-aurora",
		dateFrom: "",
		dateTo: "2026-03-31",
		deptId: "",
		riskLevel: "高",
	});
});

test("mergeProjectCockpitQueryState resets majorProjectId when programId changes", () => {
	const next = mergeProjectCockpitQueryState(
		{
			theme: "tree",
			programId: "program-core",
			majorProjectId: "major-aurora",
			dateFrom: "",
			dateTo: "",
			deptId: "",
			riskLevel: "",
		},
		{ programId: "program-opto" },
	);

	assert.equal(next.programId, "program-opto");
	assert.equal(next.majorProjectId, "");
});
