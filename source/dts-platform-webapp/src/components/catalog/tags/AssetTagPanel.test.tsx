// @vitest-environment jsdom

import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { listAssetTags, listAllEnabledCatalogTags, listTagCategories, tagAsset, untagAsset, toastError } = vi.hoisted(
	() => ({
		listAssetTags: vi.fn(),
		listAllEnabledCatalogTags: vi.fn(),
		listTagCategories: vi.fn(),
		tagAsset: vi.fn(),
		untagAsset: vi.fn(),
		toastError: vi.fn(),
	}),
);

vi.mock("@/api/catalogTagsApi", () => ({
	listAssetTags,
	listAllEnabledCatalogTags,
	listTagCategories,
	tagAsset,
	untagAsset,
}));

vi.mock("sonner", () => ({
	toast: { error: toastError, success: vi.fn() },
}));

vi.mock("antd", async () => {
	const React = await import("react");
	const flatten = (options: any[] = []): any[] =>
		options.flatMap((option) => (Array.isArray(option.options) ? flatten(option.options) : [option]));
	return {
		Alert: ({ message, description }: any) => React.createElement("div", { role: "alert" }, message, description),
		Button: ({ children, onClick, disabled, ...props }: any) =>
			React.createElement("button", { type: "button", onClick, disabled, ...props }, children),
		Card: ({ children, title }: any) =>
			React.createElement("section", null, React.createElement("h3", null, title), children),
		Empty: ({ description }: any) => React.createElement("div", { "data-empty": "true" }, description),
		Select: ({ options, value, onChange, "aria-label": ariaLabel, disabled }: any) =>
			React.createElement(
				"select",
				{
					value: value || "",
					disabled,
					"aria-label": ariaLabel,
					onChange: (event: any) => onChange(event.currentTarget.value),
				},
				[
					React.createElement("option", { key: "", value: "" }, "请选择"),
					...flatten(options).map((option) =>
						React.createElement("option", { key: option.value, value: option.value }, option.label),
					),
				],
			),
		Space: ({ children }: any) => React.createElement("div", null, children),
		Spin: ({ children }: any) => React.createElement("div", null, children),
		Tooltip: ({ children }: any) => React.createElement(React.Fragment, null, children),
	};
});

const financeTag = {
	id: "tag-finance",
	categoryId: "category-1",
	code: "BUSINESS-FINANCE",
	name: "财务",
	color: "#1677ff",
	builtin: true,
	enabled: true,
	usageCount: 3,
};
const salesTag = {
	id: "tag-sales",
	categoryId: "category-1",
	code: "BUSINESS-SALES",
	name: "销售",
	color: "#52c41a",
	builtin: true,
	enabled: true,
	usageCount: 1,
};

