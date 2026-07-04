import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const RULES_PAGE = readFileSync(new URL("./QualityRulesPage.tsx", import.meta.url), "utf8");
const REPORT_TAB = readFileSync(new URL("./components/QualityReportTab.tsx", import.meta.url), "utf8");

test("F5-T02 quality rule execution opens the report tab with dataset context", () => {
	assert.match(RULES_PAGE, /triggerRun/);
	assert.match(RULES_PAGE, /triggerQualityRun\(\{ ruleId: rule\.id \}\)/);
	assert.match(RULES_PAGE, /params\.set\("tab", "report"\)/);
	assert.match(RULES_PAGE, /params\.set\("datasetId", String\(rule\.datasetId\)\)/);
	assert.match(RULES_PAGE, /setSearchParams\(params, \{ replace: true \}\)/);
	assert.match(RULES_PAGE, /可在质量报告查看结果/);
});

test("F5-T03 quality report can be deep-linked by datasetId", () => {
	assert.match(REPORT_TAB, /useSearchParams/);
	assert.match(REPORT_TAB, /searchParams\.get\("datasetId"\)/);
	assert.match(REPORT_TAB, /handleDatasetChange/);
	assert.match(REPORT_TAB, /params\.set\("tab", "report"\)/);
	assert.match(REPORT_TAB, /params\.set\("datasetId", nextDatasetId\)/);
	assert.match(REPORT_TAB, /onChange=\{handleDatasetChange\}/);
});
