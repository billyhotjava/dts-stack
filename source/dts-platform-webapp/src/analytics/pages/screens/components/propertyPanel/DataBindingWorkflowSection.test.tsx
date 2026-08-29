// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, describe, expect, it } from "vitest";
import type { ScreenComponent } from "../../types";
import type { ComponentDataFeedback } from "../../ScreenDataFeedbackContext";
import { DataBindingWorkflowSection } from "./DataBindingWorkflowSection";

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

function createComponent(overrides: Partial<ScreenComponent> = {}): ScreenComponent {
	return {
		id: "chart-1",
		type: "bar-chart",
		name: "区域销售额",
		x: 0,
		y: 0,
		width: 640,
		height: 360,
		zIndex: 1,
		locked: false,
		visible: true,
		config: {
			_fieldMapping: { dimension: "region", measures: ["amount"] },
		},
		dataSource: {
			type: "card",
			sourceType: "card",
			cardConfig: { cardId: 42 },
		},
		...overrides,
	};
}

describe("DataBindingWorkflowSection", () => {
	let container: HTMLDivElement | null = null;
	let root: Root | null = null;

	afterEach(async () => {
		if (root) {
			await act(async () => root?.unmount());
		}
		container?.remove();
		container = null;
		root = null;
	});

	async function render(feedback?: ComponentDataFeedback, component = createComponent()) {
		container = document.createElement("div");
		document.body.appendChild(container);
		root = createRoot(container);
		await act(async () => {
			root?.render(<DataBindingWorkflowSection component={component} feedback={feedback} />);
		});
	}

	it("connects source selection, sample verification, and field mapping in one visible workflow", async () => {
		await render({
			data: {
				cols: [
					{ name: "region", display_name: "区域", base_type: "type/Text" },
					{ name: "amount", display_name: "销售额", base_type: "type/Decimal" },
				],
				rows: [["华东", 128], ["华南", 96]],
			},
			loading: false,
			error: null,
		});

		expect(container?.textContent).toContain("数据配置流程");
		expect(container?.textContent).toContain("1 数据来源");
		expect(container?.textContent).toContain("2 样例校验");
		expect(container?.textContent).toContain("3 字段映射");
		expect(container?.textContent).toContain("已读取 2 个字段 · 2 行样例");
		expect(container?.textContent).toContain("华东");
		expect(container?.textContent).toContain("128");
		expect(container?.textContent).toContain("已映射 2 个展示字段");
		expect(container?.textContent).toContain("样例数据仅用于当前编辑会话，不写入大屏配置");
	});

	it("shows the existing canvas query failure instead of starting a separate query", async () => {
		await render({ data: null, loading: false, error: "查询超时" });

		const alert = container?.querySelector('[role="alert"]');
		expect(alert?.textContent).toContain("数据读取失败");
		expect(alert?.textContent).toContain("查询超时");
		expect(container?.textContent).not.toContain("样例数据仅用于当前编辑会话");
	});
});
