import type { Key, ReactNode } from "react";
import { useMemo } from "react";
import { Table } from "antd";
import type { TableProps } from "antd";
import type { ColumnGroupType, ColumnType } from "antd/es/table";
import type { TablePaginationConfig } from "antd/es/table/interface";

export type CompactColumn<T> = ColumnType<T> | ColumnGroupType<T>;
export type CompactColumns<T> = CompactColumn<T>[];

export interface CompactTableProps<T> extends Omit<TableProps<T>, "columns"> {
	columns?: CompactColumns<T>;
	/**
	 * 当列未显式声明 ellipsis / render 时，是否自动注入 ellipsis: { showTitle: true }
	 * 默认 true
	 */
	autoEllipsis?: boolean;
	/**
	 * 是否启用紧凑模式（size=small + 单行 nowrap CSS）
	 * 默认 true
	 */
	compact?: boolean;
}

const DEFAULT_PAGE_SIZE = 10;
const DEFAULT_PAGE_SIZE_OPTIONS = ["10", "20", "50", "100"];

const COMPACT_CLASS = "dts-compact-table";

const isColumnGroup = <T,>(col: CompactColumn<T>): col is ColumnGroupType<T> =>
	Array.isArray((col as ColumnGroupType<T>).children);

/**
 * 给所有叶子列注入默认 ellipsis；保留显式声明，不覆盖 render/ellipsis。
 */
function injectEllipsis<T>(columns: CompactColumns<T>): CompactColumns<T> {
	return columns.map((col) => {
		if (isColumnGroup(col)) {
			return { ...col, children: injectEllipsis(col.children) } as ColumnGroupType<T>;
		}
		const leaf = col as ColumnType<T>;
		if (leaf.ellipsis !== undefined) return leaf;
		// 操作列约定：dataIndex 为 'action'/'actions'/'operation' 不加 ellipsis
		const di = leaf.dataIndex;
		const diStr = Array.isArray(di) ? di.join(".") : (di as string | undefined);
		if (diStr && /^(action|actions|operation|operations|op)$/i.test(diStr)) {
			return leaf;
		}
		return { ...leaf, ellipsis: { showTitle: true } } as ColumnType<T>;
	});
}

function buildPagination(
	current: TableProps<unknown>["pagination"],
	total?: number,
): TablePaginationConfig | false {
	if (current === false) return false;
	const base: TablePaginationConfig = {
		showSizeChanger: true,
		pageSizeOptions: DEFAULT_PAGE_SIZE_OPTIONS,
		size: "small",
		showTotal: (t) => `共 ${t} 条`,
	};
	const caller: TablePaginationConfig = current && typeof current === "object" ? { ...current } : {};

	// 服务端受控分页：调用方自行管理 current + onChange，原样透传（pageSize 必须保持受控）。
	const isControlled = caller.current != null || typeof caller.onChange === "function";
	if (isControlled) {
		return { ...base, pageSize: caller.pageSize ?? DEFAULT_PAGE_SIZE, ...caller };
	}

	// 客户端分页（非受控）：把固定 pageSize 收敛为 defaultPageSize。
	// 否则 antd 把 pageSize 当受控值，每次渲染都强制写回，导致"改每页条数不生效"（历史 bug）。
	// 改用 defaultPageSize 后由 antd 内部管理，用户在 showSizeChanger 里的选择才会持久生效。
	const { pageSize, defaultPageSize, ...callerRest } = caller;
	const result: TablePaginationConfig = {
		...base,
		defaultPageSize: defaultPageSize ?? pageSize ?? DEFAULT_PAGE_SIZE,
		...callerRest,
	};
	if (typeof total === "number" && result.total == null) {
		result.total = total;
	}
	return result;
}

export function CompactTable<T extends object = Record<string, unknown>>(
	props: CompactTableProps<T>,
): JSX.Element {
	const {
		columns,
		pagination,
		size,
		bordered,
		scroll,
		rowClassName,
		className,
		autoEllipsis = true,
		compact = true,
		...rest
	} = props;

	const finalColumns = useMemo<CompactColumns<T> | undefined>(() => {
		if (!columns) return columns;
		return autoEllipsis ? injectEllipsis(columns) : columns;
	}, [columns, autoEllipsis]);

	const finalPagination = useMemo(
		() => buildPagination(pagination as TableProps<unknown>["pagination"]),
		[pagination],
	);

	const finalScroll = useMemo(() => scroll ?? { x: "max-content" }, [scroll]);

	const mergedClassName = [compact ? COMPACT_CLASS : "", className].filter(Boolean).join(" ");

	return (
		<Table<T>
			columns={finalColumns}
			pagination={finalPagination}
			size={size ?? (compact ? "small" : undefined)}
			bordered={bordered ?? false}
			scroll={finalScroll}
			rowClassName={rowClassName}
			className={mergedClassName || undefined}
			{...rest}
		/>
	);
}

export interface DetailActionOptions<T> {
	label?: ReactNode;
	key?: Key;
	width?: number | string;
	fixed?: ColumnType<T>["fixed"];
	/** 与已有操作列的合并行为：merge=放在已有 actions 列首位；append=单独追加列。默认 merge */
	mode?: "merge" | "append";
	/** 操作列匹配 dataIndex，默认 'actions'/'action'/'operation' */
	matchDataIndex?: RegExp;
}

/**
 * 在 columns 末尾或现有操作列首位插入"详情"按钮。
 * 用法：
 *   const cols = appendDetailAction(baseCols, (row) => setDetailRow(row));
 */
export function appendDetailAction<T>(
	columns: CompactColumns<T>,
	onDetail: (record: T) => void,
	options: DetailActionOptions<T> = {},
): CompactColumns<T> {
	const {
		label = "详情",
		mode = "merge",
		matchDataIndex = /^(action|actions|operation|operations|op)$/i,
		key = "__compact_detail",
		width,
		fixed,
	} = options;

	const detailRender = (_: unknown, record: T) => (
		<a
			data-testid="compact-table-detail"
			onClick={(e) => {
				e.stopPropagation();
				onDetail(record);
			}}
		>
			{label}
		</a>
	);

	if (mode === "merge") {
		const idx = columns.findIndex((c) => {
			if (isColumnGroup(c)) return false;
			const di = (c as ColumnType<T>).dataIndex;
			const diStr = Array.isArray(di) ? di.join(".") : (di as string | undefined);
			return diStr ? matchDataIndex.test(diStr) : false;
		});
		if (idx >= 0) {
			const original = columns[idx] as ColumnType<T>;
			const originalRender = original.render;
			const merged: ColumnType<T> = {
				...original,
				render: (value, record, index) => {
					const originalNode = originalRender ? originalRender(value, record, index) : null;
					const safeOriginal =
						originalNode && typeof originalNode === "object" && "children" in originalNode
							? (originalNode as { children?: ReactNode }).children
							: (originalNode as ReactNode);
					return (
						<span className="inline-flex items-center gap-2 whitespace-nowrap">
							{detailRender(value, record)}
							{safeOriginal ?? null}
						</span>
					);
				},
			};
			const next = columns.slice();
			next[idx] = merged;
			return next;
		}
	}

	const appended: ColumnType<T> = {
		key,
		title: "操作",
		dataIndex: "actions" as ColumnType<T>["dataIndex"],
		width,
		fixed,
		render: detailRender,
	};
	return [...columns, appended];
}

export default CompactTable;
