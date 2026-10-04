// @vitest-environment jsdom
import { act } from "react";
import { createRoot } from "react-dom/client";
import { expect, it, vi } from "vitest";
import { ScreenListPreview, ScreenPreviewName } from "./ScreenListPreview";

vi.mock("../../../helpers/resolveAnalyticsUrl", () => ({
	resolveRouteForOpen: (path: string) => `/#${path}`,
}));

it("selects names into a single preview, switches content, and releases it on close without opening tabs", async () => {
	globalThis.IS_REACT_ACT_ENVIRONMENT = true;
	const container = document.createElement("div");
	document.body.appendChild(container);
	const root = createRoot(container);
	const open = vi.spyOn(window, "open").mockImplementation(() => null);
	try {
		await act(async () =>
			root.render(
				<ScreenListPreview>
					<ScreenPreviewName screen={{ id: 1, name: "生产总览", canRead: true }} />
					<ScreenPreviewName screen={{ id: 2, name: "质量总览", canRead: true }} />
					<ScreenPreviewName screen={{ id: 3, name: "无权查看", canRead: false }} />
				</ScreenListPreview>,
			),
		);
		expect(document.querySelector("iframe")).toBeNull();
		const names = container.querySelectorAll<HTMLButtonElement>("button[data-testid]");
		expect(names[2].disabled).toBe(true);
		await act(async () => names[0].click());
		let frame = document.querySelector("iframe")!;
		expect(frame.getAttribute("src")).toBe("/#/bi/screens/1/preview?embed=1&scaleMode=fit");
		expect(frame.title).toBe("生产总览预览");
		await act(async () => frame.dispatchEvent(new Event("load")));
		expect(frame.style.visibility).toBe("visible");
		await act(async () => names[1].click());
		expect(document.querySelectorAll("iframe")).toHaveLength(1);
		frame = document.querySelector("iframe")!;
		expect(frame.title).toBe("质量总览预览");
		expect(frame.getAttribute("src")).toContain("/screens/2/preview?");
		expect(frame.style.visibility).toBe("hidden");
		await act(async () => document.querySelector<HTMLButtonElement>(".ant-drawer-close")!.click());
		expect(document.querySelector("iframe")).toBeNull();
		expect(open).not.toHaveBeenCalled();
	} finally {
		await act(async () => root.unmount());
		container.remove();
		open.mockRestore();
	}
});
