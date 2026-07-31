import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
	buildBusinessCategoryManagementRoute,
	buildWarehouseCategoryOptions,
	buildWarehousePlanRoute,
	canArchiveWarehousePlan,
	canEditWarehousePlanHeader,
	filterWarehousePlans,
	mergeWarehousePlanHeadersMonotonic,
	normalizeCanonicalWarehousePlanId,
	replaceWarehousePlanHeader,
	resolveWarehousePlanConflictVersion,
	resolveWarehousePlanPageContext,
	resolveWarehousePlanReturnTo,
	stageStatusLabel,
	WAREHOUSE_STAGE_ORDER,
	warehouseBlockerMessage,
	warehousePlanIssueMessage,
	warehousePlanLifecycleLabel,
	warehousePlanMutationErrorMessage,
	warehouseStageActionLabel,
	warehouseStageLabel,
	withWarehousePlanContext,
} from "./warehousePlanViewModel.ts";

const apiSource = readFileSync(new URL("../../../api/warehousePlanApi.ts", import.meta.url), "utf8");

test("warehouse planning exposes one stable nine-stage vocabulary", () => {
	assert.deepEqual(WAREHOUSE_STAGE_ORDER, [
		"DATA_CONNECTION",
		"SOURCE_INVENTORY",
		"WAREHOUSE_PLANNING",
		"DATA_STANDARD",
		"MODEL_DESIGN",
		"BUILD_QUALITY_RELEASE",
		"DATA_ASSET",
		"METRIC_SYSTEM",
		"DATA_SERVICE_OPERATIONS",
	]);
	assert.equal(warehouseStageLabel("SOURCE_INVENTORY"), "来源盘点");
	assert.equal(warehouseStageLabel("BUILD_QUALITY_RELEASE"), "构建、质量与发布");
	assert.equal(warehouseStageActionLabel("DATA_CONNECTION"), "检查数据连接");
	assert.equal(warehouseStageActionLabel("DATA_SERVICE_OPERATIONS"), "查看服务与运行");
	assert.equal(warehouseBlockerMessage("CATEGORY_SCOPE_INCOMPLETE", "fallback"), "业务分类尚未确认");
	assert.equal(
		warehouseBlockerMessage("DATA_CONNECTION_NOT_STARTED", "No completion evidence is available for this stage"),
		"本阶段尚未产生可核验的完成证据",
	);
	assert.equal(
		warehouseBlockerMessage("METRIC_SYSTEM_UNKNOWN", "This stage has not produced current completion evidence"),
		"本阶段的完成证据暂时无法核验",
	);
});

test("business category management return route is plan-bound and safely reversible", () => {
	const planId = "10000000-0000-0000-0000-000000000001";
	const target = buildBusinessCategoryManagementRoute(planId);
	const url = new URL(target, "http://dts.local");
	assert.equal(url.pathname, "/data-modeling/planning/business-categories");
	assert.equal(url.searchParams.get("planId"), planId);
	assert.equal(
		url.searchParams.get("returnTo"),
		`/data-modeling/planning/spaces?tab=categories&planId=${planId}&view=baseline`,
	);
	assert.equal(
		resolveWarehousePlanReturnTo(url.searchParams.get("returnTo"), planId),
		url.searchParams.get("returnTo"),
	);
});

test("warehouse plan returnTo rejects external, protocol-relative and cross-plan targets", () => {
	const planId = "10000000-0000-0000-0000-000000000001";
	for (const unsafe of [
		"https://evil.example/data-modeling/planning/spaces?tab=categories&planId=10000000-0000-0000-0000-000000000001&view=baseline",
		"//evil.example/data-modeling/planning/spaces?tab=categories&planId=10000000-0000-0000-0000-000000000001&view=baseline",
		"/data-modeling/planning/spaces?tab=categories&planId=20000000-0000-0000-0000-000000000002&view=baseline",
		`/data-modeling/dimensions/workbench?planId=${planId}`,
		`/data-modeling/planning/spaces?tab=categories&planId=20000000-0000-0000-0000-000000000002&view=baseline`,
	]) {
		assert.equal(resolveWarehousePlanReturnTo(unsafe, planId), null, unsafe);
	}
});

test("warehouse plan page context rejects malformed, duplicate and mismatched plan context", () => {
	const planId = "10000000-0000-0000-0000-000000000001";
	const returnTo = `/data-modeling/planning/spaces?tab=categories&planId=${planId}&view=baseline`;
	assert.deepEqual(resolveWarehousePlanPageContext(new URLSearchParams({ planId, returnTo })), {
		planId,
		returnTo,
	});
	assert.deepEqual(resolveWarehousePlanPageContext(new URLSearchParams({ planId })), {
		planId,
		returnTo: null,
	});

	assert.equal(
		resolveWarehousePlanPageContext(
			new URLSearchParams({
				planId,
				returnTo:
					"/data-modeling/planning/spaces?tab=categories&planId=20000000-0000-0000-0000-000000000002&view=baseline",
			}),
		),
		null,
	);
	assert.equal(resolveWarehousePlanPageContext(new URLSearchParams({ planId: "../../ops/instances?ignored=" })), null);
	const duplicate = new URLSearchParams({ planId });
	duplicate.append("planId", "20000000-0000-0000-0000-000000000002");
	assert.equal(resolveWarehousePlanPageContext(duplicate), null);
	assert.equal(normalizeCanonicalWarehousePlanId(` ${planId} `), planId);
	assert.equal(normalizeCanonicalWarehousePlanId("plan-1"), "");
});

