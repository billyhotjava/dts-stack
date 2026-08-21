import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const EDITOR = readFileSync(new URL("./DashboardEditorPage.tsx", import.meta.url), "utf8");
const GRID = readFileSync(new URL("./dashboard/DashboardEditorGrid.tsx", import.meta.url), "utf8");
const CARD = readFileSync(new URL("./dashboard/DashboardEditorCard.tsx", import.meta.url), "utf8");
const API = readFileSync(new URL("../api/analyticsApi.ts", import.meta.url), "utf8");

test("dashboard publication saves the current draft before validating it", () => {
	assert.match(EDITOR, /saveDraft/);
	assert.match(EDITOR, /validateDashboardPublication\(saved\.id/);
	assert.match(EDITOR, /\/bi\/dashboards\/\$\{saved\.id\}\/edit/);
	assert.match(EDITOR, /发布范围（必填）/);
});

test("publication audience comes from the platform directory rather than free-form codes", () => {
	assert.match(API, /listPlatformOrgs/);
	assert.match(API, /listPlatformRoles/);
	assert.match(EDITOR, /departmentOptions/);
	assert.match(EDITOR, /roleOptions/);
	assert.doesNotMatch(EDITOR, /mode="tags"[\s\S]{0,180}audience\.deptCodes/);
	assert.doesNotMatch(EDITOR, /mode="tags"[\s\S]{0,180}audience\.roleCodes/);
});

test("dashboard composition exposes a library, external drop and component inspector", () => {
	assert.match(EDITOR, /DashboardAnalysisLibrary/);
	assert.match(EDITOR, /DashboardComponentInspector/);
	assert.match(GRID, /isDroppable/);
	assert.match(GRID, /onDrop=/);
	assert.match(CARD, /替换分析/);
	assert.match(CARD, /打开分析/);
});
