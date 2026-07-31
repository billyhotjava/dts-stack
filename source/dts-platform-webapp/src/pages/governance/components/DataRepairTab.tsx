import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
	Button,
	Card,
	Empty,
	Input,
	Modal,
	Popconfirm,
	Radio,
	Select,
	Space,
	Tag,
	Tooltip,
	Typography,
} from "antd";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { SafetyOutlined } from "@ant-design/icons";
import { useNavigate, useSearchParams } from "react-router";
import {
	listQualityRuns,
	listCleansingFunctions,
	previewCleansing,
	executeCleansing,
	listDatasets,
	previewSqlRepair,
	executeSqlRepair,
	listFailingRows,
	updateOdsRow,
} from "@/api/platformApi";
import { formatTime } from "@/utils/textUtils";

/* ---------- types ---------- */

type RepairMode = "precheck" | "fix";

type QualityRunRow = {
	id?: string;
	ruleId?: string;
	ruleVersionId?: string;
	datasetId?: string;
	triggerType?: string;
	status?: string;
	startedAt?: string;
	finishedAt?: string;
	durationMs?: number;
	message?: string;
	totalRows?: number;
	failedRows?: number;
	rowsTotal?: number;
	failingRowCount?: number;
	ruleName?: string;
	datasetName?: string;
};

type CleansingFn = {
	id?: string;
	code?: string;
	name?: string;
	sqlExpression?: string;
	description?: string;
};

type CleansingPreviewRow = {
	rowId: number;
	before: string;
	after: string;
};

/* ========== PreCheckMode ========== */

function PreCheckMode({ initialTaskId }: { initialTaskId?: number }) {
	const navigate = useNavigate();
	const taskPath = initialTaskId
		? `/foundation/data-sources/access/${encodeURIComponent(String(initialTaskId))}?tab=quality`
		: "/foundation/data-sources/access/new?kind=file";

	return (
		<div className="space-y-4">
			<Alert
				type="info"
				showIcon
				message="文件预检已并入统一数据接入流程"
				description="文件密级声明、加密上传、解析、质量预检、准入与执行必须在同一个 Revision 中完成。数据质量模块继续维护规则和运行结果，不再单独创建或直写入湖任务。"
			/>
			<Card title={initialTaskId ? `接入任务 #${initialTaskId}` : "创建离线文件接入"} size="small">
				<Space wrap>
					<Button type="primary" onClick={() => navigate(taskPath)}>
						{initialTaskId ? "查看任务预检与异常" : "前往离线文件接入"}
					</Button>
					<Button onClick={() => navigate("/governance/rules")}>维护数据质量规则</Button>
				</Space>
			</Card>
		</div>
	);
}

/* ========== SqlRepairEditor ========== */

type SqlPreviewSample = {
	rowId: any;
	columnValues: Record<string, string>;
	newValues?: Record<string, string>;
};

type FailingRowItem = {
	id?: string;
	rowId?: string;
	tableName?: string;
	columnName?: string;
	actualValue?: string;
	failReason?: string;
	rowData?: Record<string, any>;
};

