import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const source = (name: string) => readFileSync(resolve(__dirname, name), "utf8");

describe("Sprint-91 dual-mode source contracts", () => {
	it("keeps one canonical view query and removes the duplicate advanced toolbar entry", () => {
		const page = source("ModelingWorkbenchPage.tsx");
		const editor = source("ModelingWorkbenchEditor.tsx");
		expect(page).toContain('params.set("view", view)');
		expect(page).toContain('params.delete("open")');
		expect(editor).toContain("可视化模式");
		expect(editor).toContain("代码模式");
		expect(editor).not.toContain("高级 dbt 工作区");
		expect(editor).toContain("showCodeAction={false}");
		expect(source("ModelFieldEditorTable.tsx")).toContain("showCodeAction ?");
	});

	it("keeps Monaco lazy and avoids Chrome-95-incompatible worker and CSS features", () => {
		const workspace = source("AdvancedDbtWorkspace.tsx");
		const editor = source("DbtCodeEditor.tsx");
		expect(workspace).toContain("lazy(() => import(\"./DbtCodeEditor\")");
		expect(workspace).toContain("<Suspense");
		expect(`${workspace}\n${editor}`).not.toMatch(/MonacoEnvironment|getWorker|:has\(|container-type|\bdvh\b|\bsvh\b|toSorted/);
	});

	it("renders generated code as read-only and requires explicit takeover confirmation", () => {
		const workspace = source("AdvancedDbtWorkspace.tsx");
		expect(workspace).toContain("不会自动保存或转换");
		expect(workspace).toContain("接管代码实现");
		expect(workspace).toContain("TransitionConfirmation");
		expect(workspace).toContain("onTransitionSuccess?.(result)");
		expect(workspace).toContain("createDbtImplementationDraft(result.model.id");
		expect(workspace).toContain("系统生成的中间节点");
		expect(workspace).not.toContain("window.confirm");
	});
});
