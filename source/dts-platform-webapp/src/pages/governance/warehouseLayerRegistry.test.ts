import assert from "node:assert/strict";
import test from "node:test";
import { WAREHOUSE_LAYER_REGISTRY, isLayerFlowAllowed, resolveLayer } from "./warehouseLayerRegistry.ts";

test("warehouse layer registry contains five controlled layers with responsibilities", () => {
	assert.deepEqual(
		WAREHOUSE_LAYER_REGISTRY.map((layer) => layer.key),
		["ODS_RAW", "ODS_STANDARDIZED", "DWD", "DWS", "ADS"],
	);
	for (const layer of WAREHOUSE_LAYER_REGISTRY) {
		assert.ok(layer.title);
		assert.ok(layer.responsibility);
		assert.ok(Array.isArray(layer.allowedUpstream));
		assert.ok(layer.namingPrefixes.length > 0);
	}
});

test("layer flow allows governed upstream dependencies and blocks red lines", () => {
	assert.equal(isLayerFlowAllowed("ODS_RAW", "ODS_STANDARDIZED"), true);
	assert.equal(isLayerFlowAllowed("ODS_STANDARDIZED", "DWD"), true);
	assert.equal(isLayerFlowAllowed("DWD", "DWS"), true);
	assert.equal(isLayerFlowAllowed("DWS", "ADS"), true);
	assert.equal(isLayerFlowAllowed("ODS_RAW", "ADS"), false);
	assert.equal(isLayerFlowAllowed("ODS_RAW", "DWS"), false);
	assert.equal(resolveLayer("DWD")?.title, "明细事实 / 维度层");
});
