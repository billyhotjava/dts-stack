// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import DataModelingPage from "../DataModelingPage";

vi.mock("./OverviewPage", () => ({ OverviewPage: () => <div data-surface="overview">建模概览</div> }));
vi.mock("./PlanningPage", () => ({ PlanningPage: () => <div data-surface="planning">数仓规划</div> }));
vi.mock("./StandardsPage", () => ({ StandardsPage: () => <div data-surface="standards">数据标准</div> }));
vi.mock("./MetricsPage", () => ({ MetricsPage: () => <div data-surface="metrics">数据指标</div> }));
vi.mock("./ToolsPage", () => ({ ToolsPage: () => <div data-surface="tools">通用工具</div> }));
vi.mock("./RelationshipGraphPage", () => ({ RelationshipGraphPage: () => <div data-surface="graphs">关系图</div> }));
vi.mock("./ModelingWorkbenchPage", () => ({
	ModelingWorkbenchPage: () => <div data-surface="workbench">维度建模</div>,
}));
vi.mock("./ReverseModelingPage", () => ({ ReverseModelingPage: () => <div data-surface="reverse">逆向建模</div> }));

let container: HTMLDivElement;
let root: Root;

async function renderAt(path: string) {
	const router = createMemoryRouter(
		[
			{
				path: "/governance/standards/elements",
				element: <div data-surface="elements">数据元</div>,
			},
			{ path: "*", element: <DataModelingPage /> },
		],
		{ initialEntries: [path] },
	);
	await act(async () => root.render(<RouterProvider router={router} />));
	return router;
}

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

describe("prototype-owned data-modeling surface routing", () => {
	it.each([
		["/data-modeling/home/workspace", "overview"],
		["/data-modeling/dimensions/workbench", "workbench"],
		["/data-modeling/dimensions/reverse", "reverse"],
		["/data-modeling/metrics/atomic", "metrics"],
		["/data-modeling/tools/toolbox", "tools"],
		["/data-modeling/graphs/models", "graphs"],
	])("routes %s to the reviewed %s prototype workspace", async (path, expectedSurface) => {
		await renderAt(path);
		expect(container.querySelector(`[data-surface="${expectedSurface}"]`)).not.toBeNull();
	});

	it("redirects the legacy field-standard route to the canonical data-element owner", async () => {
		const router = await renderAt("/data-modeling/standards/fields?keyword=amount#catalog");

		expect(router.state.location.pathname).toBe("/governance/standards/elements");
		expect(router.state.location.search).toBe("?keyword=amount");
		expect(router.state.location.hash).toBe("#catalog");
		expect(container.querySelector('[data-surface="elements"]')).not.toBeNull();
		expect(container.querySelector('[data-surface="standards"]')).toBeNull();
	});

	// 数仓规划已迁出建模模块：/data-modeling/planning/* 统一重定向到 /data-architecture，
	// 因此这些路径不再渲染 PlanningPage。
	it.each([
		["/data-modeling/planning/processes"],
		["/data-modeling/planning/layers"],
		["/data-modeling/planning/marts"],
	])("redirects legacy planning route %s out of the modeling surface", async (path) => {
		await renderAt(path);
		expect(container.querySelector('[data-surface="planning"]')).toBeNull();
	});
});
