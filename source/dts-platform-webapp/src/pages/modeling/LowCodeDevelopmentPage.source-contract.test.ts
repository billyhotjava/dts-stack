import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const PAGE = readFileSync(new URL("./LowCodeDevelopmentPage.tsx", import.meta.url), "utf8");
const RECOMMENDATION = readFileSync(new URL("./semantic-workspace/ConformedDimensionRecommendations.tsx", import.meta.url), "utf8");

test("low-code modeling exposes conformed-dimension reuse and one dbt output contract", () => {
	assert.match(PAGE, /ConformedDimensionRecommendations|可复用维度/);
	assert.match(RECOMMENDATION, /同名|引用/);
	assert.match(PAGE, /产出物：dbt 模型（SQL \+ schema\.yml）/);
	assert.match(PAGE, /ModelingConceptCards/);
});

test("concept cards support session-level dismissal", () => {
	const cards = readFileSync(new URL("./semantic-workspace/ModelingConceptCards.tsx", import.meta.url), "utf8");
	assert.match(cards, /sessionStorage/);
	assert.match(cards, /隐藏|关闭/);
});
