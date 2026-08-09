import assert from "node:assert/strict";
import test from "node:test";
import {
	buildExistingIndicatorMutationPayload,
	buildIndicatorFormChanges,
	buildIndicatorUpsertPayload,
	nextIndicatorVersion,
	normalizeIndicatorEditValues,
	parseIndicatorDependencyCodes,
	validateIndicatorDefinition,
} from "./indicatorDefinitionContract.ts";

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

	const {
		id: _id,
		createdBy: _createdBy,
		createdDate: _createdDate,
		lastModifiedBy: _lastModifiedBy,
		lastModifiedDate: _lastModifiedDate,
		lastValidationStatus: _lastValidationStatus,
		...upsertFields
	} = original;
	assert.deepEqual(payload, {
		...upsertFields,
		businessCategoryId: null,
		dataDomainId: null,
		businessProcessId: null,
		metricType: null,
		metricGroupCode: null,
		sourceRefs: null,
		name: "成交金额（含税）",
		definition: "含税成交金额",
		status: "DRAFT",
		version: "v4",
		dependencyIndicators: null,
	});
});

test("stable business context and version-pinned source references survive upsert projection", () => {
	const sourceRefs = [{ sourceType: "SEMANTIC_MODEL_REVISION" as const, sourceId: "model-1", sourceVersion: "r7" }];
	const payload = buildIndicatorUpsertPayload(
		{
			code: "BUDGET_AMOUNT",
			name: "预算金额",
			status: "DRAFT",
			version: "v1",
			businessCategoryId: "category-1",
			dataDomainId: "domain-1",
			businessProcessId: "process-row-1",
			metricType: "ATOMIC",
			metricGroupCode: "finance.budget",
			sourceRefs,
		},
		{},
		["v1"],
	);

	assert.equal(payload.businessCategoryId, "category-1");
	assert.equal(payload.dataDomainId, "domain-1");
	assert.equal(payload.businessProcessId, "process-row-1");
	assert.equal(payload.metricType, "ATOMIC");
	assert.equal(payload.metricGroupCode, "finance.budget");
	assert.deepEqual(payload.sourceRefs, sourceRefs);
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

test("collapsed scope fields stay untouched while an explicit dimension clear is serialized as null", () => {
	const allValues = {
		name: "成交金额（含税）",
		isDerived: false,
		dimensionCodes: ["shop_id"],
	};
	const untouchedChanges = buildIndicatorFormChanges(allValues, new Set(["name"]), [
		{ name: "shop_id", comment: "门店" },
	]);
	assert.equal(Object.hasOwn(untouchedChanges, "dimensionFields"), false);
	assert.equal(
		buildIndicatorUpsertPayload(
			{
				id: "metric-1",
				code: "GMV",
				name: "成交金额",
				status: "DRAFT",
				version: "v1",
				dimensionFields: '[{"field":"shop_id","displayName":"门店"}]',
			},
			untouchedChanges,
			["v1"],
		).dimensionFields,
		'[{"field":"shop_id","displayName":"门店"}]',
	);

	const clearedChanges = buildIndicatorFormChanges({ ...allValues, dimensionCodes: [] }, new Set(["dimensionCodes"]), [
		{ name: "shop_id", comment: "门店" },
	]);
	assert.equal(clearedChanges.dimensionFields, null);
});

test("explicit edited field names include dependency and dimension values without leaking programmatic fields", () => {
	const changes = buildIndicatorFormChanges(
		{
			name: "程序回灌名称",
			category: "程序回灌分类",
			dependencyCodes: ["GMV", "ORDER_COUNT"],
			dimensionCodes: ["shop_id"],
		},
		new Set(["dependencyCodes", "dimensionCodes"]),
		[{ name: "shop_id", comment: "门店" }],
	);

	assert.equal(Object.hasOwn(changes, "name"), false);
	assert.equal(Object.hasOwn(changes, "category"), false);
	assert.equal(changes.dependencyIndicators, undefined);
	assert.deepEqual(changes.dependencyCodes, ["GMV", "ORDER_COUNT"]);
	assert.equal(changes.dimensionFields, '[{"field":"shop_id","displayName":"门店"}]');
});

test("existing mutations merge only touched values onto latest and carry the baseline CAS token", () => {
	const baseline = {
		id: "metric-1",
		code: "GMV",
		name: "成交金额",
		category: "交易",
		owner: "owner-a",
		status: "DRAFT",
		version: "v3",
		lastModifiedDate: "2026-07-25T10:20:30Z",
	};
	const latest = {
		...baseline,
		owner: "owner-from-latest",
	};
	const payload = buildExistingIndicatorMutationPayload(baseline, latest, { name: "成交金额（含税）" }, [
		"v1",
		"v2",
		"v3",
	]);

	assert.equal(payload.name, "成交金额（含税）");
	assert.equal(payload.owner, "owner-from-latest");
	assert.equal(payload.category, "交易");
	assert.equal(payload.expectedLastModifiedDate, "2026-07-25T10:20:30Z");
});

test("existing mutations reject baseline drift before a stale payload can be written", () => {
	assert.throws(
		() =>
			buildExistingIndicatorMutationPayload(
				{
					id: "metric-1",
					code: "GMV",
					status: "DRAFT",
					version: "v3",
					lastModifiedDate: "2026-07-25T10:20:30Z",
				},
				{
					id: "metric-1",
					code: "GMV",
					status: "DRAFT",
					version: "v3",
					lastModifiedDate: "2026-07-25T10:21:30Z",
				},
				{ name: "过期修改" },
				["v1", "v2", "v3"],
			),
		/已被其他用户更新/,
	);
});

test("business category remains independent from derived mode and aggregation type", () => {
	assert.deepEqual(
		normalizeIndicatorEditValues({
			code: "AVG_ORDER",
			name: "客单价",
			isDerived: true,
			category: "客户增长",
			aggregationType: "SUM",
			datasetId: "dataset-1",
			measureField: "amount",
			dependencyCodes: ["GMV", "ORDER_COUNT"],
		}),
		{
			code: "AVG_ORDER",
			name: "客单价",
			isDerived: true,
			category: "客户增长",
			aggregationType: "DERIVED",
			datasetId: null,
			measureField: null,
			numeratorExpression: null,
			denominatorExpression: null,
			dependencyCodes: ["GMV", "ORDER_COUNT"],
		},
	);
	assert.deepEqual(
		normalizeIndicatorEditValues({
			code: "ORDER_COUNT",
			name: "订单数",
			isDerived: false,
			category: "复合经营",
			aggregationType: "DERIVED",
			measureField: "order_id",
			dependencyCodes: ["GMV"],
		}),
		{
			code: "ORDER_COUNT",
			name: "订单数",
			isDerived: false,
			category: "复合经营",
			aggregationType: null,
			measureField: "order_id",
			dependencyCodes: [],
		},
	);
	assert.deepEqual(
		validateIndicatorDefinition({
			code: "ORDER_COUNT",
			name: "订单数",
			isDerived: false,
			aggregationType: "DERIVED",
			measureField: "order_id",
		}),
		["原子指标聚合方式不能为 DERIVED"],
	);
});
