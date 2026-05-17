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
