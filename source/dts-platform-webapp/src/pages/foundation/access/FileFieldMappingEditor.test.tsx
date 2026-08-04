import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { FileFieldMappingEditor } from "./FileFieldMappingEditor";

describe("FileFieldMappingEditor", () => {
	it("renders field controls and preview data in one horizontal column grid", () => {
		const preview = Array.from({ length: 12 }, (_, index) => [`order-${index + 1}`, `customer-${index + 1}`]);
		const html = renderToStaticMarkup(
			<FileFieldMappingEditor
				columns={[
					{ name: "order_id", label: "订单编号", type: "string" },
					{ name: "customer_name", label: "客户名称", type: "string" },
				]}
				targetColumns={[]}
				preview={preview}
				onChange={vi.fn()}
			/>,
		);

		expect(html).toContain("第 1 列");
		expect(html).toContain("第 2 列");
		expect(html).toContain("字段名称");
		expect(html).toContain("字段类型");
		expect(html).toContain("显示行数");
		expect(html).toContain("order-10");
		expect(html).toContain("customer-10");
		expect(html).not.toContain("order-11");
		expect(html).not.toContain("上传数据预览");
	});
});
