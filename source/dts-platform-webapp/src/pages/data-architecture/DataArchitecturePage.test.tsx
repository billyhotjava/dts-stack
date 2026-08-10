// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ planningPageProps: vi.fn() }));

vi.mock("@/pages/data-modeling/prototype/PlanningPage", () => ({
	PlanningPage: (props: unknown) => {
		mocks.planningPageProps(props);
		return <div data-testid="planning-page-stub" />;
	},
}));

import DataArchitecturePage from "./DataArchitecturePage";

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

describe("DataArchitecturePage", () => {
	it("preserves the modeling-space navigation surface without changing the architecture permission surface", async () => {
		await act(async () =>
			root.render(
				<MemoryRouter
					initialEntries={["/data-architecture?view=business-domains&source=modeling-space&planningView=domains"]}
				>
					<DataArchitecturePage />
				</MemoryRouter>,
			),
		);

		expect(mocks.planningPageProps).toHaveBeenLastCalledWith(
			expect.objectContaining({
				navigationSurface: "modeling",
				sidebarActiveView: "domains",
				surface: "architecture",
			}),
		);
	});
});
