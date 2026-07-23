import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("./warehousePlanApi.ts", import.meta.url), "utf8");

test("warehouse plan header update uses canonical PATCH and strong plan-head CAS", () => {
	assert.match(source, /export type UpdateWarehousePlanInput/);
	assert.match(source, /export const updateWarehousePlan/);
	assert.match(source, /api\.patch<WarehousePlanHeader>/);
	assert.match(source, /url: `\$\{WAREHOUSE_PLAN_RESOURCE\}\/\$\{planId\}`/);
	assert.match(source, /headers: \{ "If-Match": `"plan-head:\$\{version\}"` \}/);
	assert.match(source, /_skipErrorToast: true/);

	const input = source.match(/export type UpdateWarehousePlanInput[\s\S]*?\n\};/)?.[0] || "";
	for (const field of ["name", "objective", "scope", "ownerId", "ownerDepartmentId"]) {
		assert.match(input, new RegExp(`\\b${field}\\??:`));
	}
	assert.doesNotMatch(input, /\bcode\??:|\bonboardingMode\??:|\blifecycleStatus\??:|\btenantId\??:|\bversion\??:/);
});

test("warehouse plan archive uses the same canonical header CAS without a legacy API", () => {
	assert.match(source, /export const archiveWarehousePlan/);
	assert.match(source, /url: `\$\{WAREHOUSE_PLAN_RESOURCE\}\/\$\{planId\}\/archive`/);
	assert.match(source, /headers: \{ "If-Match": `"plan-head:\$\{version\}"` \}/);
	assert.doesNotMatch(source, /"\/modeling\/plans"|`\/modeling\/plans/);
});
