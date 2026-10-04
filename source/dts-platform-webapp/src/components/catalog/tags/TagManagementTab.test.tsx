// @vitest-environment jsdom

import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const {
	createTagCategory,
	updateTagCategory,
	listTagCategories,
	listCatalogTags,
	deleteCatalogTag,
	deleteTagCategory,
	formValidateFields,
	modalConfirm,
	toastError,
} = vi.hoisted(() => ({
	createTagCategory: vi.fn(),
	updateTagCategory: vi.fn(),
	listTagCategories: vi.fn(),
	listCatalogTags: vi.fn(),
	deleteCatalogTag: vi.fn(),
	deleteTagCategory: vi.fn(),
	formValidateFields: vi.fn(),
	modalConfirm: vi.fn(),
	toastError: vi.fn(),
}));

vi.mock("@/api/catalogTagsApi", () => ({
	createCatalogTag: vi.fn(),
	createTagCategory,
	deleteCatalogTag,
	deleteTagCategory,
	installBuiltinCatalogTags: vi.fn(),
	listCatalogTags,
	listTagCategories,
	updateCatalogTag: vi.fn(),
	updateTagCategory,
}));

vi.mock("sonner", () => ({
	toast: { error: toastError, success: vi.fn() },
}));

vi.mock("@/components/table", async (importOriginal) => {
	const React = await import("react");
	// 只桩掉 CompactTable（用于断言分页交互），actionColumn / RowActions 用真实实现，
	// 这样行内操作按钮的统一渲染同样受本测试保护。
	const actual = await importOriginal<typeof import("@/components/table")>();
	return {
		...actual,
		CompactTable: ({ dataSource = [], columns = [], pagination }: any) =>
			React.createElement(
				"div",
				{ "data-page-size": pagination?.pageSize },
				...dataSource.map((row: any) =>
					React.createElement(
						"div",
						{ key: row.id, "data-row-id": row.id },
						...columns.map((column: any, index: number) =>
							React.createElement(
								"span",
								{ key: column.key || column.dataIndex || index },
								column.render
									? column.render(column.dataIndex ? row[column.dataIndex] : undefined, row)
									: row[column.dataIndex],
							),
						),
					),
				),
				React.createElement(
					"button",
					{
						type: "button",
						"aria-label": "测试切换每页条数",
						onClick: () => pagination?.onChange?.(2, 20),
					},
					"20 条/页",
				),
				React.createElement(
					"button",
					{
						type: "button",
						"aria-label": "测试切换第二页",
						onClick: () => pagination?.onChange?.(2, pagination?.pageSize),
					},
					"第 2 页",
				),
			),
	};
});

vi.mock("antd", async () => {
	const React = await import("react");
	const Button = ({ children, onClick, disabled, ...props }: any) =>
		React.createElement("button", { type: "button", onClick, disabled, ...props }, children);
	const Modal = ({ open, children, title, onOk }: any) =>
		open
			? React.createElement(
					"section",
					{ role: "dialog" },
					React.createElement("h3", null, title),
					children,
					React.createElement("button", { type: "button", "aria-label": `保存${title}`, onClick: onOk }, "保存"),
				)
			: null;
	Modal.confirm = modalConfirm;
	const Form: any = ({ children }: any) => React.createElement("form", null, children);
	Form.Item = ({ children, label, extra }: any) =>
		React.createElement("label", null, label, children, extra ? React.createElement("small", null, extra) : null);
	Form.useForm = () => [
		{
			resetFields: vi.fn(),
			setFieldsValue: vi.fn(),
			validateFields: formValidateFields,
		},
	];
	const Input: any = (props: any) => React.createElement("input", props);
	Input.Search = (props: any) => React.createElement("input", props);
	Input.TextArea = (props: any) => React.createElement("textarea", props);
	const renderTreeButtons = (nodes: any[], onSelect: (keys: string[]) => void): ReactElement[] =>
		nodes.flatMap((node: any) => [
			React.createElement(
				"button",
				{
					type: "button",
					key: node.key,
					"data-tree-key": node.key,
					onClick: () => onSelect?.([node.key]),
				},
				node.title,
			),
			...renderTreeButtons(node.children || [], onSelect),
		]);
	const renderTreeSelectOptions = (nodes: any[]): ReactElement[] =>
		nodes.flatMap((node: any) => [
			React.createElement("option", { key: node.value, value: node.value }, node.title),
			...renderTreeSelectOptions(node.children || []),
		]);
	return {
		Alert: ({ message, description }: any) => React.createElement("div", { role: "status" }, message, description),
		Button,
		Card: ({ children, title, extra }: any) =>
			React.createElement("section", null, React.createElement("h3", null, title), extra, children),
		Form,
		Input,
		Modal,
		Select: (props: any) => React.createElement("select", props),
		Space: ({ children }: any) => React.createElement("div", null, children),
		Spin: ({ children }: any) => React.createElement("div", null, children),
		Switch: (props: any) => React.createElement("input", { type: "checkbox", ...props }),
		Tag: ({ children }: any) => React.createElement("span", null, children),
		Tooltip: ({ children, title }: any) =>
			React.createElement("span", { "data-tooltip": title || undefined }, children),
		Tree: ({ treeData = [], onSelect }: any) =>
			React.createElement("div", null, ...renderTreeButtons(treeData, onSelect)),
		TreeSelect: ({ treeData = [], disabled, placeholder }: any) =>
			React.createElement(
				"select",
				{
					"aria-label": "上级分类",
					"data-tree-select": "category-parent",
					disabled,
				},
				React.createElement("option", { value: "" }, placeholder),
				...renderTreeSelectOptions(treeData),
			),
	};
});

