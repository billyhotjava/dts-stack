import { Alert, Button, Card, Descriptions, Form, Input, InputNumber, Select, Space, Switch } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import {
	createQualityTask,
	deleteQualityTask,
	listQualityRules,
	listQualityRuns,
	listQualityTasks,
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
import { qualityPath } from "./qualityRoutes";
import {
	displayName,
	isExecutableQualityRule,
	type QualityRule,
	type QualityRun,
	type QualityTask,
	toList,
} from "./qualityTypes";
import { useDefaultLakeDatasets } from "./useDefaultLakeDatasets";
import { useQualityMaintainerAccess, useQualityTaskDeleteAccess } from "./useQualityAccess";

export function MonitorListPage() {
	const navigate = useNavigate();
	const canManage = useQualityMaintainerAccess();
	const canDeleteTasks = useQualityTaskDeleteAccess();
	const { datasets } = useDefaultLakeDatasets();
	const [tasks, setTasks] = useState<QualityTask[]>([]);
	const [rules, setRules] = useState<QualityRule[]>([]);
	const [loading, setLoading] = useState(true);
	const [actingId, setActingId] = useState("");

	const load = useCallback(async () => {
		setLoading(true);
		try {
			const [taskResponse, ruleResponse] = await Promise.all([listQualityTasks(), listQualityRules()]);
			setTasks(toList<QualityTask>(taskResponse));
			setRules(toList<QualityRule>(ruleResponse));
		} catch (error) {
			toast.error(error instanceof Error ? error.message : "质量监控加载失败");
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		void load();
	}, [load]);

	const datasetNames = useMemo(() => new Map(datasets.map((item) => [item.id, item.name])), [datasets]);
	const ruleNames = useMemo(() => new Map(rules.map((item) => [item.id, item.name || item.id])), [rules]);

	const act = async (task: QualityTask, action: "toggle" | "trigger" | "delete") => {
		setActingId(task.id);
		try {
			if (action === "toggle") await toggleQualityTask(task.id, !task.enabled);
			if (action === "trigger") {
				const result = toList<Record<string, unknown>>(await triggerQualityTask(task.id));
				const failed = result.filter((item) => item.error);
				if (failed.length && failed.length === result.length) toast.error(`巡检触发失败（${failed.length} 条规则）`);
				else if (failed.length) toast.warning(`巡检部分失败（${failed.length}/${result.length}）`);
				else toast.success("巡检已触发");
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
			title: "监控名称",
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
			title: "巡检周期",
			dataIndex: "intervalMinutes",
			width: 120,
			render: (value) => (value ? `每 ${value} 分钟` : "-"),
		},
		{ title: "最近触发", dataIndex: "lastTriggeredAt", width: 180, render: formatTime },
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
					label: "执行",
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
					confirm: "确认删除该监控？",
					onClick: () => void act(row, "delete"),
				},
			],
			{ width: 190 },
		),
	];

	return (
		<div className="dq-page">
			<QualityPageHeading
				title="质量监控"
				description="配置周期巡检、启停监控并按需立即执行。订阅通知和去噪策略不在现有合同内。"
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
						新建监控
					</Button>,
				]}
			/>
			<UnavailableCapability capability="quality-subscription" title="监控订阅暂未开放" />
			<CompactTable rowKey="id" loading={loading} columns={columns} dataSource={tasks} pagination={{ pageSize: 10 }} />
		</div>
	);
}

