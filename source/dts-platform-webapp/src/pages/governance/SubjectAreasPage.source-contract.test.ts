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
	assert.match(source, /planningResolution\.status\s*===\s*"blocked"/);
	assert.match(source, /规划草稿已失效|规划上下文不可用|重新确认规划/);
	assert.match(source, /维度建模/);
});

test("subject area details expose business processes and process planning entry", () => {
	assert.match(source, /businessProcess/);
	assert.match(source, /业务过程/);
	assert.match(source, /从示例创建|采用示例/);
	assert.match(source, /processId/);
	assert.match(source, /发起规划/);
});

test("subject area details expose the process by conformed-dimension bus matrix", () => {
	assert.match(source, /conformedDimensions/);
	assert.match(source, /总线矩阵/);
	assert.match(source, /toggleBusMatrixLink/);
	assert.match(source, /Checkbox/);
});
