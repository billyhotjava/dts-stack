import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./DatasetsPage.tsx", import.meta.url), "utf8");

test("asset map exposes remediation workspace for governance gaps and lineage failures", () => {
	assert.match(SOURCE, /治理缺口处置工作台/);
	assert.match(SOURCE, /governanceGapRows/);
	assert.match(SOURCE, /lineageFailureRows/);
	assert.match(SOURCE, /处置缺口/);
	assert.match(SOURCE, /补治理字段/);
	assert.match(SOURCE, /同步血缘/);
});

test("remediation actions route to detail tabs and refresh lineage evidence", () => {
	assert.match(SOURCE, /syncCatalogAssetV2Lineage/);
	assert.match(SOURCE, /\/catalog\/datasets\/\$\{assetId\}\?tab=governance/);
	assert.match(SOURCE, /\/catalog\/datasets\/\$\{row\.id\}\?tab=lineage/);
	assert.match(SOURCE, /loadGovernanceSignals/);
});
