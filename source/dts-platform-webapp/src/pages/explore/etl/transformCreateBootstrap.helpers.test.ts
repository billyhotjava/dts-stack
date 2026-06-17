import assert from "node:assert/strict";
import test from "node:test";
import { loadTransformCreateBootstrap } from "./transformCreateBootstrap.helpers";

test("loadTransformCreateBootstrap normalizes successful lookup payloads and picks first template by default", async () => {
	const result = await loadTransformCreateBootstrap(
		{
			loadDataSources: async () => [{ id: "ds-1", name: "pg-lake", type: "postgres" }],
			loadConnectorCapabilities: async () => [{ connectorType: "addax", capabilities: ["FULL"] }],
			loadTaskTemplates: async () => [{ id: "tpl-1", name: "默认模板" }, { id: "tpl-2", name: "备用模板" }],
				loadDefaultDestinationStatus: async () => ({
					available: true,
					writerTypeReady: true,
					writerConfigReady: true,
					destinationName: "pg-lake",
					writerType: "postgresqlwriter",
				}),
				loadTargetDataSourceSelections: async () => ({
					defaultDataSourceId: "ds-1",
					defaultSource: "ADMIN_DEFAULT",
					items: [{ id: "ds-1", name: "pg-lake", type: "postgres", recommended: true }],
				}),
				loadSqlModels: async () => [{ id: "m1", name: "ods_order" }],
			},
		undefined
	);

	assert.deepEqual(result.dataSources, [{ id: "ds-1", name: "pg-lake", type: "postgres" }]);
	assert.equal(result.capabilityLoadFailed, false);
	assert.deepEqual(result.connectorCapabilities, [{ connectorType: "addax", capabilities: ["FULL"] }]);
	assert.equal(result.selectedTemplateId, "tpl-1");
	assert.deepEqual(result.taskTemplates, [
		{ id: "tpl-1", name: "默认模板" },
		{ id: "tpl-2", name: "备用模板" },
	]);
		assert.equal(result.defaultDestinationError, "");
		assert.equal(result.defaultDestinationStatus?.writerType, "postgresqlwriter");
		assert.equal(result.targetDataSourceDefaultId, "ds-1");
		assert.deepEqual(result.targetDataSources, [{ id: "ds-1", name: "pg-lake", type: "postgres", recommended: true }]);
		assert.equal(result.targetDataSourcesError, "");
		assert.deepEqual(result.sqlModels, [{ id: "m1", name: "ods_order" }]);
		assert.equal(result.dataSourcesError, "");
		assert.equal(result.sqlModelsError, "");
});

test("loadTransformCreateBootstrap preserves selected template and degrades gracefully on partial failures", async () => {
	const result = await loadTransformCreateBootstrap(
		{
			loadDataSources: async () => {
				throw new Error("源端超时");
			},
			loadConnectorCapabilities: async () => {
				throw new Error("capabilities down");
			},
			loadTaskTemplates: async () => [{ id: "tpl-1", name: "默认模板" }],
				loadDefaultDestinationStatus: async () => {
					throw new Error("默认数据湖不可达");
				},
				loadTargetDataSourceSelections: async () => {
					throw new Error("目标湖仓列表失败");
				},
				loadSqlModels: async () => {
					throw new Error("模型接口失败");
				},
		},
		"tpl-existing"
	);

	assert.deepEqual(result.dataSources, []);
	assert.equal(result.dataSourcesError, "源端超时");
	assert.equal(result.capabilityLoadFailed, true);
	assert.deepEqual(result.connectorCapabilities, []);
	assert.equal(result.selectedTemplateId, "tpl-existing");
		assert.deepEqual(result.taskTemplates, [{ id: "tpl-1", name: "默认模板" }]);
		assert.equal(result.defaultDestinationStatus, null);
		assert.equal(result.defaultDestinationError, "默认数据湖不可达");
		assert.deepEqual(result.targetDataSources, []);
		assert.equal(result.targetDataSourcesError, "目标湖仓列表失败");
		assert.deepEqual(result.sqlModels, []);
		assert.equal(result.sqlModelsError, "模型接口失败");
	});
