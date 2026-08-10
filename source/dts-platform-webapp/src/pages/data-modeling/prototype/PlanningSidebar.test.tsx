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
	it("routes migrated planning dictionaries to the canonical data architecture owner", async () => {
		await act(async () =>
			root.render(
				<MemoryRouter initialEntries={["/data-modeling/planning/spaces"]}>
					<PlanningSidebar activeView="spaces" />
				</MemoryRouter>,
			),
		);

		expect(linkTarget("业务分类")).toBe(
			"/data-architecture?view=business-domains&source=modeling-space&planningView=business-categories",
		);
		expect(linkTarget("数据域")).toBe(
			"/data-architecture?view=business-domains&source=modeling-space&planningView=domains",
		);
		expect(linkTarget("数仓分层")).toBe("/data-architecture?view=layers&source=modeling-space&planningView=layers");
		expect(linkTarget("业务过程")).toBe(
			"/data-architecture?view=processes&source=modeling-space&planningView=processes",
		);
		expect(linkTarget("数据集市")).toBe("/data-architecture?view=marts&source=modeling-space&planningView=marts");
		expect(linkTarget("主题域")).toBe("/data-architecture?view=subjects&source=modeling-space&planningView=subjects");
		expect(linkTarget("规划参数配置")).toBe("/data-modeling/planning/system");
	});

	it("shows icons and highlights only the selected query-backed architecture view", async () => {
		await act(async () =>
			root.render(
				<MemoryRouter initialEntries={["/data-architecture?view=layers"]}>
					<PlanningSidebar activeView="layers" surface="architecture" />
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