async function renderAndFlush(ui: ReactElement): Promise<{ container: HTMLElement; root: Root; unmount: () => void }> {
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
		root,
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

describe("AssetTagPanel", () => {
	beforeEach(() => {
		(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;
		listAssetTags.mockResolvedValue([financeTag]);
		listTagCategories.mockResolvedValue([
			{
				id: "category-1",
				code: "BUSINESS",
				name: "业务域",
				sortOrder: 0,
				builtin: true,
				enabled: true,
				tagCount: 3,
				children: [],
			},
		]);
		listAllEnabledCatalogTags.mockResolvedValue([
			financeTag,
			salesTag,
			{
				...salesTag,
				id: "tag-disabled",
				name: "已停用",
				enabled: false,
			},
		]);
		tagAsset.mockResolvedValue({ created: 1, skipped: 0, removed: 0 });
		untagAsset.mockResolvedValue({ created: 0, skipped: 0, removed: 1 });
	});

	afterEach(() => {
		(globalThis as any).IS_REACT_ACT_ENVIRONMENT = false;
		vi.clearAllMocks();
		document.body.innerHTML = "";
	});

	it("loads the current tags and only offers enabled, unselected candidates", async () => {
		const { AssetTagPanel } = await import("./AssetTagPanel");
		const { container, unmount } = await renderAndFlush(
			<AssetTagPanel assetType="DATASET" assetKey="source:s/schema:p/table:orders" />,
		);

		expect(container.textContent).toContain("业务数据标签");
		expect(container.textContent).toContain("财务");
		const options = Array.from(container.querySelectorAll("option")).map((option) => option.textContent);
		expect(options).toContain("销售");
		expect(options).not.toContain("已停用");
		expect(options).not.toContain("财务");
		unmount();
	});

	it("keeps assigned tags visible when the category directory is unavailable", async () => {
		listTagCategories.mockRejectedValueOnce(new Error("标签分类服务暂时不可用"));
		const { AssetTagPanel } = await import("./AssetTagPanel");
		const { container, unmount } = await renderAndFlush(
			<AssetTagPanel assetType="DATASET" assetKey="source:s/schema:p/table:orders" canEdit />,
		);

		expect(container.textContent).toContain("财务");
		expect(container.textContent).toContain("标签分类服务暂时不可用");
		expect(container.querySelector("[data-empty='true']")).toBeNull();
		const select = container.querySelector("select[aria-label='添加业务数据标签']") as HTMLSelectElement;
		expect(select.disabled).toBe(false);
		expect(Array.from(select.options).map((option) => option.textContent)).toContain("销售");
		unmount();
	});

	it("keeps assigned tags visible and disables editing when the candidate directory is unavailable", async () => {
		listAllEnabledCatalogTags.mockRejectedValueOnce(new Error("候选标签目录暂时不可用"));
		const { AssetTagPanel } = await import("./AssetTagPanel");
		const { container, unmount } = await renderAndFlush(
			<AssetTagPanel assetType="DATASET" assetKey="source:s/schema:p/table:orders" canEdit />,
		);

		expect(container.textContent).toContain("财务");
		expect(container.textContent).toContain("候选标签目录暂时不可用");
		expect(container.querySelector("[data-empty='true']")).toBeNull();
		const select = container.querySelector("select[aria-label='添加业务数据标签']") as HTMLSelectElement;
		expect(select.disabled).toBe(true);
		unmount();
	});

	it("does not report an empty assignment when the assigned-tag request fails", async () => {
		listAssetTags.mockRejectedValueOnce(new Error("资产标签关系暂时无法读取"));
		const { AssetTagPanel } = await import("./AssetTagPanel");
		const { container, unmount } = await renderAndFlush(
			<AssetTagPanel assetType="DATASET" assetKey="source:s/schema:p/table:orders" canEdit />,
		);

		expect(container.textContent).toContain("资产标签关系暂时无法读取");
		expect(container.textContent).not.toContain("当前资产尚未设置业务数据标签");
		const select = container.querySelector("select[aria-label='添加业务数据标签']") as HTMLSelectElement;
		expect(select.disabled).toBe(true);
		unmount();
	});

	it("defaults to read-only when the parent does not provide an explicit write capability", async () => {
		const { AssetTagPanel } = await import("./AssetTagPanel");
		const { container, unmount } = await renderAndFlush(
			<AssetTagPanel assetType="DATASET" assetKey="source:s/schema:p/table:orders" />,
		);

		const select = container.querySelector("select[aria-label='添加业务数据标签']") as HTMLSelectElement;
		expect(select.disabled).toBe(true);
		expect(container.querySelector("button[aria-label='移除业务标签 财务']")).toBeNull();
		expect(container.textContent).toContain("您可以查看业务数据标签，但不能修改当前资产");
		unmount();
	});

	it("adds a selected tag with the formal asset identity", async () => {
		const { AssetTagPanel } = await import("./AssetTagPanel");
		const onChanged = vi.fn();
		const { container, unmount } = await renderAndFlush(
			<AssetTagPanel assetType="DATASET" assetKey="source:s/schema:p/table:orders" canEdit onChanged={onChanged} />,
		);

		const select = container.querySelector("select[aria-label='添加业务数据标签']") as HTMLSelectElement;
		await act(async () => {
			select.value = salesTag.id;
			select.dispatchEvent(new Event("change", { bubbles: true }));
		});
		await flush();

		expect(tagAsset).toHaveBeenCalledWith({
			assetType: "DATASET",
			assetKey: "source:s/schema:p/table:orders",
			tagIds: [salesTag.id],
		});
		expect(container.textContent).toContain("销售");
		expect(onChanged).toHaveBeenCalledTimes(1);
		unmount();
	});

	it("restores a removed tag and preserves the backend error message on failure", async () => {
		const denied = new Error("当前账号无该资产写权限");
		untagAsset.mockRejectedValueOnce(denied);
		const { AssetTagPanel } = await import("./AssetTagPanel");
		const { container, unmount } = await renderAndFlush(
			<AssetTagPanel assetType="DATASET" assetKey="source:s/schema:p/table:orders" canEdit />,
		);

		const remove = container.querySelector("button[aria-label='移除业务标签 财务']") as HTMLButtonElement;
		await act(async () => remove.click());
		await flush();

		expect(container.textContent).toContain("财务");
		expect(container.textContent).toContain("当前账号无该资产写权限");
		expect(toastError).toHaveBeenCalledWith("当前账号无该资产写权限");
		unmount();
	});

	it("clears stale tags when the asset identity changes", async () => {
		const { AssetTagPanel } = await import("./AssetTagPanel");
		const { container, root, unmount } = await renderAndFlush(
			<AssetTagPanel assetType="DATASET" assetKey="source:s/schema:p/table:orders" canEdit />,
		);
		listAssetTags.mockResolvedValueOnce([{ ...salesTag, name: "新资产标签" }]);

		await act(async () => {
			root.render(<AssetTagPanel assetType="METRIC" assetKey="metric:core/revenue" canEdit />);
		});
		await flush();

		const chips = Array.from(container.querySelectorAll("[data-business-tag='true']")).map((chip) => chip.textContent);
		expect(chips).not.toContain("财务×");
		expect(chips).toContain("新资产标签×");
		expect(listAssetTags).toHaveBeenLastCalledWith({
			assetType: "METRIC",
			assetKey: "metric:core/revenue",
		});
		unmount();
	});
});
