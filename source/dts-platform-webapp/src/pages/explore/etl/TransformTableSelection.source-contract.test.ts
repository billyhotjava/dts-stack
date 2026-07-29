import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const STEP_SOURCE = readFileSync(new URL("./steps/DbUnifiedStep.tsx", import.meta.url), "utf8");
const CREATE_SOURCE = readFileSync(new URL("./TransformCreatePage.tsx", import.meta.url), "utf8");
const DISCOVER_HANDLER_SOURCE = CREATE_SOURCE.slice(
	CREATE_SOURCE.indexOf("const handleDiscoverTables"),
	CREATE_SOURCE.indexOf("const handleApiPreview"),
);
const SELECTED_TABLES_NAME_INDEX = CREATE_SOURCE.indexOf('name="selectedTables"');
const SELECTED_TABLES_FIELD_SOURCE = CREATE_SOURCE.slice(
	CREATE_SOURCE.lastIndexOf("<Form.Item", SELECTED_TABLES_NAME_INDEX),
	CREATE_SOURCE.indexOf("</Form.Item>", SELECTED_TABLES_NAME_INDEX),
);
const EDIT_RESTORE_SOURCE = CREATE_SOURCE.slice(
	CREATE_SOURCE.indexOf("const loadTask = async"),
	CREATE_SOURCE.indexOf("const applyTemplate"),
);

test("database ingestion exposes one authoritative table-selection workflow", () => {
	assert.match(STEP_SOURCE, /批量录入表名（备用）/);
	assert.match(STEP_SOURCE, /tableSelectionMode === "manual"/);
	assert.doesNotMatch(STEP_SOURCE, />\s*应用选择\s*</);
	assert.doesNotMatch(STEP_SOURCE, /onApplyTables/);
});

test("create and draft submission share the canonical table-selection resolver", () => {
	assert.match(CREATE_SOURCE, /resolveTableSelection/);
	assert.ok(
		(CREATE_SOURCE.match(/resolveWriterTables/g) || []).length >= 3,
		"draft, submit and edit mapping should share writer table resolution",
	);
	assert.ok(
		(CREATE_SOURCE.match(/explicitTables:\s*isJsonMode\s*\?\s*undefined\s*:/g) || []).length >= 2,
		"draft and submit should treat the current JSON writer config as authoritative",
	);
	assert.match(CREATE_SOURCE, /tableMapping:\s*buildTableMapping/);
	assert.doesNotMatch(CREATE_SOURCE, /const handleApplyTables/);
	assert.doesNotMatch(CREATE_SOURCE, /onApplyTables=/);
});

test("table discovery refresh preserves the current canonical selection", () => {
	assert.doesNotMatch(DISCOVER_HANDLER_SOURCE, /syncSelectedTablesToForm\(\[\]/);
	assert.match(STEP_SOURCE, /name="sourceDataSourceId"[\s\S]*?onChange=[\s\S]*?preserveSelectionMode/);
});

test("manual selection validation stays registered while the fallback panel is collapsed", () => {
	assert.match(SELECTED_TABLES_FIELD_SOURCE, /readerTablesValidator/);
	assert.ok(
		(CREATE_SOURCE.match(/"readerType",\s*"selectedTables"/g) || []).length >= 2,
		"visual and JSON step validation should both include selectedTables",
	);
});

test("edit restore preserves the task's existing custom writer targets", () => {
	assert.doesNotMatch(EDIT_RESTORE_SOURCE, /syncSelectedTablesToForm\(restoreState\.mappingTables/);
	assert.match(EDIT_RESTORE_SOURCE, /selectedTableKeysRef\.current\s*=\s*restoreState\.mappingTables/);
});
