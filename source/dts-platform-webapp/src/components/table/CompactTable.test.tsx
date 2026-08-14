// @vitest-environment jsdom

import type { ReactElement } from "react";
import { createRoot, type Root } from "react-dom/client";
import { act } from "react-dom/test-utils";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { CompactTable, compareCell, toSpringSort } from "./CompactTable";
import type { CompactColumns } from "./CompactTable";

// antd 的响应式 observer 依赖 matchMedia，jsdom 未实现。
if (typeof window.matchMedia !== "function") {
	window.matchMedia = ((query: string) => ({
		matches: false,
		media: query,
		onchange: null,
		addListener: () => {},
		removeListener: () => {},
		addEventListener: () => {},
		removeEventListener: () => {},
		dispatchEvent: () => false,
	})) as typeof window.matchMedia;
}
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

type Row = { key: string; name: string; lastRun: string; runs: number };

const ROWS: Row[] = [
	{ key: "b", name: "表10", lastRun: "2026-08-05 10:37:09", runs: 2 },
	{ key: "a", name: "表2", lastRun: "2026-08-07 21:53:08", runs: 10 },
	{ key: "c", name: "表3", lastRun: "2026-08-04 09:21:45", runs: 1 },
];

const COLUMNS: CompactColumns<Row> = [
	{ title: "接入名称", dataIndex: "name" },
	{ title: "最近运行", dataIndex: "lastRun" },
	{ title: "次数", dataIndex: "runs" },
	{ title: "操作", dataIndex: "actions", render: () => "详情" },
];

let container: HTMLDivElement;
let root: Root;

const render = (node: ReactElement) => {
	act(() => {
		root.render(node);
	});
};

const headerCells = () => Array.from(container.querySelectorAll("thead th"));

const bodyColumn = (index: number) =>
	// 排除 antd 的隐藏测量行（.ant-table-measure-row）
	Array.from(container.querySelectorAll("tbody tr.ant-table-row")).map(
		(tr) => tr.querySelectorAll("td")[index]?.textContent ?? "",
	);

beforeEach(() => {
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});

afterEach(() => {
	act(() => {
		root.unmount();
	});
	container.remove();
});

describe("compareCell", () => {
	it("orders numbers numerically rather than lexicographically", () => {
		expect(compareCell(2, 10)).toBeLessThan(0);
	});

	it("orders date-like strings chronologically", () => {
		expect(compareCell("2026-08-04 09:21:45", "2026-08-07 21:53:08")).toBeLessThan(0);
		expect(compareCell("2026-08-07T21:53:08", "2026-08-05T10:37:09")).toBeGreaterThan(0);
	});

	it("orders embedded numbers naturally so 表2 precedes 表10", () => {
		expect(compareCell("表2", "表10")).toBeLessThan(0);
	});

	it("treats null and empty string as the smallest value", () => {
		expect(compareCell(null, "a")).toBeLessThan(0);
		expect(compareCell("", "a")).toBeLessThan(0);
		expect(compareCell(undefined, null)).toBe(0);
	});
});

describe("toSpringSort", () => {
	it("renders the antd sorter as a Spring Data sort expression", () => {
		expect(toSpringSort({ columnKey: "lastRun", order: "descend" })).toBe("lastRun,desc");
		expect(toSpringSort({ columnKey: "lastRun", order: "ascend" })).toBe("lastRun,asc");
	});

	it("maps the column key onto the backend field name", () => {
		expect(toSpringSort({ columnKey: "lastRun", order: "descend" }, { lastRun: "lastModifiedDate" })).toBe(
			"lastModifiedDate,desc",
		);
	});

	it("falls back when no column carries an order", () => {
		expect(toSpringSort({ columnKey: "lastRun" }, {}, "lastModifiedDate,desc")).toBe("lastModifiedDate,desc");
		expect(toSpringSort(undefined, {}, "lastModifiedDate,desc")).toBe("lastModifiedDate,desc");
	});

	it("picks the first ordered entry out of a multi-column sorter", () => {
		expect(toSpringSort([{ columnKey: "name" }, { columnKey: "lastRun", order: "ascend" }])).toBe("lastRun,asc");
	});

	it("derives the key from field when columnKey is absent", () => {
		expect(toSpringSort({ field: ["source", "name"], order: "ascend" })).toBe("source.name,asc");
	});
});

describe("CompactTable auto sorting", () => {
	it("makes data columns sortable and leaves the action column alone", () => {
		render(<CompactTable<Row> columns={COLUMNS} dataSource={ROWS} rowKey="key" />);

		const headers = headerCells();
		expect(headers.map((th) => th.classList.contains("ant-table-column-has-sorters"))).toEqual([
			true,
			true,
			true,
			false,
		]);
		// 表头出现升/降箭头
		expect(container.querySelectorAll(".ant-table-column-sorter").length).toBe(3);
	});

	it("sorts rows chronologically when a time header is clicked", () => {
		render(<CompactTable<Row> columns={COLUMNS} dataSource={ROWS} rowKey="key" pagination={false} />);

		expect(bodyColumn(1)).toEqual(["2026-08-05 10:37:09", "2026-08-07 21:53:08", "2026-08-04 09:21:45"]);

		act(() => {
			headerCells()[1].querySelector<HTMLElement>(".ant-table-column-sorters")?.click();
		});

		expect(bodyColumn(1)).toEqual(["2026-08-04 09:21:45", "2026-08-05 10:37:09", "2026-08-07 21:53:08"]);
	});

	it("does not inject client sorting when pagination is server controlled", () => {
		render(
			<CompactTable<Row>
				columns={COLUMNS}
				dataSource={ROWS}
				rowKey="key"
				pagination={{ current: 1, pageSize: 10, total: 42 }}
			/>,
		);

		expect(container.querySelectorAll(".ant-table-column-has-sorters").length).toBe(0);
	});

	it("keeps an explicit sorter: false column unsorted", () => {
		const columns: CompactColumns<Row> = [
			{ title: "接入名称", dataIndex: "name", sorter: false },
			{ title: "最近运行", dataIndex: "lastRun" },
		];
		render(<CompactTable<Row> columns={columns} dataSource={ROWS} rowKey="key" />);

		const headers = headerCells();
		expect(headers[0].classList.contains("ant-table-column-has-sorters")).toBe(false);
		expect(headers[1].classList.contains("ant-table-column-has-sorters")).toBe(true);
	});

	it("can be disabled wholesale via autoSort={false}", () => {
		render(<CompactTable<Row> columns={COLUMNS} dataSource={ROWS} rowKey="key" autoSort={false} />);

		expect(container.querySelectorAll(".ant-table-column-has-sorters").length).toBe(0);
	});
});
