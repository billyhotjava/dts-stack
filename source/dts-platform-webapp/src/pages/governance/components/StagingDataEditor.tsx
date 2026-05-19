import { useCallback, useEffect, useMemo, useState, useRef } from "react";
import { toast } from "sonner";
import {
	Button,
	Checkbox,
	Input,
	Space,
	Tag,
	Tooltip,
	Typography,
} from "antd";
import { CompactTable } from "@/components/table";
import { WarningOutlined, CheckCircleOutlined, } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import { ingestionTaskAPI, type StagingPage } from "@/api/ingestion";

export interface StagingDataEditorProps {
	taskId: number;
	onClose?: () => void;
}

type RowError = { column: string; rule: string; message: string };
type StagingRow = Record<string, any>;

const PAGE_SIZE = 100;

/**
 * Parse the _errors field from a staging row.
 * The backend stores it as a JSONB array of {column, rule, message}.
 * It can arrive as an already-parsed array or as a JSON string.
 */
const parseRowErrors = (row: StagingRow): RowError[] => {
	const raw = row?._errors;
	if (!raw) return [];
	if (Array.isArray(raw)) return raw as RowError[];
	if (typeof raw === "string") {
		try {
			const parsed = JSON.parse(raw);
			return Array.isArray(parsed) ? parsed : [];
		} catch {
			return [];
		}
	}
	return [];
};

/** Get set of column names that have errors for a given row */
const getErrorColumns = (errors: RowError[]): Set<string> => {
	return new Set(errors.map((e) => e.column));
};

/** Get the error message for a specific column */
const getErrorForColumn = (errors: RowError[], column: string): string | undefined => {
	const found = errors.filter((e) => e.column === column);
	if (found.length === 0) return undefined;
	return found.map((e) => `[${e.rule}] ${e.message}`).join("\n");
};

