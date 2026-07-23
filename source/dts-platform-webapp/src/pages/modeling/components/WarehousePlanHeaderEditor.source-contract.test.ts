import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const componentUrl = new URL("./WarehousePlanHeaderEditor.tsx", import.meta.url);
const component = existsSync(componentUrl) ? readFileSync(componentUrl, "utf8") : "";

test("shared warehouse plan header editor exposes only mutable canonical fields", () => {
	assert.equal(existsSync(componentUrl), true, "shared warehouse plan header editor is missing");
	for (const field of ["name", "objective", "scope", "ownerId", "ownerDepartmentId"]) {
		assert.match(component, new RegExp(`name=[{\"]${field}`));
	}
	assert.match(component, /计划编码/);
	assert.match(component, /开始方式/);
	assert.match(component, /生命周期/);
	assert.doesNotMatch(component, /name=[{"](?:code|onboardingMode|lifecycleStatus|tenantId|version)/);
});

test("owner transfer uses the real directory and never accepts an unverified free-text identity", () => {
	assert.match(component, /searchUsers/);
	assert.match(component, /showSearch/);
	assert.match(component, /filterOption=\{false\}/);
	assert.match(component, /onSearch/);
	assert.match(component, /ownerDepartmentId/);
	assert.doesNotMatch(component, /<Input[^>]+name=.*ownerId|name="ownerId"[^\n]*<Input/);
	assert.match(component, /保留当前负责人/);
});

test("editor saves with CAS and keeps explicit conflict recovery choices", () => {
	assert.match(component, /updateWarehousePlan/);
	assert.match(component, /resolveWarehousePlanConflictVersion/);
	assert.match(component, /保留当前输入并基于版本/);
	assert.match(component, /放弃并加载最新版/);
	assert.match(component, /getWarehousePlan/);
	assert.match(component, /createLatestRequestGuard/);
	assert.match(component, /if \(!isCurrent\(\)\) return/);
});

test("editor preserves customer-facing permission, missing-plan and network recovery", () => {
	assert.match(component, /response\?\.status === 403/);
	assert.match(component, /response\?\.status === 404/);
	assert.match(component, /当前账号没有规划维护权限/);
	assert.match(component, /规划已不可访问/);
	assert.match(component, /表单已保留/);
	assert.match(component, /canEditWarehousePlanHeader/);
	assert.match(component, /onUnavailable/);
	assert.match(component, /返回建设规划台账/);
	assert.match(component, /保存结果未知/);
	assert.match(component, /核对服务端状态/);
	assert.match(component, /pendingVerification/);
	assert.match(component, /WAREHOUSE_PLAN_LIFECYCLE_CONFLICT/);
});

test("an unknown PATCH result blocks every user-triggered retry until GET verification finishes", () => {
	assert.match(component, /verifiedPending/);
	assert.match(component, /pendingVerification\s*&&\s*verifiedPending\s*!==\s*pendingVerification/);
	assert.match(component, /setConflictVersion\(null\)[\s\S]{0,160}setPendingVerification/);
	assert.match(component, /disabled=\{!editable \|\| saving \|\| pendingVerification != null\}/);
});

test("lifecycle reconciliation keeps the form locked and syncs the latest plan when the drawer closes", () => {
	assert.match(component, /setLockedPlan\(latest\)/);
	assert.match(component, /if \(lockedPlan\) onPlanChange\(lockedPlan\)/);
	assert.match(component, /lockedPlan\?\.lifecycleStatus \?\? plan\.lifecycleStatus/);
});

test("header validation mirrors the backend-facing name and owner requirements", () => {
	assert.match(component, /max:\s*128/);
	assert.match(component, /maxLength=\{128\}/);
	assert.match(component, /required:\s*true[\s\S]{0,120}规划名称/);
	assert.match(component, /required:\s*true[\s\S]{0,180}负责人/);
	assert.match(component, /form\.setFields/);
	assert.match(component, /WAREHOUSE_PLAN_HEADER_INVALID/);
});
