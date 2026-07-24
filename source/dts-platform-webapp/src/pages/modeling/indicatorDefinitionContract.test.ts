import assert from "node:assert/strict";
import test from "node:test";
import {
	buildIndicatorUpsertPayload,
	nextIndicatorVersion,
	parseIndicatorDependencyCodes,
	planIndicatorDependencyReferences,
	validateIndicatorDefinition,
} from "./indicatorDefinitionContract";

test("dependency codes accept JSON arrays and reject malformed values safely", () => {
	assert.deepEqual(parseIndicatorDependencyCodes('["GMV","ORDER_COUNT","GMV"]'), ["GMV", "ORDER_COUNT"]);
	assert.deepEqual(parseIndicatorDependencyCodes("GMV, ORDER_COUNT"), ["GMV", "ORDER_COUNT"]);
	assert.deepEqual(parseIndicatorDependencyCodes("{broken"), []);
});

test("published indicators move to the next draft version without clearing untouched fields", () => {
	const original = {
		id: "metric-1",
		code: "GMV",
		name: "成交金额",
		category: "交易",
		definition: "支付成功订单金额",
		expressionSql: "sum(amount)",
		datasetId: "dataset-1",
		owner: "owner-a",
		ownerDept: "dept-a",
		dataLevel: "DATA_INTERNAL",
		status: "PUBLISHED",
		version: "v3",
		versionNotes: "第三版",
		tags: '["交易","核心"]',
		aggregationType: "SUM",
		measureField: "amount",
		numeratorExpression: null,
		denominatorExpression: null,
		staticFilter: "pay_status = 'SUCCESS'",
		dynamicFilterConfig: '{"tenant":"tenant_id"}',
		isDerived: false,
		dependencyIndicators: null,
		windowFunction: "NONE",
		dimensionFields: '["shop_id"]',
		dateColumn: "paid_at",
		timeGrain: "DAY",
		granularity: "SHOP",
		sourceTable: "dwd_order",
		joinConfig: '{"type":"NONE"}',
		sourceLayer: "DWD",
		targetLayer: "ADS",
		targetModelName: "ads_gmv",
		unit: "元",
		precisionScale: 2,
		thresholdMin: 0,
		thresholdMax: 99999999,
		direction: "POSITIVE",
		businessOwner: "张三",
		dataPrivacy: "INTERNAL",
		llmGenerated: false,
		llmConfidence: 0.98,
		llmSourceRef: "manual",
		humanVerified: true,
		domain: "交易域",
		icon: "money",
		displayOrder: 10,
		templateId: "template-1",
		createdBy: "system",
		createdDate: "2026-07-01T00:00:00Z",
		lastModifiedBy: "system",
		lastModifiedDate: "2026-07-01T00:00:00Z",
		lastValidationStatus: "SUCCESS",
	};
	const payload = buildIndicatorUpsertPayload(
		original,
		{
			name: "成交金额（含税）",
			definition: "含税成交金额",
			isDerived: false,
			dependencyCodes: [],
		},
		["v1", "v2", "v3"],
	);

	const { id: _id, createdBy: _createdBy, createdDate: _createdDate, lastModifiedBy: _lastModifiedBy, lastModifiedDate: _lastModifiedDate, lastValidationStatus: _lastValidationStatus, ...upsertFields } = original;
	assert.deepEqual(payload, {
		...upsertFields,
		name: "成交金额（含税）",
		definition: "含税成交金额",
		status: "DRAFT",
		version: "v4",
		dependencyIndicators: null,
	});
});

test("draft updates keep their version and derived dependencies use stable codes", () => {
	const payload = buildIndicatorUpsertPayload(
		{
			id: "metric-2",
			code: "AVG_ORDER",
			name: "客单价",
			status: "DRAFT",
			version: "v2",
			isDerived: true,
		},
		{
			isDerived: true,
			dependencyCodes: ["GMV", "ORDER_COUNT"],
			expressionSql: "{{metric:GMV}} / nullif({{metric:ORDER_COUNT}}, 0)",
		},
		["v1", "v2"],
	);

	assert.equal(payload.status, "DRAFT");
	assert.equal(payload.version, "v2");
	assert.equal(payload.dependencyIndicators, '["GMV","ORDER_COUNT"]');
});

test("version calculation ignores invalid labels", () => {
	assert.equal(nextIndicatorVersion([]), "v1");
	assert.equal(nextIndicatorVersion(["CURRENT", "v2", "V7", "draft"]), "v8");
});

test("dependency reference plan preserves non-indicator references and converges indicator refs", () => {
	const plan = planIndicatorDependencyReferences(
		[
			{ id: "ref-model", refType: "MODEL_SPEC_FIELD", refTarget: "model@1#amount" },
			{ id: "ref-old", refType: "INDICATOR", refTarget: "old-id" },
			{ id: "ref-kept", refType: "INDICATOR", refTarget: "gmv-id" },
		],
		[
			{ id: "gmv-id", code: "GMV", name: "成交金额" },
			{ id: "count-id", code: "ORDER_COUNT", name: "订单数" },
		],
	);

	assert.deepEqual(plan.deleteReferenceIds, ["ref-old"]);
	assert.deepEqual(plan.createReferences, [
		{ refType: "INDICATOR", refTarget: "count-id", refName: "订单数", notes: "ORDER_COUNT" },
	]);
});

test("definition preflight distinguishes atomic and derived requirements", () => {
	assert.deepEqual(
		validateIndicatorDefinition({
			code: "GMV",
			name: "成交金额",
			isDerived: false,
			aggregationType: "SUM",
			measureField: "amount",
		}),
		[],
	);
	assert.deepEqual(
		validateIndicatorDefinition({
			code: "AVG_ORDER",
			name: "客单价",
			isDerived: true,
			dependencyCodes: ["AVG_ORDER"],
			expressionSql: "",
		}),
		["派生指标至少选择一个非自身依赖指标", "派生指标必须填写受控派生表达式或计算 SQL"],
	);
});
