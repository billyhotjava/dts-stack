import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const PAGE = readFileSync(new URL("./MeasurementUnitsPage.tsx", import.meta.url), "utf8");
const API = readFileSync(new URL("../../api/platformApi.ts", import.meta.url), "utf8");
const ROUTES = readFileSync(new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url), "utf8");
const DYNAMIC = readFileSync(new URL("../../routes/sections/dashboard/dynamic-resolver.tsx", import.meta.url), "utf8");

test("measurement-unit owner exposes CRUD, version history, references and model return context", () => {
	assert.match(PAGE, /createMeasurementUnit/);
	assert.match(PAGE, /updateMeasurementUnit/);
	assert.match(PAGE, /deactivateMeasurementUnit/);
	assert.match(PAGE, /listMeasurementUnitVersions/);
	assert.match(PAGE, /getMeasurementUnitReferences/);
	assert.match(PAGE, /returnTo/);
	assert.match(API, /If-Match/);
	assert.match(ROUTES, /governance\/standards\/units/);
	assert.match(DYNAMIC, /governance\/standards\/units/);
});
