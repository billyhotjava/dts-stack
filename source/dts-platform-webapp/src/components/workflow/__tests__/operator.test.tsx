// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";

const zoomInMock = vi.fn();
const zoomOutMock = vi.fn();
const fitViewMock = vi.fn();

vi.mock("@xyflow/react", () => {
	function Panel({ children, className }: { children: ReactNode; className?: string }) {
		return <div className={className}>{children}</div>;
	}
	return {
		Panel,
		useReactFlow: () => ({
			zoomIn: zoomInMock,
			zoomOut: zoomOutMock,
			fitView: fitViewMock,
		}),
		useViewport: () => ({ x: 0, y: 0, zoom: 1.5 }),
	};
});

import { Operator } from "../operator/operator";
import { ZoomControls } from "../operator/zoom-controls";

let root: Root | null = null;
let container: HTMLDivElement | null = null;

function render(node: ReactNode) {
	container = document.createElement("div");
	document.body.appendChild(container);
	act(() => {
		root = createRoot(container as HTMLDivElement);
		root.render(node);
	});
	return container as HTMLDivElement;
}

afterEach(() => {
	if (root && container) {
		act(() => root!.unmount());
		document.body.removeChild(container);
	}
	root = null;
	container = null;
	zoomInMock.mockReset();
	zoomOutMock.mockReset();
	fitViewMock.mockReset();
});

describe("Operator toolbar", () => {
	it("renders all 4 sections (zoom / fit / screenshot / undo-redo)", () => {
		const host = render(<Operator />);
		expect(host.querySelectorAll('button[aria-label="缩小"]')).toHaveLength(1);
		expect(host.querySelectorAll('button[aria-label="放大"]')).toHaveLength(1);
		expect(host.querySelectorAll('button[aria-label="适配画布"]')).toHaveLength(1);
		expect(host.querySelectorAll('button[aria-label="导出 PNG（功能开发中）"]')).toHaveLength(1);
		expect(host.querySelectorAll('button[aria-label^="撤销"]')).toHaveLength(1);
		expect(host.querySelectorAll('button[aria-label^="重做"]')).toHaveLength(1);
		expect(host.querySelector('[role="toolbar"]')).not.toBeNull();
	});

	it("undo / redo / screenshot buttons are disabled until later sprints", () => {
		const host = render(<Operator />);
		const undo = host.querySelector<HTMLButtonElement>('button[aria-label^="撤销"]');
		const redo = host.querySelector<HTMLButtonElement>('button[aria-label^="重做"]');
		const shot = host.querySelector<HTMLButtonElement>('button[aria-label^="导出 PNG"]');
		expect(undo?.disabled).toBe(true);
		expect(redo?.disabled).toBe(true);
		expect(shot?.disabled).toBe(true);
	});
});

describe("ZoomControls", () => {
	it("displays current zoom percent and triggers zoomIn / zoomOut", () => {
		const host = render(<ZoomControls />);
		expect(host.querySelector(".workflow-operator__zoom-display")?.textContent).toBe("150%");
		const minus = host.querySelector<HTMLButtonElement>('button[aria-label="缩小"]');
		const plus = host.querySelector<HTMLButtonElement>('button[aria-label="放大"]');
		act(() => minus?.click());
		expect(zoomOutMock).toHaveBeenCalledTimes(1);
		act(() => plus?.click());
		expect(zoomInMock).toHaveBeenCalledTimes(1);
	});
});
