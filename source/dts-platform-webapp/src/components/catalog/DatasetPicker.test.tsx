// @vitest-environment jsdom

import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { getDatasetFields, listDatasets, listDomains } = vi.hoisted(() => ({
	getDatasetFields: vi.fn(),
	listDatasets: vi.fn(),
	listDomains: vi.fn(),
}));

vi.mock("@/api/platformApi", () => ({
	getDatasetFields,
	listDatasets,
	listDomains,
}));

vi.mock("antd", async () => {
	const React = await import("react");
	return {
		Select: ({ options = [], onChange, onPopupScroll, onSearch, placeholder, showSearch }: any) =>
			React.createElement(
				"div",
				{
					"data-role": showSearch ? "dataset-select" : "domain-select",
					"data-placeholder": placeholder,
					onScroll: onPopupScroll,
				},
				showSearch
					? React.createElement("button", { type: "button", onClick: () => onSearch?.("budget") }, "搜索预算资产")
					: React.createElement("button", { type: "button", onClick: () => onChange?.("domain-2") }, "选择测试主题域"),
				options.map((option: any) =>
					React.createElement("div", { key: option.value, "data-value": option.value }, option.label),
				),
			),
		Space: ({ children }: any) => React.createElement("span", null, children),
		Spin: () => React.createElement("span", null, "加载中"),
		Tag: ({ children }: any) => React.createElement("span", null, children),
		Typography: {
			Text: ({ children }: any) => React.createElement("span", null, children),
		},
	};
});

async function flushAsyncWork() {
	await act(async () => {
		await Promise.resolve();
		await Promise.resolve();
		await Promise.resolve();
	});
}

async function renderAndFlush(
	ui: ReactElement,
): Promise<{ container: HTMLElement; rerender: (nextUi: ReactElement) => Promise<void>; unmount: () => void }> {
	const container = document.createElement("div");
	document.body.appendChild(container);
	let root!: Root;
	await act(async () => {
		root = createRoot(container);
		root.render(ui);
	});
	await flushAsyncWork();
	return {
		container,
		rerender: async (nextUi) => {
			await act(async () => root.render(nextUi));
			await flushAsyncWork();
		},
		unmount: () => {
			act(() => root.unmount());
			container.remove();
		},
	};
}

const datasetPage = (page: number, size: number, total: number) => ({
	content: Array.from({ length: size }, (_, index) => {
		const offset = page * 50 + index + 1;
		return { id: `asset-${offset}`, name: `数据资产 ${offset}`, warehouseLayer: "ODS" };
	}),
	total,
	page,
	size,
});

const scrollToEnd = (element: HTMLElement) => {
	Object.defineProperties(element, {
		scrollTop: { configurable: true, value: 80 },
		clientHeight: { configurable: true, value: 20 },
		scrollHeight: { configurable: true, value: 100 },
	});
	act(() => element.dispatchEvent(new Event("scroll", { bubbles: true })));
};

