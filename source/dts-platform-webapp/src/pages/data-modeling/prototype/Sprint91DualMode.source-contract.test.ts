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
		const workflow = source("ModelWorkflowToolbar.tsx");
		const workflowStyles = source("modeling-workbench.css");
		expect(workspace).toContain('lazy(() => import("./DbtCodeEditor")');
		expect(workspace).toContain("<Suspense");
		expect(`${workspace}\n${editor}\n${workflow}\n${workflowStyles}`).not.toMatch(
			/MonacoEnvironment|getWorker|:has\(|container-type|\bdvh\b|\bsvh\b|toSorted/,
		);
	});

	it("presents one ordered model workflow and moves inspection actions out of the primary lane", () => {
		const workflow = source("ModelWorkflowToolbar.tsx");
		expect(workflow.indexOf('label: "保存草稿"')).toBeLessThan(workflow.indexOf('label: "校验"'));
		expect(workflow.indexOf('label: "校验"')).toBeLessThan(workflow.indexOf('label: "提交实现"'));
		expect(workflow.indexOf('label: "提交实现"')).toBeLessThan(workflow.indexOf('label: "发布与物化"'));
		expect(workflow).toContain("下一步：");
		expect(workflow).toContain("检查与记录");
	});

	it("treats provenance as evidence and removes ownership takeover", () => {
		const workspace = source("AdvancedDbtWorkspace.tsx");
		const editor = source("ModelingWorkbenchEditor.tsx");
		const workflow = source("ModelWorkflowToolbar.tsx");
		expect(workspace).toContain("来源只用于追溯，不限制编辑方式");
		expect(workspace).toContain('includes("EDIT_IMPLEMENTATION")');
		expect(workflow).toContain("创建新草稿版本");
		expect(editor).toContain("原始代码节点");
		expect(`${workspace}\n${editor}\n${workflow}`).not.toContain("接管代码实现");
		expect(`${workspace}\n${editor}\n${workflow}`).not.toContain("当前由代码维护");
		expect(workspace).not.toContain("window.confirm");
	});

	it("persists an open draft before its first validation", () => {
		const session = source("useModelAuthoringSession.ts");
		expect(session).toContain("!open || shouldPersistBeforeAuthoringValidation(open.state, dirty, codeDirty)");
	});

	it("reloads the authoring session explicitly and keeps request failures in one display lane", () => {
		const page = source("ModelingWorkbenchPage.tsx");
		const session = source("useModelAuthoringSession.ts");
		const editor = source("ModelingWorkbenchEditor.tsx");
		expect(session).toContain("const reload = useCallback");
		expect(session).toContain("reload,");
		expect(page).toContain("await reloadAuthoring(selectedModelId)");
		expect(session).not.toContain("setFailure(normalized)");
		expect(editor).toContain("const displayedFailure =");
		expect(editor).toContain("authoringFailure ||");
		expect(editor).toContain("authoringConflict ?");
	});

	it("uses the server-frozen dbt project identity for visual authoring", () => {
		const session = source("useModelAuthoringSession.ts");
		expect(session).toContain("session.draft.sourceBundle?.projectKey");
	});

	it("lets fact authors bind time semantics from the current model fields", () => {
		const bindingFields = source("ModelImplementationBindingFields.tsx");
		expect(bindingFields).toContain("applyFactTimeFieldSelection(draft, fieldIndex, checked)");
		expect(bindingFields).not.toContain('draft.fields.filter((field) => field.role === "TIME")');
	});

	it("uses one atomic first-save command and server-owned execution capabilities", () => {
		const service = source("services/modelWorkbenchService.ts");
		const executionFields = source("ModelImplementationExecutionFields.tsx");
		expect(service).toContain("saveModelDraftOperation(operation)");
		expect(service).toContain("getModelImplementationCapabilities()");
		expect(executionFields).toContain("capabilities.materializationsByLoadStrategy");
		expect(executionFields).not.toContain('value="SNAPSHOT"');
		expect(executionFields).not.toContain('value="ephemeral"');
	});

	it("keeps release evidence customer-facing while retaining the authoritative error code", () => {
		const release = source("ModelReleaseWorkflowPanel.tsx");
		expect(release).toContain('BUILD_RUN: "物化构建"');
		expect(release).toContain("错误码 ${item.code}");
		expect(release).not.toContain("item.message || item.code || item.state");
	});
});
