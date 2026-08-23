import { Alert, Button, Card, Descriptions, Form, Input, InputNumber, Select, Space, Switch } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import {
	cancelQualityWorkflow,
	createQualityTask,
	deleteQualityTask,
	getQualityWorkflow,
	listQualityRules,
	listQualityTasks,
	listQualityWorkflows,
	retryQualityWorkflow,
	toggleQualityTask,
	triggerQualityTask,
	updateQualityTask,
} from "@/api/platformApi";
import { actionColumn, CompactTable } from "@/components/table";
import { formatTime } from "@/utils/textUtils";
import {
	ManagePermissionHint,
	QualityEmpty,
	QualityPageHeading,
	QualityStatus,
	UnavailableCapability,
} from "./QualityShared";
import {
	hasRetryCapacity,
	isActiveWorkflow,
	isRetryableWorkflow,
	QualityWorkflowEvidenceDrawer,
	workflowTriggerLabel,
} from "./QualityWorkflowEvidenceDrawer";
import { qualityPath } from "./qualityRoutes";
import {
	displayName,
	isExecutableQualityRule,
	type QualityRule,
	type QualityTask,
	type QualityWorkflowRun,
	toList,
} from "./qualityTypes";
import { useDefaultLakeDatasets } from "./useDefaultLakeDatasets";
import { useQualityMaintainerAccess, useQualityTaskDeleteAccess } from "./useQualityAccess";

const qualityWorkflowRequestKey = (scope: string) =>
	`quality-workflow:${scope}:${Date.now()}:${Math.random().toString(36).slice(2, 10)}`;

export const nextQualityTaskRunAt = (task: Pick<QualityTask, "enabled" | "lastTriggeredAt" | "intervalMinutes">) => {
	if (!task.enabled || !task.lastTriggeredAt || !task.intervalMinutes) return undefined;
	const lastTriggeredAt = Date.parse(task.lastTriggeredAt);
	if (!Number.isFinite(lastTriggeredAt)) return undefined;
	return new Date(lastTriggeredAt + task.intervalMinutes * 60_000).toISOString();
};

