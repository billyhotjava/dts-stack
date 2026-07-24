import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const WORKBENCH = readFileSync(new URL("./MetricWorkbenchPage.tsx", import.meta.url), "utf8");
const OWNER = readFileSync(new URL("./metric-workbench/IndicatorDefinitionPanel.tsx", import.meta.url), "utf8");
const LEGACY_DICTIONARY = readFileSync(new URL("../governance/IndicatorsPage.tsx", import.meta.url), "utf8");
const PLATFORM_API = readFileSync(new URL("../../api/platformApi.ts", import.meta.url), "utf8");

test("metric workbench owns the complete indicator definition lifecycle", () => {
	assert.match(WORKBENCH, /IndicatorDefinitionPanel/);
	assert.match(OWNER, /listIndicators/);
	assert.match(OWNER, /getIndicator/);
	assert.match(OWNER, /createIndicator/);
	assert.match(OWNER, /updateIndicator/);
	assert.match(OWNER, /runIndicatorPreflight/);
	assert.match(OWNER, /publishIndicatorWithPreview/);
	assert.match(OWNER, /archiveIndicator/);
	assert.match(OWNER, /listIndicatorVersions/);
	assert.match(OWNER, /syncIndicatorDependencyReferences/);
});

test("derived indicators expose dependencies, period, modifiers, and expression preflight", () => {
	assert.match(OWNER, /dependencyIndicators|dependencyCodes/);
	assert.match(OWNER, /timeGrain/);
	assert.match(OWNER, /windowFunction/);
	assert.match(OWNER, /expressionSql/);
	assert.match(OWNER, /validateIndicatorDefinition/);
	assert.match(OWNER, /planIndicatorDependencyReferences/);
});

test("indicator dictionary deep links converge to the platform workbench without legacy metrics redirects", () => {
	assert.doesNotMatch(LEGACY_DICTIONARY, /window\.location|\/metrics\/dictionary/);
	assert.match(LEGACY_DICTIONARY, /\/modeling\/metric-workbench/);
	assert.match(LEGACY_DICTIONARY, /indicatorId/);
	assert.match(LEGACY_DICTIONARY, /returnTo/);
});

test("indicator owner remains free of retired business object writes", () => {
	assert.doesNotMatch(OWNER, /businessObject|objectId|semanticModelingApi|updateSemanticMetric/);
});

test("governance indicators expose their own derivation compiler endpoint", () => {
	assert.match(PLATFORM_API, /validateIndicatorDerivation/);
	assert.match(PLATFORM_API, /governance\/indicators\/\$\{id\}\/derivation\/validate/);
	assert.doesNotMatch(OWNER, /semantic\/metrics\/derivation\/validate/);
});
