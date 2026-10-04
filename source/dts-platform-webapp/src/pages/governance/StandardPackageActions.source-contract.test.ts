import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const ACTIONS = readFileSync(new URL("./StandardPackageActions.tsx", import.meta.url), "utf8");
const NAVIGATION = readFileSync(new URL("./standardOwnerNavigation.ts", import.meta.url), "utf8");
const DYNAMIC_RESOLVER = readFileSync(
	new URL("../../routes/sections/dashboard/dynamic-resolver.tsx", import.meta.url),
	"utf8",
);
const STATIC_ROUTES = readFileSync(
	new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url),
	"utf8",
);

test("the four data-standard owners expose one permission-aware package upload action", () => {
	assert.match(ACTIONS, /useGovernanceManageAccess/);
	assert.match(ACTIONS, /disabled=\{!canManage\}/);
	assert.match(ACTIONS, /下载标准包模板/);
	assert.match(ACTIONS, /导入标准包/);
	assert.match(ACTIONS, /buildStandardPackageImportRoute\(searchParams, source\)/);

	assert.match(DYNAMIC_RESOLVER, /"\/governance\/standards\/glossary": "glossary"/);
	assert.match(DYNAMIC_RESOLVER, /"\/governance\/standards\/elements": "elements"/);
	assert.match(DYNAMIC_RESOLVER, /"\/governance\/standards\/reference": "reference"/);
	assert.match(STATIC_ROUTES, /<StandardPackageActions source="units" \/>/);
});

test("the package round trip has a stable route and return label for every owner", () => {
	for (const source of ["elements", "glossary", "reference", "units"]) {
		assert.match(NAVIGATION, new RegExp(`${source}: \\{ source: "${source}"`));
	}
	assert.match(NAVIGATION, /buildStandardPackageReturnRoute/);
	assert.match(NAVIGATION, /applied/);
});
