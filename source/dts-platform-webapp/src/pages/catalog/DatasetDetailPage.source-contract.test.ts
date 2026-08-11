import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./DatasetDetailPage.tsx", import.meta.url), "utf8");
const SUPPORT_SOURCE = readFileSync(new URL("./DatasetDetailSupportTabs.tsx", import.meta.url), "utf8");

test("dataset detail page consumes assets-v2 as the single source of truth", () => {
	const firstAssetV2Read = SOURCE.indexOf("const detail = await getCatalogAssetV2(id)");
	const legacyRead = SOURCE.indexOf("await getDataset(id)");

	assert.ok(firstAssetV2Read > 0, "expected assets-v2 detail API to be the primary read");
	assert.ok(legacyRead > 0, "expected legacy dataset API to remain only for old deep-link resolution");
	assert.ok(firstAssetV2Read < legacyRead, "assets-v2 must be read before any legacy fallback");

	assert.match(SOURCE, /ADR-85-03：详情页以 assets-v2 为唯一事实源/);
	assert.match(SOURCE, /setLegacyOnly\(true\)/);
	assert.doesNotMatch(SOURCE, /__source/);
	assert.match(SOURCE, /企业级资产工作台/);
	assert.match(SOURCE, /授权资产/);
	assert.match(SOURCE, /字段契约/);
});

test("dataset detail page keeps asset identity separate from business description", () => {
	assert.match(SOURCE, /__fqn: asset\.fqn/);
	assert.match(
		SOURCE,
		/securityPolicyRefs: asset\.securityPolicyRefs,\s+metadataSource: asset\.metadataSource,\s+__fqn: asset\.fqn/,
	);
	assert.match(SOURCE, /const assetKey = String\(assetContract\?\.assetKey \|\| ""\)\.trim\(\)/);
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

test("legacy deep-link failure guides users back to unified data search", () => {
	assert.match(SOURCE, /该资产暂时无法打开治理详情/);
	assert.match(SOURCE, /请返回数据搜索重新选择；如仍无法打开，请联系数据管理员检查资产同步状态/);
	assert.match(SOURCE, /router\.push\("\/catalog\/search"\)/);
	assert.match(SOURCE, /返回数据搜索/);
	assert.match(SOURCE, /legacyOnly/);
	assert.doesNotMatch(SOURCE, /前往资产台账|router\.push\("\/catalog\/assets"\)|Tab 渲染统一以治理资产/);
	assert.doesNotMatch(SOURCE, /dataset\.__source === "dts-catalog"/);
	assert.doesNotMatch(SOURCE, /<LegacyGovernanceNotice/);
});

test("dataset detail page uses SPA navigation for catalog internal actions", () => {
	assert.match(SUPPORT_SOURCE, /router\.push\("\/catalog\/lineage\/graph"\)/);
	assert.doesNotMatch(`${SOURCE}\n${SUPPORT_SOURCE}`, /href=\{`\/catalog\/lineage\/graph`\}/);
});

test("dataset detail keeps business tags on the formal asset identity", () => {
	assert.match(SOURCE, /AssetTagPanel/);
	assert.match(
		SOURCE,
		/<AssetTagPanel assetType=\{grantAssetType\} assetKey=\{assetKey\} canEdit=\{assetContract\?\.canTag === true\}/,
	);
	assert.match(SOURCE, /hasFormalTagIdentity/);
	assert.match(SOURCE, /contractRequestSequence/);
	assert.doesNotMatch(SOURCE, /<AssetTagPanel[^>]+assetKey=\{grantAssetId\}/);
	assert.doesNotMatch(
		SOURCE,
		/const grantAssetType = assetContract\?\.grantAssetType \|\| \(dataset\.__source[\s\S]*?"TABLE"\)/,
	);
});

test("dataset detail visibly separates business, technical and security tags", () => {
	assert.match(SOURCE, /业务数据标签/);
	assert.match(SOURCE, /OpenMetadata 技术标签/);
	assert.match(SOURCE, /密级/);
	assert.doesNotMatch(SOURCE, /assetTags.*__tags|__tags.*assetTags/s);
});

test("dataset detail page remains within the repository file-size convention", () => {
	assert.ok(SOURCE.split(/\r?\n/).length - 1 <= 800, "DatasetDetailPage.tsx must stay at or below 800 lines");
});

test("dataset detail header remains readable inside a narrow content shell", () => {
	assert.match(SOURCE, /className="min-w-48 flex-1"/);
	assert.match(SOURCE, /className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4"/);
	assert.doesNotMatch(SOURCE, /className="grid gap-3 md:grid-cols-4"/);
});
