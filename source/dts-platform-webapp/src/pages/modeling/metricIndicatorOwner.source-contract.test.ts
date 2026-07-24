import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const WORKBENCH = readFileSync(new URL("./MetricWorkbenchPage.tsx", import.meta.url), "utf8");
const OWNER = readFileSync(new URL("./metric-workbench/IndicatorDefinitionPanel.tsx", import.meta.url), "utf8");
const OWNER_FORM = readFileSync(new URL("./metric-workbench/IndicatorDefinitionForm.tsx", import.meta.url), "utf8");
const OWNER_HISTORY = readFileSync(new URL("./metric-workbench/IndicatorVersionHistory.tsx", import.meta.url), "utf8");
const OWNER_SURFACE = `${OWNER}\n${OWNER_FORM}\n${OWNER_HISTORY}`;
const OWNER_WORKFLOW = readFileSync(new URL("./indicatorDefinitionWorkflow.ts", import.meta.url), "utf8");
const LEGACY_DICTIONARY = readFileSync(new URL("../governance/IndicatorsPage.tsx", import.meta.url), "utf8");
const PLATFORM_API = readFileSync(new URL("../../api/platformApi.ts", import.meta.url), "utf8");

test("metric workbench owns the complete indicator definition lifecycle", () => {
	assert.match(WORKBENCH, /IndicatorDefinitionPanel/);
	assert.match(OWNER, /<IndicatorDefinitionForm/);
	assert.match(OWNER, /<IndicatorVersionHistory/);
	assert.match(OWNER, /listIndicators/);
	assert.match(OWNER, /getIndicator/);
	assert.match(OWNER, /createIndicator/);
	assert.match(OWNER, /updateIndicator/);
	assert.match(OWNER, /runIndicatorPreflight/);
	assert.match(OWNER, /publishIndicatorWithPreview/);
	assert.match(OWNER, /publishIndicatorRevision/);
	assert.match(OWNER, /archiveIndicator/);
	assert.match(OWNER, /listIndicatorVersions/);
});

test("published revisions stay visible until the atomic publish-revision endpoint succeeds", () => {
	assert.match(OWNER, /current\?\.status === "PUBLISHED"/);
	assert.match(OWNER, /publishIndicatorRevision\(current\.id, payload\)/);
	assert.doesNotMatch(OWNER, /createIndicatorReference|deleteIndicatorReference|listIndicatorReferences/);
	assert.doesNotMatch(OWNER, /已发布指标会先生成一个新的草稿版本再归档/);
});

test("derived indicators expose dependencies, period, modifiers, and expression preflight", () => {
	assert.match(OWNER_SURFACE, /dependencyIndicators|dependencyCodes/);
	assert.match(OWNER_SURFACE, /timeGrain/);
	assert.match(OWNER_SURFACE, /windowFunction/);
	assert.match(OWNER_SURFACE, /expressionSql/);
	assert.match(OWNER_SURFACE, /validateIndicatorDefinition/);
	assert.match(OWNER_SURFACE, /buildIndicatorFormChanges/);
});

test("indicator dictionary deep links converge to the platform workbench without legacy metrics redirects", () => {
	assert.doesNotMatch(LEGACY_DICTIONARY, /window\.location|\/metrics\/dictionary/);
	assert.match(LEGACY_DICTIONARY, /\/modeling\/metric-workbench/);
	assert.match(LEGACY_DICTIONARY, /buildMetricWorkbenchLocation/);
});

test("indicator owner remains free of retired business object writes", () => {
	assert.doesNotMatch(OWNER_SURFACE, /businessObject|objectId|semanticModelingApi|updateSemanticMetric/);
});

test("governance indicators expose their own derivation compiler endpoint", () => {
	assert.match(PLATFORM_API, /validateIndicatorDerivation/);
	assert.match(PLATFORM_API, /governance\/indicators\/\$\{id\}\/derivation\/validate/);
	assert.doesNotMatch(OWNER, /semantic\/metrics\/derivation\/validate/);
});

test("list refresh and direct deep-link detail loading are independent", () => {
	const listStart = OWNER.indexOf("const loadList");
	const listEnd = OWNER.indexOf("\n\tuseEffect(", listStart);
	assert.ok(listStart >= 0 && listEnd > listStart);
	assert.doesNotMatch(OWNER.slice(listStart, listEnd), /openIndicator|getIndicator/);
	assert.match(OWNER, /resolveIndicatorDetailRequest/);
	assert.match(OWNER, /requestedIndicatorId/);
});

test("owner writes are touched-only, CAS guarded, and protected from late detail responses", () => {
	assert.match(OWNER, /getFieldsValue\(true/);
	assert.match(OWNER, /editedFieldNamesRef/);
	assert.match(OWNER, /changedValues/);
	assert.doesNotMatch(OWNER, /isFieldsTouched/);
	assert.doesNotMatch(OWNER, /\(\{\s*touched\s*\}\)\s*=>/);
	assert.match(OWNER, /buildExistingIndicatorMutationPayload/);
	assert.match(OWNER, /shouldApplyIndicatorDetailResponse/);
	assert.match(OWNER, /onValuesChange/);
	assert.doesNotMatch(OWNER, /onFieldsChange/);
});

test("user-triggered programmatic form changes explicitly join the edited field set", () => {
	assert.match(OWNER, /markEditedFields\(\["isDerived", "aggregationType", "dependencyCodes"\]\)/);
	assert.match(OWNER, /markEditedFields\(\["expressionSql"\]\)/);
});

test("rollback publishes atomically and business category stays an independent free-form property", () => {
	assert.match(OWNER_WORKFLOW, /publishAfterRollback: true/);
	assert.match(OWNER, /rollbackIndicatorAndPublish/);
	assert.match(OWNER_FORM, /name="category" label="指标分类"[\s\S]{0,120}<Input/);
	assert.doesNotMatch(OWNER, /setFieldsValue\(\{\s*category:/);
	assert.match(OWNER_HISTORY, /回滚并发布/);
});
