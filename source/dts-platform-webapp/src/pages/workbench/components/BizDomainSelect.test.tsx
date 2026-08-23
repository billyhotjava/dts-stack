// @vitest-environment jsdom

import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// jsdom + antd 的整树渲染在并行跑批下会超过 vitest 默认的 5s，
// 这些用例本身很快，单跑 <1s，放宽文件级超时避免假红。
vi.setConfig({ testTimeout: 20_000 });

type MockState = {
	response: Promise<unknown> | null;
};
const mockState: MockState = { response: null };

vi.mock("@/api/services/catalogDomainService", () => ({
	default: {
		list: () => mockState.response ?? Promise.resolve([]),
	},
}));

const auditLogMock = vi.fn();
vi.mock("@/utils/audit", () => ({
	auditLog: (...args: unknown[]) => auditLogMock(...args),
}));

async function renderAndFlush(element: ReactElement): Promise<{ container: HTMLElement; unmount: () => void }> {
	const container = document.createElement("div");
	document.body.appendChild(container);
	let root!: Root;
	await act(async () => {
		root = createRoot(container);
		root.render(element);
	});
	await act(async () => {
		for (let i = 0; i < 20; i += 1) await new Promise((resolve) => setTimeout(resolve, 0));
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

describe("BizDomainSelect", () => {
	beforeEach(() => {
		auditLogMock.mockReset();
		mockState.response = null;
	});

	afterEach(() => {
		document.body.innerHTML = "";
	});

	it("shows_select_when_api_returns_list", async () => {
		mockState.response = Promise.resolve([
			{ code: "D1", name: "科研" },
			{ code: "D2", name: "财务" },
		]);
		const availability = vi.fn();
		const domainLabels = vi.fn();
		const { BizDomainSelect } = await import("./BizDomainSelect");
		const { container, unmount } = await renderAndFlush(
			<BizDomainSelect
				value={null}
				onChange={() => {}}
				onAvailabilityChange={availability}
				onDomainLabelsChange={domainLabels}
			/>,
		);
		// A select has been rendered; ant-design renders the selector with role=combobox.
		expect(container.querySelector(".ant-select")).not.toBeNull();
		expect(availability).toHaveBeenCalledWith(true);
		expect(domainLabels).toHaveBeenCalledWith({ D1: "科研", D2: "财务" });
		unmount();
	});

	it("hides_when_api_returns_empty_array", async () => {
		mockState.response = Promise.resolve([]);
		const availability = vi.fn();
		const { BizDomainSelect } = await import("./BizDomainSelect");
		const { container, unmount } = await renderAndFlush(
			<BizDomainSelect value={null} onChange={() => {}} onAvailabilityChange={availability} />,
		);
		expect(container.innerHTML.trim()).toBe("");
		expect(availability).toHaveBeenCalledWith(false);
		expect(auditLogMock).toHaveBeenCalledWith("WORKBENCH_DOMAIN_API_EMPTY", expect.any(Object));
		unmount();
	});

	it("hides_when_api_rejects", async () => {
		mockState.response = Promise.reject(new Error("boom"));
		const availability = vi.fn();
		const { BizDomainSelect } = await import("./BizDomainSelect");
		const { container, unmount } = await renderAndFlush(
			<BizDomainSelect value={null} onChange={() => {}} onAvailabilityChange={availability} />,
		);
		expect(container.innerHTML.trim()).toBe("");
		expect(availability).toHaveBeenCalledWith(false);
		expect(auditLogMock).toHaveBeenCalledWith("WORKBENCH_DOMAIN_API_FAIL", expect.any(Object));
		unmount();
	});

	it("emits_availability_true_only_when_list_nonempty", async () => {
		mockState.response = Promise.resolve([{ code: "D1", name: "科研" }]);
		const availability = vi.fn();
		const { BizDomainSelect } = await import("./BizDomainSelect");
		const { unmount } = await renderAndFlush(
			<BizDomainSelect value={null} onChange={() => {}} onAvailabilityChange={availability} />,
		);
		expect(availability).toHaveBeenCalledTimes(1);
		expect(availability).toHaveBeenLastCalledWith(true);
		unmount();
	});

	it("defaults_display_value_to_ALL_when_value_is_null", async () => {
		mockState.response = Promise.resolve([{ code: "D1", name: "科研" }]);
		const availability = vi.fn();
		const { BizDomainSelect } = await import("./BizDomainSelect");
		const { container, unmount } = await renderAndFlush(
			<BizDomainSelect value={null} onChange={() => {}} onAvailabilityChange={availability} />,
		);
		// The currently selected item should show the "全部业务域" label.
		expect(container.textContent).toContain("全部业务域");
		unmount();
	});
});
