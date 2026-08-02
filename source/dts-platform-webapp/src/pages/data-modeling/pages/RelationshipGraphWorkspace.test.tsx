// @vitest-environment jsdom

import { act, type ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter, useLocation } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { DataModelingRoute } from "../types";
import { RelationshipGraphWorkspace } from "./RelationshipGraphWorkspace";

const serviceMocks = vi.hoisted(() => ({
	listPlans: vi.fn(),
	loadGraph: vi.fn(),
}));

vi.mock("@/api/services/modelingRelationshipGraphService", () => ({
	listModelingRelationshipPlans: serviceMocks.listPlans,
	loadModelingRelationshipGraph: serviceMocks.loadGraph,
	modelingRelationshipNodePath: (node: { route?: string | null }) => node.route || null,
	classifyModelingRelationshipGraphFailure: (error: { response?: { status?: number }; message?: string }) =>
		error.response?.status === 403
			? { kind: "permission", message: "当前账号无权访问该建设计划的关系图，请联系管理员授权。" }
			: { kind: "request", message: error.message || "关系图读取失败" },
}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;

const route: DataModelingRoute = {
	workspace: "graphs",
	view: "models",
	title: "模型关系",
	description: "查看规划、维度和模型对象之间的依赖关系。",
};

function LocationProbe() {
	const location = useLocation();
	return <output data-testid="location">{location.pathname}</output>;
}

async function render(element: ReactElement) {
	await act(async () => {
		root.render(
			<MemoryRouter initialEntries={["/data-modeling/graphs/models"]}>
				{element}
				<LocationProbe />
			</MemoryRouter>,
		);
		await Promise.resolve();
		await Promise.resolve();
	});
}

async function flush() {
	await act(async () => {
		await new Promise((resolve) => setTimeout(resolve, 0));
	});
}

beforeEach(() => {
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
	serviceMocks.listPlans.mockReset();
	serviceMocks.loadGraph.mockReset();
	serviceMocks.listPlans.mockResolvedValue([
		{
			id: "plan-1",
			tenantId: "tenant-1",
			code: "PLAN_1",
			name: "客户主题计划",
			ownerId: "owner-1",
			onboardingMode: "BUSINESS_FIRST",
			lifecycleStatus: "DESIGNING",
			version: 1,
		},
	]);
	serviceMocks.loadGraph.mockResolvedValue({
		planId: "plan-1",
		nodes: [
			{ id: "plan:plan-1", kind: "PLAN", label: "客户主题计划", route: "/data-modeling/planning/spaces" },
			{
				id: "model:model-1:r1",
				kind: "MODEL",
				label: "客户主题模型",
				status: "DRAFT",
				route: "/data-modeling/dimensions/workbench?modelSpecId=model-1",
			},
		],
		edges: [{ source: "plan:plan-1", target: "model:model-1:r1", kind: "CONTAINS", label: "包含" }],
		truncated: false,
		nextCursor: null,
	});
});

afterEach(() => {
	act(() => root.unmount());
	container.remove();
});

describe("RelationshipGraphWorkspace", () => {
	it("loads canonical plan graph and follows the server-provided node route", async () => {
		await render(<RelationshipGraphWorkspace route={route} />);
		await flush();

		expect(container.textContent).toContain("当前建设计划");
		expect(container.textContent).toContain("客户主题模型");
		expect(serviceMocks.loadGraph).toHaveBeenCalledWith("plan-1", {
			view: "models",
			query: "",
		});

		const modelNode = Array.from(container.querySelectorAll("button")).find((button) =>
			button.textContent?.includes("客户主题模型"),
		);
		expect(modelNode).toBeTruthy();
		await act(async () => modelNode?.click());
		expect(container.querySelector('[data-testid="location"]')?.textContent).toBe(
			"/data-modeling/dimensions/workbench",
		);
	});

	it("fails closed with a permission state and retry action", async () => {
		serviceMocks.listPlans.mockRejectedValue({ response: { status: 403 } });
		await render(<RelationshipGraphWorkspace route={route} />);
		await flush();

		expect(container.textContent).toContain("无权访问关系图");
		expect(container.textContent).toContain("重新加载");
		expect(container.textContent).not.toContain("示例数据域");
	});
});
