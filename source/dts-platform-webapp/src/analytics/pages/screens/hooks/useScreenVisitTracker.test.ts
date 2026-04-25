// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createElement, type FC } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import reportsService from "@/api/services/reportsService";
import { useScreenVisitTracker } from "./useScreenVisitTracker";

vi.mock("@/api/services/reportsService", () => ({
	default: { visit: vi.fn() },
}));

const visitMock = reportsService.visit as ReturnType<typeof vi.fn>;

interface HarnessProps {
	screenId: string | number | null | undefined;
	title?: string;
	enabled: boolean;
}

const Harness: FC<HarnessProps> = (props) => {
	useScreenVisitTracker(props);
	return null;
};

function mount(props: HarnessProps): { root: Root; container: HTMLDivElement } {
	const container = document.createElement("div");
	document.body.appendChild(container);
	const root = createRoot(container);
	act(() => {
		root.render(createElement(Harness, props));
	});
	return { root, container };
}

function unmount(root: Root, container: HTMLDivElement): void {
	act(() => {
		root.unmount();
	});
	container.remove();
}

describe("useScreenVisitTracker", () => {
	beforeEach(() => {
		vi.useFakeTimers();
		sessionStorage.clear();
		visitMock.mockReset();
		visitMock.mockResolvedValue({ ok: true });
	});

	afterEach(() => {
		vi.useRealTimers();
	});

	it("fires reportsService.visit after 3s when enabled", async () => {
		const { root, container } = mount({ screenId: 1, title: "Sales", enabled: true });
		expect(visitMock).not.toHaveBeenCalled();

		await act(async () => {
			await vi.advanceTimersByTimeAsync(3000);
		});

		expect(visitMock).toHaveBeenCalledOnce();
		expect(visitMock).toHaveBeenCalledWith({
			code: "screen-1",
			title: "Sales",
			url: "/bi/screens/1/preview",
		});
		unmount(root, container);
	});

	it("does not fire when unmounted before 3s", async () => {
		const { root, container } = mount({ screenId: 1, title: "Sales", enabled: true });
		await act(async () => {
			await vi.advanceTimersByTimeAsync(1500);
		});
		unmount(root, container);
		await act(async () => {
			await vi.advanceTimersByTimeAsync(5000);
		});

		expect(visitMock).not.toHaveBeenCalled();
	});

	it("does not re-fire within 30s for same id", async () => {
		const first = mount({ screenId: 1, title: "Sales", enabled: true });
		await act(async () => {
			await vi.advanceTimersByTimeAsync(3000);
		});
		expect(visitMock).toHaveBeenCalledOnce();
		unmount(first.root, first.container);

		const second = mount({ screenId: 1, title: "Sales", enabled: true });
		await act(async () => {
			await vi.advanceTimersByTimeAsync(3000);
		});
		expect(visitMock).toHaveBeenCalledOnce();
		unmount(second.root, second.container);
	});

	it("does not start timer when enabled=false", async () => {
		const { root, container } = mount({ screenId: 1, title: "Sales", enabled: false });
		await act(async () => {
			await vi.advanceTimersByTimeAsync(5000);
		});

		expect(visitMock).not.toHaveBeenCalled();
		unmount(root, container);
	});

	it("swallows visit errors silently", async () => {
		visitMock.mockRejectedValueOnce(new Error("boom"));

		const { root, container } = mount({ screenId: 2, title: "B", enabled: true });
		await act(async () => {
			await vi.advanceTimersByTimeAsync(3000);
			await Promise.resolve();
			await Promise.resolve();
		});

		expect(visitMock).toHaveBeenCalledOnce();
		unmount(root, container);
	});
});