function SqlRepairEditor({ runId }: { runId?: string }) {
	const [sql, setSql] = useState("");
	const [previewLoading, setPreviewLoading] = useState(false);
	const [executing, setExecuting] = useState(false);
	const [affectedRows, setAffectedRows] = useState<number | null>(null);
	const [samples, setSamples] = useState<SqlPreviewSample[]>([]);
	const [previewed, setPreviewed] = useState(false);

	const handlePreview = async () => {
		if (!sql.trim()) {
			toast.error("请输入 SQL 语句");
			return;
		}
		setPreviewLoading(true);
		setPreviewed(false);
		setAffectedRows(null);
		setSamples([]);
		try {
			const resp: any = await previewSqlRepair({ sql, limit: 10 });
			setAffectedRows(resp?.affectedRows ?? 0);
			setSamples(resp?.samples ?? []);
			setPreviewed(true);
			toast.success(`预览完成：影响 ${resp?.affectedRows ?? 0} 行`);
		} catch (error: any) {
			toast.error(error?.message || "SQL 预览失败");
		} finally {
			setPreviewLoading(false);
		}
	};

	const handleExecute = async () => {
		if (!sql.trim()) return;
		setExecuting(true);
		try {
			const resp: any = await executeSqlRepair({ sql, runId });
			toast.success(`SQL 修复完成：影响 ${resp?.affectedRows ?? 0} 行`);
			setSql("");
			setPreviewed(false);
			setAffectedRows(null);
			setSamples([]);
		} catch (error: any) {
			toast.error(error?.message || "SQL 执行失败");
		} finally {
			setExecuting(false);
		}
	};

	// Build dynamic columns from sample data
	const previewColumns = useMemo(() => {
		if (samples.length === 0) return [];
		const cols: ColumnsType<SqlPreviewSample> = [
			{
				title: "行ID",
				dataIndex: "rowId",
				width: 120,
				render: (v) => (v != null ? String(v).slice(0, 12) : "-"),
			},
		];
		// Gather all column keys from samples
		const colKeys = new Set<string>();
		for (const s of samples) {
			if (s.columnValues) {
				for (const k of Object.keys(s.columnValues)) colKeys.add(k);
			}
		}
		for (const key of colKeys) {
			cols.push({
				title: key,
				dataIndex: ["columnValues", key],
				ellipsis: true,
				render: (_: any, record: SqlPreviewSample) => {
					const before = record.columnValues?.[key] ?? "";
					const after = record.newValues?.[key];
					if (after == null) return before || "-";
					const changed = String(before) !== String(after);
					return (
						<div className="space-y-1">
							<div className={changed ? "text-red-500 line-through" : undefined}>{before || "-"}</div>
							<div className={changed ? "font-medium text-green-600" : "text-gray-500"}>{after || "-"}</div>
						</div>
					);
				},
			});
		}
		return cols;
	}, [samples]);

	return (
		<div className="space-y-3 rounded border border-solid border-blue-200 bg-blue-50/30 p-3">
			<div className="flex items-center gap-2">
				<SafetyOutlined className="text-blue-500" />
				<Typography.Text strong>SQL 修复</Typography.Text>
			</div>

			<Alert
				type="warning"
				showIcon
				message="安全约束：仅允许 UPDATE ods_* 表，禁止 DELETE/DROP/TRUNCATE/ALTER/CREATE/INSERT INTO/SELECT INTO"
				className="text-xs"
			/>

			<Input.TextArea
				value={sql}
				onChange={(e) => {
					setSql(e.target.value);
					setPreviewed(false);
				}}
				placeholder="UPDATE ods_customer SET gender = '男' WHERE gender IN ('male', 'M')"
				autoSize={{ minRows: 3, maxRows: 8 }}
				style={{ fontFamily: "monospace" }}
			/>

			<div className="flex items-center gap-2">
				<Button
					loading={previewLoading}
					onClick={handlePreview}
					disabled={!sql.trim()}
				>
					预览效果
				</Button>
				<Popconfirm
					title="确认执行 SQL 修复？"
					description={affectedRows != null ? `预计影响 ${affectedRows} 行` : undefined}
					onConfirm={handleExecute}
					okText="确认执行"
					cancelText="取消"
					disabled={!previewed}
				>
					<Button
						type="primary"
						loading={executing}
						disabled={!previewed}
					>
						执行修复
					</Button>
				</Popconfirm>
				{affectedRows != null && (
					<Tag color="orange">影响行数：{affectedRows}</Tag>
				)}
			</div>

			{samples.length > 0 && (
				<div>
					<Typography.Text type="secondary" className="mb-1 block text-xs">
						修复预览（上方为当前值，下方为执行后）
					</Typography.Text>
					<CompactTable
						rowKey={(r) => String(r.rowId ?? Math.random())}
						columns={previewColumns}
						dataSource={samples}
						size="small"
						pagination={false}
						scroll={{ x: "max-content", y: 240 }}
					/>
				</div>
			)}
		</div>
	);
}

