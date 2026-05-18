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

test("data asset portal menu routes ledger entry to assets-v2 table view", () => {
	assert.match(MENU_SEED, /"title": "资产台账"/);
	assert.match(MENU_SEED, /"externalLink": "\/catalog\/assets\?view=table"/);
	assert.match(ROLE_DEFAULTS, /"title": "资产台账"/);
	assert.match(ROLE_DEFAULTS, /"route": "\/catalog\/assets\?view=table"/);
	assert.doesNotMatch(MENU_SEED, /"externalLink": "\/catalog\/asset-detail"/);
	assert.doesNotMatch(ROLE_DEFAULTS, /"route": "\/catalog\/asset-detail"/);
});
