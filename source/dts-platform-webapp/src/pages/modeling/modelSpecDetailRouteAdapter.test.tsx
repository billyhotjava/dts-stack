// @vitest-environment jsdom

import { useEffect } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useModelSpecDetailRouteAdapter } from "./modelSpecDetailRouteAdapter";

type ResolvedContext = { modelSpecId: string; planId: string };

let container: HTMLDivElement;
let root: Root;
let latestNotify: ((context: ResolvedContext) => void) | undefined;

function Harness({ onResolvedContext }: { onResolvedContext: (context: ResolvedContext) => void }) {
	const { notifyResolvedContext } = useModelSpecDetailRouteAdapter({
		modelSpecIdOverride: "model-79",
		onResolvedContext,
	});
	useEffect(() => {
		latestNotify = notifyResolvedContext;
	}, [notifyResolvedContext]);
	return null;
}

const render = (onResolvedContext: (context: ResolvedContext) => void) => {
	act(() => {
		root.render(
			<MemoryRouter initialEntries={["/?activeStage=logical"]}>
				<Harness onResolvedContext={onResolvedContext} />
			</MemoryRouter>,
		);
	});
};

beforeEach(() => {
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
	latestNotify = undefined;
});

afterEach(() => {
	act(() => root.unmount());
	container.remove();
});

describe("useModelSpecDetailRouteAdapter", () => {
	it("keeps context notification stable while dispatching to the latest callback", () => {
		const firstHandler = vi.fn();
		const secondHandler = vi.fn();
		render(firstHandler);
		const firstNotify = latestNotify;
		expect(firstNotify).toBeTypeOf("function");

		render(secondHandler);
		expect(latestNotify).toBe(firstNotify);

		const context = { modelSpecId: "model-79", planId: "plan-79" };
		act(() => latestNotify?.(context));
		expect(firstHandler).not.toHaveBeenCalled();
		expect(secondHandler).toHaveBeenCalledWith(context);
	});
});
