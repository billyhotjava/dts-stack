import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("./ElementsPage.tsx", import.meta.url), "utf8");

test("elements page shows warehouse planning provenance and keeps the return route", () => {
	assert.match(source, /resolveWarehousePlanningContext/);
	assert.match(source, /warehouse-planning-context/);
	assert.match(source, /主题域|数仓层|维度建模/);
	assert.match(source, /planningId/);
	assert.match(source, /warehouseLayer/);
	assert.match(source, /modelingMode/);
	assert.match(source, /governance\/subjects/);
});

test("standard binding draft carries planning metadata and blocks empty inputs", () => {
	assert.match(source, /planningId.*domainId|domainId.*planningId/);
	assert.match(source, /standardDraftId|createStandardBindingDraftSnapshot/);
	assert.match(source, /当前列表没有可输出的数据元|暂无可用数据元/);
});
