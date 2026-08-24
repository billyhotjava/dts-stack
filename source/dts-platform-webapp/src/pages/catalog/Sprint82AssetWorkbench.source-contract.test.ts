import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const MAP = readFileSync(new URL("./AssetOverviewPage.tsx", import.meta.url), "utf8");
const SEARCH_PAGE = readFileSync(new URL("./DataSearchPage.tsx", import.meta.url), "utf8");
const LEDGER_VIEW = readFileSync(new URL("./assets/AssetLedgerView.tsx", import.meta.url), "utf8");
const TAG_MANAGEMENT = readFileSync(
	new URL("../../components/catalog/tags/TagManagementTab.tsx", import.meta.url),
	"utf8",
);

test("Sprint-82 keeps the map summary-only and makes the directory the tag workspace owner", () => {
	assert.doesNotMatch(MAP, /TagManagementTab/);
	assert.match(SEARCH_PAGE, /AssetTagsWorkspace/);
	assert.match(SEARCH_PAGE, /searchParams\.get\("tab"\) === "catalog-tags"/);
	assert.match(SEARCH_PAGE, /activeTab === "catalog-tags" \? <AssetTagsWorkspace \/> : <DataAssetDirectoryPage \/>/);
});

test("Sprint-82 removes row actions and uses the asset detail as the single workbench", () => {
	assert.match(LEDGER_VIEW, /router\.push\(`\/catalog\/datasets\/\$\{row\.id\}`\)/);
	assert.doesNotMatch(LEDGER_VIEW, /AssetGovernanceWorkbenchDrawer|ToolOutlined|title: "操作"/);
	assert.doesNotMatch(LEDGER_VIEW, />\s*(治理资产|申请权限|详情|更多|创建报表|生成数据产品|发布数据 API)\s*</);
});

test("Sprint-82 exposes discoverable tag-to-asset journeys", () => {
	assert.match(TAG_MANAGEMENT, /label:\s*"查看资产"/);
	assert.match(TAG_MANAGEMENT, /label:\s*"关联资产"/);
	assert.match(TAG_MANAGEMENT, /onViewAssets/);
	assert.match(TAG_MANAGEMENT, /onAssociateAssets/);
});

test("Sprint-82 uses a visual palette instead of hexadecimal color options", () => {
	assert.match(TAG_MANAGEMENT, /role="radiogroup" aria-label="显示颜色调色板"/);
	assert.match(TAG_MANAGEMENT, /type="radio"/);
	assert.match(TAG_MANAGEMENT, /checked=\{selected\}/);
	assert.match(TAG_MANAGEMENT, /<TagColorPalette \/>/);
	assert.doesNotMatch(TAG_MANAGEMENT, /label:\s*color/);
	assert.doesNotMatch(TAG_MANAGEMENT, /<Select/);
});

test("Sprint-82 supports a bounded ledger association mode using the canonical batch API", () => {
	assert.match(LEDGER_VIEW, /manageTag/);
	assert.match(LEDGER_VIEW, /rowSelection/);
	assert.match(LEDGER_VIEW, /batchTagAssets/);
	assert.match(LEDGER_VIEW, /关联选中资产/);
	assert.match(LEDGER_VIEW, /assetType:\s*String\(row\.assetType\)/);
	assert.match(LEDGER_VIEW, /assetKey:\s*String\(row\.assetKey\)/);
});
