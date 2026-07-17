import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const entry = readFileSync(new URL("./ModelingWorkbenchPage.tsx", import.meta.url), "utf8");
const frame = readFileSync(new URL("./semantic-workspace/SemanticWorkspaceFrame.tsx", import.meta.url), "utf8");

test("modeling workbench resumes one of four real stages", () => {
	assert.match(entry, /resolveModelingJourneyContext/);
	assert.match(entry, /modelingStagePath/);
	assert.doesNotMatch(entry, /版本记录|Excel 自动分析|可编辑 ER/);
});

test("semantic workspace exposes the generic four-stage journey", () => {
	for (const label of ["范围与来源", "逻辑模型", "实现与验证", "发布与运行"]) {
		assert.ok(frame.includes(label));
	}
	assert.doesNotMatch(frame, /index < activeIndex/);
	assert.doesNotMatch(frame, /ModelingConceptCards/);
});
