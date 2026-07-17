import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const businessProcessSource = readFileSync(new URL("./businessProcess.ts", import.meta.url), "utf8");
const conformedDimensionSource = readFileSync(new URL("./conformedDimensions.ts", import.meta.url), "utf8");
const subjectAreasSource = readFileSync(new URL("./SubjectAreasPage.tsx", import.meta.url), "utf8");
const modelingContractSource = readFileSync(new URL("../modeling/modelingVnextContract.ts", import.meta.url), "utf8");

test("generic modeling pages do not install customer scenarios by default", () => {
	for (const source of [businessProcessSource, conformedDimensionSource, subjectAreasSource]) {
		assert.doesNotMatch(source, /BUSINESS_PROCESS_SEEDS|CONFORMED_DIMENSION_SEEDS/);
		assert.doesNotMatch(source, /node-plan-loop|quality-zero|risk-release/);
		assert.doesNotMatch(source, /从 PJM 示例开始|从示例创建|补充示例/);
	}
	assert.doesNotMatch(modelingContractSource, /Pjm|pjm/);
});
