import assert from "node:assert/strict";
import test from "node:test";
import {
	flattenDepartmentOptions,
	hasRequiredPublicationAudience,
	isPublishedAnalysisCard,
	publicationIssueMessage,
} from "./dashboardEditorModel.ts";

test("publication audience requires a real department or role", () => {
	assert.equal(hasRequiredPublicationAudience({ deptCodes: [], roleCodes: [] }), false);
	assert.equal(hasRequiredPublicationAudience({ deptCodes: ["D100"], roleCodes: [] }), true);
	assert.equal(hasRequiredPublicationAudience({ deptCodes: [], roleCodes: ["ROLE_ANALYST"] }), true);
});

test("department tree becomes searchable path-labelled options", () => {
	assert.deepEqual(
		flattenDepartmentOptions([
			{
				id: 1,
				name: "集团总部",
				deptCode: "HQ",
				children: [{ id: 2, name: "研发中心", deptCode: "RND", children: [] }],
			},
		]),
		[
			{ value: "HQ", label: "集团总部" },
			{ value: "RND", label: "集团总部 / 研发中心" },
		],
	);
});

test("only a published governed analysis can enter a new dashboard binding", () => {
	assert.equal(
		isPublishedAnalysisCard({ id: 1, type: "analysis", lifecycle_status: "PUBLISHED", published_revision_id: 9 }),
		true,
	);
	assert.equal(
		isPublishedAnalysisCard({ id: 2, type: "question", lifecycle_status: "DRAFT", published_revision_id: null }),
		false,
	);
	assert.equal(
		isPublishedAnalysisCard({ id: 3, type: "analysis", lifecycle_status: "DRAFT", published_revision_id: null }),
		false,
	);
});

test("publication blocker names the affected component instead of exposing an English code", () => {
	const message = publicationIssueMessage(
		{
			code: "DASHBOARD_ANALYSIS_REQUIRED",
			path: "components[0]",
			message: "component must reference a governed analysis",
		},
		[{ id: 7, card_id: 2, card: { id: 2, name: "预算剩余可用" } }],
	);
	assert.equal(message, "组件“预算剩余可用”不是已发布的治理分析，请替换后重新校验。");
});
