import assert from "node:assert/strict";
import test from "node:test";
import {
	buildMetricWorkbenchLocation,
	publishIndicatorWithPreview,
	resolveIndicatorDetailRequest,
	rollbackIndicatorAndPublish,
	runIndicatorPreflight,
	shouldApplyIndicatorDetailResponse,
} from "./indicatorDefinitionWorkflow.ts";

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

test("deep-link detail loading uses the requested id without requiring catalog membership", () => {
	assert.equal(resolveIndicatorDetailRequest("metric-after-first-500", null), "metric-after-first-500");
	assert.equal(resolveIndicatorDetailRequest("metric-after-first-500", "metric-after-first-500"), null);
});

test("detail responses are ignored when a newer request or a form edit wins the race", () => {
	assert.equal(
		shouldApplyIndicatorDetailResponse({
			requestSequence: 3,
			activeRequestSequence: 3,
			formRevisionAtRequest: 7,
			currentFormRevision: 7,
		}),
		true,
	);
	assert.equal(
		shouldApplyIndicatorDetailResponse({
			requestSequence: 2,
			activeRequestSequence: 3,
			formRevisionAtRequest: 7,
			currentFormRevision: 7,
		}),
		false,
	);
	assert.equal(
		shouldApplyIndicatorDetailResponse({
			requestSequence: 3,
			activeRequestSequence: 3,
			formRevisionAtRequest: 7,
			currentFormRevision: 8,
		}),
		false,
	);
});

test("rollback is one rollback-and-publish action and returns the still-published owner", async () => {
	const result = await rollbackIndicatorAndPublish("metric-1", "v2", "回滚口径", {
		rollback: async (id, version, data) => {
			assert.equal(id, "metric-1");
			assert.equal(version, "v2");
			assert.deepEqual(data, { reason: "回滚口径", publishAfterRollback: true });
			return {
				published: true,
				indicator: { id, version: "v4", status: "PUBLISHED" },
			};
		},
	});

	assert.equal(result.status, "PUBLISHED");
	assert.equal(result.version, "v4");
});
