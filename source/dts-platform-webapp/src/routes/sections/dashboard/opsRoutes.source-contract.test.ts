import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const STATIC_ROUTES = readFileSync(new URL("./static-routes.tsx", import.meta.url), "utf8");
const DYNAMIC_RESOLVER = readFileSync(new URL("./dynamic-resolver.tsx", import.meta.url), "utf8");
const MENU_SEED = readFileSync(
	new URL("../../../../../dts-admin/src/main/resources/config/data/portal-menu-seed.json", import.meta.url),
	"utf8",
);

test("ops center menu paths render real platform pages", () => {
	assert.match(MENU_SEED, /"path": "overview"/);
	assert.match(MENU_SEED, /"path": "instances"/);
	assert.match(MENU_SEED, /"path": "alerts"/);
	assert.match(MENU_SEED, /"path": "backfill"/);

	assert.match(STATIC_ROUTES, /OpsOverviewPage/);
	assert.match(STATIC_ROUTES, /OpsInstancesPage/);
	assert.match(STATIC_ROUTES, /OpsAlertLogPage/);
	assert.match(STATIC_ROUTES, /OpsBackfillPage/);
	assert.match(STATIC_ROUTES, /path: "ops\/overview"/);
	assert.match(STATIC_ROUTES, /path: "ops\/instances"/);
	assert.match(STATIC_ROUTES, /path: "ops\/alerts"/);
	assert.match(STATIC_ROUTES, /path: "ops\/backfill"/);
});

test("dynamic menu fallback knows ops center page overrides", () => {
	assert.match(DYNAMIC_RESOLVER, /"\/ops\/overview": "\/pages\/ops\/OpsOverviewPage"/);
	assert.match(DYNAMIC_RESOLVER, /"\/ops\/instances": "\/pages\/ops\/OpsInstancesPage"/);
	assert.match(DYNAMIC_RESOLVER, /"\/ops\/alerts": "\/pages\/ops\/OpsAlertLogPage"/);
	assert.match(DYNAMIC_RESOLVER, /"\/ops\/backfill": "\/pages\/ops\/OpsBackfillPage"/);
});
