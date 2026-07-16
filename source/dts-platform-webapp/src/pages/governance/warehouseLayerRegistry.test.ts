import assert from "node:assert/strict";
import test from "node:test";
import {
	DEFAULT_WAREHOUSE_LAYER_SCHEME,
	WAREHOUSE_LAYER_REGISTRY,
	isLayerFlowAllowed,
	resolveLayer,
} from "./warehouseLayerRegistry.ts";

test("warehouse layer registry contains governed layers and explains optional STG", () => {
	assert.deepEqual(
		WAREHOUSE_LAYER_REGISTRY.map((layer) => layer.key),
		["ODS_RAW", "ODS_STANDARDIZED", "STG", "DWD", "DWS", "ADS"],
	);
	for (const layer of WAREHOUSE_LAYER_REGISTRY) {
		assert.ok(layer.title);
		assert.ok(layer.responsibility);
		assert.ok(layer.kind);
		assert.ok(Array.isArray(layer.allowedUpstream));
		assert.ok(layer.namingPrefixes.length > 0);
	}
	const stg = resolveLayer("STG");
	assert.equal(stg?.kind, "TECHNICAL");
	assert.equal(stg?.optional, true);
	assert.equal(stg?.businessOutput, false);
	assert.match(stg?.dbtRole ?? "", /dbt staging|源表/);
	assert.ok(DEFAULT_WAREHOUSE_LAYER_SCHEME.enabledLayers.includes("STG"));
	assert.ok(!DEFAULT_WAREHOUSE_LAYER_SCHEME.outputLayers.includes("STG"));
});

test("layer flow allows governed upstream dependencies and blocks red lines", () => {
	assert.equal(isLayerFlowAllowed("ODS_RAW", "ODS_STANDARDIZED"), true);
	assert.equal(isLayerFlowAllowed("ODS_STANDARDIZED", "DWD"), true);
	assert.equal(isLayerFlowAllowed("ODS_STANDARDIZED", "STG"), true);
	assert.equal(isLayerFlowAllowed("DWD", "DWS"), true);
	assert.equal(isLayerFlowAllowed("DWS", "ADS"), true);
	assert.equal(isLayerFlowAllowed("ODS_RAW", "ADS"), false);
	assert.equal(isLayerFlowAllowed("ODS_RAW", "DWS"), false);
	assert.equal(resolveLayer("DWD")?.title, "明细事实 / 维度层");
});
