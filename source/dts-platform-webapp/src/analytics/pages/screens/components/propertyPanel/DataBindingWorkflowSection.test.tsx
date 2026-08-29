// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, describe, expect, it } from "vitest";
import type { ComponentDataFeedback } from "../../ScreenDataFeedbackContext";
import type { ScreenComponent } from "../../types";
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
				rows: [
					["华东", 128],
					["华南", 96],
				],
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

	it("explains loading and stored-field fallback without inventing sample rows", async () => {
		await render(
			{ data: null, loading: true, error: null },
			createComponent({
				config: {
					_sourceColumns: [
						{ name: "region", displayName: "区域", baseType: "type/Text" },
						{ name: "amount", displayName: "销售额", baseType: "type/Decimal" },
					],
				},
			}),
		);

		expect(container?.textContent).toContain("正在读取样例数据");
		expect(container?.querySelector("table")).toBeNull();
	});

	it("keeps built-in component data distinct from external business sources", async () => {
		await render(undefined, createComponent({ dataSource: { type: "static" }, config: {} }));

		expect(container?.textContent).toContain("组件内置");
		expect(container?.textContent).toContain("当前使用组件内置数据");
		expect(container?.textContent).not.toContain("数据读取失败");
	});

	it("renders structured sample values safely, including non-serializable cells", async () => {
		const circular: Record<string, unknown> = {};
		circular.self = circular;
		const longText = "x".repeat(60);
		await render({
			data: {
				cols: [
					{ name: "profile", display_name: "概况", base_type: "type/JSON" },
					{ name: "detail", display_name: "明细", base_type: "type/JSON" },
					{ name: "note", display_name: "备注", base_type: "type/Text" },
					{ name: "empty", display_name: "空值", base_type: "type/Text" },
				],
				rows: [[{ level: "A" }, circular, longText, null]],
			},
			loading: false,
			error: null,
		});

		expect(container?.textContent).toContain('{"level":"A"}');
		expect(container?.textContent).toContain("[object Object]");
		expect(container?.textContent).toContain(`${"x".repeat(48)}…`);
		expect(container?.textContent).toContain("—");
	});

	it("uses saved field metadata while a dynamic source is waiting for its next sample", async () => {
		await render(
			undefined,
			createComponent({
				config: {
					_sourceColumns: [
						{ name: "region", displayName: "区域" },
						{ name: "amount", displayName: "销售额" },
					],
					_fieldMapping: { dimension: "region" },
					_useFieldMapping: false,
				},
			}),
		);

		expect(container?.textContent).toContain("已识别 2 个字段");
		expect(container?.textContent).toContain("字段已就绪");
		expect(container?.textContent).not.toContain("已映射 1 个展示字段");
	});

	it("distinguishes a successful empty result from a failed query", async () => {
		await render({
			data: {
				cols: [{ name: "region", display_name: "区域", base_type: "type/Text" }],
				rows: [],
			},
			loading: false,
			error: null,
		});

		expect(container?.textContent).toContain("已读取 1 个字段 · 0 行样例");
		expect(container?.textContent).toContain("查询成功，暂无样例行");
		expect(container?.textContent).not.toContain("数据读取失败");
	});
});
