import assert from "node:assert/strict";
import test from "node:test";
import { buildSqlModelBatchDeleteDetail } from "./sqlModelBatchDeleteResult.helpers.ts";

test("buildSqlModelBatchDeleteDetail marks failed ids and keeps model metadata", () => {
	const result = buildSqlModelBatchDeleteDetail({
		requestedIds: ["m1", "m2"],
		models: [
			{ id: "m1", name: "ads_overview", layer: "ADS", planName: "prj1", modelPath: "models/ads/prj1/ads_overview.sql" },
			{ id: "m2", name: "dws_risk", layer: "DWS", planName: "prj1", modelPath: "models/dws/prj1/dws_risk.sql" },
		],
		result: {
			requested: 2,
			deleted: 1,
			failed: 1,
			failures: [{ modelId: "m2", message: "存在下游引用" }],
		},
	});

	assert.equal(result.requested, 2);
	assert.equal(result.deleted, 1);
	assert.equal(result.failed, 1);
	assert.deepEqual(result.rows, [
		{
			modelId: "m1",
			name: "ads_overview",
			layer: "ADS",
			planName: "prj1",
			modelPath: "models/ads/prj1/ads_overview.sql",
			status: "success",
			message: undefined,
		},
		{
			modelId: "m2",
			name: "dws_risk",
			layer: "DWS",
			planName: "prj1",
			modelPath: "models/dws/prj1/dws_risk.sql",
			status: "failed",
			message: "存在下游引用",
		},
	]);
});

test("buildSqlModelBatchDeleteDetail falls back when model metadata is missing", () => {
	const result = buildSqlModelBatchDeleteDetail({
		requestedIds: ["ghost-model"],
		models: [],
		result: {
			requested: 1,
			deleted: 0,
			failed: 1,
			failures: [{ modelId: "ghost-model", message: "" }],
		},
	});

	assert.deepEqual(result.rows, [
		{
			modelId: "ghost-model",
			name: "ghost-model",
			layer: "未分层",
			planName: "未归档",
			modelPath: "-",
			status: "failed",
			message: "删除失败",
		},
	]);
});
