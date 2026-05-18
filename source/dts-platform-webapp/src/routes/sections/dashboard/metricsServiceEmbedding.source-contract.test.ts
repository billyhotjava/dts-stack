import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const STATIC_ROUTES = readFileSync(new URL("./static-routes.tsx", import.meta.url), "utf8");
const MENU_SEED = readFileSync(
	new URL("../../../../../dts-admin/src/main/resources/config/data/portal-menu-seed.json", import.meta.url),
	"utf8",
);
const ROLE_DEFAULTS = readFileSync(
	new URL("../../../../../dts-admin/src/main/resources/config/data/role-menu-defaults.json", import.meta.url),
	"utf8",
);

test("platform metrics bridge embeds dts-metrics instead of leaving the platform shell", () => {
	assert.match(STATIC_ROUTES, /const MetricsServiceFrame/);
	assert.match(STATIC_ROUTES, /<iframe/);
	assert.match(STATIC_ROUTES, /metricsServiceEmbeddedHrefFromPlatformLocation/);
	assert.doesNotMatch(STATIC_ROUTES, /window\.location\.replace/);
});

test("metrics menu entries target platform bridge routes", () => {
	assert.match(MENU_SEED, /"externalLink": "\/bi-apps\/metrics\/center"/);
	assert.match(MENU_SEED, /"externalLink": "\/bi-apps\/metrics\/assets"/);
	assert.match(MENU_SEED, /"externalLink": "\/bi-apps\/metrics\/semantic"/);
	assert.match(MENU_SEED, /"externalLink": "\/bi-apps\/metrics\/publish"/);
	assert.doesNotMatch(MENU_SEED, /"externalLink": "\/metrics\//);

	assert.match(ROLE_DEFAULTS, /"route": "\/bi-apps\/metrics\/center"/);
	assert.match(ROLE_DEFAULTS, /"route": "\/bi-apps\/metrics\/assets"/);
	assert.match(ROLE_DEFAULTS, /"route": "\/bi-apps\/metrics\/semantic"/);
	assert.match(ROLE_DEFAULTS, /"route": "\/bi-apps\/metrics\/publish"/);
});
