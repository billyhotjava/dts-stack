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

		expect(linkTarget("业务分类")).toBe("/data-architecture?view=business-domains");
		expect(linkTarget("数据域")).toBe("/data-architecture?view=business-domains");
		expect(linkTarget("数仓分层")).toBe("/data-architecture?view=layers");
		expect(linkTarget("业务过程")).toBe("/data-architecture?view=processes");
		expect(linkTarget("数据集市")).toBe("/data-architecture?view=marts");
		expect(linkTarget("主题域")).toBe("/data-architecture?view=subjects");
		expect(linkTarget("规划参数配置")).toBe("/data-modeling/planning/system");
	});
});