export default function StagingDataEditor({ taskId, onClose }: StagingDataEditorProps) {
	const [data, setData] = useState<StagingRow[]>([]);
	const [loading, setLoading] = useState(false);
	const [page, setPage] = useState(0);
	const [total, setTotal] = useState(0);
	const [errorsOnly, setErrorsOnly] = useState(false);
	const [submitting, setSubmitting] = useState(false);
	const [rechecking, setRechecking] = useState(false);

	// inline editing state
	const [editingCell, setEditingCell] = useState<{ rowNum: number; column: string } | null>(null);
	const [editingValue, setEditingValue] = useState("");
	const inputRef = useRef<any>(null);

	// Discover column names from data (excluding internal fields)
	const columnNames = useMemo(() => {
		if (data.length === 0) return [];
		const allKeys = new Set<string>();
		for (const row of data) {
			for (const key of Object.keys(row)) {
				if (!key.startsWith("_")) allKeys.add(key);
			}
		}
		return Array.from(allKeys);
	}, [data]);

	// Count total errors across all visible rows
	const totalErrorCount = useMemo(() => {
		let count = 0;
		for (const row of data) {
			count += parseRowErrors(row).length;
		}
		return count;
	}, [data]);

	const fetchData = useCallback(async () => {
		setLoading(true);
		try {
			const resp: StagingPage = await ingestionTaskAPI.getStagingData(taskId, {
				errorsOnly,
				page,
				size: PAGE_SIZE,
			});
			setData(Array.isArray(resp?.content) ? resp.content : []);
			setTotal(resp?.totalElements ?? 0);
		} catch (error: any) {
			if (error?.response?.status === 404) {
				setData([]);
				setTotal(0);
			} else {
				toast.error(error?.message || "暂存数据加载失败");
			}
		} finally {
			setLoading(false);
		}
	}, [taskId, errorsOnly, page]);

	useEffect(() => {
		void fetchData();
	}, [fetchData]);

	// Focus input when editing starts
	useEffect(() => {
		if (editingCell && inputRef.current) {
			inputRef.current.focus();
		}
	}, [editingCell]);

	const handleCellClick = (rowNum: number, column: string, currentValue: string) => {
		setEditingCell({ rowNum, column });
		setEditingValue(currentValue ?? "");
	};

	const handleCellSave = async () => {
		if (!editingCell) return;
		const { rowNum, column } = editingCell;
		try {
			await ingestionTaskAPI.updateStagingCell(taskId, rowNum, {
				column,
				value: editingValue,
			});
			// Update local data optimistically
			setData((prev) =>
				prev.map((row) => {
					if (row._row_num === rowNum) {
						const updatedRow = { ...row, [column]: editingValue };
						// Remove the error for this column from _errors
						const errors = parseRowErrors(row).filter((e) => e.column !== column);
						updatedRow._errors = errors.length > 0 ? errors : null;
						return updatedRow;
					}
					return row;
				}),
			);
			toast.success("单元格已更新");
		} catch (error: any) {
			toast.error(error?.message || "更新失败");
		} finally {
			setEditingCell(null);
		}
	};

	const handleCellCancel = () => {
		setEditingCell(null);
	};

	const handleReCheck = async () => {
		setRechecking(true);
		try {
			const result = await ingestionTaskAPI.reCheck(taskId);
			toast.success(`重新检查完成：${result.passedRows} 通过, ${result.failedRows} 失败`);
			void fetchData();
		} catch (error: any) {
			toast.error(error?.message || "重新检查失败");
		} finally {
			setRechecking(false);
		}
	};

	const handleSubmit = async () => {
		setSubmitting(true);
		try {
			await ingestionTaskAPI.submitFromStaging(taskId);
			toast.success("数据已提交入湖");
			onClose?.();
		} catch (error: any) {
			toast.error(error?.message || "提交入湖失败");
		} finally {
			setSubmitting(false);
		}
	};

	const handleDrop = async () => {
		try {
			await ingestionTaskAPI.dropStaging(taskId);
			toast.success("暂存数据已清除");
			onClose?.();
		} catch (error: any) {
			toast.error(error?.message || "清除暂存失败");
		}
	};

	const handleExportErrors = () => {
		// Build a simple CSV from error rows
		const errorRows = data.filter((row) => parseRowErrors(row).length > 0);
		if (errorRows.length === 0) {
			toast.info("没有错误数据可导出");
			return;
		}
		const headers = ["_row_num", ...columnNames, "_error_details"];
		const csvRows = [headers.join(",")];
		for (const row of errorRows) {
			const errors = parseRowErrors(row);
			const errorDetail = errors.map((e) => `${e.column}:${e.rule}:${e.message}`).join("; ");
			const values = [
				row._row_num ?? "",
				...columnNames.map((col) => {
					const val = String(row[col] ?? "").replace(/"/g, '""');
					return `"${val}"`;
				}),
				`"${errorDetail.replace(/"/g, '""')}"`,
			];
			csvRows.push(values.join(","));
		}
		const blob = new Blob(["\uFEFF" + csvRows.join("\n")], { type: "text/csv;charset=utf-8" });
		const url = URL.createObjectURL(blob);
		const a = document.createElement("a");
		a.href = url;
		a.download = `staging_errors_task_${taskId}.csv`;
		a.click();
		URL.revokeObjectURL(url);
	};

	// Build antd columns dynamically from data
	const columns: ColumnsType<StagingRow> = useMemo(() => {
		const rowNumCol: ColumnsType<StagingRow>[number] = {
			title: "#",
			dataIndex: "_row_num",
			width: 60,
			fixed: "left",
			render: (v: number) => <span className="text-gray-400 text-xs">{v}</span>,
		};

		const dataCols: ColumnsType<StagingRow> = columnNames.map((col) => ({
			title: col,
			dataIndex: col,
			width: 160,
			ellipsis: true,
			onCell: (record: StagingRow) => {
				const errors = parseRowErrors(record);
				const errCols = getErrorColumns(errors);
				const hasError = errCols.has(col);
				return {
					style: hasError
						? { border: "2px solid #ff4d4f", background: "#fff2f0", cursor: "pointer" }
						: { cursor: "pointer" },
				};
			},
			render: (value: any, record: StagingRow) => {
				const rowNum = record._row_num as number;
				const errors = parseRowErrors(record);
				const errorMsg = getErrorForColumn(errors, col);
				const isEditing = editingCell?.rowNum === rowNum && editingCell?.column === col;

				if (isEditing) {
					return (
						<Input
							ref={inputRef}
							size="small"
							value={editingValue}
							onChange={(e) => setEditingValue(e.target.value)}
							onBlur={handleCellSave}
							onPressEnter={handleCellSave}
							onKeyDown={(e) => {
								if (e.key === "Escape") handleCellCancel();
							}}
							style={{ margin: -4 }}
						/>
					);
				}

				const display = value != null ? String(value) : "";
				if (errorMsg) {
					return (
						<Tooltip title={errorMsg} overlayStyle={{ maxWidth: 400, whiteSpace: "pre-wrap" }}>
							<span
								className="inline-flex items-center gap-1"
								onClick={() => handleCellClick(rowNum, col, display)}
							>
								<WarningOutlined className="text-red-500 text-xs" />
								<span className="text-red-600">{display || <i className="text-gray-300">空</i>}</span>
							</span>
						</Tooltip>
					);
				}

				return (
					<span onClick={() => handleCellClick(rowNum, col, display)}>
						{display || <span className="text-gray-300">-</span>}
					</span>
				);
			},
		}));

		return [rowNumCol, ...dataCols];
	}, [columnNames, editingCell, editingValue]);

	return (
		<div className="space-y-3">
			{/* Toolbar */}
			<div className="flex items-center justify-between flex-wrap gap-2">
				<Space>
					<Tag color="blue">任务 #{taskId}</Tag>
					<Checkbox
						checked={errorsOnly}
						onChange={(e) => {
							setErrorsOnly(e.target.checked);
							setPage(0);
						}}
					>
						只看错误行
					</Checkbox>
				</Space>
				<Space>
					<Button
						loading={rechecking}
						onClick={handleReCheck}
					>
						重新检查
					</Button>
					<Button
						onClick={handleExportErrors}
					>
						导出错误报告
					</Button>
					<Button
						type="primary"
						disabled={totalErrorCount > 0}
						loading={submitting}
						onClick={handleSubmit}
					>
						提交入湖 {totalErrorCount > 0 && "🔒"}
					</Button>
					<Button
						danger
						onClick={handleDrop}
					>
						丢弃暂存
					</Button>
					{onClose && (
						<Button onClick={onClose}>返回</Button>
					)}
				</Space>
			</div>

			{/* Data table */}
			<CompactTable
				rowKey={(record) => record._row_num ?? Math.random()}
				columns={columns}
				dataSource={data}
				loading={loading}
				scroll={{ x: Math.max(800, columnNames.length * 160 + 60) }}
				size="small"
				pagination={{
					current: page + 1,
					pageSize: PAGE_SIZE,
					total,
					showTotal: (t) => `共 ${t} 行`,
					onChange: (p) => setPage(p - 1),
					showSizeChanger: false,
				}}
				rowClassName={(record) => {
					const errors = parseRowErrors(record);
					return errors.length > 0 ? "bg-red-50" : "";
				}}
			/>

			{/* Summary bar */}
			<div className="flex items-center justify-between rounded bg-gray-50 px-4 py-2 text-sm">
				{totalErrorCount > 0 ? (
					<Typography.Text type="warning">
						<WarningOutlined className="mr-1" />
						{totalErrorCount} 个错误待修复
					</Typography.Text>
				) : (
					<Typography.Text type="success">
						<CheckCircleOutlined className="mr-1" />
						无错误，可以提交入湖
					</Typography.Text>
				)}
				<Typography.Text type="secondary">
					第 {page + 1} 页 / 共 {Math.ceil(total / PAGE_SIZE) || 1} 页，{total} 行数据
				</Typography.Text>
			</div>
		</div>
	);
}
