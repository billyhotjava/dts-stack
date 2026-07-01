import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const OPS_OVERVIEW_SOURCE = readFileSync(new URL("./OpsOverviewPage.tsx", import.meta.url), "utf8");
const OPS_INSTANCES_SOURCE = readFileSync(new URL("./OpsInstancesPage.tsx", import.meta.url), "utf8");
const OPS_ALERTS_SOURCE = readFileSync(new URL("./OpsAlertLogPage.tsx", import.meta.url), "utf8");
const OPS_BACKFILL_SOURCE = readFileSync(new URL("./OpsBackfillPage.tsx", import.meta.url), "utf8");
const OPS_EVENTS_SOURCE = readFileSync(new URL("./PlatformEventObservabilityPage.tsx", import.meta.url), "utf8");
const OPS_AUDIT_SOURCE = readFileSync(new URL("./AuditEvidencePage.tsx", import.meta.url), "utf8");
const OPS_RELEASE_SOURCE = readFileSync(new URL("./ReleaseGovernancePage.tsx", import.meta.url), "utf8");
const STATIC_ROUTES_SOURCE = readFileSync(new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url), "utf8");

test("Sprint-45 ops hub exposes service evidence routes as customer-facing actions", () => {
	for (const route of ["ops/events", "ops/audit-evidence", "ops/release-governance"]) {
		assert.match(STATIC_ROUTES_SOURCE, new RegExp(route.replace("/", "\\/")));
	}
	for (const label of ["查看事件", "导出证据", "发起发布复核"]) {
		assert.match(OPS_OVERVIEW_SOURCE, new RegExp(label));
	}
});

test("Sprint-45 ops instances can reverse-locate source task, backfill, logs and related objects", () => {
	for (const label of ["查看源任务", "查看数据源", "查看资产", "补数", "查看日志", "重试", "终止实例"]) {
		assert.match(OPS_INSTANCES_SOURCE, new RegExp(label));
	}
	for (const route of ["/explore/etl/transform", "/ops/backfill", "/ops/logs", "/foundation/data-sources", "/catalog/assets"]) {
		assert.match(OPS_INSTANCES_SOURCE, new RegExp(route.replaceAll("/", "\\/")));
	}
	assert.match(OPS_INSTANCES_SOURCE, /searchParams\.get\("keyword"\)/);
	assert.match(OPS_INSTANCES_SOURCE, /searchParams\.get\("entryKey"\)/);
	assert.match(OPS_INSTANCES_SOURCE, /fetchInstances\(\{ keyword: urlKeyword, status, entryKey: urlEntryKey \}\)/);
	assert.doesNotMatch(OPS_INSTANCES_SOURCE, /默认聚焦|统一查看/);
});

test("Sprint-45 ops alerts and backfill expose todo creation and impact scope", () => {
	for (const label of ["创建待办", "查看源任务", "查看数据源", "查看资产"]) {
		assert.match(OPS_ALERTS_SOURCE, new RegExp(label));
	}
	for (const label of ["影响说明", "查看源任务", "查看实例", "查看日志"]) {
		assert.match(OPS_BACKFILL_SOURCE, new RegExp(label));
	}
});

test("Sprint-45 event, audit and release pages expose evidence actions without fake writes", () => {
	for (const label of ["查看关联链路", "导出证据"]) {
		assert.match(OPS_EVENTS_SOURCE, new RegExp(label));
		assert.match(OPS_AUDIT_SOURCE, new RegExp(label));
	}
	for (const label of ["发起发布复核", "撤回发布", "查看关联链路"]) {
		assert.match(OPS_RELEASE_SOURCE, new RegExp(label));
	}
	for (const source of [OPS_AUDIT_SOURCE, OPS_RELEASE_SOURCE]) {
		assert.match(source, /disabled/);
		assert.match(source, /后端/);
	}
});