function ManualFailingRowsEditor({ runId, onSaved }: { runId?: string; onSaved?: () => void }) {
	const [rows, setRows] = useState<FailingRowItem[]>([]);
	const [loading, setLoading] = useState(false);
	const [savingKey, setSavingKey] = useState("");
	const [draftValues, setDraftValues] = useState<Record<string, string>>({});

	const loadRows = useCallback(async () => {
		if (!runId) return;
		setLoading(true);
		try {
			const resp: any = await listFailingRows(runId, { page: 0, size: 50 });
			const list = Array.isArray(resp?.content) ? resp.content : [];
			setRows(list as FailingRowItem[]);
			const nextDrafts: Record<string, string> = {};
			for (const row of list as FailingRowItem[]) {
				const key = String(row.id || `${row.tableName}-${row.rowId}-${row.columnName}`);
				const current = row.columnName ? row.rowData?.[row.columnName] ?? row.actualValue ?? "" : "";
				nextDrafts[key] = current != null ? String(current) : "";
			}
			setDraftValues(nextDrafts);
		} catch (error: any) {
			toast.error(error?.message || "失败行加载失败");
		} finally {
			setLoading(false);
		}
	}, [runId]);

	useEffect(() => {
		void loadRows();
	}, [loadRows]);

	const saveRow = async (row: FailingRowItem) => {
		if (!row.tableName || !row.rowId || !row.columnName) {
			toast.error("失败行缺少表名、行ID或字段名，无法保存");
			return;
		}
		const key = String(row.id || `${row.tableName}-${row.rowId}-${row.columnName}`);
		setSavingKey(key);
		try {
			await updateOdsRow(row.tableName, row.rowId, {
				[row.columnName]: draftValues[key] ?? "",
				_reason: `质量问题修复${runId ? `：${runId}` : ""}`,
				_failingRowId: row.id ?? "",
			});
			toast.success("字段已修复，失败明细已移除；重新运行质量校验后可确认规则通过");
			onSaved?.();
			await loadRows();
		} catch (error: any) {
			toast.error(error?.message || "字段保存失败");
		} finally {
			setSavingKey("");
		}
	};

	const columns: ColumnsType<FailingRowItem> = [
		{ title: "表", dataIndex: "tableName", width: 180, ellipsis: true },
		{ title: "行ID", dataIndex: "rowId", width: 120, ellipsis: true },
		{ title: "字段", dataIndex: "columnName", width: 140, ellipsis: true },
		{
			title: "原因",
			dataIndex: "failReason",
			width: 180,
			ellipsis: true,
			render: (value) => value || "-",
		},
		{
			title: "修复值",
			width: 220,
			render: (_, row) => {
				const key = String(row.id || `${row.tableName}-${row.rowId}-${row.columnName}`);
				return (
					<Input
						size="small"
						value={draftValues[key]}
						onChange={(event) => setDraftValues((prev) => ({ ...prev, [key]: event.target.value }))}
					/>
				);
			},
		},
		{
			title: "操作",
			width: 90,
			render: (_, row) => {
				const key = String(row.id || `${row.tableName}-${row.rowId}-${row.columnName}`);
				return (
					<Button size="small" type="primary" loading={savingKey === key} onClick={() => saveRow(row)}>
						保存
					</Button>
				);
			},
		},
	];

	return (
		<div className="space-y-2 rounded border border-solid border-gray-200 p-3">
			<div className="flex items-center justify-between">
				<Typography.Text strong>人工编辑</Typography.Text>
				<Button size="small" loading={loading} onClick={() => void loadRows()}>
					刷新失败行
				</Button>
			</div>
			<CompactTable
				rowKey={(row) => row.id || `${row.tableName}-${row.rowId}-${row.columnName}`}
				columns={columns}
				dataSource={rows}
				loading={loading}
				size="small"
				pagination={{ defaultPageSize: 10 }}
				scroll={{ x: 900 }}
				locale={{ emptyText: "暂无失败行明细" }}
			/>
		</div>
	);
}

