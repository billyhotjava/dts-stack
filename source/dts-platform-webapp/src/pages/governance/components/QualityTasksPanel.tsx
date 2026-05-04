import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
	Button,
	Card,
	Form,
	Input,
	InputNumber,
	Modal,
	Popconfirm,
	Select,
	Space,
	Switch,
	Tag,
} from "antd";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { DeleteOutlined, EditOutlined, PlayCircleOutlined, PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import {
	createQualityTask,
	deleteQualityTask,
	listDatasets,
	listQualityRules,
	listQualityRuns,
	listQualityTasks,
	toggleQualityTask,
	triggerQualityTask,
	updateQualityTask,
} from "@/api/platformApi";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import { useActiveDept } from "@/store/contextStore";

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

type RunSummary = {
	status?: string;
	message?: string;
	finishedAt?: string;
};

type TaskForm = {
	name: string;
	datasetId: string;
	ruleId?: string;
	intervalMinutes: number;
	enabled: boolean;
	ownerDept?: string;
};

const normalizeStatus = (raw: unknown) => String(raw || "").trim().toUpperCase();

const statusColor = (status?: string) => {
	const normalized = normalizeStatus(status);
	if (normalized === "SUCCESS" || normalized === "PASSED" || normalized === "COMPLETED") return "green";
	if (normalized === "FAILED" || normalized === "ERROR") return "red";
	if (normalized === "RUNNING" || normalized === "QUEUED") return "blue";
	return "default";
};

export default function QualityTasksPanel() {
	const [tasks, setTasks] = useState<QualityTask[]>([]);
	const [loading, setLoading] = useState(false);
	const [datasets, setDatasets] = useState<Array<{ id: string; name: string }>>([]);
	const [rules, setRules] = useState<Array<{ id: string; name: string }>>([]);
	const [runSummary, setRunSummary] = useState<Record<string, RunSummary>>({});
	const [modalOpen, setModalOpen] = useState(false);
	const [editing, setEditing] = useState<QualityTask | null>(null);
	const [readOnly, setReadOnly] = useState(false);
	const [saving, setSaving] = useState(false);
	const [actionTaskId, setActionTaskId] = useState<string>("");
	const [form] = Form.useForm<TaskForm>();

	const activeDept = useActiveDept();
	const hasManageAccess = useGovernanceManageAccess();

	const canManage = useMemo(() => {
		return !readOnly && hasManageAccess;
	}, [readOnly, hasManageAccess]);

	const datasetNameMap = useMemo(() => {
		const map = new Map<string, string>();
		for (const item of datasets) {
			map.set(String(item.id), item.name || item.id);
		}
		return map;
	}, [datasets]);

	const ruleNameMap = useMemo(() => {
		const map = new Map<string, string>();
		for (const item of rules) {
			map.set(String(item.id), item.name || item.id);
		}
		return map;
	}, [rules]);

	const datasetOptions = useMemo(() => datasets.map((item) => ({ label: item.name, value: item.id })), [datasets]);

	const ruleOptions = useMemo(
		() => [{ label: "自动匹配数据集全部规则", value: "__ALL_RULES__" }, ...rules.map((item) => ({ label: item.name, value: item.id }))],
		[rules],
	);

	const parseWriteError = (error: any, fallback: string) => {
		const message = String(error?.message || fallback);
		if (message.toLowerCase().includes("access denied") || message.includes("403")) {
			setReadOnly(true);
		}
		return message;
	};

	const loadDatasetsAndRules = async () => {
		try {
			const [datasetResp, ruleResp] = await Promise.all([listDatasets({ page: 0, size: 300 }), listQualityRules()]);
			const datasetList = Array.isArray((datasetResp as any)?.content) ? (datasetResp as any).content : [];
			setDatasets(datasetList.map((item: any) => ({ id: String(item.id), name: item.name || String(item.id) })));
			const qualityRules = Array.isArray(ruleResp) ? (ruleResp as any[]) : [];
			setRules(qualityRules.map((item) => ({ id: String(item.id), name: String(item.name || item.id) })));
		} catch (error: any) {
			toast.error(error?.message || "巡检计划依赖信息加载失败");
		}
	};

	const loadRunSummary = async (inputTasks: QualityTask[]) => {
		const datasetIds = Array.from(new Set(inputTasks.map((item) => String(item.datasetId || "").trim()).filter(Boolean)));
		if (!datasetIds.length) {
			setRunSummary({});
			return;
		}
		const entries = await Promise.all(
			datasetIds.map(async (datasetId) => {
				try {
					const runs: any = await listQualityRuns({ datasetId, limit: 1 });
					const list = Array.isArray(runs) ? runs : [];
					const latest = list[0] || {};
					return [
						datasetId,
						{
							status: latest?.status,
							message: latest?.message,
							finishedAt: latest?.finishedAt || latest?.startedAt || latest?.createdDate,
						} satisfies RunSummary,
					] as const;
				} catch {
					return [datasetId, {} satisfies RunSummary] as const;
				}
			}),
		);
		setRunSummary(Object.fromEntries(entries));
	};

	const loadTasks = async () => {
		setLoading(true);
		try {
			const list = await listQualityTasks();
			const result = Array.isArray(list) ? (list as QualityTask[]) : [];
			setTasks(result);
			await loadRunSummary(result);
		} catch (error: any) {
			toast.error(error?.message || "巡检计划加载失败");
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadDatasetsAndRules();
		void loadTasks();
	}, []);

	const openModal = (task?: QualityTask) => {
		setEditing(task || null);
		form.setFieldsValue({
			name: String(task?.name || ""),
			datasetId: String(task?.datasetId || ""),
			ruleId: task?.ruleId ? String(task.ruleId) : "__ALL_RULES__",
			intervalMinutes: task?.intervalMinutes || 60,
			enabled: task?.enabled !== false,
			ownerDept: String(task?.ownerDept || ""),
		});
		setModalOpen(true);
	};

	const saveTask = async () => {
		try {
			const values = await form.validateFields();
			setSaving(true);
			const payload: any = {
				name: values.name,
				datasetId: values.datasetId,
				ruleId: values.ruleId && values.ruleId !== "__ALL_RULES__" ? values.ruleId : undefined,
				intervalMinutes: Number(values.intervalMinutes || 60),
				enabled: values.enabled !== false,
				ownerDept: values.ownerDept ? values.ownerDept.trim() : undefined,
			};
			if (editing?.id) {
				await updateQualityTask(editing.id, payload);
				toast.success("巡检计划已更新");
			} else {
				await createQualityTask(payload);
				toast.success("巡检计划已创建");
			}
			setModalOpen(false);
			await loadTasks();
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(parseWriteError(error, "巡检计划保存失败"));
		} finally {
			setSaving(false);
		}
	};

	const handleToggle = async (record: QualityTask, enabled: boolean) => {
		if (!record?.id) return;
		try {
			setActionTaskId(record.id);
			await toggleQualityTask(record.id, enabled);
			toast.success(enabled ? "巡检计划已启用" : "巡检计划已停用");
			await loadTasks();
		} catch (error: any) {
			toast.error(parseWriteError(error, "巡检计划状态更新失败"));
		} finally {
			setActionTaskId("");
		}
	};

	const handleTrigger = async (record: QualityTask) => {
		if (!record?.id) return;
		try {
			setActionTaskId(record.id);
			const result: any = await triggerQualityTask(record.id);
			const list = Array.isArray(result) ? result : [];
			const failed = list.find((item: any) => item?.error);
			if (failed) {
				toast.warning(`已触发，但存在失败规则：${failed.error}`);
			} else {
				toast.success("巡检计划已触发");
			}
			await loadTasks();
		} catch (error: any) {
			toast.error(parseWriteError(error, "巡检计划触发失败"));
		} finally {
			setActionTaskId("");
		}
	};

	const handleDelete = async (record: QualityTask) => {
		if (!record?.id) return;
		try {
			setActionTaskId(record.id);
			await deleteQualityTask(record.id);
			toast.success("巡检计划已删除");
			await loadTasks();
		} catch (error: any) {
			toast.error(parseWriteError(error, "巡检计划删除失败"));
		} finally {
			setActionTaskId("");
		}
	};

	const columns: ColumnsType<QualityTask> = [
		{ title: "计划名称", dataIndex: "name", render: (value) => value || "-" },
		{
			title: "数据集",
			dataIndex: "datasetId",
			render: (value) => datasetNameMap.get(String(value || "")) || value || "-",
		},
		{
			title: "规则范围",
			dataIndex: "ruleId",
			render: (value) => (value ? ruleNameMap.get(String(value)) || value : "自动匹配全规则"),
		},
		{
			title: "间隔(分钟)",
			dataIndex: "intervalMinutes",
			width: 120,
			render: (value) => value || 60,
		},
		{
			title: "状态",
			dataIndex: "enabled",
			width: 100,
			render: (value) => <Tag color={value ? "green" : "default"}>{value ? "启用" : "停用"}</Tag>,
		},
		{
			title: "最近执行",
			dataIndex: "datasetId",
			width: 180,
			render: (datasetId) => {
				const run = runSummary[String(datasetId || "")];
				const status = normalizeStatus(run?.status);
				if (!status) return "-";
				return <Tag color={statusColor(status)}>{status}</Tag>;
			},
		},
		{
			title: "失败原因",
			dataIndex: "datasetId",
			render: (datasetId) => {
				const run = runSummary[String(datasetId || "")];
				const status = normalizeStatus(run?.status);
				if (status !== "FAILED" && status !== "ERROR") {
					return "-";
				}
				return run?.message || "-";
			},
		},
		{
			title: "最后触发时间",
			dataIndex: "lastTriggeredAt",
			width: 200,
			render: (value) => value || "-",
		},
		{
			title: "操作",
			width: 280,
			render: (_, record) => {
				const busy = actionTaskId === record.id;
				return (
					<Space wrap>
						<Button
							size="small"
							icon={<PlayCircleOutlined />}
							disabled={!canManage}
							loading={busy}
							onClick={() => handleTrigger(record)}
						>
							手工触发
						</Button>
						<Button size="small" icon={<EditOutlined />} disabled={!canManage} onClick={() => openModal(record)}>
							编辑
						</Button>
						<Switch
							size="small"
							checked={record.enabled !== false}
							disabled={!canManage || busy}
							onChange={(checked) => handleToggle(record, checked)}
						/>
						<Popconfirm
							title="确认删除该巡检计划？"
							okText="删除"
							cancelText="取消"
							onConfirm={() => handleDelete(record)}
							disabled={!canManage}
						>
							<Button size="small" danger icon={<DeleteOutlined />} disabled={!canManage}>
								删除
							</Button>
						</Popconfirm>
					</Space>
				);
			},
		},
	];

	return (
		<Card
			title="质量巡检计划"
			extra={
				<Space>
					<Button icon={<ReloadOutlined />} onClick={() => void loadTasks()}>
						刷新
					</Button>
					<Button type="primary" icon={<PlusOutlined />} disabled={!canManage} onClick={() => openModal()}>
						新增计划
					</Button>
				</Space>
			}
		>
			<Space direction="vertical" className="w-full" size={12}>
				<Alert
					type={canManage ? "info" : "warning"}
					showIcon
					message={
						canManage
							? `当前部门上下文：${activeDept || "未设置"}，可执行创建/编辑/触发操作。`
							: "当前账号为只读模式，仅可查看巡检计划。"
					}
				/>
				<CompactTable
					rowKey={(record) => record.id || `${record.datasetId}-${record.ruleId || "all"}`}
					columns={columns}
					dataSource={tasks}
					loading={loading}
					pagination={{ pageSize: 10 }}
				/>
			</Space>

			<Modal
				open={modalOpen}
				title={editing ? "编辑巡检计划" : "新增巡检计划"}
				onCancel={() => setModalOpen(false)}
				onOk={saveTask}
				okText="保存"
				destroyOnClose
				confirmLoading={saving}
				width={720}
			>
				<Form form={form} layout="vertical" initialValues={{ intervalMinutes: 60, enabled: true, ruleId: "__ALL_RULES__" }}>
					<Form.Item label="计划名称" name="name" rules={[{ required: true, message: "请输入计划名称" }]}>
						<Input placeholder="例如：客户表每日巡检" />
					</Form.Item>
					<Form.Item label="数据集" name="datasetId" rules={[{ required: true, message: "请选择数据集" }]}>
						<Select options={datasetOptions} showSearch optionFilterProp="label" />
					</Form.Item>
					<Form.Item label="规则范围" name="ruleId">
						<Select options={ruleOptions} showSearch optionFilterProp="label" />
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
		</Card>
	);
}
