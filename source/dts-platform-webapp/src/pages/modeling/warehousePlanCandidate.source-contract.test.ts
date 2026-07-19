import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const currentDir = path.dirname(fileURLToPath(import.meta.url));
const api = fs.readFileSync(path.resolve(currentDir, "../../api/warehousePlanApi.ts"), "utf8");

test("asset-first candidate API is deterministic, server-confirmed and canonical", () => {
	assert.match(api, /model-candidates\/preview/);
	assert.match(api, /model-candidates\/confirm/);
	assert.match(api, /candidateId[\s\S]*modelSpec/);
	assert.match(api, /CreateModelSpecCommand/);
	assert.doesNotMatch(api, /objectId|processId|candidateLedger/);
});
