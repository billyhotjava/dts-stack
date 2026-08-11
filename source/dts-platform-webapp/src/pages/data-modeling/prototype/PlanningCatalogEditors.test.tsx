// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
	listPlanningCatalogDomains: vi.fn(),
}));

vi.mock("./services/planningCatalogDomainService", () => ({
	listPlanningCatalogDomains: mocks.listPlanningCatalogDomains,
	createPlanningCatalogDomain: vi.fn(),
	updatePlanningCatalogDomain: vi.fn(),
	deletePlanningCatalogDomain: vi.fn(),
}));
vi.mock("./services/planningProjectionService", () => ({
	normalizeModelingRequestFailure: (_error: unknown, fallback: string) => ({ message: fallback }),
}));

import { CatalogDomainForm } from "./PlanningCatalogEditors";

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
	vi.clearAllMocks();
});

describe("CatalogDomainForm global architecture context", () => {
	it("loads root business categories directly and keeps the parent selector editable", async () => {
		mocks.listPlanningCatalogDomains.mockResolvedValue([
			{ id: "category-1", code: "FIN", name: "财务", parentId: null },
			{ id: "category-2", code: "RND", name: "研发", parentId: null },
			{ id: "domain-1", code: "BUDGET", name: "预算域", parentId: "category-1" },
		]);

		await act(async () =>
			root.render(<CatalogDomainForm canMaintain initial={null} onDone={vi.fn()} view="domains" />),
		);
		await act(async () => undefined);

		const selector = container.querySelector("select") as HTMLSelectElement;
		expect(selector).not.toBeNull();
		expect(selector.disabled).toBe(false);
		expect([...selector.options].map((option) => option.value)).toEqual(["", "category-1", "category-2"]);
		expect(container.textContent).not.toContain("建模策略");
	});

	it("preselects the only root category without hiding the selector", async () => {
		mocks.listPlanningCatalogDomains.mockResolvedValue([
			{ id: "category-1", code: "FIN", name: "财务", parentId: null },
		]);

		await act(async () =>
			root.render(<CatalogDomainForm canMaintain initial={null} onDone={vi.fn()} view="domains" />),
		);
		await act(async () => undefined);

		const selector = container.querySelector("select") as HTMLSelectElement;
		expect(selector.value).toBe("category-1");
		expect(container.textContent).toContain("仍可按实际归属调整");
	});
});
