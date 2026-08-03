import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const STEP_SOURCE = readFileSync(new URL("./DatabaseAccessStep.tsx", import.meta.url), "utf8");
const PAYLOAD_SOURCE = readFileSync(new URL("./accessPlanPayload.ts", import.meta.url), "utf8");
const WIZARD_SOURCE = readFileSync(new URL("./useAccessPlanWizard.ts", import.meta.url), "utf8");
const DETAIL_SOURCE = readFileSync(new URL("./AccessPlanDetailPage.tsx", import.meta.url), "utf8");
const DISCOVERY_SOURCE = WIZARD_SOURCE.slice(
	WIZARD_SOURCE.indexOf("const discoverTables"),
	WIZARD_SOURCE.indexOf("const previewApi"),
);
const SUBMIT_SOURCE = WIZARD_SOURCE.slice(
	WIZARD_SOURCE.indexOf("const submit"),
	WIZARD_SOURCE.indexOf("return {", WIZARD_SOURCE.indexOf("const submit")),
);

test("database ingestion exposes one authoritative table-selection workflow", () => {
	assert.match(STEP_SOURCE, /name="tableSelectionMode"/);
	assert.match(STEP_SOURCE, /selectionMode === "manual"/);
	assert.match(STEP_SOURCE, /selectedRowKeys:\s*selectedTables/);
	assert.match(STEP_SOURCE, /form\.setFieldValue\("selectedTables", keys\.map\(String\)\)/);
	assert.doesNotMatch(STEP_SOURCE, /readerTables|批量录入表名|onApplyTables/);
});

test("database access payload uses the same selected tables for reader, streams and edit mapping", () => {
	assert.match(PAYLOAD_SOURCE, /const selectedTables = values\.selectedTables\.map\(normalizeText\)\.filter\(Boolean\)/);
	assert.match(PAYLOAD_SOURCE, /tableSelectionMode === "manual" && !selectedTables\.length/);
	assert.match(PAYLOAD_SOURCE, /readerTables:\s*values\.tableSelectionMode === "manual" \? selectedTables\.join\("\\n"\) : ""/);
	assert.match(PAYLOAD_SOURCE, /include:\s*values\.tableSelectionMode === "manual" \? selectedTables : undefined/);
	assert.match(PAYLOAD_SOURCE, /tableMapping:\s*sourceTables\.map/);
	assert.match(PAYLOAD_SOURCE, /selectedTables:\s*parseTableEntries\(legacy\.selectedTables \|\| legacy\.readerTables\)/);
});

test("table discovery invalidates stale selection and ignores superseded results", () => {
	assert.match(DISCOVERY_SOURCE, /form\.setFieldValue\("selectedTables", \[\]\)/);
	assert.match(DISCOVERY_SOURCE, /const requestId = \+\+discoveryRequestIdRef\.current/);
	assert.match(DISCOVERY_SOURCE, /databaseDiscoveryFingerprint\(form\.getFieldsValue\(true\)\) !== fingerprint/);
	assert.match(DISCOVERY_SOURCE, /数据库连接或筛选条件已变更，请重新发现源表/);
});

test("database plan submission reads preserved values from every wizard step", () => {
	assert.match(SUBMIT_SOURCE, /await form\.validateFields\(\);/);
	assert.match(SUBMIT_SOURCE, /const values = form\.getFieldsValue\(true\);/);
	assert.ok(SUBMIT_SOURCE.indexOf("await form.validateFields();") < SUBMIT_SOURCE.indexOf("form.getFieldsValue(true)"));
});

test("access detail shows the saved source-to-target mapping without exposing connection configuration", () => {
	assert.match(DETAIL_SOURCE, /展示任务已保存的源到目标映射，不读取或回显连接参数/);
	assert.match(DETAIL_SOURCE, /dataSource=\{tableMappings\}/);
	assert.doesNotMatch(DETAIL_SOURCE, /task\.sourceConfig|task\.destinationConfig/);
});
