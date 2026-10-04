import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const STATIC_ROUTES = readFileSync(new URL("./static-routes.tsx", import.meta.url), "utf8");
const DYNAMIC_RESOLVER = readFileSync(new URL("./dynamic-resolver.tsx", import.meta.url), "utf8");
const MENU_SEED = readFileSync(
	new URL("../../../../../dts-admin/src/main/resources/config/data/portal-menu-seed.json", import.meta.url),
	"utf8",
);
const ROLE_DEFAULTS = readFileSync(
	new URL("../../../../../dts-admin/src/main/resources/config/data/role-menu-defaults.json", import.meta.url),
	"utf8",
);

test("Sprint-45 workbench remains while retired studio modeling leaves only redirect", () => {
	assert.match(MENU_SEED, /"externalLink": "\/workbench\/todo"/);
	assert.match(MENU_SEED, /"externalLink": "\/data-modeling\/dimensions\/workbench"/);
	assert.doesNotMatch(MENU_SEED, /"externalLink": "\/studio\/sql-modeling"/);
	assert.doesNotMatch(MENU_SEED, /"externalLink": "\/studio\/low-code-development"/);

	assert.match(ROLE_DEFAULTS, /"route": "\/workbench\/todo"/);
	assert.match(ROLE_DEFAULTS, /"route": "\/data-modeling\/dimensions\/workbench"/);
	assert.doesNotMatch(ROLE_DEFAULTS, /"route": "\/studio\/sql-modeling"/);
	assert.doesNotMatch(ROLE_DEFAULTS, /"route": "\/studio\/low-code-development"/);

	assert.match(STATIC_ROUTES, /WorkflowCenterPage/);
	assert.match(STATIC_ROUTES, /DataModelingPage/);
	assert.match(STATIC_ROUTES, /LegacyDataModelingRedirect/);
	assert.doesNotMatch(STATIC_ROUTES, /ModelingCompatibilityPage|StudioProjectsPage|SqlModelingPage/);
	assert.match(STATIC_ROUTES, /path: "workbench\/todo"/);
	assert.match(STATIC_ROUTES, /path: "data-modeling\/\*"/);
	assert.match(STATIC_ROUTES, /path: "studio\/low-code-development"/);
	assert.match(STATIC_ROUTES, /path: "studio\/projects"/);
	assert.match(STATIC_ROUTES, /path: "studio\/sql-modeling"/);

	assert.match(DYNAMIC_RESOLVER, /"\/workbench\/todo": "\/pages\/workbench\/WorkflowCenterPage"/);
	for (const route of ["low-code-development", "projects", "sql-modeling"]) {
		assert.match(DYNAMIC_RESOLVER, new RegExp(`"/studio/${route}": "/pages/data-modeling/LegacyDataModelingRedirect"`));
	}
	assert.match(DYNAMIC_RESOLVER, /normalized\.startsWith\("\/data-modeling\/"\)/);
});
