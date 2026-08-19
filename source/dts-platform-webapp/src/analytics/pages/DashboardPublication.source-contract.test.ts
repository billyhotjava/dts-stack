import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const EDITOR_SOURCE = readFileSync(new URL("./DashboardEditorPage.tsx", import.meta.url), "utf8");
const QUERY_SOURCE = readFileSync(new URL("./dashboard/useDashboardCardQueries.ts", import.meta.url), "utf8");
const API_SOURCE = readFileSync(new URL("../api/analyticsApi.ts", import.meta.url), "utf8");
const REPORTS_SOURCE = readFileSync(new URL("../../pages/services/BiLinksPage.tsx", import.meta.url), "utf8");

test("dashboard publication validates limits, pins dependencies, and remains separate from draft save", () => {
	for (const api of [
		"validateDashboardPublication",
		"publishDashboard",
		"listDashboardVersions",
		"createDashboardDraftFromVersion",
		"retryDashboardRegistration",
	]) {
		assert.match(API_SOURCE, new RegExp(api));
	}
	for (const copy of [
		"保存草稿",
		"发布仪表板",
		"依赖快照",
		"最多允许 50 个分析组件",
		"最多允许 20 个筛选参数",
		"基于此版本创建草稿",
	]) {
		assert.match(EDITOR_SOURCE, new RegExp(copy));
	}
});

test("dashboard picker and consumer surface enforce the governed registration boundary", () => {
	assert.match(EDITOR_SOURCE, /card\.type === "analysis"/);
	assert.match(EDITOR_SOURCE, /card\.lifecycle_status === "PUBLISHED"/);
	assert.match(EDITOR_SOURCE, /PENDING_REGISTRATION/);
	assert.match(EDITOR_SOURCE, /REGISTRATION_FAILED/);
	for (const field of ["assetType", "assetKey", "assetVersion", "reconcileStatus"]) {
		assert.match(REPORTS_SOURCE, new RegExp(field));
	}
	assert.match(REPORTS_SOURCE, /受管资产请在仪表板发布端维护/);
});

test("new dashboard cards query through the card endpoint until their binding has a persisted id", () => {
	assert.match(QUERY_SOURCE, /dashboardId && dashcard\.id > 0/);
	assert.match(QUERY_SOURCE, /queryCard\(cardId/);
});
