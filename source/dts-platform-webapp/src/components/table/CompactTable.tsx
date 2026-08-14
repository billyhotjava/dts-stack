import type { Key, ReactNode } from "react";
import { useMemo } from "react";
import { Table } from "antd";
import type { TableProps } from "antd";
import type { ColumnGroupType, ColumnType } from "antd/es/table";
import type { SorterResult, TablePaginationConfig } from "antd/es/table/interface";

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
	/**
	 * 是否为未声明 sorter 的数据列自动注入客户端排序（表头出现升/降箭头）。
	 * 默认 true，但仅在“非受控分页”时生效——服务端分页的表格 dataSource 只有当前页，
	 * 客户端排序会得出错误结果，必须由调用方显式声明 sorter 并把排序条件传给后端
	 * （见 toSpringSort）。单列可用 `sorter: false` 单独退出。
	 */
	autoSort?: boolean;
}

const DEFAULT_PAGE_SIZE = 10;
const DEFAULT_PAGE_SIZE_OPTIONS = ["10", "20", "50", "100"];

const COMPACT_CLASS = "dts-compact-table";

/** 操作列约定：这些 dataIndex 既不加 ellipsis，也不加排序 */
const ACTION_DATA_INDEX = /^(action|actions|operation|operations|op)$/i;

const DATE_LIKE = /^\d{4}-\d{2}-\d{2}(?:[T ]\d{2}:\d{2}(?::\d{2})?)?/;

const collator = new Intl.Collator("zh-Hans-CN", { numeric: true, sensitivity: "base" });

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
		if (diStr && ACTION_DATA_INDEX.test(diStr)) {
			return leaf;
		}
		return { ...leaf, ellipsis: { showTitle: true } } as ColumnType<T>;
	});
}

/** 按 dataIndex（支持嵌套路径）读取单元格原始值。 */
function readCell<T>(record: T, dataIndex: ColumnType<T>["dataIndex"]): unknown {
	if (dataIndex == null) return undefined;
	const path = Array.isArray(dataIndex) ? dataIndex : [dataIndex];
	let cursor: unknown = record;
	for (const key of path) {
		if (cursor == null || typeof cursor !== "object") return undefined;
		cursor = (cursor as Record<string, unknown>)[String(key)];
	}
	return cursor;
}

/**
 * 通用单元格比较：数字按数值、日期串按时间戳、其余按中文 collator（numeric 开启，
 * 使 "表2" < "表10"）。空值恒定视为最小，因此升序排最前、降序排最后。
 */
export function compareCell(a: unknown, b: unknown): number {
	const aEmpty = a == null || a === "";
	const bEmpty = b == null || b === "";
	if (aEmpty || bEmpty) return aEmpty && bEmpty ? 0 : aEmpty ? -1 : 1;
	if (typeof a === "number" && typeof b === "number") return a - b;
	if (typeof a === "boolean" && typeof b === "boolean") return Number(a) - Number(b);
	const left = String(a);
	const right = String(b);
	if (DATE_LIKE.test(left) && DATE_LIKE.test(right)) {
		const leftTime = Date.parse(left);
		const rightTime = Date.parse(right);
		if (!Number.isNaN(leftTime) && !Number.isNaN(rightTime)) return leftTime - rightTime;
	}
	return collator.compare(left, right);
}

/**
 * 给未声明 sorter 的数据列注入客户端排序。列上写 `sorter: false` 即可单独退出。
 */
function injectSorters<T>(columns: CompactColumns<T>): CompactColumns<T> {
	return columns.map((col) => {
		if (isColumnGroup(col)) {
			return { ...col, children: injectSorters(col.children) } as ColumnGroupType<T>;
		}
		const leaf = col as ColumnType<T>;
		if (leaf.sorter !== undefined) return leaf;
		const di = leaf.dataIndex;
		if (di == null) return leaf;
		const diStr = Array.isArray(di) ? di.join(".") : (di as string);
		if (ACTION_DATA_INDEX.test(diStr)) return leaf;
		return {
			...leaf,
			sorter: (a: T, b: T) => compareCell(readCell(a, di), readCell(b, di)),
		} as ColumnType<T>;
	});
}

/** 分页是否由调用方受控（等价于服务端分页）。 */
function isControlledPagination(pagination: TableProps<unknown>["pagination"]): boolean {
	if (!pagination || typeof pagination !== "object") return false;
	return pagination.current != null || typeof pagination.onChange === "function";
}

function buildPagination(current: TableProps<unknown>["pagination"], total?: number): TablePaginationConfig | false {
	if (current === false) return false;
	const base: TablePaginationConfig = {
		showSizeChanger: true,
		pageSizeOptions: DEFAULT_PAGE_SIZE_OPTIONS,
		size: "small",
		showTotal: (t) => `共 ${t} 条`,
	};
	const caller: TablePaginationConfig = current && typeof current === "object" ? { ...current } : {};

	// 服务端受控分页：调用方自行管理 current + onChange，原样透传（pageSize 必须保持受控）。
	if (isControlledPagination(caller)) {
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

export function CompactTable<T extends object = Record<string, unknown>>(props: CompactTableProps<T>): JSX.Element {
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
		autoSort = true,
		...rest
	} = props;

	// 服务端分页时 dataSource 只有当前页，客户端排序会得出错误结果，因此不注入。
	const sortable = autoSort && !isControlledPagination(pagination as TableProps<unknown>["pagination"]);

	const finalColumns = useMemo<CompactColumns<T> | undefined>(() => {
		if (!columns) return columns;
		const withEllipsis = autoEllipsis ? injectEllipsis(columns) : columns;
		return sortable ? injectSorters(withEllipsis) : withEllipsis;
	}, [columns, autoEllipsis, sortable]);

	const finalPagination = useMemo(() => buildPagination(pagination as TableProps<unknown>["pagination"]), [pagination]);

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

/**
 * 把 antd 的 onChange sorter 转成 Spring Data 的 `field,asc|desc`。
 * 服务端分页的表格用它把表头排序传给后端（客户端排序在分页场景下是错的）。
 *
 * @param sorter    antd onChange 回调的第三个参数
 * @param fieldMap  列 key/dataIndex → 后端字段名的映射；未命中时直接用列 key
 * @param fallback  未选中任何排序时返回的默认排序
 */
export function toSpringSort<T>(
	sorter: SorterResult<T> | SorterResult<T>[] | undefined,
	fieldMap: Record<string, string> = {},
	fallback?: string,
): string | undefined {
	const first = (Array.isArray(sorter) ? sorter : [sorter]).find((entry) => entry?.order);
	if (!first?.order) return fallback;
	const rawKey =
		first.columnKey != null
			? String(first.columnKey)
			: Array.isArray(first.field)
				? first.field.join(".")
				: first.field != null
					? String(first.field)
					: "";
	if (!rawKey) return fallback;
	const field = fieldMap[rawKey] ?? rawKey;
	return `${field},${first.order === "ascend" ? "asc" : "desc"}`;
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