const category = {
	id: "category-1",
	code: "BUSINESS",
	name: "业务域",
	parentId: null,
	sortOrder: 0,
	builtin: false,
	enabled: true,
	description: "业务域标签",
	tagCount: 1,
	children: [],
};
const financeTag = {
	id: "tag-finance",
	categoryId: category.id,
	code: "BUSINESS-FINANCE",
	name: "财务",
	color: "#1677ff",
	builtin: false,
	enabled: true,
	description: "财务主题资产",
	usageCount: 3,
};
const builtinTag = {
	...financeTag,
	id: "tag-builtin",
	code: "BUSINESS-CORE",
	name: "核心数据",
	builtin: true,
	usageCount: 1,
};
const builtinCategory = {
	...category,
	id: "category-builtin",
	code: "BUILTIN",
	name: "预置业务域",
	builtin: true,
};
const grandchildCategory = {
	...category,
	id: "category-grandchild",
	code: "GRANDCHILD",
	name: "孙级分类",
	parentId: "category-child",
	tagCount: 0,
	children: [],
};
const childCategory = {
	...category,
	id: "category-child",
	code: "CHILD",
	name: "子级分类",
	parentId: category.id,
	tagCount: 0,
	children: [grandchildCategory],
};
const nestedCategory = {
	...category,
	children: [childCategory],
};
const peerCategory = {
	...category,
	id: "category-peer",
	code: "PEER",
	name: "同级分类",
	tagCount: 0,
	children: [],
};

async function renderAndFlush(ui: ReactElement): Promise<{ container: HTMLElement; unmount: () => void }> {
	const container = document.createElement("div");
	document.body.appendChild(container);
	let root!: Root;
	await act(async () => {
		root = createRoot(container);
		root.render(ui);
	});
	await flush();
	return {
		container,
		unmount: () => {
			act(() => root.unmount());
			container.remove();
		},
	};
}

async function flush() {
	await act(async () => {
		await Promise.resolve();
		await Promise.resolve();
		await Promise.resolve();
	});
}

