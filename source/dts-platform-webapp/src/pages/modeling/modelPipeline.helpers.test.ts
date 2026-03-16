import assert from "node:assert/strict";
import test from "node:test";
import { createModelPipelineSteps } from "./modelPipeline.helpers";

test("createModelPipelineSteps returns read-only steps for status display", () => {
	const steps = createModelPipelineSteps();

	assert.equal(steps.length, 4);
	assert.deepEqual(
		steps.map((step) => ({
			key: step.key,
			title: step.title,
			interactive: step.interactive,
		})),
		[
			{ key: "compile", title: "编译", interactive: false },
			{ key: "test", title: "测试", interactive: false },
			{ key: "commit", title: "提交变更", interactive: false },
			{ key: "publish", title: "发布上线", interactive: false },
		],
	);
});
