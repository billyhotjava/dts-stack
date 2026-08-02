import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (relative: string) => readFileSync(new URL(relative, import.meta.url), "utf8");
const PAGE = read("../pages/StandardsWorkspace.tsx");
const DIALOGS = read("./StandardsDialogs.tsx");
const API = read("../../../api/modelingStandardsApi.ts");

describe("data-modeling standards real capability contract", () => {
	it("removes sample truth and pending-only controls", () => {
		expect(PAGE).not.toMatch(/standardsViews|BackendPendingButton|UiStageNotice|界面示例|STD_ENTITY_ID/);
		expect(PAGE).toMatch(/loadStandardsCatalog/);
		expect(PAGE).toMatch(/archiveStandardsRow/);
	});

	it("exposes the required user-visible states and real handlers", () => {
		for (const state of ["loading", "loadError", "successMessage", "canManage", "重新加载", "暂无真实数据"]) {
			expect(PAGE).toContain(state);
		}
		expect(PAGE).toMatch(/onClick=\{\(\) => openEditor\(\)\}/);
		expect(PAGE).toMatch(/onClick=\{\(\) => setImportOpen\(true\)\}/);
		expect(PAGE).toMatch(/disabledReason/);
	});

	it("uses the existing canonical owners for CRUD, references, and package preview/apply", () => {
		for (const apiName of [
			"listStandards",
			"listMetadataStandards",
			"listReferenceCodes",
			"listGlossaryTerms",
			"listMeasurementUnits",
			"previewStandardPackageImport",
			"applyStandardPackageImport",
		]) {
			expect(API).toContain(apiName);
		}
		expect(DIALOGS).toMatch(/previewPackage/);
		expect(DIALOGS).toMatch(/applyPackage/);
		expect(DIALOGS).toMatch(/失败原因/);
	});
});
