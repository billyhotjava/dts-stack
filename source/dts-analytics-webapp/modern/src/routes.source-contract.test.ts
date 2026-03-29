import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const routesPath = new URL("./routes.tsx", import.meta.url);

test("routes.tsx wires the platform session guard into analytics routing", async () => {
	const source = await readFile(routesPath, "utf8");

	assert.equal(source.includes("PlatformSessionRouteGuard"), true);
	assert.equal(source.includes("buildPlatformLoginHref"), true);
	assert.equal(source.includes("hasPlatformSessionAccessToken"), true);
	assert.equal(source.includes("readRuntimePlatformBase"), true);
	assert.equal(source.includes('Component: PlatformSessionRouteGuard'), true);
});
