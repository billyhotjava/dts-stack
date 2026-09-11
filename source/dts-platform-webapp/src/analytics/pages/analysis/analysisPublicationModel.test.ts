import assert from "node:assert/strict";
import test from "node:test";
import {
	analysisPublicationIssueMessage,
	atLeastClassification,
	publicationClassificationFloor,
} from "./analysisPublicationModel.ts";

test("publication blockers are shown as actionable Chinese messages, not raw codes", () => {
	const audience = analysisPublicationIssueMessage({
		code: "ANALYSIS_AUDIENCE_REQUIRED",
		path: "audience",
		message: "at least one department or role is required",
	});
	assert.equal(audience, "请选择至少一个可见部门或可见角色。");

	const downgrade = analysisPublicationIssueMessage(
		{
			code: "ANALYSIS_CLASSIFICATION_DOWNGRADE",
			path: "classification",
			message: "publication classification cannot be lower",
		},
		{ classification: "DATA_SECRET" },
	);
	assert.equal(downgrade, "发布密级不能低于数据集密级（秘密），请调整为“秘密”或更高。");

	const dependency = analysisPublicationIssueMessage({
		code: "ANALYSIS_DEPENDENCY_INVALID",
		path: "querySpec.dataset",
		message: "x",
	});
	assert.match(dependency, /^分析依赖的数据集校验未通过/);
	assert.doesNotMatch(dependency, /analysis dependency/i);
});

test("publication classification defaults to the dataset floor and never lowers a stricter choice", () => {
	assert.equal(publicationClassificationFloor("SECRET"), "DATA_SECRET");
	assert.equal(publicationClassificationFloor(null), null);
	assert.equal(atLeastClassification("DATA_INTERNAL", "DATA_SECRET"), "DATA_SECRET");
	assert.equal(atLeastClassification("DATA_CONFIDENTIAL", "DATA_SECRET"), "DATA_CONFIDENTIAL");
	assert.equal(atLeastClassification("DATA_INTERNAL", null), "DATA_INTERNAL");
});
