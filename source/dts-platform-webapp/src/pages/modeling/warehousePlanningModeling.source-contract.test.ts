import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const sqlModeling = readFileSync(new URL("./SqlModelingPage.tsx", import.meta.url), "utf8");

test("SQL modeling presents a dimension draft with planning provenance and repair routes", () => {
	assert.match(sqlModeling, /resolveWarehousePlanningContext/);
	assert.match(sqlModeling, /resolveDimensionCandidateGate/);
	assert.match(sqlModeling, /sql-dimension-modeling-context/);
	assert.match(sqlModeling, /创建维度模型草稿/);
	assert.match(sqlModeling, /governance\/subjects/);
	assert.match(sqlModeling, /buildPlanningRoute/);
});

test("SQL modeling exposes the DWD grain gate", () => {
	assert.match(sqlModeling, /grainDeclaration/);
	assert.match(sqlModeling, /validateGrainApi/);
	assert.match(sqlModeling, /粒度语句/);
});
