import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (relativePath: string) => readFileSync(new URL(relativePath, import.meta.url), "utf8");

const ACTIVE_MAINLINE_FILES = [
	"../governance/SubjectAreasPage.tsx",
	"../governance/ElementsPage.tsx",
	"../workbench/DataManagementWorkbenchPage.tsx",
	"../workbench/dataManagementThemeModel.ts",
	"../explore/etl/EltConsolePage.tsx",
	"../ops/OpsInstancesPage.tsx",
	"../ops/ReleaseGovernancePage.tsx",
	"./WarehousePlanDetailPage.tsx",
	"./MetricWorkbenchPage.tsx",
	"./semantic-workspace/ModelingConceptCards.tsx",
] as const;

const RETIRED_ROUTE_PATTERN =
	/\/studio\/low-code-development|\/modeling\/dbt-files|\/modeling\/semantic\/(subjects|objects|models|metrics|publish|runs)/;

test("active customer journeys never generate a retired modeling route", () => {
	for (const file of ACTIVE_MAINLINE_FILES) {
		assert.doesNotMatch(read(file), RETIRED_ROUTE_PATTERN, `${file} must use a canonical Sprint-67 route`);
	}
});

test("static and dynamic routers send every retired path through the same compatibility page", () => {
	const staticRoutes = read("../../routes/sections/dashboard/static-routes.tsx");
	const dynamicResolver = read("../../routes/sections/dashboard/dynamic-resolver.tsx");
	for (const path of [
		"studio/low-code-development",
		"modeling/dbt-files",
		"modeling/semantic/subjects",
		"modeling/semantic/objects",
		"modeling/semantic/models",
		"modeling/semantic/metrics",
		"modeling/semantic/publish",
		"modeling/semantic/runs",
	]) {
		assert.match(
			staticRoutes,
			new RegExp(`path: "${path}"[^\\n]*<S><ModelingCompatibilityPage`),
			`static route ${path} must use ModelingCompatibilityPage`,
		);
		assert.match(
			dynamicResolver,
			new RegExp(`"/${path}": "/pages/modeling/ModelingCompatibilityPage"`),
			`dynamic route ${path} must use ModelingCompatibilityPage`,
		);
	}
});
