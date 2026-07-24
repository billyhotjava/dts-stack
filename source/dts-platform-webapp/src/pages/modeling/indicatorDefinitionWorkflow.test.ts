import assert from "node:assert/strict";
import test from "node:test";
import {
	buildMetricWorkbenchLocation,
	publishIndicatorWithPreview,
	runIndicatorPreflight,
	syncIndicatorDependencyReferences,
} from "./indicatorDefinitionWorkflow";

test("derived preflight uses the compiler-backed derivation endpoint and exposes compiled output", async () => {
	let rawValidationCalls = 0;
	const result = await runIndicatorPreflight(
		{
			id: "avg-order",
			code: "AVG_ORDER",
			isDerived: true,
			dependencyIndicators: '["GMV","ORDER_COUNT"]',
			expressionSql: "{{metric:GMV}} / nullif({{metric:ORDER_COUNT}}, 0)",
		},
		{
			validateAtomic: async () => {
				rawValidationCalls += 1;
				throw new Error("派生指标不能执行原始 SQL 校验");
			},
			validateDerivation: async (id) => {
				assert.equal(id, "avg-order");
				return {
					valid: true,
					compiledExpression: '"GMV" / nullif("ORDER_COUNT", 0)',
					issues: [],
					dependencyCodes: ["GMV", "ORDER_COUNT"],
				};
			},
		},
	);

	assert.equal(rawValidationCalls, 0);
	assert.deepEqual(result, {
		valid: true,
		compiledExpression: '"GMV" / nullif("ORDER_COUNT", 0)',
		issues: [],
		dependencyCodes: ["GMV", "ORDER_COUNT"],
	});
});

test("derived preflight returns compiler failures without treating them as executable SQL", async () => {
	const result = await runIndicatorPreflight(
		{
			id: "unsafe",
			code: "UNSAFE",
			isDerived: true,
			dependencyIndicators: '["GMV"]',
			expressionSql: "{{metric:GMV}}; drop table orders",
		},
		{
			validateAtomic: async () => ({ status: "SUCCESS" }),
			validateDerivation: async () => ({
				valid: false,
				compiledExpression: null,
				issues: [{ code: "DERIVATION_COMPILE_FAILED", message: "表达式包含不允许的 SQL 片段" }],
				dependencyCodes: ["GMV"],
			}),
		},
	);

	assert.equal(result.valid, false);
	assert.equal(result.compiledExpression, null);
	assert.equal(result.issues[0]?.code, "DERIVATION_COMPILE_FAILED");
});

test("atomic preflight uses executable-rule validation and normalizes its result", async () => {
	let derivationCalls = 0;
	const result = await runIndicatorPreflight(
		{
			id: "gmv",
			code: "GMV",
			isDerived: false,
			expressionSql: "select sum(amount) from orders",
		},
		{
			validateAtomic: async (id) => {
				assert.equal(id, "gmv");
				return { status: "SUCCESS", message: "校验通过" };
			},
			validateDerivation: async () => {
				derivationCalls += 1;
				throw new Error("原子指标不能调用派生校验");
			},
		},
	);

	assert.equal(derivationCalls, 0);
	assert.deepEqual(result, {
		valid: true,
		compiledExpression: null,
		issues: [],
		dependencyCodes: [],
		message: "校验通过",
	});
});

test("dependency reference synchronization performs the planned delete and create calls", async () => {
	const calls: string[] = [];
	const result = await syncIndicatorDependencyReferences(
		"avg-order",
		[
			{ id: "gmv-id", code: "GMV", name: "成交金额" },
			{ id: "count-id", code: "ORDER_COUNT", name: "订单数" },
		],
		{
			listReferences: async (id) => {
				assert.equal(id, "avg-order");
				return [
					{ id: "keep-model", refType: "MODEL_SPEC_FIELD", refTarget: "model@1#amount" },
					{ id: "delete-old", refType: "INDICATOR", refTarget: "old-id" },
					{ id: "keep-gmv", refType: "INDICATOR", refTarget: "gmv-id" },
				];
			},
			deleteReference: async (id, referenceId) => {
				calls.push(`delete:${id}:${referenceId}`);
			},
			createReference: async (id, reference) => {
				calls.push(`create:${id}:${reference.refTarget}`);
			},
		},
	);

	assert.deepEqual(calls, ["create:avg-order:count-id", "delete:avg-order:delete-old"]);
	assert.deepEqual(result, { deleted: 1, created: 1 });
});

test("publish preview blocks publish calls until the backend gate passes", async () => {
	let publishCalls = 0;
	await assert.rejects(
		publishIndicatorWithPreview("metric-1", {
			getPublishPreview: async () => ({
				readyToPublish: false,
				blockingIssues: [{ code: "IND_VALIDATION_FAILED", message: "校验未通过" }],
			}),
			publish: async () => {
				publishCalls += 1;
			},
		}),
		/校验未通过/,
	);
	assert.equal(publishCalls, 0);

	const result = await publishIndicatorWithPreview("metric-1", {
		getPublishPreview: async () => ({ readyToPublish: true, blockingIssues: [] }),
		publish: async (id) => {
			publishCalls += 1;
			return { id, status: "PUBLISHED" };
		},
	});
	assert.equal(publishCalls, 1);
	assert.deepEqual(result, { id: "metric-1", status: "PUBLISHED" });
});

test("legacy dictionary deep links preserve indicatorId, returnTo, other query values, and hash", () => {
	assert.equal(
		buildMetricWorkbenchLocation("?indicatorId=metric-1&returnTo=%2Fmodeling%2Fsql&tab=owner", "#versions"),
		"/modeling/metric-workbench?indicatorId=metric-1&returnTo=%2Fmodeling%2Fsql&tab=owner#versions",
	);
	assert.equal(buildMetricWorkbenchLocation("", ""), "/modeling/metric-workbench");
});
