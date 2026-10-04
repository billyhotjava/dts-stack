import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./DatasetAccessApprovalPage.tsx", import.meta.url), "utf8");

test("approval page unifies the title and keeps authorization/audit entries reachable", () => {
	assert.match(SOURCE, /数据资产 · 权限申请与审批/);
	assert.match(SOURCE, /router\.push\("\/my\/asset-grants"\)/);
	assert.match(SOURCE, /router\.push\("\/governance\/asset-grants"\)/);
	assert.match(SOURCE, /router\.push\("\/governance\/permission-audit"\)/);
	assert.doesNotMatch(SOURCE, /router\.push\("\/catalog\/assets"\)/);
});

test("approval page consumes the new-request deep link and pre-fills the asset", () => {
	assert.match(SOURCE, /searchParams\.get\("action"\)/);
	assert.match(SOURCE, /searchParams\.get\("assetId"\)/);
	assert.match(SOURCE, /getCatalogAssetV2\(deepLinkAssetId\)/);
	assert.match(SOURCE, /DatasetAccessRequestDialog/);
	assert.match(SOURCE, /action=new/);
	assert.match(SOURCE, /onSubmitted=\{async \(\) =>/);
});

test("approval page reports deep-link misses explicitly instead of failing silently", () => {
	assert.match(SOURCE, /未找到指定资产（assetId=/);
	assert.match(SOURCE, /未指定具体资产，无法预填申请/);
	assert.match(SOURCE, /deepLinkMissing/);
});
