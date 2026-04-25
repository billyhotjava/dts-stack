// @vitest-environment jsdom
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";

beforeAll(() => {
	if (!window.matchMedia) {
		Object.defineProperty(window, "matchMedia", {
			writable: true,
			value: (query: string) => ({
				matches: false,
				media: query,
				onchange: null,
				addListener: () => {},
				removeListener: () => {},
				addEventListener: () => {},
				removeEventListener: () => {},
				dispatchEvent: () => false,
			}),
		});
	}
});

type ListScreensResult = Promise<unknown>;
const listScreensMock = vi.fn<() => ListScreensResult>();

vi.mock("@/analytics/api/analyticsApi", () => ({
	analyticsApi: {
		listScreens: () => listScreensMock(),
	},
}));

import { ScreenStrip } from "./ScreenStrip";

async function renderAndFlush(element: ReactElement): Promise<{ container: HTMLElement; unmount: () => void }> {
	const container = document.createElement("div");
	document.body.appendChild(container);
	let root!: Root;
	await act(async () => {
		root = createRoot(container);
		root.render(element);
	});
	await act(async () => {
		await Promise.resolve();
		await Promise.resolve();
		await Promise.resolve();
		await Promise.resolve();
	});
	return {
		container,
		unmount: () => {
			act(() => {
				root.unmount();
			});
			container.remove();
		},
	};
}

function makeScreens(n: number) {
	return Array.from({ length: n }, (_, i) => ({ id: i + 1, name: `大屏 ${i + 1}` }));
}

const windowOpenMock = vi.fn();

describe("ScreenStrip", () => {
	beforeEach(() => {
		listScreensMock.mockReset();
		windowOpenMock.mockReset();
		Object.defineProperty(window, "open", {
			configurable: true,
			writable: true,
			value: windowOpenMock,
		});
	});

	afterEach(() => {
		document.body.innerHTML = "";
	});

	it("shows_skeleton_then_strip_when_list_nonempty", async () => {
		listScreensMock.mockResolvedValue(makeScreens(3));
		const { container, unmount } = await renderAndFlush(<ScreenStrip />);
		expect(container.querySelectorAll(".ant-tag").length).toBe(3);
		unmount();
	});

	it("hides_when_list_empty", async () => {
		listScreensMock.mockResolvedValue([]);
		const { container, unmount } = await renderAndFlush(<ScreenStrip />);
		expect(container.innerHTML.trim()).toBe("");
		unmount();
	});

	it("hides_when_api_rejects", async () => {
		listScreensMock.mockRejectedValue(new Error("boom"));
		const { container, unmount } = await renderAndFlush(<ScreenStrip />);
		expect(container.innerHTML.trim()).toBe("");
		unmount();
	});

	it("shows_top_6_when_more_than_6_with_more_link", async () => {
		listScreensMock.mockResolvedValue(makeScreens(9));
		const { container, unmount } = await renderAndFlush(<ScreenStrip />);
		expect(container.querySelectorAll(".ant-tag").length).toBe(6);
		const moreLink = Array.from(container.querySelectorAll("a")).find((a) =>
			a.textContent?.includes("更多"),
		);
		expect(moreLink).toBeDefined();
		unmount();
	});

	it("no_more_link_when_exactly_6", async () => {
		listScreensMock.mockResolvedValue(makeScreens(6));
		const { container, unmount } = await renderAndFlush(<ScreenStrip />);
		expect(container.querySelectorAll(".ant-tag").length).toBe(6);
		const moreLink = Array.from(container.querySelectorAll("a")).find((a) =>
			a.textContent?.includes("更多"),
		);
		expect(moreLink).toBeUndefined();
		unmount();
	});

	it("chip_click_opens_preview_in_new_tab", async () => {
		listScreensMock.mockResolvedValue(makeScreens(2));
		const { container, unmount } = await renderAndFlush(<ScreenStrip />);
		const firstChip = container.querySelector(".ant-tag") as HTMLElement;
		act(() => {
			firstChip.click();
		});
		expect(windowOpenMock).toHaveBeenCalledWith(
			"/bi/screens/1/preview",
			"_blank",
			"noopener,noreferrer",
		);
		unmount();
	});
});
