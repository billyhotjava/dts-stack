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

test("role candidates use admin role ID and role name terminology", async () => {
	const source = await readFile(managerPath, "utf8");

	assert.match(source, /if \(r\) return r\.description \|\| r\.name;/);
	assert.match(source, />\s*角色ID\s*</);
	assert.match(source, />\s*角色名称\s*</);
	assert.equal(source.includes("<th className={headerCls}>描述</th>"), false);
	assert.match(source, /placeholder=\{granteeType === 'USER' \? '搜索用户名或姓名\.\.\.' : '搜索角色ID或角色名称\.\.\.'\}/);
	assert.match(source, /<td className=\{`\$\{cellCls\} font-medium`\}>\{role\.name\}<\/td>/);
	assert.match(source, /<td className=\{`\$\{cellCls\} text-text-secondary`\}>\{role\.description \|\| '-'\}<\/td>/);
});
