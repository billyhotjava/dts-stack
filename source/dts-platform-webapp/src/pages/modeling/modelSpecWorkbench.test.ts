import assert from "node:assert/strict";
import test from "node:test";
import type { CanonicalModelSpecView } from "./modelSpecV2Contract.ts";
import {
	adoptCurrentDimensionRevisions,
	adoptCurrentUpstreamRevisions,
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
const NEW_UPSTREAM_ID = "30000000-0000-0000-0000-000000000002";
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

test("FACT logical update keeps grain, source, optional time semantics and optional activity on ModelSpec", () => {
	const command = buildModelSpecUpdateCommand(
		baseDraft({
			factShape: "TRANSACTION",
			timeSemanticsType: "EVENT_TIME",
			timeFieldsText: "event_time",
			businessActivityRef: "客户事件处理",
			dimensionRefIds: [DIMENSION_ID],
		}),
		[{ id: DIMENSION_ID, revision: 4, modelType: "DIMENSION" }],
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

test("FACT logical update may defer input mapping or pin an upstream model revision", () => {
	const withoutInput = buildModelSpecUpdateCommand(
		baseDraft({ sources: [], upstreamIds: [] }),
		[],
	);
	assert.deepEqual(withoutInput.sourceRefs, []);
	assert.deepEqual(withoutInput.dependsOn, []);

	const withUpstream = buildModelSpecUpdateCommand(
		baseDraft({ sources: [], upstreamIds: [UPSTREAM_ID] }),
		[{ id: UPSTREAM_ID, revision: 7, modelType: "FACT" }],
	);
	assert.deepEqual(withUpstream.sourceRefs, []);
	assert.deepEqual(withUpstream.dependsOn, [{ modelSpecId: UPSTREAM_ID, revision: 7 }]);
});

test("DIMENSION logical update creates key fields without requiring business activity", () => {
	const command = buildModelSpecUpdateCommand(
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
			dimensionCode: "DIM_ORGANIZATION",
			dimensionHierarchies: [],
			dimensionScdType: "TYPE1",
			dimensionReuseScope: "PLAN",
			businessActivityRef: "must-not-leak",
		}),
		[],
	);

	assert.equal(command.modelType, "DIMENSION");
	assert.deepEqual(command.fields, [
		{
			name: "organization_id",
			displayName: "organization_id",
			dataType: "string",
			sourceFieldRef: undefined,
			securityLevel: undefined,
			dimensionAttributeCode: undefined,
			redundant: false,
			redundancySourceRef: undefined,
			nullable: false,
			role: "KEY",
		},
	]);
	assert.deepEqual(command.generationStrategy, { type: "REFERENCE", reference: "组织主数据" });
	assert.deepEqual(command.dimensionProfile, {
		dimensionCode: "DIM_ORGANIZATION",
		hierarchies: [],
		scdPolicy: { type: "TYPE1" },
		reuseScope: "PLAN",
	});
	assert.equal(command.businessActivityRef, undefined);
});

test("DIMENSION logical update normalizes optional values omitted by conditional form fields", () => {
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

	const command = buildModelSpecUpdateCommand(sparseFormValues, []);

	assert.deepEqual(command.fields, [
		{
			name: "organization_id",
			displayName: "organization_id",
			dataType: "string",
			sourceFieldRef: undefined,
			securityLevel: undefined,
			dimensionAttributeCode: undefined,
			redundant: false,
			redundancySourceRef: undefined,
			nullable: false,
			role: "KEY",
		},
	]);
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

test("fixed target-layer validation has a business-readable issue message", () => {
	assert.equal(
		modelSpecIssueMessage("MODEL_SPEC_TYPE_LAYER_MISMATCH"),
		"模型类别与目标分层不一致，请按系统固定分层保存",
	);
	assert.equal(modelSpecIssueMessage("MODEL_SPEC_TYPE_REQUIRED"), "请选择模型类别（四类表）");
	assert.equal(
		modelSpecIssueMessage("MODEL_SPEC_UPSTREAM_LAYER_NOT_ALLOWED"),
		"所选上游模型或物理来源分层不符合当前模型类别的依赖规则",
	);
	assert.equal(
		modelSpecIssueMessage("MODEL_SPEC_INPUT_KIND_NOT_ALLOWED"),
		"当前模型类别不支持此类输入，请移除不适用的来源、依赖或类型专属字段",
	);
});

test("logical update parsing trims and deduplicates grain and time field names across supported separators", () => {
	const command = buildModelSpecUpdateCommand(
		baseDraft({
			grainKeysText: " customer_id， event_id\ncustomer_id ",
			fields: [{ name: "customer_id", dataType: "string", nullable: false, role: "KEY" }],
			factShape: "TRANSACTION",
			timeSemanticsType: "EVENT_TIME",
			timeFieldsText: " event_time，created_at\nevent_time ",
		}),
		[],
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
	const summary = buildModelSpecUpdateCommand(
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
	);
	const application = buildModelSpecUpdateCommand(
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
		dimensionDefinitionRef: null,
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

test("editing unrelated fields preserves an existing upstream revision pin", () => {
	const update = buildModelSpecUpdateCommand(
		baseDraft({
			sources: [],
			upstreamIds: [UPSTREAM_ID],
			existingUpstreamPins: [{ modelSpecId: UPSTREAM_ID, revision: 3 }],
		}),
		[{ id: UPSTREAM_ID, revision: 4, modelType: "FACT" }],
	);

	assert.deepEqual(update.dependsOn, [{ modelSpecId: UPSTREAM_ID, revision: 3 }]);
});

test("editing dependencies removes deselected pins and pins newly selected models at their current revision", () => {
	const withoutExisting = buildModelSpecUpdateCommand(
		baseDraft({
			sources: [],
			upstreamIds: [],
			existingUpstreamPins: [{ modelSpecId: UPSTREAM_ID, revision: 3 }],
		}),
		[{ id: UPSTREAM_ID, revision: 4, modelType: "FACT" }],
	);
	assert.deepEqual(withoutExisting.dependsOn, []);

	const withNewSelection = buildModelSpecUpdateCommand(
		baseDraft({
			sources: [],
			upstreamIds: [NEW_UPSTREAM_ID],
			existingUpstreamPins: [{ modelSpecId: UPSTREAM_ID, revision: 3 }],
		}),
		[{ id: NEW_UPSTREAM_ID, revision: 5, modelType: "FACT" }],
	);
	assert.deepEqual(withNewSelection.dependsOn, [{ modelSpecId: NEW_UPSTREAM_ID, revision: 5 }]);
});

test("explicitly adopting the current revision upgrades the same selected upstream id", () => {
	const remainingPins = adoptCurrentUpstreamRevisions(
		[
			{ modelSpecId: UPSTREAM_ID, revision: 3 },
			{ modelSpecId: NEW_UPSTREAM_ID, revision: 2 },
		],
		[UPSTREAM_ID],
	);
	assert.deepEqual(remainingPins, [{ modelSpecId: NEW_UPSTREAM_ID, revision: 2 }]);

	const update = buildModelSpecUpdateCommand(
		baseDraft({
			sources: [],
			upstreamIds: [UPSTREAM_ID],
			existingUpstreamPins: remainingPins,
		}),
		[{ id: UPSTREAM_ID, revision: 4, modelType: "FACT" }],
	);
	assert.deepEqual(update.dependsOn, [{ modelSpecId: UPSTREAM_ID, revision: 4 }]);
});

test("dimension revisions stay pinned until the user selectively adopts the current revision", () => {
	const ordinaryUpdate = buildModelSpecUpdateCommand(
		baseDraft({
			dimensionRefIds: [DIMENSION_ID],
			existingDimensionPins: [{ modelSpecId: DIMENSION_ID, revision: 2 }],
		}),
		[{ id: DIMENSION_ID, revision: 4, modelType: "DIMENSION" }],
	);
	assert.deepEqual(ordinaryUpdate.dimensionRefs, [{ modelSpecId: DIMENSION_ID, revision: 2 }]);

	const remainingPins = adoptCurrentDimensionRevisions(
		[
			{ modelSpecId: DIMENSION_ID, revision: 2 },
			{ modelSpecId: NEW_UPSTREAM_ID, revision: 3 },
		],
		[DIMENSION_ID],
	);
	assert.deepEqual(remainingPins, [{ modelSpecId: NEW_UPSTREAM_ID, revision: 3 }]);

	const explicitUpdate = buildModelSpecUpdateCommand(
		baseDraft({
			dimensionRefIds: [DIMENSION_ID],
			existingDimensionPins: remainingPins,
		}),
		[{ id: DIMENSION_ID, revision: 4, modelType: "DIMENSION" }],
	);
	assert.deepEqual(explicitUpdate.dimensionRefs, [{ modelSpecId: DIMENSION_ID, revision: 4 }]);
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
