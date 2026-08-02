import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), "utf8");

describe("data-modeling canonical ModelSpec editor", () => {
	it("creates and updates canonical ModelSpec revisions instead of keeping a local demo draft", () => {
		const workspace = read("./pages/DimensionalModelingWorkspace.tsx");
		const editor = read("./components/ModelingEditor.tsx");

		expect(editor).toContain("createModelSpec");
		expect(editor).toContain("createDimensionModel");
		expect(editor).toContain("updateModelSpec");
		expect(editor).toContain("definitionBinding");
		expect(editor).toContain("modelSpecRevision.revision !== 2");
		expect(editor).toContain("await onSaved");
		expect(editor).toMatch(/await onSaved\(base, command\.operationId\);\s*return;/);
		expect(editor).not.toContain("base = created.modelSpec;");
		expect(editor).not.toContain("createDimensionDefinition");
		expect(editor).not.toContain("confirmDimensionDefinition");
		expect(editor).toContain("listWarehousePlans");
		expect(editor).toContain("getWarehousePlanCategories");
		expect(editor).not.toContain("BASIC_FIELDS");
		expect(editor).not.toContain("FIELD_SEEDS");
		expect(editor).not.toContain("BackendPendingButton");
		expect(editor).not.toMatch(/财务管理|预算科目|ods_budget_execution|示例负责人/);
		expect(workspace).toContain("onSaved");
		expect(workspace).toContain("onEdit");
	});

	it("never exposes the unsupported source-table entry as a fifth ModelSpec type", () => {
		const workspace = read("./pages/DimensionalModelingWorkspace.tsx");
		expect(workspace).not.toContain('type: "source"');
		expect(workspace).toContain("逆向建模");
	});

	it("binds model fields to versioned governed metadata standards", () => {
		const editor = read("./components/ModelingEditor.tsx");
		const standardsApi = read("../../api/modelingStandardsApi.ts");

		expect(editor).toContain("listModelFieldStandardOptions");
		expect(editor).toContain("standardElementId");
		expect(editor).toContain("standardElementVersion");
		expect(editor).not.toContain("STD_ENTITY_ID");
		expect(standardsApi).toContain("listMetadataStandardsRequest");
		expect(standardsApi).toContain("version > 0");
	});
});
