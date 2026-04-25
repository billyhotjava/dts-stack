// @vitest-environment jsdom
import { afterEach, beforeAll, describe, expect, it } from "vitest";
import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { MoMSecondary, RatioSecondary, StaticSecondary } from "./KpiSecondary";

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

function render(element: ReactElement): { container: HTMLElement; unmount: () => void } {
	const container = document.createElement("div");
	document.body.appendChild(container);
	let root!: Root;
	act(() => {
		root = createRoot(container);
		root.render(element);
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

describe("MoMSecondary", () => {
	afterEach(() => {
		document.body.innerHTML = "";
	});

	it("MoMSecondary_renders_null_when_mom_is_null", () => {
		const { container, unmount } = render(<MoMSecondary mom={null} />);
		expect(container.innerHTML.trim()).toBe("");
		unmount();
	});

	it("MoMSecondary_renders_null_when_mom_is_undefined", () => {
		const { container, unmount } = render(<MoMSecondary mom={undefined} />);
		expect(container.innerHTML.trim()).toBe("");
		unmount();
	});

	it("MoMSecondary_renders_positive_green_up", () => {
		const { container, unmount } = render(<MoMSecondary mom={0.08} />);
		const span = container.querySelector("span");
		expect(span).not.toBeNull();
		expect(span?.getAttribute("style")).toContain("rgb(82, 196, 26)");
		expect(container.textContent).toContain("环比");
		expect(container.textContent).toContain("8.0%");
		// ArrowUpOutlined should be present
		expect(container.querySelector(".anticon-arrow-up")).not.toBeNull();
		unmount();
	});

	it("MoMSecondary_renders_negative_red_down", () => {
		const { container, unmount } = render(<MoMSecondary mom={-0.12} />);
		const span = container.querySelector("span");
		expect(span?.getAttribute("style")).toContain("rgb(255, 77, 79)");
		expect(container.textContent).toContain("12.0%");
		expect(container.querySelector(".anticon-arrow-down")).not.toBeNull();
		unmount();
	});

	it("MoMSecondary_renders_zero_gray_持平", () => {
		const { container, unmount } = render(<MoMSecondary mom={0} />);
		const span = container.querySelector("span");
		expect(span?.getAttribute("style")).toContain("rgb(140, 140, 140)");
		expect(container.textContent).toContain("持平");
		expect(container.querySelector(".anticon-arrow-up")).toBeNull();
		expect(container.querySelector(".anticon-arrow-down")).toBeNull();
		unmount();
	});
});

describe("RatioSecondary", () => {
	afterEach(() => {
		document.body.innerHTML = "";
	});

	it("RatioSecondary_renders_null_nothing", () => {
		const { container, unmount } = render(<RatioSecondary ratio={null} />);
		expect(container.innerHTML.trim()).toBe("");
		unmount();
	});

	it("RatioSecondary_renders_percent", () => {
		const { container, unmount } = render(<RatioSecondary ratio={0.234} />);
		expect(container.textContent).toContain("占比");
		expect(container.textContent).toContain("23.4%");
		unmount();
	});
});

describe("StaticSecondary", () => {
	afterEach(() => {
		document.body.innerHTML = "";
	});

	it("StaticSecondary_renders_null_when_text_is_empty", () => {
		const { container, unmount } = render(<StaticSecondary text="" />);
		expect(container.innerHTML.trim()).toBe("");
		unmount();
	});

	it("StaticSecondary_renders_given_text", () => {
		const { container, unmount } = render(<StaticSecondary text="近 30 天访问过" />);
		expect(container.textContent).toBe("近 30 天访问过");
		unmount();
	});
});