type TaskForm = {
	name: string;
	datasetId: string;
	ruleId?: string;
	intervalMinutes: number;
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
				if (requestedTaskId && !task) throw new Error("未找到需要编辑的质量监控");
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
								enabled: task.enabled !== false,
								ownerDept: task.ownerDept,
							}
						: { intervalMinutes: 60, enabled: true },
				);
			})
			.catch((error) => {
				if (sequence !== loadSequence.current || activeTaskId.current !== requestedTaskId) return;
				const message = error instanceof Error ? error.message : "监控配置加载失败";
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
			toast.error("质量监控尚未安全加载，请刷新后重试");
			return;
		}
		try {
			const values = await form.validateFields();
			if (activeTaskId.current !== operationTaskId || (operationTaskId && !isLoadedTaskCurrent(operationTaskId))) {
				throw new Error("质量监控已切换，请重新确认后保存");
			}
			setSaving(true);
			const payload = { ...values, ownerDept: values.ownerDept?.trim() || undefined };
			if (operationTaskId) await updateQualityTask(operationTaskId, payload);
			else await createQualityTask(payload);
			if (activeTaskId.current !== operationTaskId) return;
			toast.success(operationTaskId ? "质量监控已更新" : "质量监控已创建");
			navigate(operationTaskId ? qualityPath("monitor-detail", { taskId: operationTaskId }) : qualityPath("monitor"));
		} catch (error) {
			if ((error as { errorFields?: unknown })?.errorFields) return;
			if (activeTaskId.current !== operationTaskId) return;
			toast.error(error instanceof Error ? error.message : "质量监控保存失败");
		} finally {
			setSaving(false);
		}
	};

	if (!loading && loadError) return <QualityEmpty description={loadError} />;

	return (
		<div className="dq-page">
			<QualityPageHeading
				title={taskId ? "编辑质量监控" : "新建质量监控"}
				description="指定规则须已启用、已发布并绑定所选资产；留空时由后端匹配该资产全部可执行规则。"
				actions={<ManagePermissionHint canManage={canManage} />}
			/>
			{message ? <Alert showIcon type="warning" message={message} /> : null}
			<Card loading={loading} title="监控配置">
				<Form form={form} layout="vertical" style={{ maxWidth: 760 }}>
					<Form.Item name="name" label="监控名称" rules={[{ required: true, message: "请输入监控名称" }]}>
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
						<Form.Item name="intervalMinutes" label="巡检周期（分钟）" rules={[{ required: true }]}>
							<InputNumber min={5} max={43200} style={{ width: 220 }} />
						</Form.Item>
						<Form.Item name="ownerDept" label="责任部门">
							<Input style={{ width: 260 }} />
						</Form.Item>
						<Form.Item name="enabled" label="启用监控" valuePropName="checked">
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
							保存监控
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
	const [runs, setRuns] = useState<QualityRun[]>([]);
	const [loading, setLoading] = useState(true);
	const loadSequence = useRef(0);
	const activeTaskId = useRef(taskId);
	const loadedTaskId = useRef<string>();
	const settledTaskRequestId = useRef<string>();
	activeTaskId.current = taskId;

	const load = useCallback(async () => {
		const requestedTaskId = taskId;
		const sequence = ++loadSequence.current;
		loadedTaskId.current = undefined;
		settledTaskRequestId.current = undefined;
		setTask(undefined);
		setRuns([]);
		setLoading(true);
		try {
			const taskResponse = await listQualityTasks();
			if (sequence !== loadSequence.current || activeTaskId.current !== requestedTaskId) return;
			const nextTask = toList<QualityTask>(taskResponse).find((item) => String(item.id) === requestedTaskId);
			const nextRuns = nextTask?.datasetId
				? toList<QualityRun>(
						await listQualityRuns({
							datasetId: nextTask.datasetId,
							...(nextTask.ruleId ? { ruleId: nextTask.ruleId } : {}),
							triggerType: "SCHEDULED",
							limit: 20,
						}),
					)
				: [];
			if (sequence !== loadSequence.current || activeTaskId.current !== requestedTaskId) return;
			setTask(nextTask);
			setRuns(nextRuns);
			loadedTaskId.current = nextTask ? String(nextTask.id) : undefined;
			settledTaskRequestId.current = requestedTaskId;
		} catch (error) {
			if (sequence !== loadSequence.current || activeTaskId.current !== requestedTaskId) return;
			settledTaskRequestId.current = requestedTaskId;
			toast.error(error instanceof Error ? error.message : "监控详情加载失败");
		} finally {
			if (sequence === loadSequence.current && activeTaskId.current === requestedTaskId) setLoading(false);
		}
	}, [taskId]);

	useEffect(() => {
		void load();
	}, [load]);
	const loadedTaskIsCurrent = loadedTaskId.current === taskId && String(task?.id || "") === taskId;
	const visibleTask = loadedTaskIsCurrent ? task : undefined;
	const visibleRuns = loadedTaskIsCurrent ? runs : [];
	const detailLoading = loading || settledTaskRequestId.current !== taskId;
	if (!detailLoading && !visibleTask) return <QualityEmpty description="未找到该质量监控。" />;
	const datasetName = datasets.find((item) => item.id === visibleTask?.datasetId)?.name || visibleTask?.datasetId;

	return (
		<div className="dq-page">
			<QualityPageHeading
				title={visibleTask?.name || "监控详情"}
				description={
					visibleTask?.ruleId
						? "查看监控配置及该资产、该规则的调度运行。"
						: "查看监控配置及该资产全部调度运行；当前后端不能按监控任务标识过滤。"
				}
				actions={[
					<Button key="back" onClick={() => navigate(qualityPath("monitor"))}>
						返回监控列表
					</Button>,
					<Button
						key="edit"
						type="primary"
						disabled={!canManage || !loadedTaskIsCurrent || loading}
						onClick={() => navigate(`${qualityPath("monitor-detail", { taskId })}/edit`)}
					>
						编辑监控
					</Button>,
				]}
			/>
			<Card loading={detailLoading} title="监控信息">
				<Descriptions column={{ xs: 1, md: 2, xl: 3 }} size="small">
					<Descriptions.Item label="数据资产">{displayName(datasetName)}</Descriptions.Item>
					<Descriptions.Item label="关联规则">{displayName(visibleTask?.ruleId, "自动匹配全部规则")}</Descriptions.Item>
					<Descriptions.Item label="巡检周期">
						{visibleTask?.intervalMinutes ? `每 ${visibleTask.intervalMinutes} 分钟` : "-"}
					</Descriptions.Item>
					<Descriptions.Item label="责任部门">{displayName(visibleTask?.ownerDept)}</Descriptions.Item>
					<Descriptions.Item label="状态">
						<QualityStatus status={Boolean(visibleTask?.enabled)} />
					</Descriptions.Item>
					<Descriptions.Item label="最近触发">{formatTime(visibleTask?.lastTriggeredAt)}</Descriptions.Item>
				</Descriptions>
			</Card>
			<Card title={visibleTask?.ruleId ? "该规则调度运行" : "该资产全部调度运行"}>
				<CompactTable
					rowKey="id"
					dataSource={visibleRuns}
					pagination={false}
					columns={[
						{ title: "运行 ID", dataIndex: "id", ellipsis: true },
						{ title: "状态", dataIndex: "status", width: 110, render: (value) => <QualityStatus status={value} /> },
						{ title: "开始时间", dataIndex: "startedAt", width: 180, render: formatTime },
						{
							title: "耗时",
							dataIndex: "durationMs",
							width: 110,
							render: (value) => (value == null ? "-" : `${value} ms`),
						},
						actionColumn<QualityRun>(
							(row) => [
								{ key: "view", label: "查看", onClick: () => navigate(qualityPath("run-detail", { runId: row.id })) },
							],
							{ width: 100, fixed: false },
						),
					]}
				/>
			</Card>
		</div>
	);
}
