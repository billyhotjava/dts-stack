import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const REGISTRY_SOURCE = readFileSync(new URL("./workbenchComponentRegistry.tsx", import.meta.url), "utf8");
const WORKBENCH_SOURCE = readFileSync(new URL("./index.tsx", import.meta.url), "utf8");
const DRAWER_SOURCE = readFileSync(new URL("./components/WorkbenchCustomizeDrawer.tsx", import.meta.url), "utf8");
const STATIC_ROUTES_SOURCE = readFileSync(
	new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url),
	"utf8",
);
const DYNAMIC_RESOLVER_SOURCE = readFileSync(
	new URL("../../routes/sections/dashboard/dynamic-resolver.tsx", import.meta.url),
	"utf8",
);
const GLOBAL_CONFIG_SOURCE = readFileSync(new URL("../../global-config.ts", import.meta.url), "utf8");

const expectedWorkbenchRoutes = [
	"/workbench/todo",
	"/bi/dashboards",
	"/foundation/data-sources",
	"/workbench?section=data-management&journey=first-report",
	"/workbench?section=data-management",
	"/governance/quality",
	"/services/apis",
	"/ops/overview",
];

test("Sprint-49 F3 workbench entry cards expose stable testable actions", () => {
	assert.match(REGISTRY_SOURCE, /data-testid=\{`workbench-entry-card-\$\{definition\.key\}`\}/);
	assert.match(REGISTRY_SOURCE, /data-testid=\{`workbench-entry-action-\$\{definition\.key\}`\}/);
	assert.match(REGISTRY_SOURCE, /onClick=\{\(\) => definition\.route && navigate\(definition\.route\)\}/);
	assert.match(REGISTRY_SOURCE, /first-report/);
	assert.match(REGISTRY_SOURCE, /首张报表/);
	assert.match(REGISTRY_SOURCE, /接入一张业务表并生成报表/);
	assert.doesNotMatch(REGISTRY_SOURCE, /window\.location|href=/);
});

test("Sprint-49 F3 registered homepage actions resolve to existing dashboard routes", () => {
	for (const route of expectedWorkbenchRoutes) {
		assert.match(REGISTRY_SOURCE, new RegExp(route.replace(/[/?]/g, "\\$&")));
	}

	for (const route of ["workbench/todo", "bi/dashboards", "workbench/data-management", "ops/overview"]) {
		assert.match(STATIC_ROUTES_SOURCE, new RegExp(`path:\\s*"${route.replace(/\//g, "\\/")}"`));
	}

	for (const route of ["/foundation/data-sources", "/governance/quality", "/services/apis"]) {
		assert.match(DYNAMIC_RESOLVER_SOURCE, new RegExp(route.replace(/[/?]/g, "\\$&")));
	}

	assert.match(STATIC_ROUTES_SOURCE, /WorkbenchSectionRedirect/);
	assert.match(STATIC_ROUTES_SOURCE, /section="data-management"/);
});

test("Sprint-49 F3 workbench customization degrades locally before backend upgrade", () => {
	assert.match(GLOBAL_CONFIG_SOURCE, /WEBAPP_ENABLE_WORKBENCH_PREFERENCE_API/);
	assert.match(GLOBAL_CONFIG_SOURCE, /VITE_ENABLE_WORKBENCH_PREFERENCE_API/);
	assert.match(GLOBAL_CONFIG_SOURCE, /\?\?\s*false/);
	assert.match(WORKBENCH_SOURCE, /!GLOBAL_CONFIG\.enableWorkbenchPreferenceApi/);
	assert.match(WORKBENCH_SOURCE, /readLocalWorkbenchPreferenceItems\(preferenceOwner\)/);
	assert.match(WORKBENCH_SOURCE, /saveLocalWorkbenchPreferenceItems\(preferenceOwner/);
	assert.match(WORKBENCH_SOURCE, /resetLocalWorkbenchPreferenceItems\(preferenceOwner\)/);
	assert.doesNotMatch(WORKBENCH_SOURCE, /后端已升级|暂未启用个人工作台保存|No static resource/);
});

test("Sprint-49 F3 customization drawer stays Chrome 95 friendly", () => {
	assert.match(DRAWER_SOURCE, /data-testid="workbench-customize-drawer"/);
	assert.match(DRAWER_SOURCE, /Checkbox/);
	assert.match(DRAWER_SOURCE, /上移/);
	assert.match(DRAWER_SOURCE, /下移/);
	assert.match(DRAWER_SOURCE, /保存/);
	assert.doesNotMatch(DRAWER_SOURCE, /ResizeObserver|structuredClone|@dnd-kit|react-grid-layout|drag/i);
});
