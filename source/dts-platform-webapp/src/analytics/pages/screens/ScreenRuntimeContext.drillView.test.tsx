// @vitest-environment jsdom
import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ScreenRuntimeProvider, useScreenRuntime } from "./ScreenRuntimeContext";

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

function DrillViewTrigger() {
	const runtime = useScreenRuntime();
	return (
		<>
			<output aria-label="当前筛选">{runtime.values.selectedKey}</output>
			<button
				type="button"
				onClick={() => runtime.drillView.drillToView("detail-view", "明细", { selectedKey: "A-01" })}
			>
				进入明细
			</button>
			<button type="button" onClick={() => runtime.drillView.drillToView("leaf-view", "叶子", { selectedKey: "B-02" })}>
				进入叶子
			</button>
		</>
	);
}

describe("ScreenRuntimeProvider drill-view navigation", () => {
	let container: HTMLDivElement | null = null;

	afterEach(() => {
		container?.remove();
		container = null;
	});

	it("notifies the page shell and exposes reset navigation", async () => {
		const onDrillViewChange = vi.fn();
		container = document.createElement("div");
		document.body.appendChild(container);
		const root = createRoot(container);

		await act(async () => {
			root.render(
				<ScreenRuntimeProvider
					definitions={[{ key: "selectedKey", label: "选择项", type: "string", defaultValue: "ROOT" }]}
					onDrillViewChange={onDrillViewChange}
				>
					<DrillViewTrigger />
				</ScreenRuntimeProvider>,
			);
		});
		expect(onDrillViewChange).not.toHaveBeenCalled();
		expect(container?.querySelector('[aria-label="当前筛选"]')?.textContent).toBe("ROOT");

		await act(async () => {
			(container?.querySelector("button") as HTMLButtonElement).click();
		});
		expect(onDrillViewChange).toHaveBeenLastCalledWith("detail-view");
		expect(container?.querySelector('[aria-label="内部视图导航"]')).not.toBeNull();
		expect(container?.querySelector('[aria-label="当前筛选"]')?.textContent).toBe("A-01");

		await act(async () => {
			(
				Array.from(container?.querySelectorAll("button") ?? []).find(
					(button) => button.textContent === "进入叶子",
				) as HTMLButtonElement
			).click();
		});
		expect(container?.querySelector('[aria-label="当前筛选"]')?.textContent).toBe("B-02");

		const rollUp = Array.from(container?.querySelectorAll("nav button") ?? []).find(
			(button) => button.textContent === "返回上一层",
		) as HTMLButtonElement;
		await act(async () => rollUp.click());
		expect(container?.querySelector('[aria-label="当前筛选"]')?.textContent).toBe("A-01");

		const reset = Array.from(container?.querySelectorAll("nav button") ?? []).find(
			(button) => button.textContent === "重置",
		) as HTMLButtonElement;
		await act(async () => reset.click());
		expect(onDrillViewChange).toHaveBeenLastCalledWith(null);
		expect(container?.querySelector('[aria-label="当前筛选"]')?.textContent).toBe("ROOT");

		await act(async () => root.unmount());
	});
});