export function MonitorListPage() {
	const navigate = useNavigate();
	const canManage = useQualityMaintainerAccess();
	const canDeleteTasks = useQualityTaskDeleteAccess();
	const { datasets } = useDefaultLakeDatasets();
	const [tasks, setTasks] = useState<QualityTask[]>([]);
	const [rules, setRules] = useState<QualityRule[]>([]);
	const [workflows, setWorkflows] = useState<QualityWorkflowRun[]>([]);
	const [loading, setLoading] = useState(true);
	const [actingId, setActingId] = useState("");

	const load = useCallback(async (silent = false) => {
		if (!silent) setLoading(true);
		try {
			const [taskResponse, ruleResponse, workflowResponse] = await Promise.all([
				listQualityTasks(),
				listQualityRules(),
				listQualityWorkflows({ limit: 200 }),
			]);
			setTasks(toList<QualityTask>(taskResponse));
			setRules(toList<QualityRule>(ruleResponse));
			setWorkflows(toList<QualityWorkflowRun>(workflowResponse));
		} catch (error) {
			if (!silent) toast.error(error instanceof Error ? error.message : "运行策略加载失败");
		} finally {
			if (!silent) setLoading(false);
		}
	}, []);

	useEffect(() => {
		void load();
	}, [load]);

	const hasActiveWorkflow = workflows.some((workflow) => isActiveWorkflow(workflow.status));
	useEffect(() => {
		if (!hasActiveWorkflow) return undefined;
		const timer = window.setInterval(() => void load(true), 5_000);
		return () => window.clearInterval(timer);
	}, [hasActiveWorkflow, load]);

	const datasetNames = useMemo(() => new Map(datasets.map((item) => [item.id, item.name])), [datasets]);
	const ruleNames = useMemo(() => new Map(rules.map((item) => [item.id, item.name || item.id])), [rules]);
	const latestWorkflowByTask = useMemo(() => {
		const result = new Map<string, QualityWorkflowRun>();
		for (const workflow of workflows) {
			if (workflow.taskId && !result.has(workflow.taskId)) result.set(workflow.taskId, workflow);
		}
		return result;
	}, [workflows]);

	const act = async (task: QualityTask, action: "toggle" | "trigger" | "delete") => {
		setActingId(task.id);
		try {
			if (action === "toggle") await toggleQualityTask(task.id, !task.enabled);
			if (action === "trigger") {
				const result = (await triggerQualityTask(
					task.id,
					qualityWorkflowRequestKey(`task:${task.id}:manual`),
				)) as QualityWorkflowRun;
				const status = String(result.status || "").toUpperCase();
				if (["FAILED", "BLOCKED", "CANCELLED"].includes(status)) {
					toast.error(result.message || "质量验证未能启动");
				} else {
					toast.success(`质量验证已启动，共 ${result.expectedRunCount || 0} 条规则`);
				}
			}
			if (action === "delete") await deleteQualityTask(task.id);
			if (action !== "trigger") toast.success("操作成功");
			await load();
		} catch (error) {
			toast.error(error instanceof Error ? error.message : "操作失败");
		} finally {
			setActingId("");
		}
	};

	const columns: ColumnsType<QualityTask> = [
		{
			title: "策略名称",
			dataIndex: "name",
			minWidth: 180,
			render: (value, row) => (
				<Button type="link" onClick={() => navigate(qualityPath("monitor-detail", { taskId: row.id }))}>
					{displayName(value)}
				</Button>
			),
		},
		{
			title: "数据资产",
			dataIndex: "datasetId",
			minWidth: 160,
			ellipsis: true,
			render: (value) => datasetNames.get(String(value)) || displayName(value),
		},
		{
			title: "关联规则",
			dataIndex: "ruleId",
			minWidth: 160,
			ellipsis: true,
			render: (value) => (value ? ruleNames.get(String(value)) || value : "自动匹配该资产可执行规则"),
		},
		{
			title: "自动验证周期",
			dataIndex: "intervalMinutes",
			width: 120,
			render: (value) => (value ? `每 ${value} 分钟` : "-"),
		},
		{ title: "最近触发", dataIndex: "lastTriggeredAt", width: 180, render: formatTime },
		{
			title: "下次验证",
			key: "nextRunAt",
			width: 180,
			render: (_, row) => (row.enabled ? formatTime(nextQualityTaskRunAt(row)) : "已停用"),
		},
		{
			title: "最近结果",
			key: "latestWorkflow",
			width: 120,
			render: (_, row) => {
				const workflow = latestWorkflowByTask.get(row.id);
				return workflow ? <QualityStatus status={workflow.status} /> : "尚未验证";
			},
		},
		{
			title: "最近说明",
			key: "latestMessage",
			minWidth: 180,
			ellipsis: true,
			render: (_, row) => latestWorkflowByTask.get(row.id)?.message || "-",
		},
		{
			title: "状态",
			dataIndex: "enabled",
			width: 90,
			render: (value, row) => (
				<Switch
					size="small"
					checked={Boolean(value)}
					disabled={!canManage}
					loading={actingId === row.id}
					onChange={() => void act(row, "toggle")}
				/>
			),
		},
		actionColumn<QualityTask>(
			(row) => [
				{
					key: "trigger",
					label: "立即验证",
					disabled: !canManage,
					loading: actingId === row.id,
					onClick: () => void act(row, "trigger"),
				},
				{
					key: "edit",
					label: "编辑",
					disabled: !canManage,
					onClick: () => navigate(`${qualityPath("monitor-detail", { taskId: row.id })}/edit`),
				},
				{
					key: "delete",
					label: "删除",
					danger: true,
					hidden: !canDeleteTasks,
					confirm: "确认删除该运行策略？",
					onClick: () => void act(row, "delete"),
				},
			],
			{ width: 220 },
		),
	];

	return (
		<div className="dq-page">
			<QualityPageHeading
				title="运行策略"
				description="配置周期自动验证，并查看每次验证从启动、规则执行到最终结果的完整过程。"
				actions={[
					<ManagePermissionHint key="permission" canManage={canManage} />,
					<Button key="reload" onClick={() => void load()}>
						刷新
					</Button>,
					<Button key="noise" onClick={() => navigate(qualityPath("noise"))}>
						去噪管理
					</Button>,
					<Button
						key="new"
						type="primary"
						disabled={!canManage}
						onClick={() => navigate(qualityPath("monitor-editor"))}
					>
						新建运行策略
					</Button>,
				]}
			/>
			<UnavailableCapability capability="quality-subscription" title="结果通知暂未开放" />
			<CompactTable rowKey="id" loading={loading} columns={columns} dataSource={tasks} pagination={{ pageSize: 10 }} />
		</div>
	);
}

