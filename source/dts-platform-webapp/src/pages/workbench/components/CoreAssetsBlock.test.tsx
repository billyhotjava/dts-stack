// @vitest-environment jsdom
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";

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

import { CoreAssetsBlock, type CoreAssetItem } from "./CoreAssetsBlock";

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
		const { container, unmount } = render(
			<CoreAssetsBlock role="INST_LEADER" items={[]} loading={true} />,
		);
		expect(container.querySelector(".ant-skeleton")).not.toBeNull();
		unmount();
	});

	it("shows_empty_when_items_empty_for_INST_LEADER", () => {
		const { container, unmount } = render(
			<CoreAssetsBlock role="INST_LEADER" items={[]} loading={false} />,
		);
		expect(container.textContent).toContain("暂无核心资产");
		unmount();
	});

	it("shows_empty_when_items_empty_for_EMP", () => {
		const { container, unmount } = render(
			<CoreAssetsBlock role="EMP" items={[]} loading={false} />,
		);
		expect(container.textContent).toContain("还没有常用资产");
		unmount();
	});

	it("renders_tag_colors_correctly_for_S1_through_S4", () => {
		const { container, unmount } = render(
			<CoreAssetsBlock role="INST_LEADER" items={sample} loading={false} />,
		);
		expect(container.querySelector(".ant-tag-red")).not.toBeNull();
		expect(container.querySelector(".ant-tag-volcano")).not.toBeNull();
		expect(container.querySelector(".ant-tag-orange")).not.toBeNull();
		expect(container.querySelector(".ant-tag-blue")).not.toBeNull();
		unmount();
	});

	it("renders_bizDomain_tag_only_when_not_null", () => {
		const { container, unmount } = render(
			<CoreAssetsBlock role="INST_LEADER" items={sample} loading={false} />,
		);
		const geekblueTags = container.querySelectorAll(".ant-tag-geekblue");
		// Only two items carry bizDomain in the fixture.
		expect(geekblueTags.length).toBe(2);
		unmount();
	});

	it("clicking_row_opens_asset_detail_route", () => {
		const { container, unmount } = render(
			<CoreAssetsBlock role="INST_LEADER" items={sample} loading={false} />,
		);
		const firstItem = container.querySelector(".ant-list-item") as HTMLElement;
		act(() => {
			firstItem.click();
		});
		expect(windowOpenMock).toHaveBeenCalledWith(
			"/catalog/datasets/a1",
			"_blank",
			"noopener,noreferrer",
		);
		unmount();
	});
});
