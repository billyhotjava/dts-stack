// @vitest-environment jsdom

import { expect, test } from "vitest";
import { qualityDatasetIdFromRef, qualityRoute, resolveQualityPolicyRef } from "./AccessGovernancePanels";

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

test("a quality run without a frozen policy never falls back to the task draft", () => {
	const draftPolicy = "dataset:22222222-2222-2222-2222-222222222222";
	expect(resolveQualityPolicyRef({ qualityPolicyRef: draftPolicy }, { qualityRunId: "quality-run-8" })).toBeUndefined();
	expect(qualityRoute("/governance/quality", undefined, "quality-run-8")).toBe("/governance/rules/runs/quality-run-8");
});
