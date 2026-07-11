import assert from "node:assert/strict";
import test from "node:test";
import { isGrainDeclared, resolveGrainDeclaration } from "./grainDeclaration.ts";

test("grain is declared only when statement and at least one known key exist", () => {
	assert.equal(isGrainDeclared({ statement: "一行代表一个节点", grainKeys: ["node_id"] }, ["node_id", "status"]), true);
	assert.equal(isGrainDeclared({ statement: "一行代表一个节点", grainKeys: [] }, ["node_id"]), false);
	assert.equal(isGrainDeclared({ statement: "", grainKeys: ["node_id"] }, ["node_id"]), false);
	assert.equal(isGrainDeclared({ statement: "一行代表一个节点", grainKeys: ["missing"] }, ["node_id"]), false);
});

test("grain gate distinguishes missing declaration from unknown keys", () => {
	assert.deepEqual(resolveGrainDeclaration(undefined, ["node_id"]), {
		status: "missing",
		reason: "请填写粒度语句并至少选择一个粒度键",
	});
	assert.deepEqual(resolveGrainDeclaration({ statement: "一行代表一个节点", grainKeys: ["missing"] }, ["node_id"]), {
		status: "blocked",
		reason: "粒度键必须来自当前模型字段",
	});
});
