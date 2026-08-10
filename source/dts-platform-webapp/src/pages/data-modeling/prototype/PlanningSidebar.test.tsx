// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { PlanningSidebar } from "./PlanningSidebar";

let container: HTMLDivElement;
let root: Root;

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

const linkTarget = (label: string) =>
	Array.from(container.querySelectorAll("a"))
		.find((link) => link.textContent?.trim() === label)
		?.getAttribute("href");

describe("PlanningSidebar", () => {
	it("shows only the canonical warehouse-planning dictionaries", async () => {
		await act(async () =>
			root.render(
				<MemoryRouter initialEntries={["/data-architecture?view=business-domains"]}>
					<PlanningSidebar activeView="business-domains" />
				</MemoryRouter>,
			),
		);

		expect(linkTarget("业务分类与数据域")).toBe("/data-architecture?view=business-domains");
		expect(linkTarget("数仓分层")).toBe("/data-architecture?view=layers");
		expect(linkTarget("业务过程")).toBe("/data-architecture?view=processes");
		expect(linkTarget("数据集市")).toBe("/data-architecture?view=marts");
		expect(linkTarget("主题域")).toBe("/data-architecture?view=subjects");
		expect(container.textContent).not.toContain("建模空间");
		expect(container.textContent).not.toContain("建模策略");
	});

	it("shows icons and highlights only the selected query-backed architecture view", async () => {
		await act(async () =>
			root.render(
				<MemoryRouter initialEntries={["/data-architecture?view=layers"]}>
					<PlanningSidebar activeView="layers" />
				</MemoryRouter>,
			),
		);

		const links = Array.from(container.querySelectorAll("a"));
		expect(links.filter((link) => link.classList.contains("active")).map((link) => link.textContent?.trim())).toEqual([
			"数仓分层",
		]);
		for (const link of links) {
			const icon = link.querySelector(".dmx-planning-sidebar__icon");
			expect(icon, `${link.textContent?.trim()} should have an icon`).not.toBeNull();
			expect(icon?.getAttribute("aria-hidden")).toBe("true");
		}
	});
});
