import { describe, expect, it } from "vitest";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { resolveWorkbenchEditorAccess } from "./workbenchEditorAccess";
import type { ConceptDimensionDraft, ModelSpecDraft } from "./services/modelWorkbenchService";

const modelDraft = (status: string, compatibilityMode = "CANONICAL") =>
	({
		createKind: "dimension-table",
		base: { status, compatibilityMode } as ModelSpecView,
	} as ModelSpecDraft);

const conceptDraft = (status: string) =>
	({
		createKind: "dimension",
		definitionBase: { status } as DimensionDefinitionView,
	} as ConceptDimensionDraft);

describe("workbench editor access", () => {
	it("opens new and persisted DRAFT objects directly in edit mode", () => {
		expect(resolveWorkbenchEditorAccess(true, { createKind: "dimension-table", base: null } as ModelSpecDraft)).toMatchObject({
			mode: "CREATE_DRAFT",
			readOnly: false,
		});
		expect(resolveWorkbenchEditorAccess(true, modelDraft("DRAFT"))).toMatchObject({
			mode: "EDIT_DRAFT",
			readOnly: false,
		});
		expect(resolveWorkbenchEditorAccess(true, conceptDraft("DRAFT"))).toMatchObject({
			mode: "EDIT_DRAFT",
			readOnly: false,
		});
	});

	it("explains every read-only state instead of silently disabling the editor", () => {
		expect(resolveWorkbenchEditorAccess(false, modelDraft("DRAFT"))).toMatchObject({
			mode: "NO_PERMISSION",
			readOnly: true,
			message: expect.stringContaining("查看权限"),
		});
		expect(resolveWorkbenchEditorAccess(true, modelDraft("PUBLISHED"))).toMatchObject({
			mode: "VIEW_VERSION",
			readOnly: true,
			message: expect.stringContaining("创建新草稿版本"),
		});
		expect(resolveWorkbenchEditorAccess(true, modelDraft("DRAFT", "LEGACY_READONLY"))).toMatchObject({
			mode: "LEGACY_READONLY",
			readOnly: true,
		});
		expect(resolveWorkbenchEditorAccess(true, conceptDraft("RETIRED"))).toMatchObject({
			mode: "VIEW_VERSION",
			readOnly: true,
			message: expect.stringContaining("已退役"),
		});
	});

	it("lets maintainers revise a CURRENT dimension while preserving immutable historical revisions", () => {
		expect(resolveWorkbenchEditorAccess(true, conceptDraft("CURRENT"))).toMatchObject({
			mode: "EDIT_DRAFT",
			readOnly: false,
			message: expect.stringContaining("当前有效修订"),
		});
	});
});
