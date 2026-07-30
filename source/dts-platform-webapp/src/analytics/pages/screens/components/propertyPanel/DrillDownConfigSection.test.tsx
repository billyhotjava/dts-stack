// @vitest-environment jsdom
import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it } from "vitest";
import { buildScreenPayload, validateScreenPayload } from "../../screenSpec";
import type { ScreenComponent, ScreenConfig } from "../../types";
import { DrillDownConfigSection } from "./DrillDownConfigSection";

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

function findButton(container: HTMLElement, label: string) {
	return Array.from(container.querySelectorAll("button")).find((button) => button.textContent?.includes(label));
}

async function setInputValue(input: HTMLInputElement, value: string) {
	await act(async () => {
		Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set?.call(input, value);
		input.dispatchEvent(new Event("input", { bubbles: true }));
	});
}

describe("DrillDownConfigSection", () => {
	let container: HTMLDivElement | null = null;

	afterEach(() => {
		container?.remove();
		container = null;
	});

	it("keeps an incomplete new level out of the persisted screen until explicitly confirmed", async () => {
		const initialComponent: ScreenComponent = {
			id: "chart-1",
			type: "line-chart",
			name: "趋势图",
			x: 0,
			y: 0,
			width: 640,
			height: 360,
			zIndex: 1,
			locked: false,
			visible: true,
			config: {},
			dataSource: {
				type: "api",
				sourceType: "api",
				apiConfig: { url: "/bi/api/example", method: "GET" },
			},
		};
		let component = initialComponent;
		const config = (): ScreenConfig => ({
			id: "draft",
			name: "Drill draft",
			width: 1920,
			height: 1080,
			backgroundColor: "#08121f",
			components: [component],
		});
		const updateComponent = (_id: string, updates: Partial<ScreenComponent>) => {
			component = { ...component, ...updates };
			render();
		};

		container = document.createElement("div");
		document.body.appendChild(container);
		const root = createRoot(container);
		const render = () =>
			root.render(
				<DrillDownConfigSection
					component={component}
					updateComponent={updateComponent}
					globalVariables={[]}
					embedded
				/>,
			);

		await act(async () => render());
		await act(async () => {
			(container?.querySelector('input[type="checkbox"]') as HTMLInputElement).click();
		});
		await act(async () => findButton(container as HTMLElement, "添加下钻层级")?.click());

		expect(component.drillDown?.levels).toEqual([]);
		expect(findButton(container, "保存此层级")?.hasAttribute("disabled")).toBe(true);
		expect(validateScreenPayload(buildScreenPayload(config())).errors).toEqual([]);

		await setInputValue(container.querySelector('input[placeholder="明细"]') as HTMLInputElement, "项目明细");
		await act(async () => findButton(container as HTMLElement, "添加字段映射")?.click());
		await setInputValue(container.querySelector('input[placeholder="selectedKey"]') as HTMLInputElement, "projectId");
		expect(findButton(container, "保存此层级")?.hasAttribute("disabled")).toBe(false);
		await act(async () => findButton(container as HTMLElement, "保存此层级")?.click());

		expect(component.drillDown?.levels).toHaveLength(1);
		expect(component.drillDown?.levels[0].label).toBe("项目明细");
		expect(validateScreenPayload(buildScreenPayload(config())).errors).toEqual([]);

		await act(async () => root.unmount());
	});
});
