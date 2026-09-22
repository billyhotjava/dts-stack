import assert from "node:assert/strict";
import test from "node:test";
import {
	defaultOwnerFields,
	deptForOwner,
	deptSelectOptions,
	ownerLeavesDept,
	ownerSelectOptions,
} from "./governanceOwnerModel.ts";

const users = [
	{ username: "gaoxin", displayName: "高馨", deptCode: "IT" },
	{ username: "xiezm", displayName: "谢志民", deptCode: "OPS" },
	{ username: "biadmin" },
];

test("owner options follow the chosen department and keep a saved owner visible", () => {
	assert.deepEqual(
		ownerSelectOptions(users, "IT", "legacy_owner").map((option) => option.value),
		["legacy_owner", "gaoxin"],
	);
	assert.equal(ownerSelectOptions(users, null, null).length, 3);
	assert.equal(ownerSelectOptions(users, "OPS", null)[0].label, "谢志民（xiezm）");
});

test("picking an owner implies the department and a conflicting department is detected", () => {
	assert.equal(deptForOwner(users, "xiezm"), "OPS");
	assert.equal(deptForOwner(users, "biadmin"), undefined);
	assert.equal(ownerLeavesDept(users, "gaoxin", "OPS"), true);
	assert.equal(ownerLeavesDept(users, "biadmin", "OPS"), true);
});

test("defaults apply only where the asset has no owner or department", () => {
	assert.deepEqual(defaultOwnerFields({ owner: "", ownerDept: null }, { username: "gaoxin", deptCode: "IT" }), {
		businessOwner: "gaoxin",
		ownerDept: "IT",
	});
	assert.deepEqual(defaultOwnerFields({ owner: "biadmin", ownerDept: "OPS" }, { username: "gaoxin", deptCode: "IT" }), {
		businessOwner: "biadmin",
		ownerDept: "OPS",
	});
	assert.deepEqual(deptSelectOptions([{ code: "IT", nameZh: "信息化" }], "信息化"), [
		{ value: "信息化", label: "信息化" },
		{ value: "IT", label: "信息化（IT）" },
	]);
});

test("department labels use known directory names even when the department page omitted that code", () => {
	assert.deepEqual(deptSelectOptions([], "OPS", [{ username: "xiezm", deptCode: "OPS", deptName: "运维部" }]), [
		{ value: "OPS", label: "运维部（OPS）" },
	]);
	assert.deepEqual(
		deptSelectOptions([{ code: "OPS", nameZh: "运维中心" }], "OPS", [
			{ username: "xiezm", deptCode: "OPS", deptName: "运维部" },
		]),
		[{ value: "OPS", label: "运维中心（OPS）" }],
	);
});

test("changing a department clears an unknown owner but clearing the department keeps the owner", () => {
	assert.equal(ownerLeavesDept(users, "legacy_owner", "OPS"), true);
	assert.equal(ownerLeavesDept(users, "gaoxin", ""), false);
	assert.deepEqual(
		ownerSelectOptions(users, "OPS").map((option) => option.value),
		["xiezm"],
	);
});
