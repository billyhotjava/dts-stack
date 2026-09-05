// @vitest-environment jsdom
import { act } from "react";
import { createRoot } from "react-dom/client";
import { expect, it, vi } from "vitest";
import { SensitiveFieldDetectionPanel } from "./SensitiveFieldDetectionPanel";

const api = vi.hoisted(() => ({ fields: vi.fn(), list: vi.fn(), create: vi.fn() }));
vi.mock("@/api/platformApi", () => ({
	getDatasetFields: api.fields,
	listMaskingRules: api.list,
	createMaskingRule: api.create,
}));
vi.mock("antd", () => ({
	Card: ({ children }: any) => <div>{children}</div>,
	Space: ({ children }: any) => <div>{children}</div>,
	Alert: ({ message }: any) => <p>{message}</p>,
	Button: ({ children, disabled, onClick }: any) => (
		<button disabled={disabled} onClick={onClick}>
			{children}
		</button>
	),
	Select: ({ onChange, options, disabled }: any) => (
		<select disabled={disabled} onChange={(event) => onChange(event.target.value)}>
			<option value="">选择数据集</option>
			{options.map((item: any) => (
				<option key={item.value} value={item.value}>
					{item.label}
				</option>
			))}
		</select>
	),
}));
vi.mock("@/components/table", () => ({
	CompactTable: ({ dataSource, rowSelection }: any) => (
		<div>
			{dataSource.map((item: any) => (
				<label key={item.column}>
					<input
						type="checkbox"
						aria-label={item.column}
						checked={rowSelection.selectedRowKeys.includes(item.column)}
						onChange={() => rowSelection.onChange([...rowSelection.selectedRowKeys, item.column])}
					/>
					{item.column}
				</label>
			))}
		</div>
	),
}));

it("requires selection and retries only failed fields while skipping newly existing rules", async () => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	api.fields.mockResolvedValue([{ name: "phone" }, { name: "email" }, { name: "id_card" }]);
	api.list.mockResolvedValueOnce([]).mockResolvedValue([{ dataset: { id: "dataset-1" }, column: "id_card" }]);
	api.create.mockResolvedValueOnce({}).mockRejectedValueOnce(new Error("temporary")).mockResolvedValue({});
	const onComplete = vi.fn().mockResolvedValue(undefined);
	const node = document.createElement("div");
	const root = createRoot(node);
	const button = (text: string) =>
		Array.from(node.querySelectorAll("button")).find((item) => item.textContent?.includes(text))!;
	try {
		await act(async () =>
			root.render(
				<SensitiveFieldDetectionPanel datasets={[{ id: "dataset-1", name: "客户" }]} onComplete={onComplete} />,
			),
		);
		await act(async () => {
			const select = node.querySelector("select")!;
			select.value = "dataset-1";
			select.dispatchEvent(new Event("change", { bubbles: true }));
		});
		await act(async () => button("识别敏感字段").click());
		expect(button("保存选中规则").disabled).toBe(true);
		expect(api.create).not.toHaveBeenCalled();
		for (const name of ["phone", "email", "id_card"]) {
			await act(async () => (node.querySelector(`[aria-label="${name}"]`) as HTMLInputElement).click());
		}
		await act(async () => button("保存选中规则").click());
		expect(api.create).toHaveBeenCalledTimes(2);
		expect(node.textContent).toContain("以下字段保存失败，可重试：email");
		expect(node.querySelector('[aria-label="phone"]')).toBeNull();
		expect(node.querySelector('[aria-label="id_card"]')).toBeNull();
		await act(async () => button("保存选中规则").click());
		expect(api.create).toHaveBeenCalledTimes(3);
		expect(api.create.mock.calls.map(([payload]) => payload.column)).toEqual(["phone", "email", "email"]);
		expect(onComplete).toHaveBeenCalledTimes(2);
	} finally {
		await act(async () => root.unmount());
	}
});
