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

test("Sprint-45 P0 workbench and studio menu leaves resolve to real platform pages", () => {
	assert.match(MENU_SEED, /"externalLink": "\/workbench\/todo"/);
	assert.match(MENU_SEED, /"externalLink": "\/studio\/low-code-development"/);
	assert.match(MENU_SEED, /"externalLink": "\/studio\/projects"/);
	assert.match(MENU_SEED, /"externalLink": "\/studio\/sql-modeling"/);

	assert.match(ROLE_DEFAULTS, /"route": "\/workbench\/todo"/);
	assert.match(ROLE_DEFAULTS, /"route": "\/studio\/low-code-development"/);
	assert.match(ROLE_DEFAULTS, /"route": "\/studio\/projects"/);
	assert.match(ROLE_DEFAULTS, /"route": "\/studio\/sql-modeling"/);

	assert.match(STATIC_ROUTES, /WorkflowCenterPage/);
	assert.match(STATIC_ROUTES, /LowCodeDevelopmentPage/);
	assert.match(STATIC_ROUTES, /StudioProjectsPage/);
	assert.match(STATIC_ROUTES, /SqlModelingPage/);
	assert.match(STATIC_ROUTES, /path: "workbench\/todo"/);
	assert.match(STATIC_ROUTES, /path: "studio\/low-code-development"/);
	assert.match(STATIC_ROUTES, /path: "studio\/projects"/);
	assert.match(STATIC_ROUTES, /path: "studio\/sql-modeling"/);

	assert.match(DYNAMIC_RESOLVER, /"\/workbench\/todo": "\/pages\/workbench\/WorkflowCenterPage"/);
	assert.match(DYNAMIC_RESOLVER, /"\/studio\/low-code-development": "\/pages\/modeling\/LowCodeDevelopmentPage"/);
	assert.match(DYNAMIC_RESOLVER, /"\/studio\/projects": "\/pages\/modeling\/ModelTemplatesPage"/);
	assert.match(DYNAMIC_RESOLVER, /"\/studio\/sql-modeling": "\/pages\/modeling\/SqlModelingPage"/);
});
