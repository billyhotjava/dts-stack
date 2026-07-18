import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
	WAREHOUSE_STAGE_ORDER,
	buildWarehousePlanRoute,
	warehouseBlockerMessage,
	warehouseStageActionLabel,
	withWarehousePlanContext,
	stageStatusLabel,
	warehouseStageLabel,
} from "./warehousePlanViewModel.ts";

const apiSource = readFileSync(new URL("../../api/warehousePlanApi.ts", import.meta.url), "utf8");

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
	assert.equal(warehouseBlockerMessage("BUSINESS_SCOPE_INCOMPLETE", "fallback"), "业务范围尚未确认");
	assert.equal(
		warehouseBlockerMessage("DATA_CONNECTION_NOT_STARTED", "No completion evidence is available for this stage"),
		"本阶段尚未产生可核验的完成证据",
	);
	assert.equal(
		warehouseBlockerMessage("METRIC_SYSTEM_UNKNOWN", "This stage has not produced current completion evidence"),
		"本阶段的完成证据暂时无法核验",
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
		"/modeling/plans/10000000-0000-0000-0000-000000000001/baseline?tab=sources&planId=10000000-0000-0000-0000-000000000001",
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
