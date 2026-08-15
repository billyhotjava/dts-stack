// @vitest-environment jsdom

import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";

beforeAll(() => {
	if (!window.matchMedia) {
		Object.defineProperty(window, "matchMedia", {
			writable: true,
			value: (query: string) => ({
				matches: false,
				media: query,
				onchange: null,
				addListener: () => {},
				removeListener: () => {},
				addEventListener: () => {},
				removeEventListener: () => {},
				dispatchEvent: () => false,
			}),
		});
	}
});

import { type CoreAssetItem, CoreAssetsBlock } from "./CoreAssetsBlock";

function render(element: ReactElement): { container: HTMLElement; unmount: () => void } {
	const container = document.createElement("div");
	document.body.appendChild(container);
	let root!: Root;
	act(() => {
		root = createRoot(container);
		root.render(element);
	});
	return {
		container,
		unmount: () => {
			act(() => {
				root.unmount();
			});
			container.remove();
		},
	};
}

const NOW = new Date().toISOString();

const sample: CoreAssetItem[] = [
	{ id: "a1", name: "客户主数据", classification: "S1", updatedAt: NOW, bizDomain: "SALES" },
	{ id: "a2", name: "财务总账", classification: "S2", updatedAt: NOW, bizDomain: null },
	{ id: "a3", name: "员工花名册", classification: "S3", updatedAt: NOW, bizDomain: "HR" },
	{ id: "a4", name: "公共日历", classification: "S4", updatedAt: NOW, bizDomain: null },
];

const windowOpenMock = vi.fn();

describe("CoreAssetsBlock", () => {
	beforeEach(() => {
		windowOpenMock.mockReset();
		Object.defineProperty(window, "open", {
			configurable: true,
			writable: true,
			value: windowOpenMock,
		});
	});

	afterEach(() => {
		document.body.innerHTML = "";
	});

	it("shows_skeleton_when_loading", () => {
		const { container, unmount } = render(<CoreAssetsBlock role="INST_LEADER" items={[]} loading={true} />);
		expect(container.querySelector(".ant-skeleton")).not.toBeNull();
		unmount();
	});

	it("shows_empty_when_items_empty_for_INST_LEADER", () => {
		const { container, unmount } = render(<CoreAssetsBlock role="INST_LEADER" items={[]} loading={false} />);
		expect(container.textContent).toContain("暂无核心资产");
		unmount();
	});

	it("shows_empty_when_items_empty_for_EMP", () => {
		const { container, unmount } = render(<CoreAssetsBlock role="EMP" items={[]} loading={false} />);
		expect(container.textContent).toContain("还没有常用资产");
		unmount();
	});

	it("renders_tag_colors_correctly_for_S1_through_S4", () => {
		const { container, unmount } = render(<CoreAssetsBlock role="INST_LEADER" items={sample} loading={false} />);
		// 密级配色由共享的 ClassificationTag 统一定义：S1=red / S2=gold / S3=blue / S4=default（无色板 class）
		expect(container.querySelector(".ant-tag-red")).not.toBeNull();
		expect(container.querySelector(".ant-tag-gold")).not.toBeNull();
		expect(container.querySelector(".ant-tag-blue")).not.toBeNull();
		expect(container.querySelectorAll(".ant-tag-volcano, .ant-tag-orange")).toHaveLength(0);
		unmount();
	});

	it("renders_bizDomain_tag_only_when_not_null", () => {
		const { container, unmount } = render(<CoreAssetsBlock role="INST_LEADER" items={sample} loading={false} />);
		const geekblueTags = container.querySelectorAll(".ant-tag-geekblue");
		// Only two items carry bizDomain in the fixture.
		expect(geekblueTags.length).toBe(2);
		unmount();
	});

	it("clicking_row_opens_asset_detail_route", () => {
		const { container, unmount } = render(<CoreAssetsBlock role="INST_LEADER" items={sample} loading={false} />);
		const firstItem = container.querySelector(".ant-list-item") as HTMLElement;
		act(() => {
			firstItem.click();
		});
		expect(windowOpenMock).toHaveBeenCalledWith("/catalog/datasets/a1", "_blank", "noopener,noreferrer");
		unmount();
	});

	it("view_all_link_points_to_catalog_assets_home", () => {
		const { container, unmount } = render(<CoreAssetsBlock role="INST_LEADER" items={sample} loading={false} />);
		const link = Array.from(container.querySelectorAll("a")).find((a) => a.textContent?.includes("查看全部"));
		expect(link?.getAttribute("href")).toBe("/catalog/assets");
		unmount();
	});
});
