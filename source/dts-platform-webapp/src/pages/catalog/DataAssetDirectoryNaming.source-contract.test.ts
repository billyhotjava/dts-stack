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
	externalLink?: string;
	children?: MenuNode[];
};

const MENU = JSON.parse(MENU_SEED) as { portalNavSections: MenuNode[] };

test("the unified search and ledger surface uses the formal data asset directory name", () => {
	const governance = MENU.portalNavSections.find((item) => item.key === "governance");
	const assets = governance?.children?.find((item) => item.key === "assets");
	const directory = assets?.children?.find((item) => item.key === "search");
	assert.equal(directory?.title, "数据资产目录");
	assert.equal(directory?.externalLink, "/catalog/search");
	assert.match(ROLE_DEFAULTS, /"title": "数据资产目录"[\s\S]*?"route": "\/catalog\/search"/);
	assert.doesNotMatch(MENU_SEED, /"title": "数据搜索"/);
});
