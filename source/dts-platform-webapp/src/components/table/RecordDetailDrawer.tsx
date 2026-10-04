import type { Key, ReactNode } from "react";
import { useMemo } from "react";
import { Descriptions, Drawer, Empty } from "antd";
import type { ColumnGroupType, ColumnType } from "antd/es/table";
import type { CompactColumns } from "./CompactTable";

export interface RecordDetailDrawerProps<T> {
	open: boolean;
	onClose: () => void;
	record: T | null;
	/**
	 * 渲染依据。通常直接传 Table 的 columns 即可；隐藏字段或操作列会自动跳过。
	 */
	columns?: CompactColumns<T>;
	title?: ReactNode;
	width?: number | string;
	/** 描述列数 */
	colSpan?: 1 | 2 | 3;
	/** 额外内容（在字段表之后） */
	extra?: ReactNode;
}

const SKIP_DATA_INDEX = /^(action|actions|operation|operations|op)$/i;

const isColumnGroup = <T,>(c: ColumnType<T> | ColumnGroupType<T>): c is ColumnGroupType<T> =>
	Array.isArray((c as ColumnGroupType<T>).children);

function flattenColumns<T>(cols: CompactColumns<T>): ColumnType<T>[] {
	const out: ColumnType<T>[] = [];
	for (const c of cols) {
		if (isColumnGroup(c)) out.push(...flattenColumns(c.children));
		else out.push(c);
	}
	return out;
}

function pickValue<T>(record: T, dataIndex: ColumnType<T>["dataIndex"]): unknown {
	if (dataIndex === undefined || dataIndex === null) return undefined;
	if (Array.isArray(dataIndex)) {
		return dataIndex.reduce<unknown>((acc, key) => {
			if (acc && typeof acc === "object") return (acc as Record<string, unknown>)[String(key)];
			return undefined;
		}, record as unknown);
	}
	if (record && typeof record === "object") {
		return (record as Record<string, unknown>)[String(dataIndex)];
	}
	return undefined;
}

function formatPrimitive(v: unknown): ReactNode {
	if (v === null || v === undefined || v === "") return "-";
	if (typeof v === "boolean") return v ? "是" : "否";
	if (typeof v === "object") {
		try {
			return <pre className="m-0 whitespace-pre-wrap break-all text-xs">{JSON.stringify(v, null, 2)}</pre>;
		} catch {
			return String(v);
		}
	}
	return String(v);
}

export function RecordDetailDrawer<T extends object = Record<string, unknown>>(
	props: RecordDetailDrawerProps<T>,
): JSX.Element {
	const { open, onClose, record, columns, title = "详情", width = 560, colSpan = 1, extra } = props;

	const items = useMemo(() => {
		if (!record || !columns) return [];
		const flat = flattenColumns(columns);
		return flat
			.filter((col) => {
				const di = col.dataIndex;
				const diStr = Array.isArray(di) ? di.join(".") : (di as string | undefined);
				if (!diStr) return false;
				if (SKIP_DATA_INDEX.test(diStr)) return false;
				return true;
			})
			.map((col) => {
				const di = col.dataIndex;
				const raw = pickValue(record, di);
				let content: ReactNode;
				try {
					if (col.render) {
						const rendered = col.render(raw, record, 0);
						content =
							rendered && typeof rendered === "object" && "children" in rendered
								? ((rendered as { children?: ReactNode }).children ?? null)
								: (rendered as ReactNode);
					} else {
						content = formatPrimitive(raw);
					}
				} catch {
					content = formatPrimitive(raw);
				}
				const fallbackKey = Array.isArray(di) ? di.join(".") : typeof di === "string" || typeof di === "number" ? String(di) : Math.random().toString(36).slice(2);
				const key: Key = (col.key as Key | undefined) ?? fallbackKey;
				const labelNode: ReactNode = (col.title as ReactNode) ?? String(fallbackKey);
				return {
					key,
					label: labelNode,
					content,
				};
			});
	}, [columns, record]);

	return (
		<Drawer
			open={open}
			onClose={onClose}
			title={title}
			width={width}
			destroyOnClose
		>
			{record ? (
				<>
					<Descriptions column={colSpan} bordered size="small">
						{items.map((it) => (
							<Descriptions.Item key={it.key} label={it.label}>
								{it.content}
							</Descriptions.Item>
						))}
					</Descriptions>
					{extra ? <div className="mt-4">{extra}</div> : null}
				</>
			) : (
				<Empty description="无数据" />
			)}
		</Drawer>
	);
}

export default RecordDetailDrawer;
