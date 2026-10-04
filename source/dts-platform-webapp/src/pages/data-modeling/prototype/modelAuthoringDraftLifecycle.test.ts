import { describe, expect, it } from "vitest";
import { shouldPersistBeforeAuthoringValidation } from "./modelAuthoringDraftLifecycle";

describe("model authoring draft validation lifecycle", () => {
	it.each([
		[undefined, false, false, true],
		["DRAFT", false, false, true],
		["VALIDATED", true, false, true],
		["VALIDATED", false, true, true],
		["VALIDATED", false, false, false],
	] as const)(
		"persists state=%s when modelDirty=%s and codeDirty=%s: %s",
		(state, modelDirty, codeDirty, expected) => {
			expect(shouldPersistBeforeAuthoringValidation(state, modelDirty, codeDirty)).toBe(expected);
		},
	);
});
