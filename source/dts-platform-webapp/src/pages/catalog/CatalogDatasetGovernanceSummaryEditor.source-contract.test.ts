import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const source = readFileSync(new URL("./CatalogDatasetGovernanceSummaryEditor.tsx", import.meta.url), "utf8");

test("keeps the loaded dataset version for PATCH and protects stale async reads", () => {
	assert.match(source, /updateDatasetGovernanceSummary\(datasetId, .*dataset\.version\)/s);
	assert.match(source, /requestSequence/);
	assert.match(source, /sequence !== requestSequence\.current/);
});

test("conflict keeps input and allows explicit reload while a failed load disables writes", () => {
	assert.match(source, /资产已被其他操作修改/);
	assert.match(source, /setDataset\(null\)/);
	assert.match(source, /!dataset \|\| !canMaintain/);
});
