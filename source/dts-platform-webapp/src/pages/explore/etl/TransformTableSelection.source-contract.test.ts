import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const STEP_SOURCE = readFileSync(
	new URL("./steps/DbUnifiedStep.tsx", import.meta.url),
	"utf8",
);
const CREATE_SOURCE = readFileSync(
	new URL("./TransformCreatePage.tsx", import.meta.url),
	"utf8",
);

test("database ingestion exposes one authoritative table-selection workflow", () => {
	assert.match(STEP_SOURCE, /批量录入表名（备用）/);
	assert.match(STEP_SOURCE, /tableSelectionMode === "manual"/);
	assert.doesNotMatch(STEP_SOURCE, />\s*应用选择\s*</);
	assert.doesNotMatch(STEP_SOURCE, /onApplyTables/);
});

test("create and draft submission share the canonical table-selection resolver", () => {
	assert.match(CREATE_SOURCE, /resolveTableSelection/);
	assert.doesNotMatch(CREATE_SOURCE, /const handleApplyTables/);
	assert.doesNotMatch(CREATE_SOURCE, /onApplyTables=/);
});
