import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const MENU_SEED = readFileSync(
	new URL("../../../../dts-admin/src/main/resources/config/data/portal-menu-seed.json", import.meta.url),
	"utf8",
);
const ROLE_DEFAULTS = readFileSync(
	new URL("../../../../dts-admin/src/main/resources/config/data/role-menu-defaults.json", import.meta.url),
	"utf8",
);
type MenuNode = {
	key: string;
	title?: string;
	titleKey?: string;
	externalLink?: string;
	children?: MenuNode[];
};

const MENU = JSON.parse(MENU_SEED) as { portalNavSections: MenuNode[] };

test("data asset portal menu routes ledger entry to assets-v2 ledger view", () => {
	assert.match(MENU_SEED, /"title": "资产台账"/);
	assert.match(MENU_SEED, /"externalLink": "\/catalog\/assets\/ledger"/);
	assert.match(ROLE_DEFAULTS, /"title": "资产台账"/);
	assert.match(ROLE_DEFAULTS, /"route": "\/catalog\/assets\/ledger"/);
	assert.doesNotMatch(MENU_SEED, /"externalLink": "\/catalog\/asset-detail"/);
	assert.doesNotMatch(ROLE_DEFAULTS, /"route": "\/catalog\/asset-detail"/);
});

test("data asset portal separates source structure collection from metadata management", () => {
	const resource = MENU.portalNavSections.find((item) => item.key === "resource");
	const governance = MENU.portalNavSections.find((item) => item.key === "governance");
	const assets = governance?.children?.find((item) => item.key === "assets");
	assert.ok(resource, "missing data integration menu group");
	assert.ok(governance, "missing data governance menu group");
	assert.ok(assets, "missing data map and assets menu group");

	const sourceStructure = resource.children?.find((item) => item.key === "metadata");
	assert.equal(sourceStructure?.title, "数据源结构采集");
	assert.equal(sourceStructure?.externalLink, "/catalog/metadata");
	const metadataManagement = assets.children?.find((item) => item.key === "metadata-management");
	assert.equal(metadataManagement?.title, "元数据管理");
	assert.equal(metadataManagement?.titleKey, "sys.nav.portal.dataPortalMetadataManagement");
	assert.equal(metadataManagement?.externalLink, "/catalog/metadata-management");
	assert.equal(assets.children?.filter((item) => item.externalLink === "/catalog/metadata").length, 0);
	assert.doesNotMatch(JSON.stringify(assets), /数据源结构采集/);
	assert.equal([...MENU_SEED.matchAll(/"externalLink": "\/catalog\/metadata"/g)].length, 1);
	assert.equal([...MENU_SEED.matchAll(/"externalLink": "\/catalog\/metadata-management"/g)].length, 1);
	assert.equal([...ROLE_DEFAULTS.matchAll(/"route": "\/catalog\/metadata"/g)].length, 1);
	assert.equal([...ROLE_DEFAULTS.matchAll(/"route": "\/catalog\/metadata-management"/g)].length, 1);
	assert.match(ROLE_DEFAULTS, /"title": "数据源结构采集"/);
	assert.match(ROLE_DEFAULTS, /"title": "元数据管理"/);
});

test("data tags reuse the data asset page and do not create another menu entry", () => {
	const governance = MENU.portalNavSections.find((item) => item.key === "governance");
	const assets = governance?.children?.find((item) => item.key === "assets");
	const assetMap = assets?.children?.find((item) => item.key === "map");
	assert.equal(assetMap?.title, "资产地图");
	assert.equal(assetMap?.externalLink, "/catalog/assets");
	assert.doesNotMatch(MENU_SEED, /"title": "数据标签"/);
	assert.doesNotMatch(ROLE_DEFAULTS, /"title": "数据标签"/);
	assert.equal([...MENU_SEED.matchAll(/"externalLink": "\/catalog\/assets"/g)].length, 1);
	assert.equal([...ROLE_DEFAULTS.matchAll(/"route": "\/catalog\/assets"/g)].length, 1);
});
