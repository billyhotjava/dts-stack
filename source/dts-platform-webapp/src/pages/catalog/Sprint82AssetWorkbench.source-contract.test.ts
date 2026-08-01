import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const MAP = readFileSync(new URL("./AssetOverviewPage.tsx", import.meta.url), "utf8");
const LEDGER_PAGE = readFileSync(new URL("./DatasetsPage.tsx", import.meta.url), "utf8");
const LEDGER_VIEW = readFileSync(new URL("./assets/AssetLedgerView.tsx", import.meta.url), "utf8");
const TAG_MANAGEMENT = readFileSync(
	new URL("../../components/catalog/tags/TagManagementTab.tsx", import.meta.url),
	"utf8",
);

test("Sprint-82 keeps the map summary-only and makes the ledger the tag workspace owner", () => {
	assert.doesNotMatch(MAP, /TagManagementTab/);
	assert.match(LEDGER_PAGE, /AssetTagsWorkspace/);
	assert.match(LEDGER_PAGE, /tab.*catalog-tags/);
	assert.match(LEDGER_PAGE, /资产列表/);
	assert.match(LEDGER_PAGE, /数据标签/);
});

test("Sprint-82 collapses row actions into one asset governance entry", () => {
	assert.match(LEDGER_VIEW, /AssetGovernanceWorkbenchDrawer/);
	assert.match(LEDGER_VIEW, />\s*治理资产\s*</);
	assert.doesNotMatch(LEDGER_VIEW, />\s*申请权限\s*</);
	assert.doesNotMatch(LEDGER_VIEW, />\s*详情\s*</);
	assert.doesNotMatch(LEDGER_VIEW, />\s*更多\s*</);
	assert.doesNotMatch(LEDGER_VIEW, /创建报表|生成数据产品|发布数据 API/);
});

test("Sprint-82 exposes discoverable tag-to-asset journeys", () => {
	assert.match(TAG_MANAGEMENT, />\s*查看资产\s*</);
	assert.match(TAG_MANAGEMENT, />\s*关联资产\s*</);
	assert.match(TAG_MANAGEMENT, /onViewAssets/);
	assert.match(TAG_MANAGEMENT, /onAssociateAssets/);
});

test("Sprint-82 supports a bounded ledger association mode using the canonical batch API", () => {
	assert.match(LEDGER_VIEW, /manageTag/);
	assert.match(LEDGER_VIEW, /rowSelection/);
	assert.match(LEDGER_VIEW, /batchTagAssets/);
	assert.match(LEDGER_VIEW, /关联选中资产/);
	assert.match(LEDGER_VIEW, /assetType:\s*String\(row\.assetType\)/);
	assert.match(LEDGER_VIEW, /assetKey:\s*String\(row\.assetKey\)/);
});
