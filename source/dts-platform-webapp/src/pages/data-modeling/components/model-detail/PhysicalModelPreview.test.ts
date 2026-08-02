import { describe, expect, it, vi } from "vitest";

vi.mock("@/api/modelPhysicalPreviewApi", () => ({
	getModelPhysicalPreview: vi.fn(),
	getModelPhysicalStructure: vi.fn(),
}));
import { mapPhysicalPreviewFailure, resolvePhysicalSurfaceState } from "./PhysicalModelPreview";

describe("physical model preview state", () => {
	it("distinguishes serving, materializing, stale and candidate-unavailable states", () => {
		expect(resolvePhysicalSurfaceState({ scope: "SERVING", status: "UNAVAILABLE", hasReference: false })).toBe(
			"NO_SERVING_EVIDENCE",
		);
		expect(resolvePhysicalSurfaceState({ scope: "SERVING", status: "MATERIALIZING", hasReference: false })).toBe(
			"MATERIALIZING",
		);
		expect(resolvePhysicalSurfaceState({ scope: "SERVING", status: "FAILED_STALE", hasReference: true })).toBe(
			"FAILED_STALE",
		);
		expect(
			resolvePhysicalSurfaceState({
				scope: "CANDIDATE",
				status: "READY",
				hasReference: true,
				canPreviewCandidate: false,
			}),
		).toBe("CANDIDATE_UNAVAILABLE");
	});

	it("maps only stable error codes and a safe correlation id", () => {
		expect(
			mapPhysicalPreviewFailure({
				response: {
					data: {
						code: "PHYSICAL_PREVIEW_ACCESS_DENIED",
						message: "Physical preview failed; correlationId=corr-123; sql=select secret",
					},
				},
			}),
		).toEqual({ state: "ACCESS_DENIED", code: "PHYSICAL_PREVIEW_ACCESS_DENIED", correlationId: "corr-123" });
		expect(
			mapPhysicalPreviewFailure({ response: { data: { code: "PHYSICAL_PREVIEW_MASKING_UNAVAILABLE" } } }),
		).toEqual({ state: "POLICY_BLOCKED", code: "PHYSICAL_PREVIEW_MASKING_UNAVAILABLE", correlationId: null });
	});
});
