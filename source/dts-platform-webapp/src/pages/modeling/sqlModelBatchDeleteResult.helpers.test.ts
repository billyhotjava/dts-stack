import assert from "node:assert/strict";
import test from "node:test";
import {
	buildSqlModelBatchDeleteDetail,
	buildSqlModelGovernanceDetail,
} from "./sqlModelBatchDeleteResult.helpers.ts";

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
	assert.equal(result.skipped, 0);
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

test("buildSqlModelGovernanceDetail keeps skipped and failed governance items explainable", () => {
	const result = buildSqlModelGovernanceDetail({
		requestedIds: ["m1", "m2", "m3"],
		preview: [
			{ modelId: "m1", name: "ads_overview", layer: "ADS", planName: "prj1", modelPath: "models/ads/prj1/ads_overview.sql" },
			{ modelId: "m2", name: "dws_risk", layer: "DWS", planName: "prj1", modelPath: "models/dws/prj1/dws_risk.sql" },
			{ modelId: "m3", name: "dim_node_type", layer: "DWD", planName: "prj1", modelPath: "models/dwd/prj1/dim_node_type.sql" },
		],
		result: {
			requested: 3,
			deleted: 1,
			skipped: 1,
			failed: 1,
			items: [
				{ modelId: "m1", name: "ads_overview", result: "DELETED", message: "模型记录已删除" },
				{ modelId: "m2", name: "dws_risk", result: "FAILED", message: "删除模型失败" },
				{ modelId: "m3", name: "dim_node_type", result: "SKIPPED", message: "当前账号无权限治理该模型" },
			],
		},
	});

	assert.equal(result.deleted, 1);
	assert.equal(result.failed, 1);
	assert.equal(result.skipped, 1);
	assert.deepEqual(result.rows.map((row) => ({ modelId: row.modelId, status: row.status, message: row.message })), [
		{ modelId: "m1", status: "success", message: "模型记录已删除" },
		{ modelId: "m2", status: "failed", message: "删除模型失败" },
		{ modelId: "m3", status: "skipped", message: "当前账号无权限治理该模型" },
	]);
});
