import assert from "node:assert/strict";
import test from "node:test";
import { API_RAW_RECORD_COLUMN, buildApiReaderConfig, mapTaskToForm } from "./ingestionFormHelpers";

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
	assert.deepEqual(config.resource.cursor, {
		field: "updatedAt",
		injectInto: "query",
		parameterName: "updatedAfter",
	});
});

test("mapTaskToForm restores api resource pagination and cursor fields", () => {
	const form = mapTaskToForm({
		name: "crm-orders-api",
		sourceType: "httpreader",
		sourceDataSourceId: "source-1",
		syncMode: "incremental",
		sourceConfig: {
			connectorType: "api",
			sourceCategory: "api",
			sourceSystem: "CRM",
			resource: {
				resourceId: "orders",
				displayName: "订单",
				path: "/v1/orders",
				method: "POST",
				recordPath: "$.data.items",
				query: { status: "paid" },
				bodyTemplate: { tenant: "${tenant}" },
				pagination: { type: "page", pageParam: "page", sizeParam: "size", pageSize: 100 },
				cursor: { type: "field", field: "updatedAt", injectInto: "query", parameterName: "updatedAfter" },
			},
		},
		tableMapping: [{ source: "orders", target: "ods_api_crm_orders" }],
	} as any);

	assert.equal(form.sourceCategory, "api");
	assert.equal(form.apiResourceId, "orders");
	assert.equal(form.apiResourcePath, "/v1/orders");
	assert.equal(form.apiMethod, "POST");
	assert.equal(form.apiRecordPath, "$.data.items");
	assert.equal(form.apiPaginationJson, '{\n  "type": "page",\n  "pageParam": "page",\n  "sizeParam": "size",\n  "pageSize": 100\n}');
	assert.equal(form.apiCursorJson, '{\n  "type": "field",\n  "field": "updatedAt",\n  "injectInto": "query",\n  "parameterName": "updatedAfter"\n}');
});