/* ========== QualityFixMode ========== */

function QualityFixMode({ initialRunId }: { initialRunId?: string }) {
	const [runs, setRuns] = useState<QualityRunRow[]>([]);
	const [loading, setLoading] = useState(false);
	const [datasets, setDatasets] = useState<{ id: string; name: string }[]>([]);

	// Batch cleansing modal state
	const [cleansingModalOpen, setCleansingModalOpen] = useState(false);
	const [selectedRunId, setSelectedRunId] = useState<string>();
	const [cleansingFunctions, setCleansingFunctions] = useState<CleansingFn[]>([]);
	const [selectedFnId, setSelectedFnId] = useState<string>();
	const [previewRows, setPreviewRows] = useState<CleansingPreviewRow[]>([]);
	const [previewAffectedRows, setPreviewAffectedRows] = useState<number | null>(null);
	const [previewUnresolvableRows, setPreviewUnresolvableRows] = useState<number>(0);
	const [previewLoading, setPreviewLoading] = useState(false);
	const [executing, setExecuting] = useState(false);

	const datasetMap = useMemo(() => {
		const map = new Map<string, string>();
		for (const d of datasets) map.set(d.id, d.name);
		return map;
	}, [datasets]);

	const loadRuns = useCallback(async () => {
		setLoading(true);
		try {
			const resp: any = await listQualityRuns({ status: "FAILED", limit: 200 });
			const list = Array.isArray(resp?.content) ? resp.content : Array.isArray(resp) ? resp : [];
			setRuns(list as QualityRunRow[]);
		} catch (error: any) {
			toast.error(error?.message || "加载失败任务列表出错");
		} finally {
			setLoading(false);
		}
	}, []);

	const loadDatasets = async () => {
		try {
			const resp: any = await listDatasets({ page: 0, size: 200 });
			const list = Array.isArray(resp?.content) ? resp.content : [];
			setDatasets(list.map((item: any) => ({ id: String(item.id), name: item.name || item.id })));
		} catch {
			// non-critical
		}
	};

	const loadCleansingFunctions = async () => {
		try {
			const list = await listCleansingFunctions();
			setCleansingFunctions(Array.isArray(list) ? (list as CleansingFn[]) : []);
		} catch {
			// non-critical
		}
	};

	useEffect(() => {
		void loadRuns();
		void loadDatasets();
		void loadCleansingFunctions();
	}, [loadRuns]);

	// Auto-open cleansing for initialRunId
	useEffect(() => {
		if (initialRunId && runs.length > 0) {
			const found = runs.find((r) => r.id === initialRunId);
			if (found) {
				openCleansingModal(found.id!);
			}
		}
	}, [initialRunId, runs]);

	const openCleansingModal = (runId: string) => {
		setSelectedRunId(runId);
		setSelectedFnId(undefined);
		setPreviewRows([]);
		setPreviewAffectedRows(null);
		setPreviewUnresolvableRows(0);
		setCleansingModalOpen(true);
	};

	const handlePreview = async () => {
		if (!selectedRunId || !selectedFnId) {
			toast.error("请选择清洗函数");
			return;
		}
		setPreviewLoading(true);
		try {
			const resp: any = await previewCleansing({
				runId: selectedRunId,
				functionId: selectedFnId,
				limit: 20,
			});
			setPreviewRows(resp?.samples ?? []);
			setPreviewAffectedRows(resp?.affectedRows ?? 0);
			setPreviewUnresolvableRows(resp?.unresolvableRows ?? 0);
			const unresolved = Number(resp?.unresolvableRows ?? 0);
			if (unresolved > 0) {
				toast.warning(`预览完成：预计影响 ${resp?.affectedRows ?? 0} 行，${unresolved} 行无法解析`);
			} else {
				toast.success(`预览完成：预计影响 ${resp?.affectedRows ?? 0} 行`);
			}
		} catch (error: any) {
			toast.error(error?.message || "预览失败");
		} finally {
			setPreviewLoading(false);
		}
	};

	const handleExecute = async () => {
		if (!selectedRunId || !selectedFnId) return;
		setExecuting(true);
		try {
			const resp: any = await executeCleansing({
				runId: selectedRunId,
				functionId: selectedFnId,
			});
			const unresolved = Number(resp?.unresolvableRows ?? 0);
			if (unresolved > 0) {
				toast.warning(`清洗完成：影响 ${resp?.affectedRows ?? 0} 行，${unresolved} 行无法解析`);
			} else {
				toast.success(`清洗完成：影响 ${resp?.affectedRows ?? 0} 行`);
			}
			setCleansingModalOpen(false);
			void loadRuns();
		} catch (error: any) {
			toast.error(error?.message || "执行清洗失败");
		} finally {
			setExecuting(false);
		}
	};

	const handleIgnore = (run: QualityRunRow) => {
		toast.info(`已忽略运行 ${run.id}`);
		setRuns((prev) => prev.filter((r) => r.id !== run.id));
	};

	const handleManualRowSaved = () => {
		if (!selectedRunId) return;
		setRuns((prev) =>
			prev.map((run) => {
				if (run.id !== selectedRunId) return run;
				const nextFailedRows = run.failedRows != null ? Math.max(0, run.failedRows - 1) : run.failedRows;
				const nextFailingRowCount =
					run.failingRowCount != null ? Math.max(0, run.failingRowCount - 1) : run.failingRowCount;
				return { ...run, failedRows: nextFailedRows, failingRowCount: nextFailingRowCount };
			}),
		);
	};

	const columns: ColumnsType<QualityRunRow> = [
		{
			title: "运行ID",
			dataIndex: "id",
			width: 200,
			ellipsis: true,
			render: (v) => (
				<Tooltip title={v}>
					<Typography.Text copyable={{ text: v }} className="text-xs">
						{v ? String(v).slice(0, 12) + "..." : "-"}
					</Typography.Text>
				</Tooltip>
			),
		},
		{
			title: "规则",
			dataIndex: "ruleName",
			sorter: (a, b) => (a.ruleName || "").localeCompare(b.ruleName || ""),
			width: 160,
			render: (v, record) => v || record.ruleId || "-",
		},
		{
			title: "数据集",
			dataIndex: "datasetId",
			width: 160,
			render: (v) => datasetMap.get(String(v)) || v || "-",
		},
		{
			title: "失败行数",
			dataIndex: "failedRows",
			width: 100,
			render: (v, record) => {
				const count = v ?? record.failingRowCount;
				return count != null ? <Tag color="red">{count}</Tag> : "-";
			},
		},
		{
			title: "时间",
			dataIndex: "finishedAt",
			sorter: (a, b) => {
				const ta = a.finishedAt ? new Date(a.finishedAt as any).getTime() : 0;
				const tb = b.finishedAt ? new Date(b.finishedAt as any).getTime() : 0;
				return ta - tb;
			},
			width: 180,
			render: formatTime,
		},
		{
			title: "操作",
			width: 220,
			render: (_, record) => (
				<Space>
					<Button
						size="small"
						type="primary"
						onClick={() => openCleansingModal(record.id!)}
						disabled={!record.id}
					>
						去修复
					</Button>
					<Button
						size="small"
						onClick={() => handleIgnore(record)}
					>
						忽略
					</Button>
				</Space>
			),
		},
	];

	const previewColumns: ColumnsType<CleansingPreviewRow> = [
		{ title: "行号", dataIndex: "rowId", width: 80 },
		{ title: "修复前", dataIndex: "before", ellipsis: true },
		{ title: "修复后", dataIndex: "after", ellipsis: true },
	];

	return (
		<div className="space-y-4">
			<Card
				title="待修复任务列表"
				size="small"
				extra={
					<Button
						onClick={() => void loadRuns()}
						loading={loading}
					>
						刷新
					</Button>
				}
			>
				{runs.length === 0 && !loading ? (
					<Empty description="暂无失败的质量检查任务" />
				) : (
					<CompactTable
						rowKey={(r) => r.id || Math.random().toString(36)}
						columns={columns}
						dataSource={runs}
						loading={loading}
						size="small"
						pagination={{ showSizeChanger: true, defaultPageSize: 10 }}
					/>
				)}
			</Card>

			{/* Batch cleansing modal */}
			<Modal
				open={cleansingModalOpen}
				title="数据修复"
				onCancel={() => setCleansingModalOpen(false)}
				footer={null}
				width={800}
				destroyOnClose
			>
				<div className="space-y-4">
					<Typography.Text type="secondary">
						运行 ID：{selectedRunId}
					</Typography.Text>

					{/* Repair mode selection */}
					<Card size="small" title="修复方式">
						<div className="space-y-3">
							{/* Batch cleansing */}
							<div className="flex flex-wrap items-end gap-3">
								<div>
									<Typography.Text className="mb-1 block text-xs text-gray-500">
										批量清洗函数
									</Typography.Text>
									<Select
										style={{ width: 260 }}
										placeholder="选择清洗函数"
										options={cleansingFunctions.map((fn) => ({
											label: fn.name || fn.code || fn.id,
											value: fn.id,
										}))}
										value={selectedFnId}
										onChange={(value) => {
											setSelectedFnId(value);
											setPreviewRows([]);
											setPreviewAffectedRows(null);
											setPreviewUnresolvableRows(0);
										}}
										allowClear
									/>
								</div>
								<Button
									loading={previewLoading}
									onClick={handlePreview}
									disabled={!selectedFnId}
								>
									预览效果
								</Button>
								<Button
									type="primary"
									loading={executing}
									onClick={handleExecute}
									disabled={!selectedFnId || !previewAffectedRows}
								>
									执行清洗
								</Button>
							</div>

							{previewAffectedRows != null && (
								<Space>
									<Tag color={previewAffectedRows > 0 ? "orange" : "default"}>
										预计影响：{previewAffectedRows} 行
									</Tag>
									{previewUnresolvableRows > 0 && (
										<Tag color="red">无法解析：{previewUnresolvableRows} 行</Tag>
									)}
								</Space>
							)}

							{/* Preview table */}
							{previewRows.length > 0 && (
								<CompactTable
									rowKey={(r) => r.rowId}
									columns={previewColumns}
									dataSource={previewRows}
									size="small"
									pagination={false}
									scroll={{ y: 300 }}
								/>
							)}

							{/* SQL repair */}
							<SqlRepairEditor runId={selectedRunId} />

							<ManualFailingRowsEditor runId={selectedRunId} onSaved={handleManualRowSaved} />
						</div>
					</Card>
				</div>
			</Modal>
		</div>
	);
}

