import assert from "node:assert/strict";
import test from "node:test";
import {
	buildTruncateOutputRelationPreview,
	buildRebuildOutputRelationPreview,
	resolveOutputActionLoadErrorMessage,
} from "./sqlModelOutputAction.helpers.ts";

test("buildTruncateOutputRelationPreview derives async truncate preview without warehouse inspection", () => {
	const summary = buildTruncateOutputRelationPreview(
		{
			id: "m1",
			name: "biz_ads_major_project_overview",
			alias: "major_project_overview",
			schemaName: "public",
			materialized: "table",
			dagSelector: "tag:project-management",
		},
		{
			config: {
				database: "biadmin",
			},
		},
	);

	assert.deepEqual(summary, {
		modelId: "m1",
		modelName: "biz_ads_major_project_overview",
		selector: "tag:project-management",
		database: "biadmin",
		schema: "public",
		identifier: "major_project_overview",
		qualifiedName: "\"public\".\"major_project_overview\"",
		materialized: "table",
		relationType: undefined,
		exists: false,
		truncateAllowed: true,
		downstreamRefCount: 0,
		message: "将通过 dbt run-operation truncate_relation 异步清空产出 relation，执行时会在 dbt 侧检查 relation 是否可清空。",
		checkSkipped: true,
		checkMessage: "清空产出表将直接提交后台任务，不再预先检查目标库 relation。",
	});
});

test("buildRebuildOutputRelationPreview derives rebuild summary without warehouse inspection", () => {
	const summary = buildRebuildOutputRelationPreview(
		{
			id: "m1",
			name: "biz_ads_major_project_overview",
			alias: "major_project_overview",
			schemaName: "public",
			materialized: "table",
			dagSelector: "tag:project-management",
		},
		{
			config: {
				database: "biadmin",
			},
		},
	);

	assert.deepEqual(summary, {
		modelId: "m1",
		modelName: "biz_ads_major_project_overview",
		selector: "tag:project-management",
		database: "biadmin",
		schema: "public",
		identifier: "major_project_overview",
		qualifiedName: "\"public\".\"major_project_overview\"",
		materialized: "table",
		relationType: undefined,
		exists: false,
		truncateAllowed: false,
		downstreamRefCount: 0,
		message: "将通过 dbt --full-refresh 安全重建产出 relation，当前步骤不依赖平台直连目标数仓。",
		checkSkipped: true,
		checkMessage: "重建产出表将直接提交 dbt --full-refresh，不再预先检查目标库 relation。",
	});
});

test("resolveOutputActionLoadErrorMessage falls back to explainable target warehouse hint", () => {
	assert.equal(
		resolveOutputActionLoadErrorMessage(undefined, "truncate"),
		"检查产出表失败，请检查目标数仓连接、JDBC 驱动和数据源配置。",
	);
});

test("resolveOutputActionLoadErrorMessage preserves backend message when present", () => {
	assert.equal(
		resolveOutputActionLoadErrorMessage(
			{ message: "检查产出表失败: 目标数仓连接失败，请检查数据源配置后重试。" },
			"truncate",
		),
		"检查产出表失败: 目标数仓连接失败，请检查数据源配置后重试。",
	);
});
