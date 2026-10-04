import assert from "node:assert/strict";
import test from "node:test";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract.ts";
import type { IndicatorDefinition } from "@/features/modeling/indicators/indicatorDefinitionContract.ts";
import {
	bindAtomicMetricModel,
	bindIndicatorDependencies,
	bindMetricImplementationModel,
	governedMetricModels,
	metricModelKey,
	selectedMetricModelKey,
} from "./indicatorDefinitionBindingService.ts";

const publishedFact = {
	contractVersion: 2,
	compatibilityMode: "CANONICAL",
	id: "model-1",
	name: "项目事实表",
	status: "PUBLISHED",
	revision: 3,
	modelType: "FACT",
	layer: "DWD",
	fields: [
		{ name: "project_id", dataType: "STRING", nullable: false, role: "KEY" },
		{ name: "task_total", displayName: "任务总数", dataType: "BIGINT", nullable: false, role: "MEASURE" },
	],
} as ModelSpecView;

test("only published canonical fact summary and application models can define metrics", () => {
	const draft = { ...publishedFact, id: "model-2", status: "DRAFT" } as ModelSpecView;
	const dimension = { ...publishedFact, id: "model-3", modelType: "DIMENSION" } as ModelSpecView;
	assert.deepEqual(
		governedMetricModels([draft, dimension, publishedFact]).map((item) => item.id),
		["model-1"],
	);
});

test("atomic model selection pins one revision and one real measure field", () => {
	assert.deepEqual(bindAtomicMetricModel({ metricType: "ATOMIC" }, publishedFact), {
		metricType: "ATOMIC",
		measureField: null,
		sourceRefs: [{ sourceType: "SEMANTIC_MODEL_REVISION", sourceId: "model-1", sourceVersion: "r3" }],
	});
});

test("model selection identity includes the pinned revision", () => {
	assert.equal(metricModelKey(publishedFact), "model-1@r3");
	assert.equal(
		selectedMetricModelKey({
			sourceRefs: [{ sourceType: "SEMANTIC_MODEL_REVISION", sourceId: "model-1", sourceVersion: "r2" }],
		}),
		"model-1@r2",
	);
});

test("derived dependency selection pins published indicator versions", () => {
	const catalog: IndicatorDefinition[] = [
		{
			id: "metric-1",
			code: "BUDGET_TOTAL",
			name: "预算总额",
			status: "PUBLISHED",
			version: "v2",
			businessCategoryId: "category-1",
			dataDomainId: "domain-1",
			businessProcessId: "process-1",
			domain: "BUDGET",
		},
		{ id: "metric-2", code: "DRAFT_ONLY", name: "草稿", status: "DRAFT", version: "v1" },
	];
	assert.deepEqual(bindIndicatorDependencies({ metricType: "DERIVED" }, ["metric-2", "metric-1"], catalog), {
		metricType: "DERIVED",
		businessCategoryId: "category-1",
		dataDomainId: "domain-1",
		businessProcessId: "process-1",
		domain: "BUDGET",
		dependencyCodes: ["BUDGET_TOTAL"],
		sourceRefs: [{ sourceType: "INDICATOR_VERSION", sourceId: "metric-1", sourceVersion: "v2" }],
	});
});

test("derived implementation binding keeps semantic upstream versions and binds one result field", () => {
	const values = {
		metricType: "DERIVED",
		measureField: "task_total",
		sourceRefs: [{ sourceType: "INDICATOR_VERSION" as const, sourceId: "metric-1", sourceVersion: "v2" }],
	};
	assert.deepEqual(bindMetricImplementationModel(values, publishedFact), {
		...values,
		implementationRef: { modelSpecId: "model-1", modelRevision: 3, fieldName: "task_total" },
		targetModelName: "项目事实表",
		sourceTable: "项目事实表",
		sourceLayer: "DWD",
		targetLayer: "DWD",
	});
});

test("cross-domain composite dependencies keep their shared category without inventing one domain", () => {
	const catalog: IndicatorDefinition[] = [
		{
			id: "metric-1",
			code: "TASK_TOTAL",
			status: "PUBLISHED",
			version: "v1",
			businessCategoryId: "category-1",
			dataDomainId: "domain-1",
		},
		{
			id: "metric-2",
			code: "BUDGET_TOTAL",
			status: "PUBLISHED",
			version: "v3",
			businessCategoryId: "category-1",
			dataDomainId: "domain-2",
		},
	];

	const bound = bindIndicatorDependencies({ metricType: "COMPOSITE" }, ["metric-1", "metric-2"], catalog);

	assert.equal(bound.businessCategoryId, "category-1");
	assert.equal(bound.dataDomainId, null);
	assert.equal(bound.businessProcessId, null);
});
