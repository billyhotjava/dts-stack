// @vitest-environment jsdom

import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { listAllEnabledCatalogTags, listTagCategories } = vi.hoisted(() => ({
	listAllEnabledCatalogTags: vi.fn(),
	listTagCategories: vi.fn(),
}));

vi.mock("@/api/catalogTagsApi", () => ({
	listAllEnabledCatalogTags,
	listTagCategories,
}));

vi.mock("antd", async () => {
	const React = await import("react");
	const flatten = (options: any[] = []): any[] =>
		options.flatMap((option) => (Array.isArray(option.options) ? flatten(option.options) : [option]));
	return {
		Button: ({ children, onClick, disabled, ...props }: any) =>
			React.createElement("button", { type: "button", onClick, disabled, ...props }, children),
		Select: ({ options, value, onChange, "aria-label": ariaLabel }: any) =>
			React.createElement(
				"select",
				{
					multiple: true,
					value,
					"aria-label": ariaLabel,
					onChange: (event: any) =>
						onChange(Array.from(event.currentTarget.selectedOptions).map((option: any) => option.value)),
				},
				flatten(options).map((option) =>
					React.createElement("option", { key: option.value, value: option.value }, option.label),
				),
			),
		Spin: ({ children }: any) => React.createElement("div", null, children),
	};
});

async function renderAndFlush(ui: ReactElement): Promise<{ container: HTMLElement; unmount: () => void }> {
	const container = document.createElement("div");
	document.body.appendChild(container);
	let root!: Root;
	await act(async () => {
		root = createRoot(container);
		root.render(ui);
	});
	await act(async () => {
		await Promise.resolve();
		await Promise.resolve();
	});
	return {
		container,
		unmount: () => {
			act(() => root.unmount());
			container.remove();
		},
	};
}

describe("AssetTagFilter", () => {
	beforeEach(() => {
		listTagCategories.mockResolvedValue([
			{
				id: "category-1",
				code: "BUSINESS",
				name: "业务域",
				sortOrder: 0,
				builtin: true,
				enabled: true,
				tagCount: 2,
				children: [],
			},
		]);
		listAllEnabledCatalogTags.mockResolvedValue([
			{
				id: "tag-1",
				categoryId: "category-1",
				code: "BUSINESS-FINANCE",
				name: "财务",
				builtin: true,
				enabled: true,
				usageCount: 3,
			},
			{
				id: "tag-disabled",
				categoryId: "category-1",
				code: "OLD",
				name: "已停用",
				builtin: false,
				enabled: false,
				usageCount: 0,
			},
		]);
	});

	afterEach(() => {
		vi.clearAllMocks();
		document.body.innerHTML = "";
	});

	it("loads enabled catalog tags and states the AND semantics", async () => {
		const { AssetTagFilter } = await import("./AssetTagFilter");
		const { container, unmount } = await renderAndFlush(<AssetTagFilter value={["tag-1"]} onChange={() => {}} />);

		expect(container.textContent).toContain("同时包含所选标签");
		expect(listAllEnabledCatalogTags).toHaveBeenCalledTimes(1);
		const options = Array.from(container.querySelectorAll("option")).map((option) => option.textContent);
		expect(options).toContain("财务");
		expect(options).not.toContain("已停用");
		unmount();
	});

	it("is controlled and clears every selected tag in one action", async () => {
		const onChange = vi.fn();
		const { AssetTagFilter } = await import("./AssetTagFilter");
		const { container, unmount } = await renderAndFlush(<AssetTagFilter value={["tag-1"]} onChange={onChange} />);

		const clear = container.querySelector("button[aria-label='清除标签筛选']") as HTMLButtonElement;
		act(() => clear.click());
		expect(onChange).toHaveBeenCalledWith([]);
		unmount();
	});
});
