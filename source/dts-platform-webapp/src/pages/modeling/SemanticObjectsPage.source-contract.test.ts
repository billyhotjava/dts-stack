import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("./SemanticObjectsPage.tsx", import.meta.url), "utf8");

test("direct business-object entry keeps creation actionable without silently dropping process context", () => {
	assert.match(source, /const openCreate = \(\) =>/);
	assert.match(source, /选择业务过程后新建/);
	assert.match(source, /from=business-object-ledger/);
	assert.doesNotMatch(source, /disabled=\{!processId\}/);
});

test("business object code is system-generated, never typed by hand", () => {
	assert.match(source, /generateBusinessObjectCode/);
	assert.match(source, /business-object-auto-code/);
	assert.match(source, /系统自动生成/);
	assert.match(source, /code: autoCode/);
	// 创建表单不得出现手工编码输入
	assert.doesNotMatch(source, /name="code"/);
});

test("business object ledger keeps only the essentials", () => {
	// 名称与编码合并为一列；业务过程/来源模型/标准列移出台账
	assert.doesNotMatch(source, /title: "对象编码"/);
	assert.doesNotMatch(source, /title: "业务过程"/);
	assert.doesNotMatch(source, /title: "来源模型"/);
	assert.doesNotMatch(source, /title: "标准"/);
	// 页面动作只保留新建；跨页导航交给工作台页签与上下文条
	assert.doesNotMatch(source, />\s*业务过程目录\s*</);
	assert.doesNotMatch(source, />\s*进入模型管理\s*</);
	// 创建表单不再混入物理映射字段（主表/主键在详情映射里维护）
	assert.doesNotMatch(source, /name="mainTable"/);
	assert.doesNotMatch(source, /name="primaryKey"/);
});

test("business object code is system generated instead of typed by hand", () => {
	assert.match(source, /generateBusinessObjectCode/);
	assert.match(source, /business-object-auto-code/);
	assert.match(source, /系统自动生成/);
	// 创建时编码来自 autoCode，而非表单输入
	assert.match(source, /code: autoCode/);
	assert.doesNotMatch(source, /name="code"/);
});

test("business object ledger keeps only the essentials of an object", () => {
	// 列表按当前业务过程过滤，不再重复展示业务过程列；来源模型/标准状态移入详情
	assert.doesNotMatch(source, /title: "业务过程"/);
	assert.doesNotMatch(source, /title: "来源模型"/);
	assert.doesNotMatch(source, /title: "标准"/);
	// 页面动作只保留新建；跨页导航交给工作台页签与菜单
	assert.doesNotMatch(source, /业务过程目录\s*<\/Button>/);
	assert.doesNotMatch(source, /进入模型管理/);
	// 创建表单不再混入物理映射字段
	assert.doesNotMatch(source, /name="mainTable"/);
	assert.doesNotMatch(source, /name="primaryKey"/);
});
