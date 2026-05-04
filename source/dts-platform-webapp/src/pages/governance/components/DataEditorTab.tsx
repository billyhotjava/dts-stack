import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Button, Form, Input, Modal, Select, Tag } from "antd";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { PlusOutlined, EditOutlined } from "@ant-design/icons";
import {
	listOdsTables,
	listOdsColumns,
	listOdsRows,
	updateOdsRow,
	insertOdsRow,
	listEditLogs,
} from "@/api/platformApi";

type OdsColumn = {
	name?: string;
	type?: string;
	nullable?: boolean;
};

type OdsRow = Record<string, any>;

type AuditLog = {
	id?: string;
	tableName?: string;
	rowId?: string;
	action?: string;
	changedBy?: string;
	changedAt?: string;
	details?: string;
};

export default function DataEditorTab({ canManage }: { canManage: boolean }) {
	const [tables, setTables] = useState<string[]>([]);
	const [selectedTable, setSelectedTable] = useState<string | undefined>();
	const [columns, setColumns] = useState<OdsColumn[]>([]);
	const [rows, setRows] = useState<OdsRow[]>([]);
	const [rowsLoading, setRowsLoading] = useState(false);
	const [page, setPage] = useState(0);
	const [pageSize, setPageSize] = useState(20);
	const [totalRows, setTotalRows] = useState(0);

	const [editModalOpen, setEditModalOpen] = useState(false);
	const [editingRow, setEditingRow] = useState<OdsRow | null>(null);
	const [isInsert, setIsInsert] = useState(false);
	const [form] = Form.useForm();

	const [logsOpen, setLogsOpen] = useState(false);
	const [logs, setLogs] = useState<AuditLog[]>([]);
	const [logsLoading, setLogsLoading] = useState(false);

	const loadTables = async () => {
		try {
			const result = await listOdsTables();
			const list = Array.isArray(result) ? (result as string[]) : [];
			setTables(list);
		} catch (error: any) {
			toast.error(error?.message || "表列表加载失败");
		}
	};

	const loadColumns = async (tableName: string) => {
		try {
			const result = await listOdsColumns(tableName);
			setColumns(Array.isArray(result) ? (result as OdsColumn[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "列信息加载失败");
		}
	};

	const loadRows = async (tableName: string, p = 0, s = 20) => {
		setRowsLoading(true);
		try {
			const result: any = await listOdsRows(tableName, { page: p, size: s });
			if (result?.content) {
				setRows(Array.isArray(result.content) ? result.content : []);
				setTotalRows(result.totalElements ?? 0);
			} else if (Array.isArray(result)) {
				setRows(result);
				setTotalRows(result.length);
			} else {
				setRows([]);
				setTotalRows(0);
			}
		} catch (error: any) {
			toast.error(error?.message || "行数据加载失败");
		} finally {
			setRowsLoading(false);
		}
	};

	const loadLogs = async () => {
		if (!selectedTable) return;
		setLogsLoading(true);
		try {
			const result: any = await listEditLogs({ tableName: selectedTable, size: 50 });
			const content = Array.isArray(result?.content) ? result.content : Array.isArray(result) ? result : [];
			setLogs(content as AuditLog[]);
		} catch (error: any) {
			toast.error(error?.message || "审计日志加载失败");
		} finally {
			setLogsLoading(false);
		}
	};

	useEffect(() => {
		void loadTables();
	}, []);

	useEffect(() => {
		if (selectedTable) {
			void loadColumns(selectedTable);
			void loadRows(selectedTable, 0, pageSize);
			setPage(0);
		} else {
			setColumns([]);
			setRows([]);
		}
	}, [selectedTable]);

	const openEdit = (row?: OdsRow) => {
		setIsInsert(!row);
		setEditingRow(row || null);
		const initial: Record<string, any> = {};
		for (const col of columns) {
			if (col.name) {
				initial[col.name] = row?.[col.name] ?? "";
			}
		}
		form.setFieldsValue(initial);
		setEditModalOpen(true);
	};

	const saveRow = async () => {
		if (!canManage || !selectedTable) return;
		try {
			const values = await form.validateFields();
			if (isInsert) {
				await insertOdsRow(selectedTable, values);
				toast.success("行已插入");
			} else {
				const rowId = editingRow?.id || editingRow?.rowId || "";
				await updateOdsRow(selectedTable, String(rowId), values);
				toast.success("行已更新");
			}
			setEditModalOpen(false);
			await loadRows(selectedTable, page, pageSize);
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "保存失败");
		}
	};

	const tableColumns: ColumnsType<OdsRow> = [
		...columns.map((col) => ({
			title: col.name || "-",
			dataIndex: col.name,
			key: col.name,
			ellipsis: true,
			render: (v: any) => (v != null ? String(v) : "-"),
		})),
		{
			title: "操作",
			width: 80,
			fixed: "right" as const,
			render: (_: any, record: OdsRow) => (
				<Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)} disabled={!canManage}>
					编辑
				</Button>
			),
		},
	];

	const logColumns: ColumnsType<AuditLog> = [
		{ title: "表", dataIndex: "tableName", width: 160, render: (v) => v || "-" },
		{ title: "行ID", dataIndex: "rowId", width: 120, render: (v) => v || "-" },
		{ title: "操作", dataIndex: "action", width: 100, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "操作人", dataIndex: "changedBy", width: 120, render: (v) => v || "-" },
		{ title: "时间", dataIndex: "changedAt", width: 180, render: (v) => v || "-" },
		{ title: "详情", dataIndex: "details", ellipsis: true, render: (v) => v || "-" },
	];

	return (
		<>
			<div className="mb-3 flex items-center gap-2">
				<Select
					style={{ width: 300 }}
					placeholder="选择 ODS 表"
					value={selectedTable}
					onChange={setSelectedTable}
					allowClear
					showSearch
					options={tables.map((t) => ({ label: t, value: t }))}
				/>
				{selectedTable && (
					<>
						<Button
							type="primary"
							icon={<PlusOutlined />}
							onClick={() => openEdit()}
							disabled={!canManage || columns.length === 0}
						>
							插入行
						</Button>
						<Button onClick={() => { void loadLogs(); setLogsOpen(true); }}>
							审计日志
						</Button>
					</>
				)}
			</div>

			{selectedTable && (
				<CompactTable
					rowKey={(r, idx) => r.id || r.rowId || String(idx)}
					columns={tableColumns}
					dataSource={rows}
					loading={rowsLoading}
					scroll={{ x: Math.max(columns.length * 160, 800) }}
					pagination={{
						current: page + 1,
						pageSize,
						total: totalRows,
						showSizeChanger: true,
						onChange: (p, s) => {
							setPage(p - 1);
							setPageSize(s);
							void loadRows(selectedTable, p - 1, s);
						},
					}}
				/>
			)}

			<Modal
				open={editModalOpen}
				title={isInsert ? "插入行" : "编辑行"}
				onCancel={() => setEditModalOpen(false)}
				onOk={saveRow}
				okText="保存"
				destroyOnClose
				width={640}
			>
				<Form form={form} layout="vertical">
					{columns.map((col) => (
						<Form.Item key={col.name} label={`${col.name || ""} (${col.type || "unknown"})`} name={col.name}>
							<Input />
						</Form.Item>
					))}
				</Form>
			</Modal>

			<Modal
				open={logsOpen}
				title="数据编辑审计日志"
				onCancel={() => setLogsOpen(false)}
				footer={null}
				width={960}
			>
				<CompactTable
					rowKey={(r, idx) => r.id || String(idx)}
					columns={logColumns}
					dataSource={logs}
					loading={logsLoading}
					pagination={{ pageSize: 10 }}
				/>
			</Modal>
		</>
	);
}
