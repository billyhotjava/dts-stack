import { describe, expect, it } from "vitest";
import {
	ARTIFACT_VALIDATION_API_NAMES,
	createDataProductArtifactValidator,
	resolveArtifactValidations,
	toArtifactValidationMap,
	type ArtifactValidator,
} from "./journeyArtifactValidation";

const draftLookup = (knownIds: string[]) => ({
	findDraft: (id: string) => (knownIds.includes(id) ? { id } : null),
	isBackendDraftId: (id: string) => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id),
});

describe("journey artifact validation", () => {
	it("validates standard drafts against the local draft registry", () => {
		const validator = createDataProductArtifactValidator({ standardDraft: draftLookup(["standard-draft-1-ok"]) });

		expect(validator("standardDraftId", "standard-draft-1-ok")).toBe("valid");
		expect(validator("standardDraftId", "standard-draft-missing")).toBe("invalid");
		expect(validator("standardDraftId", "123e4567-e89b-42d3-a456-426614174000")).toBe("unknown");
	});

	it("treats artifacts without a validation source as unknown", () => {
		const validator = createDataProductArtifactValidator();

		expect(validator("modelId", "m-1")).toBe("unknown");
		expect(validator("sourceId", "ds-1")).toBe("unknown");
		expect(validator("standardDraftId", "anything")).toBe("unknown");
	});

	it("resolves only provided params and attaches reasons and api names", () => {
		const validator = createDataProductArtifactValidator({ standardDraft: draftLookup([]) });
		const results = resolveArtifactValidations(
			{ standardDraftId: "standard-draft-gone", modelId: "m-1" },
			validator,
		);

		expect(results).toHaveLength(2);
		const byKey = toArtifactValidationMap(results);
		expect(byKey.standardDraftId?.status).toBe("invalid");
		expect(byKey.standardDraftId?.reason).toContain("不存在或已失效");
		expect(byKey.standardDraftId?.apiName).toBe(ARTIFACT_VALIDATION_API_NAMES.standardDraftId);
		expect(byKey.modelId?.status).toBe("unknown");
		expect(byKey.modelId?.apiName).toBe(ARTIFACT_VALIDATION_API_NAMES.modelId);
		expect(byKey.sourceId).toBeUndefined();
	});

	it("keeps valid results free of api gap markers", () => {
		const validator = createDataProductArtifactValidator({ standardDraft: draftLookup(["standard-draft-1-ok"]) });
		const [result] = resolveArtifactValidations({ standardDraftId: "standard-draft-1-ok" }, validator);

		expect(result.status).toBe("valid");
		expect(result.reason).toBeUndefined();
		expect(result.apiName).toBeUndefined();
	});

	it("degrades a throwing validator to unknown instead of crashing", () => {
		const throwing: ArtifactValidator = () => {
			throw new Error("boom");
		};
		const [result] = resolveArtifactValidations({ modelId: "m-1" }, throwing);

		expect(result.status).toBe("unknown");
	});
});
