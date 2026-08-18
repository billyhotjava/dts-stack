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
		const dialogs = read("../pages/data-modeling/prototype/ModelWorkbenchDialog.tsx");
		const editor = read("../pages/data-modeling/prototype/AdvancedDbtWorkspace.tsx");

		expect(workbench).toContain("useDataModelingMenuGrant");
		expect(workbench).toMatch(/<AdvancedDbtWorkspace[\s\S]*canMaintain=\{canMaintain\}/);
		expect(workbench).toContain("onDirtyChange={setAdvancedDbtDirty}");
		expect(workbench).toContain("const unsavedChanges = dirty || advancedDbtDirty");
		expect(workbench).toContain('import { AdvancedDbtWorkspace } from "./AdvancedDbtWorkspace"');
		// Sprint-91：高级 dbt 工作区的挂载条件由 dialog 状态改为 URL 的 view=code 模式。
		expect(workbench).toMatch(/requestedView === "code" && selectedModel \? \(\s*<AdvancedDbtWorkspace/s);
		expect(dialogs).not.toContain("AdvancedDbtDialog");
		// 原断言禁止任何 <Modal>，本意是“工作区是内联页面而不是弹窗”。
		// Sprint-91 用 TransitionConfirmation(Modal) 取代了 window.confirm，故改为表达真实意图：
		// 工作区本体内联渲染，Modal 只用于接管确认。
		expect(editor).toMatch(/<section aria-label="高级 dbt 工作区"/);
		expect(editor.match(/<Modal/g) ?? []).toHaveLength(1);
		expect(editor).toContain("TransitionConfirmation");
		expect(editor).toContain("返回模型设计");
		expect(editor).toContain('representationScope: "TECHNICAL"');
		expect(editor).toContain("if (!canMaintain)");
		expect(editor).toContain('includes("OPEN_ADVANCED_DBT")');
		expect(editor).toContain("dirty");
		expect(editor).toContain("conflict");
		expect(editor).toContain("validateDbtImplementationDraft");
		expect(editor).toContain("commitDbtImplementationDraft");
		expect(editor).toContain("dependencyChecksum: validation.dependencyValidation?.dependencyChecksum");
		expect(editor).toContain("isManagedDependencyPath");
		expect(editor).toContain("系统依赖");
		expect(editor).toContain("created.sourceBundle?.files");
		expect(editor).not.toContain("initialFiles");
		expect(editor).not.toContain('path: "dbt_project.yml"');
		expect(editor).not.toContain("model-paths: [models]");
		expect(`${workbench}\n${dialogs}\n${editor}`).not.toContain("/api/etl/dbt/files");
	});
});
