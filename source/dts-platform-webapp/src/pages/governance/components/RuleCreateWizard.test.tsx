// @vitest-environment jsdom

import { act, type ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const { createQualityRule, getDefaultDestinationStatus, listQualityTemplates, previewTemplateSQL } = vi.hoisted(() => ({
	createQualityRule: vi.fn(),
	getDefaultDestinationStatus: vi.fn(),
	listQualityTemplates: vi.fn(),
	previewTemplateSQL: vi.fn(),
}));

vi.mock("@/api/platformApi", () => ({
	createQualityRule,
	listQualityTemplates,
	previewTemplateSQL,
}));

vi.mock("@/api/ingestion", () => ({
	ingestionTaskAPI: {
		getDefaultDestinationStatus,
	},
}));

vi.mock("@/components/catalog/DatasetPicker", async () => {
	const React = await import("react");
	return {
		DatasetPicker: ({ onChange, onDatasetSelected }: any) =>
			React.createElement(
				"div",
				null,
				React.createElement(
					"button",
					{
						type: "button",
						onClick: () => {
							onDatasetSelected?.({
								id: "dataset-1",
								name: "订单资产",
								hiveDatabase: "public",
								hiveTable: "ods_orders",
								warehouseLayer: "ODS",
							});
							onChange?.("dataset-1");
						},
					},
					"选择订单资产",
				),
				React.createElement(
					"button",
					{
						type: "button",
						onClick: () => {
							onDatasetSelected?.({
								id: "dataset-2",
								name: "退款资产",
								hiveDatabase: "public",
								hiveTable: "ods_refunds",
								warehouseLayer: "ODS",
							});
							onChange?.("dataset-2");
						},
					},
					"选择退款资产",
				),
				React.createElement(
					"button",
					{
						type: "button",
						onClick: () => {
							onDatasetSelected?.({
								id: "dataset-without-table",
								name: "缺少物理表的资产",
								warehouseLayer: "ODS",
							});
							onChange?.("dataset-without-table");
						},
					},
					"选择缺少物理表的资产",
				),
				React.createElement(
					"button",
					{
						type: "button",
						onClick: () => {
							onDatasetSelected?.(undefined);
							onChange?.(undefined);
						},
					},
					"清空资产",
				),
			),
	};
});

vi.mock("sonner", () => ({
	toast: {
		error: vi.fn(),
		success: vi.fn(),
	},
}));

if (!window.matchMedia) {
	Object.defineProperty(window, "matchMedia", {
		writable: true,
		value: vi.fn().mockImplementation(() => ({
			addEventListener: vi.fn(),
			addListener: vi.fn(),
			dispatchEvent: vi.fn(),
			matches: false,
			media: "",
			onchange: null,
			removeEventListener: vi.fn(),
			removeListener: vi.fn(),
		})),
	});
}

class ResizeObserverStub {
	observe() {}
	unobserve() {}
	disconnect() {}
}

vi.stubGlobal("ResizeObserver", ResizeObserverStub);
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
const getComputedStyle = window.getComputedStyle.bind(window);
vi.spyOn(window, "getComputedStyle").mockImplementation((element) => getComputedStyle(element));
vi.setConfig({ testTimeout: 15_000 });

async function flushAsyncWork() {
	await act(async () => {
		await Promise.resolve();
		await Promise.resolve();
		await Promise.resolve();
	});
}

async function renderAndFlush(ui: ReactElement): Promise<{ root: Root; unmount: () => void }> {
	const container = document.createElement("div");
	document.body.appendChild(container);
	let root!: Root;
	await act(async () => {
		root = createRoot(container);
		root.render(ui);
	});
	await flushAsyncWork();
	return {
		root,
		unmount: () => {
			act(() => root.unmount());
			container.remove();
		},
	};
}

function clickText(text: string) {
	const element =
		Array.from(document.body.querySelectorAll<HTMLButtonElement>("button")).find(
			(candidate) => candidate.textContent?.replace(/\s/g, "") === text.replace(/\s/g, ""),
		) ??
		Array.from(document.body.querySelectorAll<HTMLElement>("div")).find(
			(candidate) => candidate.textContent?.replace(/\s/g, "") === text.replace(/\s/g, ""),
		);
	if (!element) throw new Error(`找不到元素: ${text}; 当前页面: ${document.body.textContent}`);
	const clickTarget = element.closest<HTMLElement>(".cursor-pointer") ?? element;
	if (clickTarget instanceof HTMLButtonElement && clickTarget.disabled) {
		throw new Error(`按钮不可用: ${text}; 当前页面: ${document.body.textContent}`);
	}
	act(() => clickTarget.click());
}

function setInput(placeholder: string, value: string) {
	const input = document.body.querySelector(`input[placeholder="${placeholder}"]`) as HTMLInputElement | null;
	if (!input) throw new Error(`找不到输入框: ${placeholder}; 当前页面: ${document.body.textContent}`);
	const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value")?.set;
	act(() => {
		setter?.call(input, value);
		input.dispatchEvent(new Event("input", { bubbles: true }));
	});
}

function setTextarea(placeholderFragment: string, value: string) {
	const textarea = Array.from(document.body.querySelectorAll<HTMLTextAreaElement>("textarea")).find((candidate) =>
		candidate.placeholder.includes(placeholderFragment),
	);
	if (!textarea) throw new Error(`找不到文本框: ${placeholderFragment}`);
	const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, "value")?.set;
	act(() => {
		setter?.call(textarea, value);
		textarea.dispatchEvent(new Event("input", { bubbles: true }));
	});
}

