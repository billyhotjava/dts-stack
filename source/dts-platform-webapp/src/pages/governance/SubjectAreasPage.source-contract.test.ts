import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("./SubjectAreasPage.tsx", import.meta.url), "utf8");

test("subject areas expose a DWD dimension planning entry", () => {
	assert.match(source, /warehousePlanningContext/);
	assert.match(source, /warehouse-planning-card/);
	assert.match(source, /warehouseLayer.*DWD|DWD.*warehouseLayer/);
	assert.match(source, /modelingMode.*dimension|dimension.*modelingMode/);
	assert.ok(source.includes("governance/standards/elements"));
	assert.match(source, /planningId/);
});

test("subject area planning exposes blocked and restore states", () => {
	assert.match(source, /resolveWarehousePlanningContext/);
	assert.match(source, /resolveWarehousePlanningStatus/);
	assert.match(source, /规划草稿已失效|规划上下文不可用|重新确认规划/);
	assert.match(source, /维度建模/);
});
