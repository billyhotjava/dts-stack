import assert from "node:assert/strict";
import test from "node:test";
import {
	createPrimaryModelingActions,
	createSecondaryModelingActions,
	shouldRenderInlineGitCommit,
} from "./modelingToolbar.helpers";

test("createPrimaryModelingActions keeps only compile test and release as main actions", () => {
	const actions = createPrimaryModelingActions();

	assert.deepEqual(
		actions.map((action) => ({
			key: action.key,
			label: action.label,
		})),
		[
			{ key: "compile", label: "编译" },
			{ key: "test", label: "测试" },
			{ key: "release", label: "上线" },
		],
	);
});

test("createSecondaryModelingActions moves secondary actions into more menu", () => {
	const actions = createSecondaryModelingActions();

	assert.deepEqual(
		actions.map((action) => ({
			key: action.key,
			label: action.label,
		})),
		[
			{ key: "commit", label: "提交到 Git" },
			{ key: "sync", label: "同步模型" },
			{ key: "docs", label: "生成文档" },
			{ key: "rollback", label: "回退" },
		],
	);
});

test("shouldRenderInlineGitCommit disables the duplicate commit entry in git panel", () => {
	assert.equal(shouldRenderInlineGitCommit(), false);
});