type TaskForm = {
	name: string;
	datasetId: string;
	ruleId?: string;
	intervalMinutes: number;
	maxRetryAttempts: number;
	retryBackoffSeconds: number;
	enabled: boolean;
	ownerDept?: string;
};

export function MonitorEditorPage() {
	const { taskId } = useParams();
	const navigate = useNavigate();
	const canManage = useQualityMaintainerAccess();
	const { datasets, loading: datasetsLoading, message, lakeName } = useDefaultLakeDatasets();
	const [rules, setRules] = useState<QualityRule[]>([]);
	const [loading, setLoading] = useState(Boolean(taskId));
	const [saving, setSaving] = useState(false);
	const [loadError, setLoadError] = useState("");
	const [form] = Form.useForm<TaskForm>();
	const loadSequence = useRef(0);
	const activeTaskId = useRef(taskId);
	const loadedTaskId = useRef<string>();
	activeTaskId.current = taskId;
	const isLoadedTaskCurrent = (operationTaskId: string) =>
		activeTaskId.current === operationTaskId && loadedTaskId.current === operationTaskId;

	useEffect(() => {
		const requestedTaskId = taskId;
		const sequence = ++loadSequence.current;
		loadedTaskId.current = undefined;
		setRules([]);
		setLoading(true);
		setLoadError("");
		form.resetFields();
		void Promise.all([listQualityRules(), requestedTaskId ? listQualityTasks() : Promise.resolve([])])
			.then(([ruleResponse, taskResponse]) => {
				if (sequence !== loadSequence.current || activeTaskId.current !== requestedTaskId) return;
				const nextRules = toList<QualityRule>(ruleResponse);
				setRules(nextRules);
				const task = toList<QualityTask>(taskResponse).find((item) => String(item.id) === requestedTaskId);
				if (requestedTaskId && !task) throw new Error("未找到需要编辑的运行策略");
				loadedTaskId.current = task ? String(task.id) : undefined;
				const validTaskRuleId =
					task?.datasetId &&
					nextRules.some(
						(rule) => String(rule.id) === String(task.ruleId || "") && isExecutableQualityRule(rule, task.datasetId),
					)
						? task?.ruleId
						: undefined;
				form.setFieldsValue(
					task
						? {
								name: task.name || "",
								datasetId: task.datasetId || "",
								ruleId: validTaskRuleId,
								intervalMinutes: task.intervalMinutes || 60,
								maxRetryAttempts: task.maxRetryAttempts ?? 1,
								retryBackoffSeconds: task.retryBackoffSeconds ?? 0,
								enabled: task.enabled !== false,
								ownerDept: task.ownerDept,
							}
						: { intervalMinutes: 60, maxRetryAttempts: 1, retryBackoffSeconds: 0, enabled: true },
				);
			})
			.catch((error) => {
				if (sequence !== loadSequence.current || activeTaskId.current !== requestedTaskId) return;
				const message = error instanceof Error ? error.message : "运行策略加载失败";
				loadedTaskId.current = undefined;
				setLoadError(message);
				toast.error(message);
			})
			.finally(() => {
				if (sequence === loadSequence.current && activeTaskId.current === requestedTaskId) setLoading(false);
			});
	}, [form, taskId]);

	const selectedDataset = Form.useWatch("datasetId", form);
	const ruleOptions = useMemo(
		() =>
			rules
				.filter((rule) => isExecutableQualityRule(rule, selectedDataset))
				.map((rule) => ({ value: rule.id, label: rule.name || rule.id })),
		[rules, selectedDataset],
	);

	const save = async () => {
		const operationTaskId = taskId;
		if (!canManage || loadError || loading) return;
		if (operationTaskId && !isLoadedTaskCurrent(operationTaskId)) {
			toast.error("运行策略尚未安全加载，请刷新后重试");
			return;
		}
		try {
			const values = await form.validateFields();
			if (activeTaskId.current !== operationTaskId || (operationTaskId && !isLoadedTaskCurrent(operationTaskId))) {
				throw new Error("运行策略已切换，请重新确认后保存");
			}
			setSaving(true);
			const payload = { ...values, ownerDept: values.ownerDept?.trim() || undefined };
			if (operationTaskId) await updateQualityTask(operationTaskId, payload);
			else await createQualityTask(payload);
			if (activeTaskId.current !== operationTaskId) return;
			toast.success(operationTaskId ? "运行策略已更新" : "运行策略已创建");
			navigate(operationTaskId ? qualityPath("monitor-detail", { taskId: operationTaskId }) : qualityPath("monitor"));
		} catch (error) {
			if ((error as { errorFields?: unknown })?.errorFields) return;
			if (activeTaskId.current !== operationTaskId) return;
			toast.error(error instanceof Error ? error.message : "运行策略保存失败");
		} finally {
			setSaving(false);
		}
	};

	if (!loading && loadError) return <QualityEmpty description={loadError} />;

	return (
		<div className="dq-page">
			<QualityPageHeading
				title={taskId ? "编辑运行策略" : "新建运行策略"}
				description="指定规则须已启用、已发布并绑定所选资产；留空时由后端匹配该资产全部可执行规则。"
				actions={<ManagePermissionHint canManage={canManage} />}
			/>
			{message ? <Alert showIcon type="warning" message={message} /> : null}
			<Card loading={loading} title="策略配置">
				<Form form={form} layout="vertical" style={{ maxWidth: 760 }}>
					<Form.Item name="name" label="策略名称" rules={[{ required: true, message: "请输入策略名称" }]}>
						<Input />
					</Form.Item>
					<Form.Item
						name="datasetId"
						label={`数据资产（${lakeName}）`}
						rules={[{ required: true, message: "请选择数据资产" }]}
					>
						<Select
							showSearch
							optionFilterProp="label"
							loading={datasetsLoading}
							disabled={Boolean(message)}
							options={datasets.map((item) => ({ value: item.id, label: item.name }))}
							onChange={() => form.setFieldValue("ruleId", undefined)}
						/>
					</Form.Item>
					<Form.Item
						name="ruleId"
						label="关联规则"
						extra="仅列出已启用、已发布且绑定该资产的规则；留空时自动匹配全部符合条件的规则"
					>
						<Select allowClear disabled={!selectedDataset} options={ruleOptions} />
					</Form.Item>
					<Space align="start" size={16} wrap>
						<Form.Item name="intervalMinutes" label="自动验证周期（分钟）" rules={[{ required: true }]}>
							<InputNumber min={5} max={43200} style={{ width: 220 }} />
						</Form.Item>
						<Form.Item name="maxRetryAttempts" label="最多重试次数" extra="仅对未通过或被阻断的验证生效">
							<InputNumber min={0} max={5} style={{ width: 180 }} />
						</Form.Item>
						<Form.Item name="retryBackoffSeconds" label="重试等待（秒）">
							<InputNumber min={0} max={86400} style={{ width: 180 }} />
						</Form.Item>
						<Form.Item name="ownerDept" label="责任部门">
							<Input style={{ width: 260 }} />
						</Form.Item>
						<Form.Item name="enabled" label="启用自动验证" valuePropName="checked">
							<Switch />
						</Form.Item>
					</Space>
					<Space>
						<Button
							onClick={() => navigate(taskId ? qualityPath("monitor-detail", { taskId }) : qualityPath("monitor"))}
						>
							取消
						</Button>
						<Button
							type="primary"
							loading={saving}
							disabled={!canManage || Boolean(message) || loading || Boolean(loadError)}
							onClick={() => void save()}
						>
							保存运行策略
						</Button>
					</Space>
				</Form>
			</Card>
		</div>
	);
}