test("warehouse plan route encodes unsafe plan identifiers as query context", () => {
	const unsafePlanId = "../../ops/instances?ignored=";
	const route = buildWarehousePlanRoute(unsafePlanId, "baseline", { tab: "categories" });
	const parsed = new URL(route, "http://dts.local");
	assert.notEqual(parsed.pathname, "/ops/instances");
	assert.equal(parsed.pathname, "/data-modeling/planning/spaces");
	assert.equal(parsed.searchParams.get("planId"), unsafePlanId);
});

test("warehouse plan conflicts read the server currentVersion without guessing", () => {
	assert.equal(
		resolveWarehousePlanConflictVersion({
			response: {
				status: 409,
				data: { code: "WAREHOUSE_PLAN_EDIT_UNIT_VERSION_CONFLICT", data: { currentVersion: 7 } },
			},
		}),
		7,
	);
	assert.equal(
		resolveWarehousePlanConflictVersion({
			response: {
				status: 409,
				data: { code: "WAREHOUSE_PLAN_LIFECYCLE_CONFLICT", data: { currentVersion: 7 } },
			},
		}),
		null,
	);
	assert.equal(
		resolveWarehousePlanConflictVersion({ response: { status: 409, data: { data: { currentVersion: 7 } } } }),
		null,
	);
	assert.equal(resolveWarehousePlanConflictVersion({ response: { status: 409, data: { currentVersion: 8 } } }), null);
	assert.equal(
		resolveWarehousePlanConflictVersion({ response: { status: 400, data: { data: { currentVersion: 9 } } } }),
		null,
	);
	assert.equal(resolveWarehousePlanConflictVersion(new Error("conflict")), null);
});

test("warehouse plan issue copy stays customer-readable when backend details are technical", () => {
	assert.equal(
		warehousePlanIssueMessage("CATEGORY_DOMAIN_FORBIDDEN", "The business category is no longer accessible"),
		"当前账号已无法访问该业务分类，请替换或申请权限",
	);
	assert.equal(warehousePlanIssueMessage("DEFAULT_TIME_ZONE_INVALID", "invalid ZoneId"), "请输入有效的 IANA 时区名称");
	assert.equal(warehousePlanIssueMessage("UNKNOWN", "服务端给出的中文说明"), "服务端给出的中文说明");
	assert.equal(warehousePlanIssueMessage("UNKNOWN", "internal validation failed"), "请检查当前规划设置");
});

test("category options override catalog labels for forbidden and archived bindings", () => {
	const options = buildWarehouseCategoryOptions(
		[
			{ id: "forbidden", name: "不应泄露的分类" },
			{ id: "archived", name: "历史分类" },
		],
		[
			{
				domainId: "forbidden",
				confirmationStatus: "CONFIRMED",
				resolutionStatus: "FORBIDDEN",
				name: "不应泄露的分类",
				code: "SECRET",
			},
			{
				domainId: "archived",
				confirmationStatus: "CONFIRMED",
				resolutionStatus: "ARCHIVED",
				name: "历史分类",
			},
		],
	);
	assert.deepEqual(options, [
		{ value: "forbidden", label: "不可访问分类" },
		{ value: "archived", label: "历史分类（已归档）" },
	]);
});

test("mutation errors map stable response codes and never expose technical details", () => {
	assert.equal(
		warehousePlanMutationErrorMessage(
			{ response: { data: { code: "WAREHOUSE_PLAN_CATEGORY_FORBIDDEN", message: "internal ACL detail" } } },
			"业务分类保存失败",
		),
		"当前账号不能使用所选业务分类，请替换或申请权限",
	);
	assert.equal(
		warehousePlanMutationErrorMessage(
			{ response: { data: { code: "WAREHOUSE_PLAN_LIFECYCLE_CONFLICT", message: "internal state" } } },
			"保存失败",
		),
		"当前计划已不可编辑，请重新加载计划状态",
	);
	assert.equal(
		warehousePlanMutationErrorMessage(
			{ response: { data: { code: "UNKNOWN", message: "database constraint detail" } } },
			"数仓分层策略保存失败",
		),
		"数仓分层策略保存失败",
	);
	assert.equal(
		warehousePlanMutationErrorMessage(
			{ response: { data: { code: "WAREHOUSE_PLAN_HEADER_INVALID", message: "internal validation detail" } } },
			"规划保存失败",
		),
		"请检查规划名称和负责人",
	);
	assert.equal(
		warehousePlanMutationErrorMessage(
			{ response: { data: { code: "WAREHOUSE_PLAN_OWNER_FORBIDDEN", message: "internal ACL detail" } } },
			"规划保存失败",
		),
		"当前账号不能转派给所选负责人",
	);
});