async function moveToTemplateConfiguration() {
	clickText("从模板创建");
	await flushAsyncWork();
	clickText("非空检查");
	await flushAsyncWork();
	clickText("下一步");
	await flushAsyncWork();
	setInput("例如：订单金额非空检查", "订单金额非空规则");
	setInput("检查列", "amount");
}

describe("RuleCreateWizard", () => {
	beforeEach(() => {
		listQualityTemplates.mockResolvedValue([
			{
				id: "template-1",
				code: "NOT_NULL",
				name: "非空检查",
				category: "COMPLETENESS",
				paramSchema: [
					{ name: "table", label: "目标表", type: "table_select", required: true },
					{ name: "column", label: "检查列", type: "column_select", required: true },
				],
			},
		]);
		getDefaultDestinationStatus.mockResolvedValue({
			available: true,
			dataSourceId: "lake-1",
			destinationName: "数仓 (biadmin)",
		});
		previewTemplateSQL.mockImplementation((_id: string, params: { table?: string }) => {
			if (!params.table) return Promise.reject(new Error("参数 [目标表] 不能为空"));
			return Promise.resolve({ sql: `select * from ${params.table} where amount is null` });
		});
		createQualityRule.mockResolvedValue({ id: "rule-1" });
	});

	afterEach(() => {
		vi.clearAllMocks();
		document.body.innerHTML = "";
	});

	it("publishes the selected asset physical table when template schema is returned as a JSON array", async () => {
		const { default: RuleCreateWizard } = await import("./RuleCreateWizard");
		const onSuccess = vi.fn();
		const { unmount } = await renderAndFlush(<RuleCreateWizard open onClose={vi.fn()} onSuccess={onSuccess} />);

		await moveToTemplateConfiguration();
		clickText("下一步");
		await flushAsyncWork();

		clickText("选择订单资产");
		await flushAsyncWork();
		clickText("发布");
		await flushAsyncWork();

		expect(previewTemplateSQL).toHaveBeenCalledWith("template-1", {
			table: "ods_orders",
			column: "amount",
		});
		expect(createQualityRule).toHaveBeenCalledWith(
			expect.objectContaining({
				datasetId: "dataset-1",
				templateParams: JSON.stringify({ table: "ods_orders", column: "amount" }),
			}),
		);
		expect(onSuccess).toHaveBeenCalledOnce();
		unmount();
	});

	it("updates the target table and invalidates the old SQL preview when the selected asset changes", async () => {
		const { default: RuleCreateWizard } = await import("./RuleCreateWizard");
		const { unmount } = await renderAndFlush(<RuleCreateWizard open onClose={vi.fn()} onSuccess={vi.fn()} />);
		await moveToTemplateConfiguration();
		clickText("下一步");
		await flushAsyncWork();

		clickText("选择订单资产");
		clickText("上一步");
		await flushAsyncWork();
		clickText("预览 SQL");
		await flushAsyncWork();
		expect(document.body.textContent).toContain("已生成");

		clickText("下一步");
		await flushAsyncWork();
		clickText("选择退款资产");
		clickText("上一步");
		await flushAsyncWork();

		expect(document.body.textContent).not.toContain("已生成");
		clickText("预览 SQL");
		await flushAsyncWork();
		expect(previewTemplateSQL).toHaveBeenLastCalledWith("template-1", {
			table: "ods_refunds",
			column: "amount",
		});
		unmount();
	});

	it("clears the old target table and does not publish when the asset is cleared or lacks a physical table", async () => {
		const { default: RuleCreateWizard } = await import("./RuleCreateWizard");
		const { unmount } = await renderAndFlush(<RuleCreateWizard open onClose={vi.fn()} onSuccess={vi.fn()} />);
		await moveToTemplateConfiguration();
		clickText("下一步");
		await flushAsyncWork();

		clickText("选择订单资产");
		clickText("清空资产");
		clickText("发布");
		await flushAsyncWork();
		expect(previewTemplateSQL).not.toHaveBeenCalled();
		expect(createQualityRule).not.toHaveBeenCalled();

		clickText("选择缺少物理表的资产");
		clickText("发布");
		await flushAsyncWork();
		expect(previewTemplateSQL).toHaveBeenLastCalledWith("template-1", {
			table: undefined,
			column: "amount",
		});
		expect(createQualityRule).not.toHaveBeenCalled();
		unmount();
	});

	it("does not reuse a template target table after switching through custom SQL mode", async () => {
		const { default: RuleCreateWizard } = await import("./RuleCreateWizard");
		const { unmount } = await renderAndFlush(<RuleCreateWizard open onClose={vi.fn()} onSuccess={vi.fn()} />);
		await moveToTemplateConfiguration();
		clickText("下一步");
		await flushAsyncWork();
		clickText("选择订单资产");

		clickText("上一步");
		await flushAsyncWork();
		clickText("上一步");
		await flushAsyncWork();
		clickText("自定义 SQL");
		clickText("下一步");
		await flushAsyncWork();
		setInput("例如：订单金额非空检查", "自定义检查");
		setTextarea("SELECT count(*)", "SELECT 0 AS fail_count");
		clickText("下一步");
		await flushAsyncWork();
		clickText("清空资产");

		clickText("上一步");
		await flushAsyncWork();
		clickText("上一步");
		await flushAsyncWork();
		clickText("从模板创建");
		clickText("非空检查");
		clickText("下一步");
		await flushAsyncWork();
		setInput("例如：订单金额非空检查", "重新选择模板规则");
		setInput("检查列", "amount");
		clickText("下一步");
		await flushAsyncWork();
		clickText("发布");
		await flushAsyncWork();

		expect(previewTemplateSQL).not.toHaveBeenCalled();
		expect(createQualityRule).not.toHaveBeenCalled();
		unmount();
	});
});