export function MonitorDetailPage() {
	const { taskId = "" } = useParams();
	const navigate = useNavigate();
	const canManage = useQualityMaintainerAccess();
	const { datasets } = useDefaultLakeDatasets();
	const [task, setTask] = useState<QualityTask>();
	const [rules, setRules] = useState<QualityRule[]>([]);
	const [workflows, setWorkflows] = useState<QualityWorkflowRun[]>([]);
	const [selectedWorkflow, setSelectedWorkflow] = useState<QualityWorkflowRun>();
	const [workflowLoading, setWorkflowLoading] = useState(false);
	const [actingId, setActingId] = useState("");
	const [loading, setLoading] = useState(true);
	const loadSequence = useRef(0);
	const activeTaskId = useRef(taskId);
	const loadedTaskId = useRef<string>();
	const settledTaskRequestId = useRef<string>();
	activeTaskId.current = taskId;

	const load = useCallback(
		async (silent = false) => {
			const requestedTaskId = taskId;
			const sequence = ++loadSequence.current;
			if (!silent) {
				loadedTaskId.current = undefined;
				settledTaskRequestId.current = undefined;
				setTask(undefined);
				setRules([]);
				setWorkflows([]);
				setLoading(true);
			}
			try {
				const [taskResponse, ruleResponse] = await Promise.all([listQualityTasks(), listQualityRules()]);
				if (sequence !== loadSequence.current || activeTaskId.current !== requestedTaskId) return;
				const nextTask = toList<QualityTask>(taskResponse).find((item) => String(item.id) === requestedTaskId);
				const nextWorkflows = nextTask
					? toList<QualityWorkflowRun>(await listQualityWorkflows({ taskId: requestedTaskId, limit: 50 }))
					: [];
				if (sequence !== loadSequence.current || activeTaskId.current !== requestedTaskId) return;
				setTask(nextTask);
				setRules(toList<QualityRule>(ruleResponse));
				setWorkflows(nextWorkflows);
				loadedTaskId.current = nextTask ? String(nextTask.id) : undefined;
				settledTaskRequestId.current = requestedTaskId;
			} catch (error) {
				if (sequence !== loadSequence.current || activeTaskId.current !== requestedTaskId) return;
				if (!silent) {
					settledTaskRequestId.current = requestedTaskId;
					toast.error(error instanceof Error ? error.message : "运行策略详情加载失败");
				}
			} finally {
				if (!silent && sequence === loadSequence.current && activeTaskId.current === requestedTaskId) setLoading(false);
			}
		},
		[taskId],
	);

	const openWorkflow = useCallback(
		async (workflowId: string, silent = false) => {
			if (!silent) setWorkflowLoading(true);
			try {
				const detail = (await getQualityWorkflow(workflowId)) as QualityWorkflowRun;
				if (activeTaskId.current !== taskId || detail.taskId !== taskId) return;
				setSelectedWorkflow(detail);
			} catch (error) {
				if (!silent) toast.error(error instanceof Error ? error.message : "验证详情加载失败");
			} finally {
				if (!silent) setWorkflowLoading(false);
			}
		},
		[taskId],
	);

	const triggerNow = async () => {
		if (!canManage || !loadedTaskId.current) return;
		setActingId(taskId);
		try {
			const result = (await triggerQualityTask(
				taskId,
				qualityWorkflowRequestKey(`task:${taskId}:manual`),
			)) as QualityWorkflowRun;
			if (["FAILED", "BLOCKED", "CANCELLED"].includes(String(result.status || "").toUpperCase())) {
				toast.error(result.message || "质量验证未能启动");
			} else {
				toast.success(`质量验证已启动，共 ${result.expectedRunCount || 0} 条规则`);
			}
			await load();
			setSelectedWorkflow(result);
		} catch (error) {
			toast.error(error instanceof Error ? error.message : "质量验证启动失败");
		} finally {
			setActingId("");
		}
	};

	const retryWorkflow = async (workflow: QualityWorkflowRun) => {
		if (!canManage || !isRetryableWorkflow(workflow.status) || !hasRetryCapacity(workflow)) return;
		setActingId(workflow.id);
		try {
			const retried = (await retryQualityWorkflow(
				workflow.id,
				`quality-workflow:retry:${workflow.id}`,
			)) as QualityWorkflowRun;
			toast.success("重新验证已启动");
			await load();
			setSelectedWorkflow(retried);
		} catch (error) {
			toast.error(error instanceof Error ? error.message : "重新验证启动失败");
		} finally {
			setActingId("");
		}
	};

	const cancelWorkflow = async (workflow: QualityWorkflowRun) => {
		if (!canManage || !isActiveWorkflow(workflow.status)) return;
		setActingId(workflow.id);
		try {
			const cancelled = (await cancelQualityWorkflow(workflow.id)) as QualityWorkflowRun;
			toast.success("本次质量验证已取消，已生成的规则证据继续保留");
			await load(true);
			setSelectedWorkflow(cancelled);
		} catch (error) {
			toast.error(error instanceof Error ? error.message : "取消质量验证失败");
		} finally {
			setActingId("");
		}
	};

	useEffect(() => {
		void load();
	}, [load]);
	const hasActiveWorkflow = workflows.some((workflow) => isActiveWorkflow(workflow.status));
	useEffect(() => {
		if (!hasActiveWorkflow && !isActiveWorkflow(selectedWorkflow?.status)) return undefined;
		const timer = window.setInterval(() => {
			void load(true);
			if (selectedWorkflow?.id) void openWorkflow(selectedWorkflow.id, true);
		}, 5_000);
		return () => window.clearInterval(timer);
	}, [hasActiveWorkflow, load, openWorkflow, selectedWorkflow?.id, selectedWorkflow?.status]);
	const loadedTaskIsCurrent = loadedTaskId.current === taskId && String(task?.id || "") === taskId;
	const visibleTask = loadedTaskIsCurrent ? task : undefined;
	const visibleWorkflows = loadedTaskIsCurrent ? workflows : [];
	const detailLoading = loading || settledTaskRequestId.current !== taskId;
	const ruleNames = useMemo(() => new Map(rules.map((item) => [item.id, item.name || item.id])), [rules]);
	if (!detailLoading && !visibleTask) return <QualityEmpty description="未找到该运行策略。" />;
	const datasetName = datasets.find((item) => item.id === visibleTask?.datasetId)?.name || visibleTask?.datasetId;

	return (
		<div className="dq-page">
			<QualityPageHeading
				title={visibleTask?.name || "运行策略详情"}
				description="查看周期和人工验证的完整执行记录；每次重新验证都会保留独立证据。"
				actions={[
					<Button key="back" onClick={() => navigate(qualityPath("monitor"))}>
						返回运行策略
					</Button>,
					<Button
						key="edit"
						type="primary"
						disabled={!canManage || !loadedTaskIsCurrent || loading}
						onClick={() => navigate(`${qualityPath("monitor-detail", { taskId })}/edit`)}
					>
						编辑策略
					</Button>,
					<Button
						key="validate"
						type="primary"
						loading={actingId === taskId}
						disabled={!canManage || !loadedTaskIsCurrent || loading}
						onClick={() => void triggerNow()}
					>
						立即验证
					</Button>,
				]}
			/>
			<Card loading={detailLoading} title="策略信息">
				<Descriptions column={{ xs: 1, md: 2, xl: 3 }} size="small">
					<Descriptions.Item label="数据资产">{displayName(datasetName)}</Descriptions.Item>
					<Descriptions.Item label="关联规则">{displayName(visibleTask?.ruleId, "自动匹配全部规则")}</Descriptions.Item>
					<Descriptions.Item label="自动验证周期">
						{visibleTask?.intervalMinutes ? `每 ${visibleTask.intervalMinutes} 分钟` : "-"}
					</Descriptions.Item>
					<Descriptions.Item label="重试策略">
						最多 {visibleTask?.maxRetryAttempts ?? 1} 次，等待 {visibleTask?.retryBackoffSeconds ?? 0} 秒
					</Descriptions.Item>
					<Descriptions.Item label="责任部门">{displayName(visibleTask?.ownerDept)}</Descriptions.Item>
					<Descriptions.Item label="状态">
						<QualityStatus status={Boolean(visibleTask?.enabled)} />
					</Descriptions.Item>
					<Descriptions.Item label="最近触发">{formatTime(visibleTask?.lastTriggeredAt)}</Descriptions.Item>
				</Descriptions>
			</Card>
			<Card title="验证记录">
				<CompactTable
					rowKey="id"
					dataSource={visibleWorkflows}
					pagination={false}
					columns={[
						{
							title: "触发方式",
							dataIndex: "triggerType",
							width: 140,
							render: workflowTriggerLabel,
						},
						{ title: "状态", dataIndex: "status", width: 110, render: (value) => <QualityStatus status={value} /> },
						{
							title: "规则进度",
							width: 120,
							render: (_, row) => `${row.completedRunCount || 0}/${row.expectedRunCount || 0}`,
						},
						{ title: "开始时间", dataIndex: "startedAt", width: 180, render: formatTime },
						{ title: "完成时间", dataIndex: "finishedAt", width: 180, render: formatTime },
						actionColumn<QualityWorkflowRun>(
							(row) => [
								{ key: "view", label: "查看", onClick: () => void openWorkflow(row.id) },
								{
									key: "cancel",
									label: "取消",
									danger: true,
									hidden: !isActiveWorkflow(row.status),
									disabled: !canManage,
									confirm: "确认取消本次质量验证？已生成的规则记录会保留。",
									loading: actingId === row.id,
									onClick: () => void cancelWorkflow(row),
								},
								{
									key: "retry",
									label: "重新验证",
									disabled: !canManage || !isRetryableWorkflow(row.status) || !hasRetryCapacity(row),
									loading: actingId === row.id,
									onClick: () => void retryWorkflow(row),
								},
							],
							{ width: 220, fixed: false },
						),
					]}
				/>
			</Card>
			<QualityWorkflowEvidenceDrawer
				workflow={selectedWorkflow}
				loading={workflowLoading}
				canManage={canManage}
				retrying={Boolean(selectedWorkflow && actingId === selectedWorkflow.id)}
				cancelling={Boolean(selectedWorkflow && actingId === selectedWorkflow.id)}
				ruleNames={ruleNames}
				onClose={() => setSelectedWorkflow(undefined)}
				onRetry={(workflow) => void retryWorkflow(workflow)}
				onCancel={(workflow) => void cancelWorkflow(workflow)}
			/>
		</div>
	);
}
