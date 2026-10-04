// @vitest-environment jsdom

import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { describe, expect, it, vi } from "vitest";
import { AssetTagChips } from "./AssetTagChips";

function render(ui: ReactElement): { container: HTMLElement; unmount: () => void } {
	const container = document.createElement("div");
	document.body.appendChild(container);
	let root!: Root;
	act(() => {
		root = createRoot(container);
		root.render(ui);
	});
	return {
		container,
		unmount: () => {
			act(() => root.unmount());
			container.remove();
		},
	};
}

const tags = [
	{
		id: "tag-1",
		categoryId: "category-1",
		code: "BUSINESS-FINANCE",
		name: "财务",
		color: "#1677ff",
		builtin: true,
		enabled: true,
		description: "财务主题资产",
		usageCount: 3,
		categoryName: "业务域",
	},
];

describe("AssetTagChips", () => {
	it("renders business tags as neutral, labelled chips instead of security tags", () => {
		const { container, unmount } = render(<AssetTagChips tags={tags} />);

		const chip = container.querySelector("[data-business-tag='true']");
		expect(chip?.textContent).toContain("财务");
		expect(container.querySelector("ul")?.getAttribute("aria-label")).toContain("业务数据标签");
		expect(chip?.getAttribute("title")).toContain("业务域");
		expect(chip?.className).toContain("rounded");
		expect(container.querySelector(".ant-tag")).toBeNull();
		unmount();
	});

	it("does not render a placeholder for an empty inline list", () => {
		const { container, unmount } = render(<AssetTagChips tags={[]} />);
		expect(container.innerHTML).toBe("");
		unmount();
	});

	it("exposes an explicit remove action when requested", () => {
		const onRemove = vi.fn();
		const { container, unmount } = render(<AssetTagChips tags={tags} removable onRemove={onRemove} />);

		const remove = container.querySelector("button[aria-label='移除业务标签 财务']") as HTMLButtonElement;
		act(() => remove.click());
		expect(onRemove).toHaveBeenCalledWith(tags[0]);
		unmount();
	});
});
