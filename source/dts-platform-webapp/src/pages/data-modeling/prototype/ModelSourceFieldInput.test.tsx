// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeAll, expect, it, vi } from "vitest";
import type { ModelInputFieldSource } from "@/api/modelInputInspectionApi";
import { ModelSourceFieldInput } from "./useModelSourceFields";

let root: Root;
let container: HTMLDivElement;
beforeAll(() => {
	(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
});
afterEach(() => {
	act(() => root.unmount());
	container.remove();
});
const sources = (state: ModelInputFieldSource["schemaState"]): ModelInputFieldSource[] => [
	{
		index: 0,
		input: null,
		alias: "src_0",
		schemaState: state,
		fields: [
			{ name: "project_code", dataType: "text", nullable: false },
			{ name: "task_code", dataType: "text", nullable: false },
		],
	},
];
const mount = (value: string, state: ModelInputFieldSource["schemaState"], onChange = vi.fn()) => {
	container = document.createElement("div");
	document.body.append(container);
	root = createRoot(container);
	act(() =>
		root.render(
			<ModelSourceFieldInput
				label="来源字段 project_code"
				value={value}
				onChange={onChange}
				sources={sources(state)}
				labels={["上游"]}
				selectWhenResolved
			/>,
		),
	);
	return onChange;
};

it("offers every resolved upstream field as a dropdown and keeps a bare same-name mapping selected", () => {
	const onChange = mount("project_code", "RESOLVED");
	const select = container.querySelector<HTMLSelectElement>('select[aria-label="来源字段 project_code"]');

	expect(select).not.toBeNull();
	expect(Array.from(select!.options).map((option) => option.value)).toEqual([
		"",
		"src_0.project_code",
		"src_0.task_code",
	]);
	expect(select!.value).toBe("src_0.project_code");
	act(() => {
		select!.value = "src_0.task_code";
		select!.dispatchEvent(new Event("change", { bubbles: true }));
	});
	expect(onChange).toHaveBeenCalledWith("src_0.task_code");
});

it("keeps an out-of-directory value visible instead of silently dropping it", () => {
	mount("src_0.removed_field", "RESOLVED");
	const select = container.querySelector<HTMLSelectElement>("select");
	expect(select!.value).toBe("src_0.removed_field");
	expect(container.textContent).toContain("字段不在当前来源目录中");
});

it("falls back to free input while the directory is unresolved", () => {
	mount("project_code", "UNAVAILABLE");
	expect(container.querySelector("select")).toBeNull();
	expect(container.querySelector<HTMLInputElement>('input[aria-label="来源字段 project_code"]')?.value).toBe(
		"project_code",
	);
});
