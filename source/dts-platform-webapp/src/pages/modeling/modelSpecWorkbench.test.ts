import assert from "node:assert/strict";
import test from "node:test";
import type { CanonicalModelSpecView } from "./modelSpecV2Contract.ts";
import {
	buildModelSpecCreateCommand,
	buildModelSpecUpdateCommand,
	createEmptyModelSpecDraft,
	isModelSpecStatusReadonly,
	type ModelSpecDraft,
	modelSpecDraftFromView,
	modelSpecErrorMessage,
	modelSpecIssueMessage,
} from "./modelSpecWorkbench.ts";

const PLAN_ID = "10000000-0000-0000-0000-000000000001";
const DOMAIN_ID = "20000000-0000-0000-0000-000000000001";
const SOURCE_BINDING_ID = "50000000-0000-0000-0000-000000000001";
const UPSTREAM_ID = "30000000-0000-0000-0000-000000000001";
const DIMENSION_ID = "40000000-0000-0000-0000-000000000001";

const baseDraft = (overrides: Partial<ModelSpecDraft> = {}): ModelSpecDraft => ({
	...createEmptyModelSpecDraft("FACT", { planId: PLAN_ID, domainId: DOMAIN_ID }),
	name: "customer_event_detail",
	description: "客户事件明细",
	grainStatement: "一行代表一次客户事件",
	grainKeysText: "customer_id, event_id",
	sources: [
		{
			kind: "TABLE",
			ref: "ods.customer_event",
			layer: "ODS",
			role: "PRIMARY",
			sourceBindingId: SOURCE_BINDING_ID,
			resolvedVersion: "v1",
		},
	],
	...overrides,
});

test("FACT command keeps grain, source, optional time semantics and optional activity on ModelSpec", () => {
	const command = buildModelSpecCreateCommand(
		baseDraft({
			factShape: "TRANSACTION",
			timeSemanticsType: "EVENT_TIME",
			timeFieldsText: "event_time",
			businessActivityRef: "客户事件处理",
			dimensionRefIds: [DIMENSION_ID],
		}),
		[{ id: DIMENSION_ID, revision: 4, modelType: "DIMENSION" }],
		"create-fact-1",
	);

	assert.equal(command.modelType, "FACT");
	assert.deepEqual(command.grain, { statement: "一行代表一次客户事件", keys: ["customer_id", "event_id"] });
	assert.equal(command.sourceRefs?.[0]?.sourceBindingId, SOURCE_BINDING_ID);
	assert.deepEqual(command.timeSemantics, { type: "EVENT_TIME", fields: ["event_time"] });
	assert.deepEqual(command.dimensionRefs, [{ modelSpecId: DIMENSION_ID, revision: 4 }]);
	assert.equal(command.businessActivityRef, "客户事件处理");
	assert.equal("objectId" in command, false);
	assert.equal("processId" in command, false);
});

test("DIMENSION command creates key fields without requiring business activity", () => {
	const command = buildModelSpecCreateCommand(
		baseDraft({
			modelType: "DIMENSION",
			layer: "DWD",
			name: "organization_dimension",
			grainStatement: "一行代表一个组织机构",
			grainKeysText: "organization_id",
			keyDataType: "string",
			sources: [],
			generationStrategyType: "REFERENCE",
			generationStrategyReference: "组织主数据",
			businessActivityRef: "must-not-leak",
		}),
		[],
		"create-dimension-1",
	);

	assert.equal(command.modelType, "DIMENSION");
	assert.deepEqual(command.fields, [{ name: "organization_id", dataType: "string", nullable: false, role: "KEY" }]);
	assert.deepEqual(command.generationStrategy, { type: "REFERENCE", reference: "组织主数据" });
	assert.equal(command.businessActivityRef, undefined);
});

