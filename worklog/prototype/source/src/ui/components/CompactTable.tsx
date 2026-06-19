import { Table } from "antd";
import type { TablePaginationConfig, TableProps } from "antd";
import { useEffect, useState } from "react";

export const DEFAULT_PAGE_SIZE = 10;

export interface CompactColumn<T> {
	key: string;
	title: React.ReactNode;
	dataIndex?: keyof T | string;
	width?: number | string;
	align?: "left" | "right" | "center";
	render?: (value: unknown, record: T, index: number) => React.ReactNode;
}

interface CompactTableProps<T> extends Omit<TableProps<T>, "columns" | "pagination" | "dataSource"> {
	columns: CompactColumn<T>[];
	data: T[];
	rowKey: keyof T | ((record: T) => string);
	/** 默认 10；可覆盖但仍遵守"切条数回第 1 页"约定 */
	defaultPageSize?: number;
	loading?: boolean;
}

/**
 * 高密度数据表 —— 统一分页约定：
 * 1. 默认 10 条/页
 * 2. 切换每页条数 → 回到第 1 页（避免越界空页）
 * 3. tabular-nums 数字对齐
 * pageNum 采用 1-based，与 PageResult 一致。
 */
export function CompactTable<T extends object>({
	columns,
	data,
	rowKey,
	defaultPageSize = DEFAULT_PAGE_SIZE,
	loading,
	...rest
}: CompactTableProps<T>) {
	const [pageNum, setPageNum] = useState(1);
	const [pageSize, setPageSize] = useState(defaultPageSize);

	// 切换条数后若当前页越界，回到第 1 页
	useEffect(() => {
		const maxPage = Math.max(1, Math.ceil(data.length / pageSize));
		if (pageNum > maxPage) setPageNum(1);
	}, [pageSize, data.length, pageNum]);

	const pagination: TablePaginationConfig = {
		current: pageNum,
		pageSize,
		total: data.length,
		showSizeChanger: true,
		pageSizeOptions: ["10", "20", "50"],
		size: "small",
		showTotal: (total) => `共 ${total} 条`,
		onChange: (next) => setPageNum(next),
		onShowSizeChange: (_current, size) => {
			setPageSize(size);
			setPageNum(1); // 切条数务必回第 1 页
		},
	};

	return (
		<Table<T>
			size="small"
			columns={columns as TableProps<T>["columns"]}
			dataSource={data}
			rowKey={rowKey as TableProps<T>["rowKey"]}
			pagination={pagination}
			loading={loading}
			{...rest}
		/>
	);
}
