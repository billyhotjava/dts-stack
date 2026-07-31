import { JSDOM } from "jsdom";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import * as createModalModule from "../../src/pages/modeling/WarehousePlanCreateModal";

describe("WarehousePlanOnboardingModeSelector", () => {
	it("renders two native radio choices without nested labels", () => {
		const Selector = (
			createModalModule as typeof createModalModule & {
				WarehousePlanOnboardingModeSelector?: (props: {
					value: "BUSINESS_FIRST" | "ASSET_FIRST";
					disabled: boolean;
					onChange: (value: "BUSINESS_FIRST" | "ASSET_FIRST") => void;
				}) => React.ReactNode;
			}
		).WarehousePlanOnboardingModeSelector;
		expect(typeof Selector).toBe("function");
		if (!Selector) return;

		const markup = renderToStaticMarkup(<Selector value="BUSINESS_FIRST" disabled={false} onChange={vi.fn()} />);
		const document = new JSDOM(markup).window.document;
		const radios = [...document.querySelectorAll<HTMLInputElement>('input[type="radio"]')];
		const optionLabels = radios.map((radio) => radio.closest("label")?.textContent?.replace(/\s+/g, " ").trim());

		expect(radios).toHaveLength(2);
		expect(document.querySelectorAll("label label")).toHaveLength(0);
		expect(document.querySelector('[role="radiogroup"]')?.getAttribute("aria-label")).toBe("建设计划创建模式");
		expect(optionLabels).toEqual([
			expect.stringContaining("从业务目标开始"),
			expect.stringContaining("从现有数据开始"),
		]);
		expect(radios[0]?.name).not.toBe("");
		expect(radios[1]?.name).toBe(radios[0]?.name);
	});
});
