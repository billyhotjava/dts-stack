import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), "utf8");

describe("data-modeling independent review corrections", () => {
	it("hands planning maintenance to the registered governance owner", () => {
		const source = read("./pages/PlanningWorkspace.tsx");
		expect(source).toContain('path: "/governance/subjects"');
		expect(source).toContain('path: "/governance/subjects?tab=data-marts"');
		expect(source).not.toContain("/governance/subject-areas");
	});

	it("resolves graph links to exact model and dimension revisions", () => {
		const source = read("./pages/DimensionalModelingWorkspace.tsx");
		expect(source).toContain("getModelSpecRevision");
		expect(source).toContain("getDimensionDefinitionRevision");
		expect(source).toContain("requestedRevision");
		expect(source).toContain("dimensionDefinitionId");
	});

	it("keeps idempotency keys stable across uncertain retries", () => {
		const planning = read("./pages/PlanningWorkspace.tsx");
		const editor = read("./components/ModelingEditor.tsx");
		const dialogs = read("./components/ModelingDialogs.tsx");
		const operation = read("../../features/modeling/operations/dimensionModelOperationUrl.ts");
		const draftSession = read("../../features/modeling/operations/dimensionModelDraftSession.ts");
		const workspace = read("./pages/DimensionalModelingWorkspace.tsx");

		expect(planning).toContain("createPlanIdempotencyKeyRef");
		expect(editor).toContain("modelCreateIdempotencyKeyRef");
		expect(editor).toContain("createDimensionModel");
		expect(editor).toContain("persistDimensionModelDraft");
		expect(editor).toContain("currentModelSpec");
		expect(editor).toContain("await onSaved");
		expect(editor).not.toContain("pendingDimensionRef");
		expect(workspace).toContain("ensureDimensionModelOperationId");
		expect(workspace).toContain("getDimensionModelOperation");
		expect(workspace).toContain("readDimensionModelDraft");
		expect(workspace).toContain("dmOperationId");
		expect(editor).not.toContain("createDimensionDefinition");
		expect(editor).not.toContain("confirmDimensionDefinition");
		expect(operation).not.toContain("localStorage");
		expect(operation).toContain('const OPERATION_PARAM = "dmOperationId"');
		expect(draftSession).toContain("window.sessionStorage");
		expect(draftSession).toContain('crypto.subtle.digest("SHA-256"');
		expect(draftSession).not.toContain("localStorage");
		expect(dialogs).toContain("buildIntentIdempotencyRef");
		expect(dialogs).toContain("publicationIntentIdempotencyRef");
	});

	it("prevents stale standard catalog and detail responses from overwriting current state", () => {
		const source = read("./pages/StandardsWorkspace.tsx");
		expect(source).toContain("catalogRequestSequenceRef");
		expect(source).toContain("detailRequestSequenceRef");
	});

	it("keeps the model variant code aligned with the canonical uppercase contract", () => {
		const source = read("./components/ModelingEditor.tsx");
		expect(source).toContain('placeholder="UPPER_SNAKE_CASE"');
		expect(source).not.toContain('placeholder="lower_snake_case"');
		expect(source.match(/variantCode\.trim\(\)\.toUpperCase\(\) \|\| null/g)).toHaveLength(2);
	});
});
