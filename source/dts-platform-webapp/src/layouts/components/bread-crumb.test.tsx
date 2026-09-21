// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, expect, it, vi } from "vitest";

const views = [
	["business-domains", "业务分类与数据域"],
	["processes", "业务过程"],
	["layers", "数仓分层"],
	["marts", "数据集市"],
	["subjects", "主题域"],
] as const;
vi.mock("@/locales/use-locale", () => ({ default: () => ({ t: (value: string) => value }) }));
vi.mock("@/layouts/dashboard/nav", () => ({
	useFilteredNavData: () => [
		{
			items: [
				{
					path: "/data-architecture",
					title: "数仓规划",
					children: views.map(([view, title]) => ({ path: `/data-architecture?view=${view}`, title })),
				},
				{ path: "/workbench", title: "工作台" },
			],
		},
	],
}));

import BreadCrumb from "./bread-crumb";

let root: Root;
let container: HTMLDivElement;
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

it("follows all planning views, ignores unrelated parameters and preserves ordinary breadcrumbs", async () => {
	const router = createMemoryRouter(
		[
			{ path: "/data-architecture", element: <BreadCrumb /> },
			{ path: "/workbench", element: <BreadCrumb /> },
		],
		{ initialEntries: ["/data-architecture"] },
	);
	await act(async () => root.render(<RouterProvider router={router} />));
	expect(container.textContent).toContain("业务分类与数据域");
	for (const [view, title] of views) {
		await act(async () => {
			await router.navigate(`/data-architecture?active=record&view=${view}&returnPlanId=plan`);
		});
		expect(container.textContent).toContain("数仓规划");
		expect(container.querySelector('[aria-current="page"]')?.textContent).toBe(title);
	}
	await act(async () => {
		await router.navigate("/data-architecture?view=unknown");
	});
	expect(container.querySelector('[aria-current="page"]')?.textContent).toBe("业务分类与数据域");
	await act(async () => {
		await router.navigate("/workbench");
	});
	expect(container.querySelector('[aria-current="page"]')?.textContent).toBe("工作台");
});