test("DIMENSION command normalizes optional values omitted by conditional form fields", () => {
	const sparseFormValues = {
		...baseDraft({
			modelType: "DIMENSION",
			layer: "DWD",
			name: "organization_dimension",
			grainStatement: "一行代表一个组织机构",
			grainKeysText: "organization_id",
			sources: [],
		}),
		timeFieldsText: undefined,
		fields: undefined,
		metricRefs: undefined,
		standardBindings: undefined,
		existingUpstreamPins: undefined,
		existingDimensionPins: undefined,
	} as unknown as ModelSpecDraft;

	const command = buildModelSpecCreateCommand(sparseFormValues, [], "create-dimension-sparse-form");

	assert.deepEqual(command.fields, [{ name: "organization_id", dataType: "string", nullable: false, role: "KEY" }]);
	assert.deepEqual(command.metricRefs, []);
	assert.deepEqual(command.standardBindings, []);
	assert.equal(command.timeSemantics, undefined);
});

test("DIMENSION editor uses business wording and requires its definition", async () => {
	const workbench = await import("./modelSpecWorkbench.ts");
	assert.equal(typeof workbench.modelSpecEditorCopy, "function");
	assert.deepEqual(workbench.modelSpecEditorCopy?.("DIMENSION"), {
		nameLabel: "维度名称",
		nameRequiredMessage: "请输入维度名称",
		descriptionLabel: "维度定义",
		descriptionRequiredMessage: "请输入维度定义",
	});
	assert.deepEqual(workbench.modelSpecEditorCopy?.("FACT"), {
		nameLabel: "模型名称",
		nameRequiredMessage: "请输入模型名称",
		descriptionLabel: "用途说明",
		descriptionRequiredMessage: undefined,
	});
});

test("command parsing trims and deduplicates grain and time field names across supported separators", () => {
	const command = buildModelSpecCreateCommand(
		baseDraft({
			grainKeysText: " customer_id， event_id\ncustomer_id ",
			fields: [{ name: "customer_id", dataType: "string", nullable: false, role: "KEY" }],
			factShape: "TRANSACTION",
			timeSemanticsType: "EVENT_TIME",
			timeFieldsText: " event_time，created_at\nevent_time ",
		}),
		[],
		"create-fact-shared-parser",
	);

	assert.deepEqual(command.grain?.keys, ["customer_id", "event_id"]);
	assert.deepEqual(command.timeSemantics?.fields, ["event_time", "created_at"]);
	assert.deepEqual(
		command.fields?.map((field) => field.name),
		["customer_id", "event_id"],
	);
});

test("SUMMARY and APPLICATION pin selected upstream revisions and keep their own grain", () => {
	const upstream = [{ id: UPSTREAM_ID, revision: 7, modelType: "FACT" as const }];
	const summary = buildModelSpecCreateCommand(
		baseDraft({
			modelType: "SUMMARY",
			layer: "DWS",
			name: "daily_customer_summary",
			grainStatement: "一行代表一天一个客户",
			grainKeysText: "business_date, customer_id",
			sources: [],
			upstreamIds: [UPSTREAM_ID],
		}),
		upstream,
		"create-summary-1",
	);
	const application = buildModelSpecCreateCommand(
		baseDraft({
			modelType: "APPLICATION",
			layer: "ADS",
			name: "customer_operation_view",
			grainStatement: "一行代表一个客户的当前运营视图",
			grainKeysText: "customer_id",
			sources: [],
			upstreamIds: [UPSTREAM_ID],
			consumptionScenario: "客户运营报表",
		}),
		upstream,
		"create-application-1",
	);

	assert.deepEqual(summary.dependsOn, [{ modelSpecId: UPSTREAM_ID, revision: 7 }]);
	assert.deepEqual(application.dependsOn, [{ modelSpecId: UPSTREAM_ID, revision: 7 }]);
	assert.equal(application.consumptionScenario, "客户运营报表");
	assert.equal(summary.consumptionScenario, undefined);
});

