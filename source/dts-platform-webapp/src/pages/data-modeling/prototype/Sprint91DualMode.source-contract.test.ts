import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const source = (name: string) => readFileSync(resolve(__dirname, name), "utf8");

describe("Sprint-92 unified authoring source contracts", () => {
	it("keeps one canonical view query and removes the duplicate advanced toolbar entry", () => {
		const page = source("ModelingWorkbenchPage.tsx");
		const editor = source("ModelingWorkbenchEditor.tsx");
		const workspace = source("AdvancedDbtWorkspace.tsx");
		expect(page).toContain('params.set("view", view)');
		expect(page).toContain('params.delete("open")');
		expect(page.indexOf("▤")).toBeLessThan(page.indexOf("返回模型列表"));
		expect(page).toContain('className="dmx-editor-tab__back dmx-table-action"');
		expect(editor).toContain("可视化模式");
		expect(editor).toContain("代码模式");
		expect(workspace).toContain("返回可视化模式");
		expect(workspace).toContain("onClick={onBack}");
		expect(editor).not.toContain("高级 dbt 工作区");
		expect(editor).toContain("showCodeAction={false}");
		expect(source("ModelFieldEditorTable.tsx")).toContain("showCodeAction ?");
	});

	it("keeps Monaco lazy and avoids Chrome-95-incompatible worker and CSS features", () => {
		const workspace = source("AdvancedDbtWorkspace.tsx");
		const editor = source("DbtCodeEditor.tsx");
		expect(workspace).toContain('lazy(() => import("./DbtCodeEditor")');
		expect(workspace).toContain("<Suspense");
		expect(`${workspace}\n${editor}`).not.toMatch(
			/MonacoEnvironment|getWorker|:has\(|container-type|\bdvh\b|\bsvh\b|toSorted/,
		);
	});

	it("treats provenance as evidence and removes ownership takeover", () => {
		const workspace = source("AdvancedDbtWorkspace.tsx");
		const editor = source("ModelingWorkbenchEditor.tsx");
		expect(workspace).toContain("来源只用于追溯，不限制编辑方式");
		expect(workspace).toContain('includes("EDIT_IMPLEMENTATION")');
		expect(editor).toContain("创建新草稿版本");
		expect(editor).toContain("原始代码节点");
		expect(`${workspace}\n${editor}`).not.toContain("接管代码实现");
		expect(`${workspace}\n${editor}`).not.toContain("当前由代码维护");
		expect(workspace).not.toContain("window.confirm");
	});
});
