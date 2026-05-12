import assert from "node:assert/strict";
import test from "node:test";
import {
	applyPastedOdsFieldsToFileColumns,
	parsePastedOdsFields,
} from "./fileOdsPasteMapping.helpers";

test("parsePastedOdsFields supports commas, chinese commas, new lines and tabs", () => {
	assert.deepEqual(
		parsePastedOdsFields("field_a, field_b，field_c\nfield_d\tfield_e"),
		["field_a", "field_b", "field_c", "field_d", "field_e"]
	);
});

test("parsePastedOdsFields ignores blanks and repeated separators", () => {
	assert.deepEqual(parsePastedOdsFields("field_a,,\n，\tfield_b"), ["field_a", "field_b"]);
	assert.deepEqual(parsePastedOdsFields(" \n\t "), []);
});

test("applyPastedOdsFieldsToFileColumns maps pasted fields by order and marks extra excel columns", () => {
	const result = applyPastedOdsFieldsToFileColumns(
		[
			{ name: "excel_col_1", type: "string", _odsMatched: false, _odsExtra: false },
			{ name: "excel_col_2", type: "string", _odsMatched: false, _odsExtra: false },
			{ name: "excel_col_3", type: "string", _odsMatched: false, _odsExtra: false },
		],
		["ods_field_1", "ods_field_2"]
	);

	assert.equal(result.matchedCount, 2);
	assert.deepEqual(result.unmatchedFields, []);
	assert.deepEqual(
		result.columns.map((column) => ({
			name: column.name,
			_odsMatched: column._odsMatched,
			_odsExtra: column._odsExtra,
		})),
		[
			{ name: "ods_field_1", _odsMatched: true, _odsExtra: false },
			{ name: "ods_field_2", _odsMatched: true, _odsExtra: false },
			{ name: "excel_col_3", _odsMatched: false, _odsExtra: true },
		]
	);
});

test("applyPastedOdsFieldsToFileColumns keeps unmatched pasted fields when ods list is longer than excel columns", () => {
	const result = applyPastedOdsFieldsToFileColumns(
		[
			{ name: "excel_col_1", type: "string", _odsMatched: false, _odsExtra: false },
			{ name: "excel_col_2", type: "string", _odsMatched: false, _odsExtra: false },
		],
		["ods_field_1", "ods_field_2", "ods_field_3"]
	);

	assert.equal(result.matchedCount, 2);
	assert.deepEqual(result.unmatchedFields, ["ods_field_3"]);
	assert.deepEqual(
		result.columns.map((column) => ({
			name: column.name,
			_odsMatched: column._odsMatched,
			_odsExtra: column._odsExtra,
		})),
		[
			{ name: "ods_field_1", _odsMatched: true, _odsExtra: false },
			{ name: "ods_field_2", _odsMatched: true, _odsExtra: false },
		]
	);
});

test("applyPastedOdsFieldsToFileColumns ignores system-managed ODS fields", () => {
	const result = applyPastedOdsFieldsToFileColumns(
		[
			{ name: "excel_col_1", type: "string", _odsMatched: false, _odsExtra: false },
			{ name: "excel_col_2", type: "string", _odsMatched: false, _odsExtra: false },
		],
		["id", "_dts_source_system", "ods_field_1", "_DTS_BATCH_ID", "import_time", "ods_field_2"]
	);

	assert.equal(result.matchedCount, 2);
	assert.deepEqual(result.unmatchedFields, []);
	assert.deepEqual(
		result.columns.map((column) => ({
			name: column.name,
			_odsMatched: column._odsMatched,
			_odsExtra: column._odsExtra,
		})),
		[
			{ name: "ods_field_1", _odsMatched: true, _odsExtra: false },
			{ name: "ods_field_2", _odsMatched: true, _odsExtra: false },
		]
	);
});
