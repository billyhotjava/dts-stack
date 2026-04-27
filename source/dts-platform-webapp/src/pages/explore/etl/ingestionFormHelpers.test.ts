import assert from "node:assert/strict";
import test from "node:test";
import { API_RAW_RECORD_COLUMN, buildApiReaderConfig } from "./ingestionFormHelpers";

test("buildApiReaderConfig adds raw ODS landing and schema snapshot contract", () => {
	const config = buildApiReaderConfig({
		sourceSystem: "CRM",
		apiResourcePath: "/v1/orders",
		apiMethod: "GET",
		apiRecordPath: "data.items",
		apiCursorJson: '{"field":"updatedAt","injectInto":"query","parameterName":"updatedAfter"}',
	});

	assert.equal(config.readerType, "httpreader");
	assert.equal(config.connectorType, "api");
	assert.equal(config.sourceSystem, "CRM");
	assert.equal(config.resource.resourceId, "v1_orders");
	assert.equal(config.resource.targetTable, "ods_api_crm_v1_orders");
	assert.deepEqual(config.resource.landing, {
		mode: "raw_record",
		rawRecordColumn: API_RAW_RECORD_COLUMN,
		technicalColumns: [
			"_dts_source_system",
			"_dts_source_resource",
			"_dts_endpoint",
			"_dts_import_time",
			"_dts_batch_id",
			"_dts_execution_id",
			"_dts_page_no",
			"_dts_record_no",
			"_dts_cursor_value",
		],
	});
	assert.deepEqual(config.resource.schemaSnapshot, {
		enabled: true,
		driftPolicy: "notify",
	});
});