test("editing rebuilds a complete update command while immutable context stays unchanged", () => {
	const view: CanonicalModelSpecView = {
		contractVersion: 2,
		id: "60000000-0000-0000-0000-000000000001",
		planId: PLAN_ID,
		domainId: DOMAIN_ID,
		modelType: "FACT",
		layer: "DWD",
		name: "customer_event_detail",
		description: null,
		implementationMode: "DESIGNER_GENERATED",
		materialization: "table",
		businessActivityRef: null,
		consumptionScenario: null,
		grain: { statement: "一行代表一次客户事件", keys: ["event_id"] },
		factShape: "TRANSACTION",
		timeSemantics: { type: "EVENT_TIME", fields: ["event_time"] },
		fields: [{ name: "event_id", dataType: "string", nullable: false, role: "KEY" }],
		sourceRefs: [
			{
				kind: "TABLE",
				ref: "ods.customer_event",
				layer: "ODS",
				role: "PRIMARY",
				sortOrder: 0,
				sourceBindingId: SOURCE_BINDING_ID,
				resolvedVersion: "v1",
			},
		],
		dependsOn: [],
		dimensionRefs: [],
		metricRefs: [],
		standardBindings: [],
		generationStrategy: null,
		status: "DRAFT",
		revision: 3,
		checksum: "a".repeat(64),
		createdAt: "2026-07-19T00:00:00Z",
		updatedAt: "2026-07-19T00:00:00Z",
		compatibilityMode: "CANONICAL",
		legacyRefs: null,
	};
	const draft = modelSpecDraftFromView(view);
	draft.name = "customer_event_detail_v2";
	const update = buildModelSpecUpdateCommand(draft, []);

	assert.equal(update.planId, PLAN_ID);
	assert.equal(update.domainId, DOMAIN_ID);
	assert.equal(update.modelType, "FACT");
	assert.equal(update.name, "customer_event_detail_v2");
	assert.equal("idempotencyKey" in update, false);
});

test("duplicate field drafts remain visible to contract validation instead of being silently merged", () => {
	const update = buildModelSpecUpdateCommand(
		baseDraft({
			grainKeysText: "event_id",
			fields: [
				{ name: "event_id", dataType: "string", nullable: false, role: "KEY" },
				{ name: "event_id", dataType: "bigint", nullable: false, role: "ATTRIBUTE" },
			],
		}),
		[],
	);

	assert.equal(update.fields?.length, 2);
	assert.deepEqual(
		update.fields?.map((field) => field.name),
		["event_id", "event_id"],
	);
});

test("customer errors describe recovery without exposing backend text", () => {
	assert.equal(
		modelSpecErrorMessage({ response: { status: 403, data: { code: "secret" } } }),
		"当前账号只能浏览模型，请联系计划负责人申请编辑权限",
	);
	assert.equal(
		modelSpecErrorMessage({ response: { status: 409, data: { code: "MODEL_SPEC_REVISION_CONFLICT" } } }),
		"模型已被其他人更新，请选择保留当前输入重试或加载最新版本",
	);
	const statusReadonlyError = { response: { status: 409, data: { code: "MODEL_SPEC_STATUS_READONLY" } } };
	assert.equal(isModelSpecStatusReadonly(statusReadonlyError), true);
	assert.equal(modelSpecErrorMessage(statusReadonlyError), "模型状态已变化，当前输入已保留；请加载最新状态后继续");
	assert.equal(
		modelSpecErrorMessage(new Error("SQLSTATE 23505 technical detail")),
		"操作未完成，当前输入已保留，请稍后重试",
	);
});

test("contract issue codes are translated into concise field guidance", () => {
	assert.equal(modelSpecIssueMessage("MODEL_SPEC_GRAIN_REQUIRED"), "请说明一行数据代表什么，并填写粒度键");
	assert.equal(modelSpecIssueMessage("MODEL_SPEC_UPSTREAM_REQUIRED"), "请至少选择一个上游模型");
	assert.equal(modelSpecIssueMessage("UNKNOWN_TECHNICAL_CODE"), "请检查当前字段");
});
