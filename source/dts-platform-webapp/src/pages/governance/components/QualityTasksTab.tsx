import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
	Button,
	Card,
	DatePicker,
	Descriptions,
	Drawer,
	Form,
	Input,
	InputNumber,
	Modal,
	Popconfirm,
	Progress,
	Select,
	Space,
	Switch,
	Tag,
} from "antd";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import {
	DeleteOutlined,
	EyeOutlined,
	PauseCircleOutlined,
	PlayCircleOutlined,
	EditOutlined,
	PlusOutlined,
	ReloadOutlined,
} from "@ant-design/icons";
import { useSearchParams } from "react-router";
import {
	createQualityTask,
	deleteQualityTask,
	getQualityRun,
	listDatasets,
	listQualityRules,
	listQualityRuns,
	listQualityTasks,
	toggleQualityTask,
	triggerQualityTask,
	updateQualityTask,
} from "@/api/platformApi";
import { ingestionTaskAPI } from "@/api/ingestion";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import { formatTime } from "@/utils/textUtils";

const { RangePicker } = DatePicker;

/* ---------- types ---------- */

type QualityTask = {
	id?: string;
	name?: string;
	datasetId?: string;
	ruleId?: string;
	ownerDept?: string;
	intervalMinutes?: number;
	enabled?: boolean;
	lastTriggeredAt?: string;
};

type QualityTaskForm = {
	name: string;
	datasetId: string;
	ruleId?: string;
	intervalMinutes: number;
	enabled: boolean;
	ownerDept?: string;
};

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
	inputParamsJson?: string;
	errorCategory?: string;
	metrics?: Array<Record<string, any>>;
	metricsJson?: string;
	executedSql?: string;
	totalRows?: number;
	passedRows?: number;
	failedRows?: number;
	failingRowsSample?: Array<Record<string, any>>;
};

type RunFilters = {
	triggerType?: string;
	status?: string;
	ruleId?: string;
	datasetId?: string;
	dateRange?: [any, any] | null;
};

/* ---------- constants ---------- */

const STATUS_COLOR: Record<string, string> = {
	SUCCESS: "green",
	SUCCEEDED: "green",
	PASSED: "green",
	FAILED: "red",
	ERROR: "red",
	RUNNING: "blue",
	QUEUED: "default",
	SKIPPED: "default",
};

const TRIGGER_LABEL: Record<string, string> = {
	SCHEDULE: "调度",
	MANUAL: "手动",
	DRY_RUN: "入湖预检",
	INGESTION: "入湖预检",
};

const TRIGGER_OPTIONS = [
	{ label: "调度", value: "SCHEDULE" },
	{ label: "手动", value: "MANUAL" },
	{ label: "入湖预检", value: "DRY_RUN" },
];

const STATUS_OPTIONS = [
	{ label: "排队中", value: "QUEUED" },
	{ label: "运行中", value: "RUNNING" },
	{ label: "成功", value: "SUCCESS" },
	{ label: "成功", value: "SUCCEEDED" },
	{ label: "失败", value: "FAILED" },
];

const ALL_RULES_VALUE = "__ALL_RULES__";

/* ---------- component ---------- */

