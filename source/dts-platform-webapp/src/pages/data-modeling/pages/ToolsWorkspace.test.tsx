// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { DataModelingRoute } from "../types";
import { ToolsWorkspace } from "./ToolsWorkspace";

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;

const routeFor = (view: string): DataModelingRoute => ({
	workspace: "tools",
	view,
	title: view === "exports" ? "导出记录" : view === "imports" ? "导入记录" : "工具箱",
	description: "真实 owner 流程入口",
});

async function renderView(view: string) {
	const router = createMemoryRouter(
		[
			{
				path: `/data-modeling/tools/${view}`,
				element: <ToolsWorkspace route={routeFor(view)} />,
			},
			{ path: "*", element: <output>目标流程</output> },
		],
		{ initialEntries: [`/data-modeling/tools/${view}`] },
	);

	await act(async () => {
		root.render(<RouterProvider router={router} />);
		await Promise.resolve();
	});
	return router;
}

beforeEach(() => {
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});

afterEach(() => {
	act(() => root.unmount());
	container.remove();
});

describe("ToolsWorkspace", () => {
	it("opens the canonical dbt ZIP reverse-modeling owner", async () => {
		const router = await renderView("toolbox");
		const card = Array.from(document.querySelectorAll("article")).find((node) =>
			node.textContent?.includes("dbt ZIP 反向建模"),
		);
		const button = card?.querySelector("button");
		expect(button).toBeTruthy();

		await act(async () => button?.click());
		expect(router.state.location.pathname).toBe("/data-modeling/dimensions/reverse");
	});

	it("shows owner-grouped imports without a fabricated history table", async () => {
		await renderView("imports");

		expect(document.body.textContent).toContain("真实导入流程");
		expect(document.body.textContent).toContain("没有统一的工具运行台账");
		expect(document.querySelector("table")).toBeNull();
	});

	it("shows an explicit empty boundary when no canonical export owner exists", async () => {
		await renderView("exports");

		expect(document.body.textContent).toContain("当前没有归属明确的建模导出流程");
		expect(document.querySelector("button")).toBeNull();
	});
});
