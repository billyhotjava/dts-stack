import assert from "node:assert/strict";
import test from "node:test";
import { hasQualityMaintainerRole, hasQualityTaskDeleteRole, normalizeQualityRoleCode } from "./qualityAccess.ts";

test("normalizes string and object role shapes by code then name", () => {
	assert.equal(normalizeQualityRoleCode(" role_op_admin "), "ROLE_OP_ADMIN");
	assert.equal(normalizeQualityRoleCode({ code: "ROLE_DEPT_DATA_OWNER", name: "ignored" }), "ROLE_DEPT_DATA_OWNER");
	assert.equal(normalizeQualityRoleCode({ name: "ROLE_INST_LEADER" }), "ROLE_INST_LEADER");
});

test("governance writes follow the backend governance maintainer role set", () => {
	assert.equal(hasQualityMaintainerRole([{ code: "ROLE_DEPT_DATA_OWNER" }]), true);
	assert.equal(hasQualityMaintainerRole(["ROLE_EMPLOYEE"]), false);
});

test("quality task deletion is exposed only to ROLE_OP_ADMIN", () => {
	assert.equal(hasQualityTaskDeleteRole([{ code: "ROLE_OP_ADMIN" }]), true);
	assert.equal(hasQualityTaskDeleteRole(["ROLE_ADMIN"]), false);
	assert.equal(hasQualityTaskDeleteRole(["OP_ADMIN"]), false);
});
