import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./DatasetDetailPage.tsx", import.meta.url), "utf8");

test("dataset detail page treats assets-v2 as the primary asset workspace", () => {
	const firstAssetV2Read = SOURCE.indexOf("getCatalogAssetV2(id)");
	const legacyFallbackRead = SOURCE.indexOf("const legacyDataset: any = await getDataset(id)");

	assert.ok(firstAssetV2Read > 0, "expected assets-v2 detail API to be called");
	assert.ok(legacyFallbackRead > 0, "expected legacy dataset fallback to remain available");
	assert.ok(firstAssetV2Read < legacyFallbackRead, "assets-v2 must be attempted before legacy dataset fallback");

	assert.match(SOURCE, /企业级资产工作台/);
	assert.match(SOURCE, /授权资产/);
	assert.match(SOURCE, /字段契约/);
});

test("dataset detail page keeps asset identity separate from business description", () => {
	assert.match(SOURCE, /__fqn: asset\.fqn/);
	assert.match(SOURCE, /const assetKey = assetContract\?\.assetKey \|\| dataset\.__fqn \|\| dataset\.id \|\| "-"/);
	assert.doesNotMatch(SOURCE, /assetContract\?\.assetKey \|\| dataset\.description \|\| "-"/);
});

test("dataset detail page can deep-link to remediation tabs", () => {
	assert.match(SOURCE, /useSearchParams/);
	assert.match(SOURCE, /DETAIL_TAB_KEYS/);
	assert.match(SOURCE, /DETAIL_TAB_ALIASES/);
	assert.match(SOURCE, /lineage-impact/);
	assert.match(SOURCE, /schema-contract/);
	assert.match(SOURCE, /quality-sla/);
	assert.match(SOURCE, /activeKey=\{activeTab\}/);
	assert.match(SOURCE, /setSearchParams\(\{ tab: next \}\)/);
});

test("dataset detail page exposes enterprise asset workbench tabs", () => {
	assert.match(SOURCE, /label: "字段契约"/);
	assert.match(SOURCE, /label: "治理责任"/);
	assert.match(SOURCE, /label: "质量与SLA"/);
	assert.match(SOURCE, /label: "血缘与影响"/);
	assert.match(SOURCE, /DatasetSchemaContractTab/);
	assert.match(SOURCE, /DatasetQualitySlaTab/);
	assert.match(SOURCE, /DatasetLineageImpactTab/);
});
