import assert from "node:assert/strict";
import test from "node:test";
import type { IngestionTaskDTO } from "@/api/ingestion";
import {
	buildTransformEditRestoreState,
	parseTransformCreateDraft,
	resolveTemplateSourceCategory,
	serializeTransformCreateDraft,
} from "./transformCreateState.helpers";

test("parseTransformCreateDraft returns null for invalid payloads and restores valid drafts", () => {
	assert.equal(parseTransformCreateDraft(null), null);
	assert.equal(parseTransformCreateDraft("not-json"), null);
	assert.equal(parseTransformCreateDraft(JSON.stringify({ name: "demo" })), null);

	const savedAt = "2026-03-24T10:00:00.000Z";
	const raw = serializeTransformCreateDraft({ name: "excel-task", sourceCategory: "file" }, savedAt);
	assert.deepEqual(parseTransformCreateDraft(raw), {
		savedAt,
		formValues: { name: "excel-task", sourceCategory: "file" },
		sourceCategory: "file",
	});
});

test("buildTransformEditRestoreState normalizes file-flow restore metadata", () => {
	const state = buildTransformEditRestoreState(
		{
			id: 1,
			name: "excel-import",
			sourceType: "txtfilereader",
			sourceConfig: {},
			syncMode: "full_refresh",
			destinationConfig: {
				_extraColumns: [{ name: "etl_batch_no", type: "string" }],
			},
			tableMapping: [{ source: "sheet1", target: "ods_excel_demo" }],
		} as IngestionTaskDTO,
		{
			mapTaskToForm: () => ({ name: "excel-import", sourceCategory: "database" }),
			extractFileUploadResult: () => ({
				hostPath: "/tmp/demo.csv",
				containerPath: "/data/demo.csv",
				fileType: "csv",
				columns: [{ name: "col_1", type: "string" }],
				originalName: "demo.xlsx",
			}),
			extractMappingTables: () => ["sheet1"],
			tryParseJson: raw => (typeof raw === "object" && raw ? (raw as Record<string, any>) : undefined),
		}
	);

	assert.deepEqual(state.formValues, { name: "excel-import", sourceCategory: "database" });
	assert.equal(state.sourceCategory, "file");
	assert.equal(state.forceReaderType, "txtfilereader");
	assert.deepEqual(state.mappingTables, ["sheet1"]);
	assert.deepEqual(state.extraColumns, [{ name: "etl_batch_no", type: "string" }]);
	assert.equal(state.fileUploadResult?.originalName, "demo.xlsx");
});

test("resolveTemplateSourceCategory prefers current form value and normalizes template fallbacks", () => {
	assert.equal(resolveTemplateSourceCategory("file", { sourceCategory: "database" }, "database"), "file");
	assert.equal(resolveTemplateSourceCategory("", { sourceCategory: "FILE" }, "database"), "file");
	assert.equal(resolveTemplateSourceCategory("", {}, "DATABASE"), "database");
	assert.equal(resolveTemplateSourceCategory("", { sourceCategory: "api" }, "api"), undefined);
});
