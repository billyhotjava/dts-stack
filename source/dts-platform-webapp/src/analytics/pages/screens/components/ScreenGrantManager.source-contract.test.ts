import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const managerPath = new URL("./ScreenGrantManager.tsx", import.meta.url);

test("existing screen grants prefer platform user directory fields and show department", async () => {
	const source = await readFile(managerPath, "utf8");

	assert.match(source, /const findGrantUser = \(row: ScreenAclEntry\): PlatformUser \| undefined =>/);
	assert.match(source, /const u = findGrantUser\(row\);\s*if \(u\) return u\.displayName \|\| u\.username;/s);
	assert.match(source, /const resolveGrantDepartment = \(row: ScreenAclEntry\): string =>/);
	assert.match(source, /u\.deptName \|\| u\.deptCode/);
	assert.match(source, /sortKey="department"/);
	assert.match(source, />\s*部门\s*</);
	assert.match(source, /resolveGrantDepartment\(row\)/);
});
