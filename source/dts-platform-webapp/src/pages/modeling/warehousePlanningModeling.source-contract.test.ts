import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const lowCode = readFileSync(new URL("./LowCodeDevelopmentPage.tsx", import.meta.url), "utf8");
const sqlModeling = readFileSync(new URL("./SqlModelingPage.tsx", import.meta.url), "utf8");

test("low-code modeling consumes the planning context and gates dimension candidates", () => {
	assert.match(lowCode, /resolveWarehousePlanningContext/);
	assert.match(lowCode, /resolveDimensionCandidateGate/);
	assert.match(lowCode, /dimension-modeling-context/);
	assert.match(lowCode, /维度模型候选/);
	assert.match(lowCode, /buildPlanningRoute/);
	assert.match(lowCode, /candidateGate\.status|dimensionCandidateGate\.status/);
});

test("SQL modeling presents a dimension draft with planning provenance and repair routes", () => {
	assert.match(sqlModeling, /resolveWarehousePlanningContext/);
	assert.match(sqlModeling, /resolveDimensionCandidateGate/);
	assert.match(sqlModeling, /sql-dimension-modeling-context/);
	assert.match(sqlModeling, /创建维度模型草稿/);
	assert.match(sqlModeling, /governance\/subjects/);
	assert.match(sqlModeling, /buildPlanningRoute/);
});
