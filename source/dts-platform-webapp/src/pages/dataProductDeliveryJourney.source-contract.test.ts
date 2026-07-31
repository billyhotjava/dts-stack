import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path: string) => readFileSync(new URL(path, import.meta.url), "utf8");
const OPS = read("./ops/OpsInstancesPage.tsx");
const BACKFILL = read("./ops/OpsBackfillPage.tsx");
const AUDIT = read("./ops/AuditEvidencePage.tsx");
const API = read("./services/ApiServicesPage.tsx");
const PRODUCT = read("./services/DataProductsPage.tsx");

test("operations pages preserve journey context", () => {
	for (const source of [OPS, BACKFILL, AUDIT]) {
		assert.match(source, /JourneyContextBar/);
		assert.match(source, /modelId|journey=e2e-data-product|buildJourneyUrl|searchParams/);
	}
	assert.match(OPS, /modelId/);
	assert.match(BACKFILL, /buildJourneyUrl|searchParams/);
	assert.match(AUDIT, /journey=e2e-data-product|searchParams/);
});

test("service pages accept model context and expose customer consumption targets", () => {
	for (const source of [API, PRODUCT]) {
		assert.match(source, /JourneyContextBar/);
		assert.match(source, /modelId|datasetId/);
	}
	assert.match(API, /新建 API/);
	assert.match(PRODUCT, /数据产品/);
	assert.match(PRODUCT, /datasets/);
});
