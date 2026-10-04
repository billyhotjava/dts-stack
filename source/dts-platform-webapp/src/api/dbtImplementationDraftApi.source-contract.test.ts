import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (path: string) => readFileSync(new URL(path, import.meta.url), "utf8");

describe("advanced dbt implementation draft contract", () => {
	it("uses the isolated draft create/save/validate/commit API and never the legacy shared projectDir API", () => {
		const api = read("./dbtImplementationDraftApi.ts");

		expect(api).toContain("/dbt-drafts");
		expect(api).toContain("/files");
		expect(api).toContain("/validate");
		expect(api).toContain("/commit");
		expect(api).toContain("baseModelRevision");
		expect(api).toContain("baseImplementationRevision");
		expect(api).toContain("targetPhysicalName");
		expect(api).toContain("expectedEtag");
		expect(api).toContain("sourceBundle");
		expect(api).toContain("dependencySnapshot");
		expect(api).toContain("dependencyChecksum");
		expect(api).toContain("managedDependencyAliases");
		expect(api).toContain("dependencyValidation");
		expect(api).toContain("bundleChecksum");
		expect(api).toContain("projectChecksum");
		expect(api).toContain("byteSize");
		expect(api).toContain("checksum");
		expect(api).not.toContain("/api/etl/dbt/files");
		expect(api).not.toContain("/etl/dbt/files");
	});

	it("keeps maintainer-only editing in the data-modeling workbench with dirty and conflict states", () => {
		const workbench = read("../pages/data-modeling/prototype/ModelingWorkbenchPage.tsx");
		const authoringSession = read("../pages/data-modeling/prototype/useModelAuthoringSession.ts");
		const dialogs = read("../pages/data-modeling/prototype/ModelWorkbenchDialog.tsx");
		const editor = read("../pages/data-modeling/prototype/AdvancedDbtWorkspace.tsx");
		const authoringApi = read("./modelAuthoringApi.ts");

		expect(workbench).toContain("useDataModelingMenuGrant");
		expect(workbench).toMatch(/<AdvancedDbtWorkspace[\s\S]*canMaintain=\{canMaintain\}/);
		expect(authoringSession).toContain("setCodeDirty");
		expect(workbench).toContain("const unsavedChanges = dirty || authoringCodeDirty");
		expect(workbench).toContain('import { AdvancedDbtWorkspace } from "./AdvancedDbtWorkspace"');
		expect(workbench).toMatch(/requestedView === "code" && selectedModel \? \(\s*<AdvancedDbtWorkspace/s);
		expect(dialogs).not.toContain("AdvancedDbtDialog");
		expect(editor).toMatch(/<section aria-label="模型代码视图"/);
		expect(editor).not.toContain("<Modal");
		expect(editor).not.toContain("TransitionConfirmation");
		expect(editor).toContain('includes("EDIT_IMPLEMENTATION")');
		expect(editor).toContain("dirty");
		expect(editor).toContain("conflict");
		expect(authoringSession).toContain("validateModelAuthoringDraft");
		expect(authoringSession).toContain("commitModelAuthoringDraft");
		expect(authoringSession).toContain("dependencyChecksum: checked.dependencyValidation?.dependencyChecksum");
		expect(editor).toContain("isManagedDependencyPath");
		expect(editor).toContain("系统依赖");
		expect(authoringApi).toContain("/authoring-context");
		expect(authoringApi).toContain("/authoring-drafts");
		expect(authoringApi).toContain('activeView: "VISUAL" | "CODE"');
		expect(authoringApi).toContain("modelSpecSnapshot: ModelAuthoringSnapshot");
		expect(authoringApi).toContain("files: DbtDraftFile[]");
		expect(authoringSession).toContain("saved.files");
		expect(authoringSession).toContain("modelDraftToAuthoringSnapshot");
		expect(authoringSession).toContain("Boolean(prepared.implementationInputMode)");
		expect(`${workbench}\n${authoringSession}\n${authoringApi}`).not.toMatch(
			/transitionImplementationOwnership|convertToDesignerGenerated/,
		);
		expect(`${workbench}\n${dialogs}\n${editor}`).not.toContain("/api/etl/dbt/files");
	});
});