/* ========== DataRepairTab (container) ========== */

export default function DataRepairTab() {
	const [searchParams, setSearchParams] = useSearchParams();

	const mode: RepairMode = (searchParams.get("mode") as RepairMode) || "precheck";
	const taskIdParam = searchParams.get("taskId");
	const runIdParam = searchParams.get("runId");

	const initialTaskId = taskIdParam ? Number(taskIdParam) : undefined;
	const initialRunId = runIdParam || undefined;

	const handleModeChange = (value: RepairMode) => {
		const params = new URLSearchParams(searchParams);
		params.set("tab", "repair");
		params.set("mode", value);
		// Clear context params when switching modes
		params.delete("taskId");
		params.delete("runId");
		setSearchParams(params, { replace: true });
	};

	return (
		<div className="space-y-4">
			<Radio.Group
				value={mode}
				onChange={(e) => handleModeChange(e.target.value)}
				optionType="button"
				buttonStyle="solid"
			>
				<Radio.Button value="precheck">入湖预检</Radio.Button>
				<Radio.Button value="fix">质量问题修复</Radio.Button>
			</Radio.Group>

			{mode === "precheck" ? (
				<PreCheckMode initialTaskId={initialTaskId} />
			) : (
				<QualityFixMode initialRunId={initialRunId} />
			)}
		</div>
	);
}
