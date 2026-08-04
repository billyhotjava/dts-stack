// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ModelSpecField } from "@/features/modeling/contracts/modelSpecV2Contract";
import { ModelFieldEditorTable, dimensionNonNullPatch, dimensionPrimaryKeyPatch, isBlankModelField } from "./ModelFieldEditorTable";

let container: HTMLDivElement;
let root: Root;

const field: ModelSpecField = {
	name: "subject_code",
	displayName: "科目编码",
	dataType: "STRING",
	nullable: true,
	role: "ATTRIBUTE",
	dimensionAttributeCode: "subjectCode",
};

const props = () => ({
	fields: [field],
	bindings: [],
	standards: [{ id: "standard-1", code: "SUBJECT_CODE", name: "科目编码", dataType: "STRING", version: 1 }],
	fieldRowIds: ["field-1"],
	dimensionMode: true,
	readOnly: false,
	onAddFields: vi.fn(),
	onRemoveBlankFields: vi.fn(),
	onUpdate: vi.fn(),
	onDelete: vi.fn(),
	onStandardChange: vi.fn(),
	canAssociate: true,
	canOpenCode: true,
	onOpenCode: vi.fn(),
	onOpenAssociation: vi.fn(),
});

async function render(nextProps = props()) {
	await act(async () => root.render(<ModelFieldEditorTable {...nextProps} />));
	return nextProps;
}

function click(label: string) {
	const button = Array.from(container.querySelectorAll("button")).find((item) => item.textContent?.includes(label));
	expect(button).toBeDefined();
	act(() => button?.dispatchEvent(new MouseEvent("click", { bubbles: true })));
}

function changeCheckbox(index: number) {
	const checkbox = container.querySelectorAll<HTMLInputElement>('input[type="checkbox"]')[index];
	expect(checkbox).toBeDefined();
	act(() => checkbox.click());
}

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});

afterEach(async () => {
	await act(async () => root.unmount());
	container.remove();
});

describe("ModelFieldEditorTable", () => {
	it("builds dimension key and non-null patches without changing other roles", () => {
		expect(dimensionPrimaryKeyPatch(true)).toEqual({ role: "KEY", nullable: false });
		expect(dimensionPrimaryKeyPatch(false)).toEqual({ role: "ATTRIBUTE" });
		expect(dimensionNonNullPatch(true)).toEqual({ nullable: false });
		expect(dimensionNonNullPatch(false)).toEqual({ nullable: true });
	});

	it("recognizes a field with no name or display name as blank", () => {
		expect(isBlankModelField({ name: " ", displayName: "", dataType: "STRING" } as ModelSpecField)).toBe(true);
	});

	it("renders only the approved default dimension columns and applies checkbox patches", async () => {
		const nextProps = await render();
		const headers = Array.from(container.querySelectorAll("th")).map((header) => header.textContent);
		expect(headers).toEqual(["序号", "字段名称", "类型", "字段显示名", "主键", "非空", "维度属性编码", "操作"]);
		expect(container.textContent).not.toContain("字段作用");
		expect(container.textContent).not.toContain("字段标准");
		expect(container.textContent).not.toContain("允许为空");
		expect(container.textContent).not.toContain("安全等级");

		changeCheckbox(0);
		changeCheckbox(1);
		expect(nextProps.onUpdate).toHaveBeenNthCalledWith(1, 0, { role: "KEY", nullable: false });
		expect(nextProps.onUpdate).toHaveBeenNthCalledWith(2, 0, { nullable: false });
	});

	it("reveals field standards only through display settings", async () => {
		await render();
		expect(container.textContent).not.toContain("字段标准");
		click("字段显示设置");
		expect(container.textContent).toContain("字段标准");
	});

	it("opens code and persisted-model association, while import remains contract-disabled", async () => {
		const nextProps = await render();
		click("代码模式");
		click("字段关联");
		expect(nextProps.onOpenCode).toHaveBeenCalledOnce();
		expect(nextProps.onOpenAssociation).toHaveBeenCalledOnce();
		const importButton = Array.from(container.querySelectorAll("button")).find((item) => item.textContent?.includes("从表/视图导入"));
		expect(importButton).toHaveProperty("disabled", true);
		expect(importButton?.getAttribute("title")).toBe("当前版本尚无字段级表结构导入契约");
	});
});
