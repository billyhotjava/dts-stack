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
	const router = createMemoryRouter([{ path: "*", element: <DataModelingPage /> }], { initialEntries: [path] });
	await act(async () => root.render(<RouterProvider router={router} />));
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
		["/data-modeling/planning/processes", "planning"],
		["/data-modeling/standards/fields", "standards"],
		["/data-modeling/dimensions/workbench", "workbench"],
		["/data-modeling/dimensions/reverse", "reverse"],
		["/data-modeling/metrics/atomic", "metrics"],
		["/data-modeling/tools/toolbox", "tools"],
		["/data-modeling/graphs/models", "graphs"],
	])("routes %s to the reviewed %s prototype workspace", async (path, expectedSurface) => {
		await renderAt(path);
		expect(container.querySelector(`[data-surface="${expectedSurface}"]`)).not.toBeNull();
	});
});