describe("DatasetPicker", () => {
	beforeEach(() => {
		listDomains.mockResolvedValue({ content: [] });
		getDatasetFields.mockResolvedValue([]);
		listDatasets.mockImplementation(({ page }: { page: number }) =>
			Promise.resolve(page === 0 ? datasetPage(0, 50, 75) : datasetPage(1, 25, 75)),
		);
	});

	afterEach(() => {
		vi.useRealTimers();
		vi.clearAllMocks();
		document.body.innerHTML = "";
	});

	it("loads the next server page when the user scrolls to the end", async () => {
		const { DatasetPicker } = await import("./DatasetPicker");
		const { container, unmount } = await renderAndFlush(<DatasetPicker sourceId="lake-1" />);

		expect(container.textContent).toContain("数据资产 50");
		expect(container.textContent).not.toContain("数据资产 75");

		const select = container.querySelector('[data-role="dataset-select"]') as HTMLElement;
		scrollToEnd(select);
		await flushAsyncWork();

		expect(container.textContent).toContain("数据资产 75");
		unmount();
	});

	it("deduplicates consecutive end-scroll events while the next page is pending", async () => {
		let resolveNextPage!: (value: ReturnType<typeof datasetPage>) => void;
		const nextPage = new Promise<ReturnType<typeof datasetPage>>((resolve) => {
			resolveNextPage = resolve;
		});
		listDatasets.mockImplementation(({ page }: { page: number }) =>
			page === 0 ? Promise.resolve(datasetPage(0, 50, 75)) : nextPage,
		);
		const { DatasetPicker } = await import("./DatasetPicker");
		const { container, unmount } = await renderAndFlush(<DatasetPicker sourceId="lake-1" />);
		const select = container.querySelector('[data-role="dataset-select"]') as HTMLElement;

		scrollToEnd(select);
		scrollToEnd(select);

		expect(listDatasets.mock.calls.filter(([params]) => params.page === 1)).toHaveLength(1);
		resolveNextPage(datasetPage(1, 25, 75));
		await flushAsyncWork();
		expect(container.textContent).toContain("数据资产 75");
		unmount();
	});

	it("resets to the first page when source, search, domain, or disabled state changes", async () => {
		vi.useFakeTimers();
		listDatasets.mockImplementation(({ domainId, keyword, page, sourceId }: any) =>
			Promise.resolve({
				content: [{ id: `${sourceId}-${domainId || "all"}-${keyword || "all"}-${page}`, name: `${sourceId}-${page}` }],
				total: sourceId === "lake-1" && !domainId && !keyword ? 2 : 1,
				page,
				size: 50,
			}),
		);
		const { DatasetPicker } = await import("./DatasetPicker");
		const { container, rerender, unmount } = await renderAndFlush(<DatasetPicker sourceId="lake-1" />);
		scrollToEnd(container.querySelector('[data-role="dataset-select"]') as HTMLElement);
		await flushAsyncWork();
		expect(listDatasets.mock.calls.some(([params]) => params.sourceId === "lake-1" && params.page === 1)).toBe(true);

		await rerender(<DatasetPicker sourceId="lake-2" />);
		expect(listDatasets.mock.calls.at(-1)?.[0]).toMatchObject({ sourceId: "lake-2", page: 0 });

		act(() => {
			(container.querySelector('[data-role="dataset-select"] button') as HTMLButtonElement).click();
			vi.advanceTimersByTime(300);
		});
		await flushAsyncWork();
		expect(listDatasets.mock.calls.at(-1)?.[0]).toMatchObject({ sourceId: "lake-2", keyword: "budget", page: 0 });

		act(() => (container.querySelector('[data-role="domain-select"] button') as HTMLButtonElement).click());
		await flushAsyncWork();
		expect(listDatasets.mock.calls.at(-1)?.[0]).toMatchObject({
			sourceId: "lake-2",
			keyword: "budget",
			domainId: "domain-2",
			page: 0,
		});

		const callsBeforeDisable = listDatasets.mock.calls.length;
		await rerender(<DatasetPicker sourceId="lake-2" disabled />);
		expect(container.querySelectorAll('[data-role="dataset-select"] [data-value]')).toHaveLength(0);
		expect(listDatasets).toHaveBeenCalledTimes(callsBeforeDisable);
		await rerender(<DatasetPicker sourceId="lake-2" />);
		expect(listDatasets.mock.calls.at(-1)?.[0]).toMatchObject({ sourceId: "lake-2", page: 0 });
		unmount();
	});

	it("ignores an older response after the selected source changes", async () => {
		let resolveOldSource!: (value: ReturnType<typeof datasetPage>) => void;
		const oldSourceResponse = new Promise<ReturnType<typeof datasetPage>>((resolve) => {
			resolveOldSource = resolve;
		});
		listDatasets.mockImplementation(({ sourceId }: { sourceId: string }) =>
			sourceId === "lake-old"
				? oldSourceResponse
				: Promise.resolve({ content: [{ id: "new-1", name: "新数据湖资产" }], total: 1, page: 0, size: 50 }),
		);
		const { DatasetPicker } = await import("./DatasetPicker");
		const { container, rerender, unmount } = await renderAndFlush(<DatasetPicker sourceId="lake-old" />);

		await rerender(<DatasetPicker sourceId="lake-new" />);
		expect(container.textContent).toContain("新数据湖资产");
		resolveOldSource({ content: [{ id: "old-1", name: "旧数据湖资产" }], total: 1, page: 0, size: 50 });
		await flushAsyncWork();

		expect(container.textContent).toContain("新数据湖资产");
		expect(container.textContent).not.toContain("旧数据湖资产");
		unmount();
	});

	it("stops requesting pages after every selectable asset is loaded", async () => {
		listDatasets.mockResolvedValue(datasetPage(0, 50, 50));
		const { DatasetPicker } = await import("./DatasetPicker");
		const { container, unmount } = await renderAndFlush(<DatasetPicker sourceId="lake-1" />);

		scrollToEnd(container.querySelector('[data-role="dataset-select"]') as HTMLElement);
		await flushAsyncWork();

		expect(listDatasets).toHaveBeenCalledTimes(1);
		unmount();
	});

	it("shows the selected source and the complete selectable asset count", async () => {
		const { DatasetPicker } = await import("./DatasetPicker");
		const PickerWithSource = DatasetPicker as any;
		const { container, unmount } = await renderAndFlush(
			<PickerWithSource sourceId="lake-1" sourceName="数仓 (biadmin)" />,
		);

		expect(container.textContent).toContain("当前来源：数仓 (biadmin)");
		expect(container.textContent).toContain("共 75 条可选数据资产");
		unmount();
	});
});