describe("TagManagementTab", () => {
	beforeEach(() => {
		(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
		listTagCategories.mockResolvedValue([category]);
		listCatalogTags.mockResolvedValue({
			content: [financeTag],
			total: 1,
			page: 0,
			size: 10,
		});
		deleteCatalogTag.mockResolvedValue(true);
		deleteTagCategory.mockResolvedValue(true);
		createTagCategory.mockResolvedValue({ ...category, id: "category-created" });
		updateTagCategory.mockResolvedValue(category);
		formValidateFields.mockResolvedValue({});
	});

	afterEach(() => {
		(globalThis as any).IS_REACT_ACT_ENVIRONMENT = false;
		vi.clearAllMocks();
		document.body.innerHTML = "";
	});

	it("loads ten rows by default and returns to page one when page size changes", async () => {
		const { TagManagementTab } = await import("./TagManagementTab");
		const { container, unmount } = await renderAndFlush(<TagManagementTab canManage />);

		expect(listCatalogTags).toHaveBeenCalledWith({
			categoryId: category.id,
			keyword: undefined,
			page: 0,
			size: 10,
		});
		expect(container.querySelector("[data-page-size='10']")).not.toBeNull();
		const sizeButton = container.querySelector("button[aria-label='测试切换每页条数']") as HTMLButtonElement;
		await act(async () => sizeButton.click());
		await flush();
		expect(listCatalogTags).toHaveBeenLastCalledWith({
			categoryId: category.id,
			keyword: undefined,
			page: 0,
			size: 20,
		});
		unmount();
	});

	it("returns to page one when the keyword changes", async () => {
		const { TagManagementTab } = await import("./TagManagementTab");
		const { container, unmount } = await renderAndFlush(<TagManagementTab canManage />);

		const secondPageButton = container.querySelector("button[aria-label='测试切换第二页']") as HTMLButtonElement;
		await act(async () => secondPageButton.click());
		await flush();
		expect(listCatalogTags).toHaveBeenLastCalledWith({
			categoryId: category.id,
			keyword: undefined,
			page: 1,
			size: 10,
		});

		const search = container.querySelector("input[placeholder='按标签名称或编码查询']") as HTMLInputElement;
		const setNativeValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set;
		await act(async () => {
			setNativeValue?.call(search, "财务");
			search.dispatchEvent(new Event("input", { bubbles: true }));
		});
		await flush();
		expect(listCatalogTags).toHaveBeenLastCalledWith({
			categoryId: category.id,
			keyword: "财务",
			page: 0,
			size: 10,
		});
		unmount();
	});

	it("keeps the newest tag page when an older request resolves last", async () => {
		let resolveFirst!: (value: unknown) => void;
		let resolveSecond!: (value: unknown) => void;
		listCatalogTags
			.mockReset()
			.mockImplementationOnce(
				() =>
					new Promise((resolve) => {
						resolveFirst = resolve;
					}),
			)
			.mockImplementationOnce(
				() =>
					new Promise((resolve) => {
						resolveSecond = resolve;
					}),
			);
		const { TagManagementTab } = await import("./TagManagementTab");
		const { container, unmount } = await renderAndFlush(<TagManagementTab canManage />);

		const sizeButton = container.querySelector("button[aria-label='测试切换每页条数']") as HTMLButtonElement;
		await act(async () => sizeButton.click());
		await flush();
		await act(async () => {
			resolveSecond({
				content: [{ ...financeTag, id: "tag-new", name: "新请求标签" }],
				total: 1,
				page: 0,
				size: 20,
			});
		});
		await flush();
		await act(async () => {
			resolveFirst({
				content: [{ ...financeTag, id: "tag-old", name: "旧请求标签" }],
				total: 1,
				page: 0,
				size: 10,
			});
		});
		await flush();

		expect(container.textContent).toContain("新请求标签");
		expect(container.textContent).not.toContain("旧请求标签");
		unmount();
	});

	it("renders nested categories and loads tags for a deeply nested selection", async () => {
		listTagCategories.mockResolvedValue([nestedCategory, peerCategory]);
		const { TagManagementTab } = await import("./TagManagementTab");
		const { container, unmount } = await renderAndFlush(<TagManagementTab canManage />);

		expect(container.querySelector("button[data-tree-key='category-child']")).not.toBeNull();
		const grandchild = container.querySelector("button[data-tree-key='category-grandchild']") as HTMLButtonElement;
		expect(grandchild).not.toBeNull();
		await act(async () => grandchild.click());
		await flush();
		expect(listCatalogTags).toHaveBeenLastCalledWith({
			categoryId: grandchildCategory.id,
			keyword: undefined,
			page: 0,
			size: 10,
		});
		unmount();
	});

	it("creates a child category with the selected parent id", async () => {
		listTagCategories.mockResolvedValue([nestedCategory, peerCategory]);
		formValidateFields.mockResolvedValueOnce({
			code: "NEW_CHILD",
			name: "新增子分类",
			parentId: childCategory.id,
			sortOrder: 3,
			enabled: true,
			description: "用于验证多级分类",
		});
		const { TagManagementTab } = await import("./TagManagementTab");
		const { container, unmount } = await renderAndFlush(<TagManagementTab canManage />);

		const create = container.querySelector("button[aria-label='新建标签分类']") as HTMLButtonElement;
		await act(async () => create.click());
		await flush();
		const parentSelect = container.querySelector("select[aria-label='上级分类']") as HTMLSelectElement;
		expect(Array.from(parentSelect.options).map((option) => option.value)).toEqual(
			expect.arrayContaining([category.id, childCategory.id, grandchildCategory.id, peerCategory.id]),
		);
		const save = container.querySelector("button[aria-label='保存新建标签分类']") as HTMLButtonElement;
		await act(async () => save.click());
		await flush();
		expect(createTagCategory).toHaveBeenCalledWith({
			code: "NEW_CHILD",
			name: "新增子分类",
			parentId: childCategory.id,
			sortOrder: 3,
			enabled: true,
			description: "用于验证多级分类",
		});
		unmount();
	});

	it("excludes the edited category branch from parent choices and rejects a crafted cycle", async () => {
		listTagCategories.mockResolvedValue([nestedCategory, peerCategory]);
		const { TagManagementTab } = await import("./TagManagementTab");
		const { container, unmount } = await renderAndFlush(<TagManagementTab canManage />);

		const edit = Array.from(container.querySelectorAll("button")).find((button) =>
			button.textContent?.includes("编辑分类"),
		) as HTMLButtonElement;
		await act(async () => edit.click());
		await flush();

		const parentSelect = container.querySelector("select[aria-label='上级分类']") as HTMLSelectElement;
		const parentIds = Array.from(parentSelect.options).map((option) => option.value);
		expect(parentIds).toContain(peerCategory.id);
		expect(parentIds).not.toContain(category.id);
		expect(parentIds).not.toContain(childCategory.id);
		expect(parentIds).not.toContain(grandchildCategory.id);

		formValidateFields.mockResolvedValueOnce({
			code: category.code,
			name: category.name,
			parentId: childCategory.id,
			sortOrder: category.sortOrder,
			enabled: true,
			description: category.description,
		});
		const save = container.querySelector("button[aria-label='保存编辑标签分类']") as HTMLButtonElement;
		await act(async () => save.click());
		await flush();
		expect(updateTagCategory).not.toHaveBeenCalled();
		expect(toastError).toHaveBeenCalledWith("上级分类不能选择当前分类或其子分类");

		formValidateFields.mockResolvedValueOnce({
			code: category.code,
			name: category.name,
			parentId: peerCategory.id,
			sortOrder: category.sortOrder,
			enabled: true,
			description: category.description,
		});
		await act(async () => save.click());
		await flush();
		expect(updateTagCategory).toHaveBeenCalledWith(category.id, {
			code: category.code,
			name: category.name,
			parentId: peerCategory.id,
			sortOrder: category.sortOrder,
			enabled: true,
			description: category.description,
		});
		unmount();
	});

	it("keeps the catalog visible but removes mutation controls for read-only users", async () => {
		const { TagManagementTab } = await import("./TagManagementTab");
		const { container, unmount } = await renderAndFlush(<TagManagementTab canManage={false} />);

		expect(container.textContent).toContain("您可以查看标签目录");
		expect(container.textContent).toContain("财务");
		expect(container.querySelector("button[aria-label='新建标签']")).toBeNull();
		expect(container.querySelector("button[aria-label='删除标签 财务']")).toBeNull();
		unmount();
	});

	it("shows the usage count and requires force confirmation for an assigned tag", async () => {
		const { TagManagementTab } = await import("./TagManagementTab");
		const { container, unmount } = await renderAndFlush(<TagManagementTab canManage />);

		expect(container.textContent).toContain("3 个资产");
		const remove = container.querySelector("button[aria-label='删除标签 财务']") as HTMLButtonElement;
		act(() => remove.click());
		expect(modalConfirm).toHaveBeenCalledTimes(1);
		const options = modalConfirm.mock.calls[0][0];
		expect(String(options.content)).toContain("3");
		await options.onOk();
		expect(deleteCatalogTag).toHaveBeenCalledWith(financeTag.id, true);
		unmount();
	});

	it("protects builtin definitions and exposes a visual color palette", async () => {
		listCatalogTags.mockResolvedValue({
			content: [builtinTag],
			total: 1,
			page: 0,
			size: 10,
		});
		const { TagManagementTab } = await import("./TagManagementTab");
		const { container, unmount } = await renderAndFlush(<TagManagementTab canManage />);

		const remove = container.querySelector("button[aria-label='删除标签 核心数据']") as HTMLButtonElement;
		expect(remove).not.toBeNull();
		expect(remove.disabled).toBe(true);
		expect(remove.title).toContain("预置标签由系统维护");
		expect(remove.closest("[data-tooltip]")?.getAttribute("data-tooltip")).toContain("预置标签由系统维护");
		const edit = container.querySelector("button[aria-label='编辑标签 核心数据']") as HTMLButtonElement;
		await act(async () => edit.click());
		await flush();
		const nameLabel = Array.from(container.querySelectorAll("label")).find((node) =>
			node.textContent?.includes("标签名称"),
		);
		const descriptionLabel = Array.from(container.querySelectorAll("label")).find((node) =>
			node.textContent?.includes("业务说明"),
		);
		expect(nameLabel?.querySelector("input")?.disabled).toBe(true);
		expect(descriptionLabel?.querySelector("textarea")?.disabled).toBe(true);
		expect(container.querySelector("[role='radiogroup'][aria-label='显示颜色调色板']")).not.toBeNull();
		expect(container.querySelectorAll("input[type='radio'][name='tag-display-color']")).toHaveLength(8);
		expect(container.textContent).not.toContain("#1677ff");
		expect(container.querySelector("input[type='color']")).toBeNull();
		unmount();
	});

	it("selects a palette color without exposing its hexadecimal value", async () => {
		const onChange = vi.fn();
		const { TagColorPalette } = await import("./TagManagementTab");
		const { container, unmount } = await renderAndFlush(<TagColorPalette value="#1677ff" onChange={onChange} />);

		const blue = container.querySelector("input[aria-label='选择蓝色']") as HTMLInputElement;
		const green = container.querySelector("input[aria-label='选择绿色']") as HTMLInputElement;
		expect(blue.checked).toBe(true);
		expect(green.checked).toBe(false);
		expect(container.textContent).not.toContain("#1677ff");
		act(() => green.click());
		expect(onChange).toHaveBeenCalledWith("#52c41a");
		unmount();
	});

	it("marks builtin categories and makes every immutable field read-only", async () => {
		listTagCategories.mockResolvedValue([builtinCategory]);
		const { TagManagementTab } = await import("./TagManagementTab");
		const { container, unmount } = await renderAndFlush(<TagManagementTab canManage />);

		expect(container.textContent).toContain("预置");
		const remove = Array.from(container.querySelectorAll("button")).find(
			(button) => button.textContent === "删除分类",
		) as HTMLButtonElement;
		expect(remove).not.toBeNull();
		expect(remove.disabled).toBe(true);
		expect(remove.title).toContain("预置分类由系统维护");
		expect(remove.closest("[data-tooltip]")?.getAttribute("data-tooltip")).toContain("预置分类由系统维护");
		const edit = Array.from(container.querySelectorAll("button")).find((button) =>
			button.textContent?.includes("编辑分类"),
		) as HTMLButtonElement;
		await act(async () => edit.click());
		await flush();

		for (const labelText of ["分类名称", "分类编码", "分类说明", "显示顺序"]) {
			const label = Array.from(container.querySelectorAll("label")).find((node) =>
				node.textContent?.includes(labelText),
			);
			const control = label?.querySelector("input, textarea") as HTMLInputElement | HTMLTextAreaElement | null;
			expect(control?.disabled, `${labelText} should be disabled`).toBe(true);
		}
		expect((container.querySelector("select[aria-label='上级分类']") as HTMLSelectElement).disabled).toBe(true);
		unmount();
	});

	it("shows the backend deletion reason without replacing it", async () => {
		deleteCatalogTag.mockRejectedValueOnce(new Error("标签仍被三个资产使用，需确认后再删除"));
		const { TagManagementTab } = await import("./TagManagementTab");
		const { container, unmount } = await renderAndFlush(<TagManagementTab canManage />);
		const remove = container.querySelector("button[aria-label='删除标签 财务']") as HTMLButtonElement;
		act(() => remove.click());
		const options = modalConfirm.mock.calls[0][0];
		await expect(options.onOk()).rejects.toThrow("标签仍被三个资产使用，需确认后再删除");
		expect(toastError).toHaveBeenCalledWith("标签仍被三个资产使用，需确认后再删除");
		unmount();
	});

	it("exposes view and association journeys for the selected tag", async () => {
		const onViewAssets = vi.fn();
		const onAssociateAssets = vi.fn();
		const { TagManagementTab } = await import("./TagManagementTab");
		const { container, unmount } = await renderAndFlush(
			<TagManagementTab canManage onViewAssets={onViewAssets} onAssociateAssets={onAssociateAssets} />,
		);

		const view = Array.from(container.querySelectorAll("button")).find(
			(button) => button.textContent === "查看资产",
		) as HTMLButtonElement;
		const associate = Array.from(container.querySelectorAll("button")).find(
			(button) => button.textContent === "关联资产",
		) as HTMLButtonElement;
		act(() => view.click());
		act(() => associate.click());

		expect(onViewAssets).toHaveBeenCalledWith(financeTag);
		expect(onAssociateAssets).toHaveBeenCalledWith(financeTag);
		unmount();
	});
});