test("unknown and stale evidence remain visibly non-complete", () => {
	assert.equal(stageStatusLabel("COMPLETE", "CURRENT"), "已完成");
	assert.equal(stageStatusLabel("COMPLETE", "STALE"), "证据已过期");
	assert.equal(stageStatusLabel("UNKNOWN", "UNAVAILABLE"), "证据未知");
	assert.notEqual(stageStatusLabel("COMPLETE", "STALE"), "已完成");
});

test("all new modeling routes carry canonical planId", () => {
	assert.equal(
		buildWarehousePlanRoute("10000000-0000-0000-0000-000000000001", "baseline", { tab: "sources" }),
		"/data-modeling/planning/spaces?tab=sources&planId=10000000-0000-0000-0000-000000000001&view=baseline",
	);
	assert.equal(
		withWarehousePlanContext("/foundation/data-sources", "10000000-0000-0000-0000-000000000001"),
		"/foundation/data-sources?planId=10000000-0000-0000-0000-000000000001",
	);
});

test("typed API uses only the canonical warehouse plan resource", () => {
	assert.match(apiSource, /const WAREHOUSE_PLAN_RESOURCE = "\/modeling\/warehouse-plans"/);
	assert.match(apiSource, /"BUSINESS_FIRST" \| "ASSET_FIRST"/);
	assert.doesNotMatch(apiSource, /DATA_FIRST/);
	assert.match(apiSource, /\/stage-projection/);
	assert.match(apiSource, /\/baseline/);
	assert.doesNotMatch(apiSource, /sessionStorage|localStorage|\/modeling\/plans/);
});

const warehousePlans = [
	{
		id: "plan-active",
		tenantId: "tenant-1",
		code: "PLAN_SALES",
		name: "经营分析数仓",
		objective: "统一销售分析",
		scope: "华东区域",
		ownerId: "alice",
		ownerDepartmentId: "sales",
		onboardingMode: "BUSINESS_FIRST" as const,
		lifecycleStatus: "DESIGNING" as const,
		version: 2,
	},
	{
		id: "plan-archived",
		tenantId: "tenant-1",
		code: "PLAN_HISTORY",
		name: "历史供应链规划",
		objective: "留档",
		scope: null,
		ownerId: "bob",
		ownerDepartmentId: null,
		onboardingMode: "ASSET_FIRST" as const,
		lifecycleStatus: "ARCHIVED" as const,
		version: 5,
	},
];

test("warehouse plan ledger defaults to active plans and filters customer-visible fields", () => {
	assert.deepEqual(
		filterWarehousePlans(warehousePlans, { lifecycleStatus: "ACTIVE" }).map((plan) => plan.id),
		["plan-active"],
	);
	assert.deepEqual(
		filterWarehousePlans(warehousePlans, { lifecycleStatus: "ALL", keyword: "plan_history" }).map((plan) => plan.id),
		["plan-archived"],
	);
	assert.deepEqual(
		filterWarehousePlans(warehousePlans, { lifecycleStatus: "ARCHIVED", ownerId: "bob" }).map((plan) => plan.id),
		["plan-archived"],
	);
});

test("late list snapshots never replace a locally observed newer plan header", () => {
	const current = [{ ...warehousePlans[0], name: "已保存的新名称", version: 4 }];
	const staleList = [{ ...warehousePlans[0], name: "旧名称", version: 3 }, warehousePlans[1]];
	assert.deepEqual(mergeWarehousePlanHeadersMonotonic(current, staleList), [current[0], warehousePlans[1]]);
	assert.deepEqual(mergeWarehousePlanHeadersMonotonic(current, [{ ...warehousePlans[0], version: 5 }]), [
		{ ...warehousePlans[0], version: 5 },
	]);
});

test("warehouse plan lifecycle and write actions are derived from permission and server status", () => {
	assert.equal(warehousePlanLifecycleLabel("READY_TO_PUBLISH"), "待发布");
	assert.equal(warehousePlanLifecycleLabel("ARCHIVED"), "已归档");
	assert.equal(canEditWarehousePlanHeader(true, "DRAFT"), true);
	assert.equal(canEditWarehousePlanHeader(true, "PUBLISHED"), false);
	assert.equal(canEditWarehousePlanHeader(true, "ARCHIVED"), false);
	assert.equal(canEditWarehousePlanHeader(false, "DRAFT"), false);
	assert.equal(canArchiveWarehousePlan(true, "PUBLISHED"), true);
	assert.equal(canArchiveWarehousePlan(true, "ARCHIVED"), false);
	assert.equal(canArchiveWarehousePlan(false, "DESIGNING"), false);
});

test("archiving replaces the canonical header so archived filters can reveal it", () => {
	const archived = { ...warehousePlans[0], lifecycleStatus: "ARCHIVED" as const, version: 3 };
	const replaced = replaceWarehousePlanHeader(warehousePlans, archived);
	assert.equal(replaced.length, 2);
	assert.equal(replaced[0], archived);
	assert.deepEqual(
		filterWarehousePlans(replaced, { lifecycleStatus: "ARCHIVED" }).map((plan) => plan.id),
		["plan-active", "plan-archived"],
	);
});
