import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

// 旅程配置里的每个 route 都必须真实存在于应用路由注册源，防止页面改名/迁移造成旅程断链。
const JOURNEY_SOURCES = [
	"./journeyContext.ts",
	"./journeyStageState.ts",
	"./gateEvidence.ts",
	"./dataProductAcceptancePackage.ts",
] as const;

const STATIC_ROUTES = readFileSync(
	new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url),
	"utf8",
);
const DYNAMIC_RESOLVER = readFileSync(
	new URL("../../routes/sections/dashboard/dynamic-resolver.tsx", import.meta.url),
	"utf8",
);

const collectJourneyRoutes = () => {
	const routes = new Map<string, Set<string>>();
	for (const source of JOURNEY_SOURCES) {
		const content = readFileSync(new URL(source, import.meta.url), "utf8");
		const pattern = /(?:currentRoute|nextRoute|evidenceRoute|supportingRoute|route):\s*"(\/[^"?]+)/g;
		for (const match of content.matchAll(pattern)) {
			const route = match[1];
			if (!routes.has(route)) routes.set(route, new Set());
			routes.get(route)?.add(source);
		}
	}
	return routes;
};

const isRegisteredRoute = (route: string) => {
	if (DYNAMIC_RESOLVER.includes(`"${route}"`)) return true;
	const staticPath = route.replace(/^\//, "");
	return STATIC_ROUTES.includes(`path: "${staticPath}"`);
};

test("every journey route is registered in static routes or the dynamic resolver", () => {
	const routes = collectJourneyRoutes();
	assert.ok(routes.size >= 15, `expected to collect journey routes, got ${routes.size}`);

	const unregistered = [...routes.entries()].filter(([route]) => !isRegisteredRoute(route));
	assert.deepEqual(
		unregistered.map(([route, sources]) => `${route} (referenced by ${[...sources].join(", ")})`),
		[],
		"journey routes missing from route registration — page renamed or moved without updating journey config",
	);
});

test("journey routes cover the eight stage entry pages", () => {
	const routes = collectJourneyRoutes();
	for (const route of [
		"/foundation/data-sources",
		"/governance/subjects",
		"/governance/standards/elements",
		"/modeling/models",
		"/modeling/metric-workbench",
		"/explore/etl/scripts",
		"/services/apis",
		"/ops/instances",
	]) {
		assert.ok(routes.has(route), `stage entry route ${route} should be collected from journey sources`);
	}
});
