import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./AssetDetailPage.tsx", import.meta.url), "utf8");
const GOVERNANCE = readFileSync(new URL("./AssetGovernanceOverview.tsx", import.meta.url), "utf8");

test("legacy asset detail loads the formal identity contract before mounting tag editor", () => {
	assert.match(SOURCE, /getCatalogAssetV2Contract/);
	assert.match(SOURCE, /setAssetContract\(null\)/);
	assert.match(SOURCE, /assetContract\?\.grantAssetType/);
	assert.match(SOURCE, /assetContract\?\.assetKey/);
	assert.match(
		SOURCE,
		/<AssetTagPanel\s+assetType=\{assetContract\.grantAssetType\}\s+assetKey=\{assetContract\.assetKey\}/,
	);
	assert.match(SOURCE, /canEdit=\{assetContract\?\.canTag === true\}/);
	assert.doesNotMatch(SOURCE, /<AssetTagPanel[^>]+assetKey=\{detailRow\?\.id\}/);
	assert.match(SOURCE, /资产身份合同不可用，暂不能维护业务数据标签/);
});

test("legacy free-text tags remain editable and are clearly separated", () => {
	assert.match(SOURCE, /Form\.useWatch\("tags"/);
	assert.match(SOURCE, /tags:\s*\(values\?\.tags \|\| ""\)\.trim\(\)/);
	assert.match(SOURCE, /历史自由文本标签（兼容字段）/);
	assert.match(SOURCE, /不会自动转成业务数据标签/);
});

test("asset and tag write permissions fail closed when the backend omits an explicit grant", () => {
	assert.match(SOURCE, /editable:\s*item\.editable === true/);
	assert.match(SOURCE, /if \(detailDataset\.editable !== true\)/);
	assert.match(SOURCE, /disabled=\{detailDataset\?\.editable !== true \|\| detailLoading\}/);
	assert.match(SOURCE, /disabled=\{!profileChanged \|\| detailDataset\?\.editable !== true\}/);
	assert.doesNotMatch(SOURCE, /item\.editable !== false/);
});

test("asset detail keeps governance summary in a dedicated presentational component", () => {
	assert.match(SOURCE, /from "\.\/AssetGovernanceOverview"/);
	assert.match(SOURCE, /<AssetGovernanceOverview/);
	assert.match(GOVERNANCE, /权限授权/);
	assert.match(GOVERNANCE, /治理健康/);
	assert.match(GOVERNANCE, /查看质量报告/);
});

test("asset detail page remains within the repository file-size convention", () => {
	assert.ok(SOURCE.split(/\r?\n/).length - 1 <= 800, "AssetDetailPage.tsx must stay at or below 800 lines");
});
