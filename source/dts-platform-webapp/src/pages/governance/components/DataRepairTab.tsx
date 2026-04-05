import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
	Button,
	Card,
	Empty,
	Input,
	InputNumber,
	Modal,
	Popconfirm,
	Radio,
	Select,
	Space,
	Table,
	Tag,
	Tooltip,
	Typography,
	Upload,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import {
	CloudUploadOutlined,
	ExperimentOutlined,
	SearchOutlined,
	ToolOutlined,
	InfoCircleOutlined,
	SafetyOutlined,
} from "@ant-design/icons";
import { useSearchParams } from "react-router";
import {
	listQualityRuns,
	listCleansingFunctions,
	previewCleansing,
	executeCleansing,
	listDatasets,
	previewSqlRepair,
	executeSqlRepair,
} from "@/api/platformApi";
import { ingestionTaskAPI } from "@/api/ingestion";
import { formatTime } from "@/utils/textUtils";
import StagingDataEditor from "./StagingDataEditor";

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
	const [taskId, setTaskId] = useState<number | undefined>(initialTaskId);
	const [datasets, setDatasets] = useState<{ id: string; name: string }[]>([]);
	const [selectedDataset, setSelectedDataset] = useState<string>();
	const [uploading, setUploading] = useState(false);
	const [checking, setChecking] = useState(false);
	const [showEditor, setShowEditor] = useState(!!initialTaskId);

	useEffect(() => {
		void loadDatasets();
	}, []);

	useEffect(() => {
		if (initialTaskId) {
			setTaskId(initialTaskId);
			setShowEditor(true);
		}
	}, [initialTaskId]);

	const loadDatasets = async () => {
		try {
			const resp: any = await listDatasets({ page: 0, size: 200 });
			const list = Array.isArray(resp?.content) ? resp.content : [];
			setDatasets(list.map((item: any) => ({ id: String(item.id), name: item.name || item.id })));
		} catch {
			// non-critical
		}
	};

	const handleUpload = async (file: File) => {
		setUploading(true);
		try {
			const result = await ingestionTaskAPI.uploadFile(file);
			toast.success(`文件上传成功：${result.originalName}，${result.rowCount ?? 0} 行`);
			// NOTE: In a full integration, the upload would create/associate with an ingestion task.
			// For now we show a note about connecting to the ingestion flow.
		} catch (error: any) {
			toast.error(error?.message || "文件上传失败");
		} finally {
			setUploading(false);
		}
		return false; // prevent antd default upload behavior
	};

	const handleStartCheck = async () => {
		if (!taskId) {
			toast.error("请输入任务ID");
			return;
		}
		setChecking(true);
		try {
			const result = await ingestionTaskAPI.preCheck(taskId);
			toast.success(`预检完成：${result.passedRows} 通过, ${result.failedRows} 失败`);
			setShowEditor(true);
		} catch (error: any) {
			toast.error(error?.message || "预检失败");
		} finally {
			setChecking(false);
		}
	};

	if (showEditor && taskId) {
		return (
			<StagingDataEditor
				taskId={taskId}
				onClose={() => setShowEditor(false)}
			/>
		);
	}

	return (
		<div className="space-y-4">
			<Alert
				type="info"
				showIcon
				icon={<InfoCircleOutlined />}
				message="入湖预检说明"
				description="上传 Excel/CSV 文件后，系统会根据目标数据集绑定的质量规则进行预检。检出的错误数据可在暂存编辑器中逐条修复后再提交入湖。当前模式需要先创建入湖任务，后续将与接入流程深度集成。"
				className="mb-2"
			/>

			{/* Upload area */}
			<Card title="上传文件" size="small">
				<Upload.Dragger
					accept=".xlsx,.xls,.csv"
					showUploadList={false}
					beforeUpload={handleUpload}
					disabled={uploading}
				>
					<p className="ant-upload-drag-icon">
						<CloudUploadOutlined style={{ fontSize: 40, color: "#1677ff" }} />
					</p>
					<p className="ant-upload-text">点击或拖拽 Excel / CSV 文件到此区域</p>
					<p className="ant-upload-hint">支持 .xlsx, .xls, .csv 格式</p>
				</Upload.Dragger>
			</Card>

			{/* Target dataset + task ID */}
			<Card title="预检配置" size="small">
				<div className="flex flex-wrap items-end gap-4">
					<div>
						<Typography.Text className="mb-1 block text-xs text-gray-500">
							目标数据集
						</Typography.Text>
						<Select
							style={{ width: 260 }}
							placeholder="选择目标数据集"
							options={datasets.map((d) => ({ label: d.name, value: d.id }))}
							value={selectedDataset}
							onChange={setSelectedDataset}
							allowClear
							showSearch
							optionFilterProp="label"
						/>
					</div>
					<div>
						<Typography.Text className="mb-1 block text-xs text-gray-500">
							入湖任务 ID
						</Typography.Text>
						<InputNumber
							style={{ width: 180 }}
							placeholder="输入任务ID"
							value={taskId}
							onChange={(v) => setTaskId(v ?? undefined)}
							min={1}
						/>
					</div>
					<Button
						type="primary"
						icon={<SearchOutlined />}
						loading={checking}
						onClick={handleStartCheck}
						disabled={!taskId}
					>
						开始检查
					</Button>
				</div>
			</Card>
		</div>
	);
}

/* ========== SqlRepairEditor ========== */

type SqlPreviewSample = {
	rowId: any;
	columnValues: Record<string, string>;
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
				render: (_: any, record: SqlPreviewSample) => record.columnValues?.[key] ?? "-",
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
						当前数据预览（修复前）
					</Typography.Text>
					<Table
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
			const resp: any = await listQualityRuns({ status: "FAILED" });
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
			toast.success(`预览完成：影响 ${resp?.affectedRows ?? 0} 行`);
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
			toast.success(`清洗完成：影响 ${resp?.affectedRows ?? 0} 行`);
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
			render: (v) => (v != null ? <Tag color="red">{v}</Tag> : "-"),
		},
		{
			title: "时间",
			dataIndex: "finishedAt",
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
						icon={<ToolOutlined />}
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
						icon={<ExperimentOutlined />}
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
					<Table
						rowKey={(r) => r.id || Math.random().toString(36)}
						columns={columns}
						dataSource={runs}
						loading={loading}
						size="small"
						pagination={{ showSizeChanger: true, defaultPageSize: 20 }}
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
										onChange={setSelectedFnId}
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
									disabled={!selectedFnId || previewRows.length === 0}
								>
									执行清洗
								</Button>
							</div>

							{/* Preview table */}
							{previewRows.length > 0 && (
								<Table
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

							{/* Manual edit - placeholder */}
							<div className="rounded border border-dashed border-gray-300 p-3 text-center text-gray-400">
								<ToolOutlined className="mr-1" />
								人工编辑 — 即将支持
							</div>
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
