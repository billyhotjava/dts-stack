import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const fields = readFileSync(new URL("./components/ModelSpecFieldsTab.tsx", import.meta.url), "utf8");

test("field design uses a compact table-like editor with the key mapping columns in one row", () => {
	assert.match(fields, /<table/);
	assert.match(fields, /<thead>/);
	assert.match(fields, /<tbody/);
	assert.match(fields, /<colgroup>/);
	assert.match(fields, /技术编码/);
	assert.match(fields, /业务名称/);
	assert.match(fields, /数据类型/);
	assert.match(fields, /字段作用/);
	assert.match(fields, /允许为空/);
	assert.match(fields, /维度属性编码/);
	assert.match(fields, /table-fixed/);
});

test("secondary governance fields stay available without expanding every row by default", () => {
	assert.match(fields, /<details/);
	assert.match(fields, /来源与治理/);
	assert.match(fields, /sourceFieldRef/);
	assert.match(fields, /securityLevel/);
	assert.match(fields, /redundancySourceRef/);
	assert.doesNotMatch(fields, /className="rounded-lg border border-gray-200 p-3"/);
});

test("a hidden governance validation error opens and marks the exact field row", () => {
	assert.match(fields, /"redundancySourceRef"/);
	assert.match(fields, /getFieldError\(\["fields", field\.name, property\]\)/);
	assert.match(fields, /open=\{governanceHasErrors \|\| expandedGovernanceRows\.has\(field\.key\)\}/);
	assert.match(fields, /待修正/);
	assert.match(fields, /onToggle=/);
});
