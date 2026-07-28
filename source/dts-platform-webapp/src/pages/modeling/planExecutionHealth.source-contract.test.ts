import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const api = readFileSync(new URL("../../api/modelSpecApi.ts", import.meta.url), "utf8");
const panel = readFileSync(new URL("./components/PlanExecutionHealthPanel.tsx", import.meta.url), "utf8");

test("plan execution UI uses one server-owned health projection and commands", () => {
	assert.match(api, /getPlanExecutionWorkspace/);
	assert.match(api, /runPlanExecutionNow/);
	assert.match(api, /repairPlanExecutionBinding/);
	assert.match(api, /"If-Match": `"plan-execution-binding:/);
	assert.match(panel, /binding\.allowedActions\.includes\("RUN_NOW"\)/);
	assert.match(panel, /binding\.allowedActions\.includes\("REPAIR_DEPLOYMENT"\)/);
	assert.match(panel, /document\.visibilityState === "visible"/);
});

test("plan execution view does not expose runtime internals", () => {
	for (const forbidden of ["runtimeSpecToken", "projectDir", "dbtSelector", "credentialVersionRef"]) {
		assert.doesNotMatch(panel, new RegExp(forbidden));
	}
	assert.match(panel, /DAG、selector、target 和运行凭据均由服务端生成/);
});