export default function QualityTasksTab() {
	const [, setSearchParams] = useSearchParams();
	const canManage = useGovernanceManageAccess();

	/* --- reference data --- */
	const [datasets, setDatasets] = useState<{ id: string; name: string }[]>([]);
	const [datasetLoadMessage, setDatasetLoadMessage] = useState<string>();
	const [rules, setRules] = useState<{ id: string; name: string }[]>([]);

	/* --- schedule section --- */
	const [tasks, setTasks] = useState<QualityTask[]>([]);
	const [tasksLoading, setTasksLoading] = useState(false);
	const [actionTaskId, setActionTaskId] = useState("");
	const [taskModalOpen, setTaskModalOpen] = useState(false);
	const [editingTask, setEditingTask] = useState<QualityTask | null>(null);
	const [savingTask, setSavingTask] = useState(false);
	const [taskForm] = Form.useForm<QualityTaskForm>();

	/* --- execution history --- */
	const [runs, setRuns] = useState<QualityRunRow[]>([]);
	const [runsLoading, setRunsLoading] = useState(false);
	const [runFilters, setRunFilters] = useState<RunFilters>({});
	const [runPage, setRunPage] = useState(1);
	const [runPageSize, setRunPageSize] = useState(20);
	const [runTotal, setRunTotal] = useState(0);

	/* --- detail drawer --- */
	const [drawerOpen, setDrawerOpen] = useState(false);
	const [detailRun, setDetailRun] = useState<QualityRunRow | null>(null);
	const [detailLoading, setDetailLoading] = useState(false);

	/* --- lookup maps --- */
	const datasetMap = useMemo(() => {
		const m = new Map<string, string>();
		for (const d of datasets) m.set(d.id, d.name);
		return m;
	}, [datasets]);

	const ruleMap = useMemo(() => {
		const m = new Map<string, string>();
		for (const r of rules) m.set(r.id, r.name);
		return m;
	}, [rules]);

	const datasetOptions = useMemo(
		() => datasets.map((d) => ({ label: d.name, value: d.id })),
		[datasets],
	);
	const ruleOptions = useMemo(
		() => rules.map((r) => ({ label: r.name, value: r.id })),
		[rules],
	);
	const taskRuleOptions = useMemo(
		() => [{ label: "自动匹配数据集全部规则", value: ALL_RULES_VALUE }, ...ruleOptions],
		[ruleOptions],
	);

	/* --- loaders --- */

	const loadRefData = useCallback(async () => {
		try {
			const lake = await ingestionTaskAPI.getDefaultDestinationStatus();
			const datasetPromise = lake?.available && lake.dataSourceId
				? listDatasets({ page: 0, size: 300, enabledOnly: true, sourceId: lake.dataSourceId })
				: Promise.resolve({ content: [] });
			if (!lake?.available || !lake.dataSourceId) {
				setDatasetLoadMessage(lake?.message || "未识别默认数据湖连接");
			}
			const [dsResp, ruleResp] = await Promise.all([
				datasetPromise,
				listQualityRules(),
			]);
			const dsList = Array.isArray((dsResp as any)?.content) ? (dsResp as any).content : [];
			setDatasets(dsList.map((d: any) => ({ id: String(d.id), name: d.name || String(d.id) })));
			if (lake?.available && lake.dataSourceId) {
				setDatasetLoadMessage(dsList.length ? undefined : "默认数据湖连接下暂无可用数据集");
			}
			const rList = Array.isArray(ruleResp) ? (ruleResp as any[]) : [];
			setRules(rList.map((r) => ({ id: String(r.id), name: String(r.name || r.id) })));
		} catch (err: any) {
			setDatasets([]);
			setDatasetLoadMessage(err?.message || "默认数据湖连接读取失败");
			toast.error(err?.message || "参考数据加载失败");
		}
	}, []);

	const loadTasks = useCallback(async () => {
		setTasksLoading(true);
		try {
			const list = await listQualityTasks();
			setTasks(Array.isArray(list) ? (list as QualityTask[]) : []);
		} catch (err: any) {
			toast.error(err?.message || "调度计划加载失败");
		} finally {
			setTasksLoading(false);
		}
	}, []);

	const loadRuns = useCallback(async () => {
		setRunsLoading(true);
		try {
			const requestLimit = Math.max(runPage * runPageSize, runPageSize);
			const params: Record<string, any> = {
				limit: requestLimit,
			};
			if (runFilters.triggerType) params.triggerType = runFilters.triggerType;
			if (runFilters.status) params.status = runFilters.status;
			if (runFilters.ruleId) params.ruleId = runFilters.ruleId;
			if (runFilters.datasetId) params.datasetId = runFilters.datasetId;
			if (runFilters.dateRange?.[0]) params.startedFrom = runFilters.dateRange[0].startOf("day").toISOString();
			if (runFilters.dateRange?.[1]) params.startedTo = runFilters.dateRange[1].endOf("day").toISOString();

			const resp: any = await listQualityRuns(params);
			// API may return paginated or plain array
			if (Array.isArray(resp)) {
				const start = (runPage - 1) * runPageSize;
				setRuns(resp.slice(start, start + runPageSize));
				setRunTotal(resp.length);
			} else if (resp?.content) {
				setRuns(Array.isArray(resp.content) ? resp.content : []);
				setRunTotal(Number(resp.totalElements || resp.content?.length || 0));
			} else {
				setRuns([]);
				setRunTotal(0);
			}
		} catch (err: any) {
			toast.error(err?.message || "执行记录加载失败");
		} finally {
			setRunsLoading(false);
		}
	}, [runPage, runPageSize, runFilters]);

	useEffect(() => {
		void loadRefData();
		void loadTasks();
	}, [loadRefData, loadTasks]);

	useEffect(() => {
		void loadRuns();
	}, [loadRuns]);

	/* --- schedule actions --- */

	const handleToggleTask = async (task: QualityTask) => {
		if (!task.id) return;
		try {
			setActionTaskId(task.id);
			await toggleQualityTask(task.id, !task.enabled);
			toast.success(task.enabled ? "已暂停" : "已启用");
			await loadTasks();
		} catch (err: any) {
			toast.error(err?.message || "操作失败");
		} finally {
			setActionTaskId("");
		}
	};

	const handleTriggerTask = async (task: QualityTask) => {
		if (!task.id) return;
		try {
			setActionTaskId(task.id);
			await triggerQualityTask(task.id);
			toast.success("已触发执行");
			await loadTasks();
			await loadRuns();
		} catch (err: any) {
			toast.error(err?.message || "触发失败");
		} finally {
			setActionTaskId("");
		}
	};

	const openTaskModal = (task?: QualityTask) => {
		setEditingTask(task || null);
		taskForm.setFieldsValue({
			name: String(task?.name || ""),
			datasetId: String(task?.datasetId || ""),
			ruleId: task?.ruleId ? String(task.ruleId) : ALL_RULES_VALUE,
			intervalMinutes: task?.intervalMinutes || 60,
			enabled: task?.enabled !== false,
			ownerDept: String(task?.ownerDept || ""),
		});
		setTaskModalOpen(true);
	};

	const saveTask = async () => {
		try {
			const values = await taskForm.validateFields();
			setSavingTask(true);
			const payload = {
				name: values.name,
				datasetId: values.datasetId,
				ruleId: values.ruleId && values.ruleId !== ALL_RULES_VALUE ? values.ruleId : undefined,
				intervalMinutes: Number(values.intervalMinutes || 60),
				enabled: values.enabled !== false,
				ownerDept: values.ownerDept?.trim() || undefined,
			};
			if (editingTask?.id) {
				await updateQualityTask(editingTask.id, payload);
				toast.success("调度计划已更新");
			} else {
				await createQualityTask(payload);
				toast.success("调度计划已创建");
			}
			setTaskModalOpen(false);
			await loadTasks();
		} catch (err: any) {
			if (err?.errorFields) return;
			toast.error(err?.message || "调度计划保存失败");
		} finally {
			setSavingTask(false);
		}
	};

	const handleDeleteTask = async (task: QualityTask) => {
		if (!task.id) return;
		try {
			setActionTaskId(task.id);
			await deleteQualityTask(task.id);
			toast.success("调度计划已删除");
			await loadTasks();
		} catch (err: any) {
			toast.error(err?.message || "调度计划删除失败");
		} finally {
			setActionTaskId("");
		}
	};

	/* --- detail drawer --- */

	const openDetail = async (run: QualityRunRow) => {
		setDrawerOpen(true);
		setDetailRun(run);
		if (run.id) {
			setDetailLoading(true);
			try {
				const detail = (await getQualityRun(run.id)) as QualityRunRow;
				setDetailRun(detail);
			} catch {
				// keep the row-level data if detail fetch fails
			} finally {
				setDetailLoading(false);
			}
		}
	};

	const goToRepair = (runId?: string) => {
		const params = new URLSearchParams();
		params.set("tab", "repair");
		if (runId) params.set("runId", runId);
		setSearchParams(params, { replace: true });
	};

	/* --- schedule columns --- */

	const scheduleColumns: ColumnsType<QualityTask> = [
		{ title: "名称", dataIndex: "name", render: (v) => v || "-" , sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
		{
			title: "关联规则",
			dataIndex: "ruleId",
			render: (v) => (v ? ruleMap.get(String(v)) || v : "自动匹配全规则"),
		},
		{
			title: "数据集",
			dataIndex: "datasetId",
			render: (v) => datasetMap.get(String(v || "")) || v || "-",
		},
		{
			title: "周期",
			dataIndex: "intervalMinutes",
			width: 120,
			render: (v) => (v ? `每 ${v} 分钟` : "-"),
		},
		{
			title: "状态",
			dataIndex: "enabled",
			width: 90,
			render: (v) => <Tag color={v ? "green" : "default"}>{v ? "运行中" : "已暂停"}</Tag>,
		},
		{
			title: "操作",
			width: 320,
			render: (_, record) => {
				const busy = actionTaskId === record.id;
				return (
					<Space wrap>
						<Button
							size="small"
							icon={record.enabled ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
							disabled={!canManage}
							loading={busy}
							onClick={() => handleToggleTask(record)}
						>
							{record.enabled ? "暂停" : "启用"}
						</Button>
						<Button
							size="small"
							icon={<PlayCircleOutlined />}
							disabled={!canManage}
							loading={busy}
							onClick={() => handleTriggerTask(record)}
						>
							立即执行
						</Button>
						<Button size="small" icon={<EditOutlined />} disabled={!canManage} onClick={() => openTaskModal(record)}>
							编辑
						</Button>
						<Popconfirm
							title="确认删除该调度计划？"
							okText="删除"
							cancelText="取消"
							disabled={!canManage}
							onConfirm={() => handleDeleteTask(record)}
						>
							<Button size="small" danger icon={<DeleteOutlined />} disabled={!canManage || busy}>
								删除
							</Button>
						</Popconfirm>
					</Space>
				);
			},
		},
	];

	/* --- run columns --- */

	const runColumns: ColumnsType<QualityRunRow> = [
		{
			title: "运行ID",
			dataIndex: "id",
			width: 180,
			ellipsis: true,
			render: (v) => v || "-",
		},
		{
			title: "状态",
			dataIndex: "status",
			width: 100,
			render: (v) => {
				const s = String(v || "").toUpperCase();
				return <Tag color={STATUS_COLOR[s] || "default"}>{s || "-"}</Tag>;
			},
		},
		{
			title: "触发方式",
			dataIndex: "triggerType",
			width: 110,
			render: (v) => TRIGGER_LABEL[String(v || "")] || v || "-",
		},
		{
			title: "规则",
			dataIndex: "ruleId",
			render: (v) => ruleMap.get(String(v || "")) || v || "-",
		},
		{
			title: "数据集",
			dataIndex: "datasetId",
			render: (v) => datasetMap.get(String(v || "")) || v || "-",
		},
		{
			title: "开始时间",
			dataIndex: "startedAt",
			sorter: (a, b) => {
				const ta = a.startedAt ? new Date(a.startedAt as any).getTime() : 0;
				const tb = b.startedAt ? new Date(b.startedAt as any).getTime() : 0;
				return ta - tb;
			},
			width: 180,
			render: formatTime,
		},
		{
			title: "耗时(ms)",
			dataIndex: "durationMs",
			width: 110,
			render: (v) => (v != null ? Number(v).toLocaleString() : "-"),
		},
		{
			title: "操作",
			width: 110,
			render: (_, record) => (
				<Button size="small" icon={<EyeOutlined />} onClick={() => openDetail(record)}>
					查看详情
				</Button>
			),
		},
	];

	/* --- compute detail metrics --- */

	const detailTotal = detailRun?.totalRows ?? 0;
	const detailPassed = detailRun?.passedRows ?? 0;
	const detailFailed = detailRun?.failedRows ?? 0;
	const passRate = detailTotal > 0 ? Math.round((detailPassed / detailTotal) * 10000) / 100 : 0;

	const metricsData = useMemo(() => {
		if (detailRun?.metrics && detailRun.metrics.length > 0) return detailRun.metrics;
		if (detailRun?.metricsJson) {
			try {
				const parsed = JSON.parse(detailRun.metricsJson);
				return Array.isArray(parsed) ? parsed : [];
			} catch {
				return [];
			}
		}
		return [];
	}, [detailRun]);

	const failingSample = detailRun?.failingRowsSample || [];
	const sampleColumns = useMemo(() => {
		if (failingSample.length === 0) return [];
		const keys = Object.keys(failingSample[0]);
		return keys.map((k) => ({
			title: k,
			dataIndex: k,
			ellipsis: true,
			render: (v: any) => (v != null ? String(v) : "-"),
		}));
	}, [failingSample]);

	/* --- render --- */

	return (
		<div className="space-y-6">
			{/* Section 1: Schedules */}
			<Card
				title="调度计划"
				extra={
					<Space>
						<Button icon={<ReloadOutlined />} onClick={() => void loadTasks()}>
							刷新
						</Button>
						<Button type="primary" icon={<PlusOutlined />} disabled={!canManage} onClick={() => openTaskModal()}>
							新增计划
						</Button>
					</Space>
				}
			>
				<CompactTable
					rowKey={(r) => r.id || `${r.datasetId}-${r.ruleId || "all"}`}
					columns={scheduleColumns}
					dataSource={tasks}
					loading={tasksLoading}
					pagination={false}
					size="small"
					locale={{ emptyText: "暂无调度计划" }}
				/>
			</Card>

			<Modal
				open={taskModalOpen}
				title={editingTask ? "编辑调度计划" : "新增调度计划"}
				onCancel={() => setTaskModalOpen(false)}
				onOk={saveTask}
				okText="保存"
				destroyOnClose
				confirmLoading={savingTask}
				width={720}
			>
				<Form form={taskForm} layout="vertical" initialValues={{ intervalMinutes: 60, enabled: true, ruleId: ALL_RULES_VALUE }}>
					<Form.Item label="计划名称" name="name" rules={[{ required: true, message: "请输入计划名称" }]}>
						<Input placeholder="例如：客户表每日巡检" />
					</Form.Item>
					<Form.Item label="数据集" name="datasetId" rules={[{ required: true, message: "请选择数据集" }]}>
						<Select options={datasetOptions} showSearch optionFilterProp="label" disabled={!!datasetLoadMessage} />
					</Form.Item>
					{datasetLoadMessage && (
						<Alert
							className="mb-4"
							type="warning"
							showIcon
							message="质量巡检仅允许选择默认数据湖下的数据集"
							description={datasetLoadMessage}
						/>
					)}
					<Form.Item label="规则范围" name="ruleId">
						<Select options={taskRuleOptions} showSearch optionFilterProp="label" />
					</Form.Item>
					<Form.Item label="执行间隔(分钟)" name="intervalMinutes" rules={[{ required: true, message: "请输入执行间隔" }]}>
						<InputNumber min={1} max={1440} style={{ width: "100%" }} />
					</Form.Item>
					<Form.Item label="归属部门(可选)" name="ownerDept">
						<Input placeholder="默认按当前上下文自动填充" />
					</Form.Item>
					<Form.Item label="启用状态" name="enabled" valuePropName="checked">
						<Switch />
					</Form.Item>
				</Form>
			</Modal>

			{/* Section 2: Execution History */}
			<Card
				title="执行记录"
				extra={
					<Button icon={<ReloadOutlined />} onClick={() => void loadRuns()}>
						刷新
					</Button>
				}
			>
				{/* Filters */}
				<div className="mb-3 flex flex-wrap items-center gap-2">
					<Select
						style={{ width: 140 }}
						allowClear
						placeholder="触发方式"
						options={TRIGGER_OPTIONS}
						value={runFilters.triggerType}
						onChange={(v) => {
							setRunFilters((prev) => ({ ...prev, triggerType: v }));
							setRunPage(1);
						}}
					/>
					<Select
						style={{ width: 130 }}
						allowClear
						placeholder="状态"
						options={STATUS_OPTIONS}
						value={runFilters.status}
						onChange={(v) => {
							setRunFilters((prev) => ({ ...prev, status: v }));
							setRunPage(1);
						}}
					/>
					<Select
						style={{ width: 200 }}
						allowClear
						showSearch
						optionFilterProp="label"
						placeholder="规则"
						options={ruleOptions}
						value={runFilters.ruleId}
						onChange={(v) => {
							setRunFilters((prev) => ({ ...prev, ruleId: v }));
							setRunPage(1);
						}}
					/>
					<Select
						style={{ width: 200 }}
						allowClear
						showSearch
						optionFilterProp="label"
						placeholder="数据集"
						options={datasetOptions}
						value={runFilters.datasetId}
						onChange={(v) => {
							setRunFilters((prev) => ({ ...prev, datasetId: v }));
							setRunPage(1);
						}}
					/>
					<RangePicker
						value={runFilters.dateRange}
						onChange={(dates) => {
							setRunFilters((prev) => ({ ...prev, dateRange: dates as [any, any] | null }));
							setRunPage(1);
						}}
					/>
					<Button
						onClick={() => {
							setRunFilters({});
							setRunPage(1);
						}}
					>
						重置
					</Button>
				</div>

				<CompactTable
					rowKey={(r) => r.id || Math.random().toString(36)}
					columns={runColumns}
					dataSource={runs}
					loading={runsLoading}
					scroll={{ x: 1100 }}
					pagination={{
						current: runPage,
						pageSize: runPageSize,
						total: runTotal,
						showSizeChanger: true,
						onChange: (page, size) => {
							setRunPage(page);
							setRunPageSize(size);
						},
					}}
				/>
			</Card>

			{/* Detail Drawer */}
			<Drawer
				title="执行详情"
				open={drawerOpen}
				onClose={() => {
					setDrawerOpen(false);
					setDetailRun(null);
				}}
				width={640}
				loading={detailLoading}
			>
				{detailRun && (
					<div className="space-y-5">
						{/* Basic info */}
						<Descriptions column={1} bordered size="small">
							<Descriptions.Item label="运行ID">{detailRun.id || "-"}</Descriptions.Item>
							<Descriptions.Item label="规则">
								{ruleMap.get(String(detailRun.ruleId || "")) || detailRun.ruleId || "-"}
							</Descriptions.Item>
							<Descriptions.Item label="数据集">
								{datasetMap.get(String(detailRun.datasetId || "")) || detailRun.datasetId || "-"}
							</Descriptions.Item>
							<Descriptions.Item label="触发方式">
								{TRIGGER_LABEL[String(detailRun.triggerType || "")] || detailRun.triggerType || "-"}
							</Descriptions.Item>
							<Descriptions.Item label="状态">
								<Tag color={STATUS_COLOR[String(detailRun.status || "").toUpperCase()] || "default"}>
									{String(detailRun.status || "-").toUpperCase()}
								</Tag>
							</Descriptions.Item>
							<Descriptions.Item label="开始时间">{formatTime(detailRun.startedAt)}</Descriptions.Item>
							<Descriptions.Item label="结束时间">{formatTime(detailRun.finishedAt)}</Descriptions.Item>
							<Descriptions.Item label="耗时">
								{detailRun.durationMs != null ? `${Number(detailRun.durationMs).toLocaleString()} ms` : "-"}
							</Descriptions.Item>
							{detailRun.message && (
								<Descriptions.Item label="消息">{detailRun.message}</Descriptions.Item>
							)}
						</Descriptions>

						{/* Check result */}
						{detailTotal > 0 && (
							<Card size="small" title="检查结果">
								<div className="space-y-3">
									<div className="flex items-center gap-4 text-sm">
										<span>总行数: <strong>{detailTotal.toLocaleString()}</strong></span>
										<span className="text-green-600">通过: <strong>{detailPassed.toLocaleString()}</strong></span>
										<span className="text-red-500">失败: <strong>{detailFailed.toLocaleString()}</strong></span>
									</div>
									<div className="flex items-center gap-3">
										<span className="shrink-0 text-sm">通过率</span>
										<Progress
											percent={passRate}
											status={passRate >= 100 ? "success" : passRate >= 80 ? "normal" : "exception"}
											style={{ flex: 1 }}
										/>
									</div>
								</div>
							</Card>
						)}

						{/* Metrics (if available) */}
						{metricsData.length > 0 && (
							<Card size="small" title="指标详情">
								<pre className="rounded bg-gray-50 p-3 text-xs" style={{ whiteSpace: "pre-wrap", maxHeight: 200, overflow: "auto" }}>
									{JSON.stringify(metricsData, null, 2)}
								</pre>
							</Card>
						)}

						{/* Failing rows sample */}
						{failingSample.length > 0 && (
							<Card size="small" title={`失败行预览 (前 ${failingSample.length} 条)`}>
								<CompactTable
									rowKey={(_, i) => String(i)}
									columns={sampleColumns}
									dataSource={failingSample}
									pagination={false}
									scroll={{ x: true }}
									size="small"
								/>
							</Card>
						)}

						{/* Executed SQL */}
						{detailRun.executedSql && (
							<Card size="small" title="执行 SQL">
								<pre className="rounded bg-gray-50 p-3 text-xs" style={{ whiteSpace: "pre-wrap", maxHeight: 300, overflow: "auto" }}>
									{detailRun.executedSql}
								</pre>
							</Card>
						)}

						{/* Go to repair */}
						{String(detailRun.status || "").toUpperCase() === "FAILED" && (
							<div className="flex justify-end">
								<Button type="primary" onClick={() => goToRepair(detailRun.id)}>
									去修复
								</Button>
							</div>
						)}
					</div>
				)}
			</Drawer>
		</div>
	);
}
