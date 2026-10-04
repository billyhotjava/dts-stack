import { afterEach, describe, expect, it, vi } from "vitest";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));

vi.mock("./apiClient", () => ({ default: { get } }));

import {
	getModelPhysicalPreview,
	getModelPhysicalStructure,
	type PhysicalPreviewReference,
} from "./modelPhysicalPreviewApi";

describe("model physical preview API", () => {
	afterEach(() => vi.clearAllMocks());

	it("sends every immutable evidence pin and forces a no-store response path", async () => {
		get.mockResolvedValue({ maskedRows: [] });
		const reference: PhysicalPreviewReference = {
			modelSpecId: "model / 1",
			modelRevision: 4,
			modelChecksum: "a".repeat(64),
			implementationRevision: 7,
			implementationChecksum: "b".repeat(64),
			scope: "SERVING",
			candidateId: "20000000-0000-0000-0000-000000000001",
			candidateVersion: 11,
			attempt: 2,
			pipelineRunId: "50000000-0000-0000-0000-000000000001",
			observationAttempt: 1,
			relationEvidenceId: "60000000-0000-0000-0000-000000000001",
			evidenceChecksum: "c".repeat(64),
		};

		await getModelPhysicalPreview(reference, 100);

		expect(get).toHaveBeenCalledWith({
			url: "/modeling/model-specs/model%20%2F%201/implementations/7/physical-preview",
			params: {
				modelRevision: 4,
				modelChecksum: reference.modelChecksum,
				implementationChecksum: reference.implementationChecksum,
				mode: "SAMPLE",
				scope: "SERVING",
				candidateId: reference.candidateId,
				candidateVersion: 11,
				attempt: 2,
				pipelineRunId: reference.pipelineRunId,
				observationAttempt: 1,
				relationEvidenceId: reference.relationEvidenceId,
				evidenceChecksum: reference.evidenceChecksum,
				limit: 100,
			},
			headers: { "Cache-Control": "no-store", Pragma: "no-cache" },
			_skipErrorToast: true,
		});
		expect(get.mock.calls[0]?.[0]?.params).not.toHaveProperty("relationRef");
		expect(get.mock.calls[0]?.[0]?.params).not.toHaveProperty("schema");
		expect(get.mock.calls[0]?.[0]?.params).not.toHaveProperty("table");
	});

	it("loads policy-filtered structure by default without sending a row limit", async () => {
		get.mockResolvedValue({ previewMode: "STRUCTURE", columns: [], maskedRows: [] });
		const reference: PhysicalPreviewReference = {
			modelSpecId: "model-1",
			modelRevision: 4,
			modelChecksum: "a".repeat(64),
			implementationRevision: 7,
			implementationChecksum: "b".repeat(64),
			scope: "SERVING",
			candidateId: "candidate-1",
			candidateVersion: 1,
			attempt: 1,
			pipelineRunId: "pipeline-1",
			observationAttempt: 1,
			relationEvidenceId: "evidence-1",
			evidenceChecksum: "c".repeat(64),
		};

		await getModelPhysicalStructure(reference);

		expect(get.mock.calls[0]?.[0]?.params.mode).toBe("STRUCTURE");
		expect(get.mock.calls[0]?.[0]?.params).not.toHaveProperty("limit");
	});

	it.each([20, 50, 100, 500] as const)("accepts the bounded %i row option", async (limit) => {
		get.mockResolvedValue({ maskedRows: [] });
		await getModelPhysicalPreview(
			{
				modelSpecId: "model-1",
				modelRevision: 1,
				modelChecksum: "a".repeat(64),
				implementationRevision: 1,
				implementationChecksum: "b".repeat(64),
				scope: "CANDIDATE",
				candidateId: "candidate-1",
				candidateVersion: 1,
				attempt: 1,
				pipelineRunId: "pipeline-1",
				observationAttempt: 1,
				relationEvidenceId: "evidence-1",
				evidenceChecksum: "a".repeat(64),
			},
			limit,
		);
		expect(get.mock.calls[0]?.[0]?.params.limit).toBe(limit);
	});
});
