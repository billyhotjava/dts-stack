import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const STATIC_ROUTES = readFileSync(new URL("./static-routes.tsx", import.meta.url), "utf8");
const DYNAMIC_RESOLVER = readFileSync(new URL("./dynamic-resolver.tsx", import.meta.url), "utf8");

const assertRouteBinding = (path: string, component: string) => {
	const escapedPath = path.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
	assert.match(
		STATIC_ROUTES,
		new RegExp(`path: [\\"']${escapedPath}[\\"'],[\\s\\S]{0,180}?<${component} \\/>`),
		`${path} must render ${component}`,
	);
};

test("data integration workspace owns the canonical access routes", () => {
	assertRouteBinding("foundation/data-sources", "AccessWorkspacePage");
	assertRouteBinding("foundation/data-sources/database", "AccessWorkspacePage");
	assertRouteBinding("foundation/data-sources/api", "AccessWorkspacePage");
	assertRouteBinding("foundation/data-sources/files", "AccessWorkspacePage");
	assertRouteBinding("foundation/data-sources/defaults", "AccessDefaultsPage");
	assertRouteBinding("foundation/data-sources/access/new", "AccessPlanWizardPage");
	assertRouteBinding("foundation/data-sources/access/:taskId", "AccessPlanDetailPage");
});

test("legacy ingestion routes redirect into the access workspace", () => {
	assert.match(STATIC_ROUTES, /LegacyDataIntegrationRedirect/);
	for (const path of [
		"explore/etl/transform",
		"explore/etl/transform/new",
		"explore/etl/transform/:id",
		"foundation/access-changes",
	]) {
		assertRouteBinding(path, "LegacyDataIntegrationRedirect");
	}

	assert.match(DYNAMIC_RESOLVER, /foundation\/data-sources[^\n]*AccessWorkspacePage/);
	assert.doesNotMatch(DYNAMIC_RESOLVER, /foundation\/data-sources[^\n]*DataSourcesPage/);
});
