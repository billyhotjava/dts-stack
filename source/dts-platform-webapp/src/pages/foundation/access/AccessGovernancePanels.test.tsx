// @vitest-environment jsdom

import { expect, test } from "vitest";
import { qualityDatasetIdFromRef, qualityRoute, resolveQualityPolicyRef } from "./AccessGovernancePanels";
import SOURCE from "./AccessGovernancePanels.tsx?raw";

test("file quality detection is optional and never activates the access plan", () => {
	expect(SOURCE).toContain("质量检测为可选项");
	expect(SOURCE).toContain("等待启动");
	expect(SOURCE).toContain("等待自动重试");
	expect(SOURCE).toContain("自动重试已用尽");
	expect(SOURCE).toContain("接入后正式验证");
	expect(SOURCE).toContain("qualityWorkflowNextRetryAt");
	expect(SOURCE).not.toContain('kind !== "file" && ["RETRY_WAIT", "EXHAUSTED"]');
	expect(SOURCE).not.toContain("文件预检通过，接入计划已自动生效");
	expect(SOURCE).not.toContain("await ingestionTaskAPI.admitTask(taskId)");
});

test("qualityDatasetIdFromRef accepts only canonical dataset references", () => {
	const datasetId = "6b6dc758-b894-4213-8008-62d0439f17d7";
	expect(qualityDatasetIdFromRef(`dataset:${datasetId}`)).toBe(datasetId);
	expect(qualityDatasetIdFromRef(` dataset:${datasetId} `)).toBe(datasetId);
	expect(qualityDatasetIdFromRef(datasetId)).toBeUndefined();
	expect(qualityDatasetIdFromRef(`connection:${datasetId}`)).toBeUndefined();
	expect(qualityDatasetIdFromRef("dataset:not-a-uuid")).toBeUndefined();
});

test("a recorded quality run resolves the policy frozen on that execution before the task draft", () => {
	const activePolicy = "dataset:11111111-1111-1111-1111-111111111111";
	const draftPolicy = "dataset:22222222-2222-2222-2222-222222222222";
	expect(
		resolveQualityPolicyRef(
			{ qualityPolicyRef: draftPolicy },
			{ qualityRunId: "quality-run-7", qualityPolicyRef: activePolicy },
		),
	).toBe(activePolicy);
	expect(resolveQualityPolicyRef({ qualityPolicyRef: draftPolicy }, null)).toBe(draftPolicy);
});

test("a recorded quality workflow resolves its frozen policy and opens aggregate evidence", () => {
	const activePolicy = "dataset:11111111-1111-1111-1111-111111111111";
	const draftPolicy = "dataset:22222222-2222-2222-2222-222222222222";
	expect(
		resolveQualityPolicyRef(
			{ qualityPolicyRef: draftPolicy },
			{ qualityWorkflowId: "quality-workflow-7", qualityPolicyRef: activePolicy },
		),
	).toBe(activePolicy);
	expect(qualityRoute("/governance/quality", undefined, undefined, "quality-workflow-7")).toBe(
		"/governance/rules/runs?workflowId=quality-workflow-7",
	);
});

test("a quality run without a frozen policy never falls back to the task draft", () => {
	const draftPolicy = "dataset:22222222-2222-2222-2222-222222222222";
	expect(resolveQualityPolicyRef({ qualityPolicyRef: draftPolicy }, { qualityRunId: "quality-run-8" })).toBeUndefined();
	expect(qualityRoute("/governance/quality", undefined, "quality-run-8")).toBe("/governance/rules/runs/quality-run-8");
});
