// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AssetDeliveryStatusPanel } from "./AssetDeliveryStatusPanel";

const routerPush = vi.hoisted(() => vi.fn());

vi.mock("@/routes/hooks", () => ({ useRouter: () => ({ push: routerPush }) }));

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	window.matchMedia = vi.fn().mockImplementation((query: string) => ({
		matches: false,
		media: query,
		onchange: null,
		addListener: vi.fn(),
		removeListener: vi.fn(),
		addEventListener: vi.fn(),
		removeEventListener: vi.fn(),
		dispatchEvent: vi.fn(),
	}));
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
	routerPush.mockReset();
});

afterEach(async () => {
	await act(async () => root.unmount());
	container.remove();
});

describe("asset delivery status panel", () => {
	it("renders delivery evidence and returns to the canonical model workbench", async () => {
		await act(async () =>
			root.render(
				<AssetDeliveryStatusPanel
					dataset={{
						consumptionEligibility: "ELIGIBLE",
						servingSync: { status: "SYNCED" },
						qualityStatus: "PASSED",
						statusAxes: { publication: "PUBLISHED", serving: "HEALTHY" },
						modelRefs: [{ modelSpecId: "model-1", modelRevision: 3, serving: true }],
					}}
				/>,
			),
		);

		expect(container.textContent).toContain("交付与模型证据");
		expect(container.textContent).toContain("模型 r3（当前服务）");
		const modelLink = Array.from(container.querySelectorAll("button")).find((item) =>
			item.textContent?.includes("模型 r3"),
		);
		await act(async () => modelLink?.click());

		expect(routerPush).toHaveBeenCalledWith("/data-modeling/dimensions/workbench?modelSpecId=model-1");
	});
});
