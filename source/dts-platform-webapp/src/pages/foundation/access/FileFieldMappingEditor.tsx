import { Button, Input, InputNumber, Space, Tag, Typography } from "antd";
import { type ReactNode, useState } from "react";
import type { ManagedFileColumn } from "@/api/ingestion";
import { type CompactColumns, CompactTable } from "@/components/table";
import {
	buildFilePreviewRows,
	DEFAULT_FILE_PREVIEW_LIMIT,
	MAX_FILE_PREVIEW_LIMIT,
	normalizeFilePreviewLimit,
} from "./shared/filePreview";
import {
	fillUnmatchedByPosition,
	type TargetSchemaColumn,
	validateFileTargetColumns,
} from "./shared/fileTargetSchemaMapping";

type Props = {
	columns: ManagedFileColumn[];
	targetColumns: TargetSchemaColumn[];
	preview?: string[][];
	onChange: (columns: ManagedFileColumn[]) => void;
	renderClassification?: (column: ManagedFileColumn, index: number) => ReactNode;
};

type GridRow =
	| { key: "source" | "name" | "type" | "description" | "status" | "classification"; label: string }
	| { key: string; label: string; previewIndex: number };

export function FileFieldMappingEditor({ columns, targetColumns, preview, onChange, renderClassification }: Props) {
	const [rowLimit, setRowLimit] = useState(DEFAULT_FILE_PREVIEW_LIMIT);
	const issues = validateFileTargetColumns(columns);
	const issuesByIndex = new Map(issues.map((issue) => [issue.index, issue]));
	const previewRows = buildFilePreviewRows(preview, rowLimit);
	const updateColumn = (index: number, patch: Partial<ManagedFileColumn>) => {
		onChange(columns.map((column, columnIndex) => (columnIndex === index ? { ...column, ...patch } : column)));
	};
	const rows: GridRow[] = [
		{ key: "source", label: "文件字段" },
		{ key: "name", label: "字段名称" },
		{ key: "type", label: "字段类型" },
		{ key: "description", label: "字段描述" },
		{ key: "status", label: "映射状态" },
		...(renderClassification ? ([{ key: "classification", label: "字段密级" }] as GridRow[]) : []),
		...previewRows.map((_row, previewIndex) => ({
			key: `preview-${previewIndex}`,
			label: String(previewIndex + 1),
			previewIndex,
		})),
	];
	const renderCell = (row: GridRow, column: ManagedFileColumn, columnIndex: number): ReactNode => {
		if ("previewIndex" in row) {
			const value = previewRows[row.previewIndex]?.[columnIndex] ?? "";
			return <span title={value}>{value}</span>;
		}
		switch (row.key) {
			case "source":
				return <span title={column.label || column.name}>{column.label || column.name}</span>;
			case "name":
				return (
					<Input
						aria-label={`第 ${columnIndex + 1} 列字段名称`}
						value={column.name}
						status={issuesByIndex.has(columnIndex) ? "error" : undefined}
						onChange={(event) => updateColumn(columnIndex, { name: event.target.value, _odsMatched: false })}
					/>
				);
			case "type":
				return (
					<Input
						aria-label={`第 ${columnIndex + 1} 列字段类型`}
						value={column.type}
						onChange={(event) => updateColumn(columnIndex, { type: event.target.value })}
					/>
				);
			case "description":
				return (
					<Input
						aria-label={`第 ${columnIndex + 1} 列字段描述`}
						value={column.description}
						placeholder="可选"
						onChange={(event) => updateColumn(columnIndex, { description: event.target.value })}
					/>
				);
			case "status": {
				const issue = issuesByIndex.get(columnIndex);
				if (issue) return <Tag color="red">{issue.message}</Tag>;
				return <Tag color={column._odsMatched ? "green" : "default"}>{column._odsMatched ? "已匹配" : "待确认"}</Tag>;
			}
			case "classification":
				return renderClassification ? <Space>{renderClassification(column, columnIndex)}</Space> : null;
		}
	};
	const tableColumns: CompactColumns<GridRow> = [
		{
			title: "",
			dataIndex: "label",
			key: "rowLabel",
			width: 120,
			fixed: "left",
			render: (value, row) => <Typography.Text strong={!("previewIndex" in row)}>{value}</Typography.Text>,
		},
		...columns.map((column, columnIndex) => ({
			title: `第 ${columnIndex + 1} 列`,
			key: `column-${columnIndex}`,
			width: 190,
			render: (_value: unknown, row: GridRow) => renderCell(row, column, columnIndex),
		})),
	];

	return (
		<div className="space-y-3">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<div>
					<Typography.Text strong>字段映射与数据预览</Typography.Text>
					<div>
						<Typography.Text type="secondary">
							每个文件列横向展示字段配置和样例数据；系统仅按名称自动匹配。
						</Typography.Text>
					</div>
				</div>
				<Space wrap>
					<Button
						disabled={!targetColumns.length || columns.every((column) => column._odsMatched)}
						onClick={() => onChange(fillUnmatchedByPosition(columns, targetColumns))}
					>
						按顺序填充未匹配字段
					</Button>
					<Typography.Text>显示行数</Typography.Text>
					<InputNumber
						aria-label="预览显示行数"
						min={1}
						max={MAX_FILE_PREVIEW_LIMIT}
						precision={0}
						value={rowLimit}
						onChange={(value) => setRowLimit(normalizeFilePreviewLimit(value))}
					/>
				</Space>
			</div>
			<CompactTable<GridRow>
				size="small"
				bordered
				autoEllipsis={false}
				rowKey="key"
				dataSource={rows}
				pagination={false}
				scroll={{ x: Math.max(720, columns.length * 190 + 120) }}
				columns={tableColumns}
			/>
		</div>
	);
}
