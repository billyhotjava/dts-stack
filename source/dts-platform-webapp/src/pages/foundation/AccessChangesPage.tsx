import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
	Button,
	Card,
	Col,
	Descriptions,
	Form,
	Input,
	Modal,
	Row,
	Select,
	Space,
	Table,
	Tag,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { PageHeader } from "@/components/page-header";
import { ingestionTaskAPI, type IngestionChangeLogDTO, type IngestionTaskDTO } from "@/api/ingestion";
import { formatDateTime } from "@/utils/format";

const { Text } = Typography;

const CHANGE_TYPE_LABELS: Record<string, string> = {
	TASK_CREATE: "新建任务",
	CONN_PARAM: "连接参数变更",
	CATALOG_CHANGE: "同步范围变更",
	SCHEDULE_CHANGE: "调度配置变更",
	TASK_UPDATE: "任务信息更新",
};

const STATUS_LABELS: Record<string, string> = {
	DONE: "已完成",
	PENDING: "待处理",
	APPROVAL: "待审批",
	REJECTED: "已驳回",
};

const changeTypeOptions = [
	{ label: "全部类型", value: "ALL" },
	{ label: "新建任务", value: "TASK_CREATE" },
	{ label: "连接参数变更", value: "CONN_PARAM" },
	{ label: "同步范围变更", value: "CATALOG_CHANGE" },
	{ label: "调度配置变更", value: "SCHEDULE_CHANGE" },
	{ label: "任务信息更新", value: "TASK_UPDATE" },
];

const statusOptions = [
	{ label: "全部状态", value: "ALL" },
	{ label: "已完成", value: "DONE" },
	{ label: "待处理", value: "PENDING" },
	{ label: "待审批", value: "APPROVAL" },
	{ label: "已驳回", value: "REJECTED" },
];

const riskTag = (risk?: string) => {
	if (risk === "L") return <Tag color="green">低</Tag>;
	if (risk === "M") return <Tag color="orange">中</Tag>;
	if (risk === "H") return <Tag color="red">高</Tag>;
	return <Tag>未知</Tag>;
};

const statusTag = (status?: string) => {
	const label = status ? STATUS_LABELS[status] || status : "未知";
	if (status === "DONE") return <Tag color="green">{label}</Tag>;
	if (status === "APPROVAL") return <Tag color="gold">{label}</Tag>;
	if (status === "PENDING") return <Tag color="blue">{label}</Tag>;
	if (status === "REJECTED") return <Tag color="red">{label}</Tag>;
	return <Tag>{label}</Tag>;
};

const actionLabel: Record<"SUBMIT" | "APPROVE" | "REJECT", string> = {
	SUBMIT: "提交审批",
	APPROVE: "审批通过",
	REJECT: "审批驳回",
};

export default function AccessChangesPage() {
	const [changes, setChanges] = useState<IngestionChangeLogDTO[]>([]);
	const [tasks, setTasks] = useState<IngestionTaskDTO[]>([]);
	const [taskId, setTaskId] = useState<number | "ALL">("ALL");
	const [changeType, setChangeType] = useState("ALL");
	const [status, setStatus] = useState("ALL");
	const [assigneeKeyword, setAssigneeKeyword] = useState("");
	const [keyword, setKeyword] = useState("");
	const [selected, setSelected] = useState<IngestionChangeLogDTO | null>(null);
	const [modalOpen, setModalOpen] = useState(false);
	const [actionModalOpen, setActionModalOpen] = useState(false);
	const [actionType, setActionType] = useState<"SUBMIT" | "APPROVE" | "REJECT" | null>(null);
	const [actionTarget, setActionTarget] = useState<IngestionChangeLogDTO | null>(null);
	const [loading, setLoading] = useState(false);
	const [pageState, setPageState] = useState({ page: 1, size: 8, total: 0 });
	const [form] = Form.useForm();
	const [actionForm] = Form.useForm();

	const taskOptions = useMemo(() => {
		const opts = tasks.map((task) => ({
			label: task.name,
			value: task.id ?? 0,
		}));
		return [{ label: "全部任务", value: "ALL" }, ...opts];
	}, [tasks]);

	const loadTasks = async () => {
		try {
			const result = await ingestionTaskAPI.getTasks({ page: 0, size: 200 });
			setTasks(Array.isArray(result?.content) ? result.content : []);
		} catch (error) {
			console.error(error);
			toast.error("获取任务列表失败");
		}
	};

	const loadChanges = async (nextPage = pageState.page, nextSize = pageState.size) => {
		setLoading(true);
		try {
			const result = await ingestionTaskAPI.getChangeLogs({
				taskId: taskId === "ALL" ? undefined : Number(taskId),
				changeType: changeType === "ALL" ? undefined : changeType,
				status: status === "ALL" ? undefined : status,
				assignee: assigneeKeyword.trim() || undefined,
				keyword: keyword.trim() || undefined,
				page: nextPage - 1,
				size: nextSize,
				sort: "createdDate,desc",
			});
			const content = Array.isArray(result?.content) ? result.content : [];
			setChanges(content);
			setPageState({
				page: typeof result?.number === "number" ? result.number + 1 : nextPage,
				size: typeof result?.size === "number" ? result.size : nextSize,
				total: typeof result?.totalElements === "number" ? result.totalElements : content.length,
			});
		} catch (error) {
			console.error(error);
			toast.error("加载变更记录失败");
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		loadTasks();
	}, []);

	useEffect(() => {
		loadChanges(1, pageState.size);
		setSelected(null);
	}, [taskId, changeType, status, assigneeKeyword, keyword]);

	const columns: ColumnsType<IngestionChangeLogDTO> = [
		{ title: "时间", dataIndex: "createdDate", render: (value) => formatDateTime(value) || "-" },
		{ title: "任务", dataIndex: "taskName", render: (_, row) => row.taskName || `任务 #${row.taskId}` },
		{
			title: "类型",
			dataIndex: "changeType",
			render: (value) => CHANGE_TYPE_LABELS[value as string] || value,
		},
		{ title: "摘要", dataIndex: "summary", render: (value) => value || "-" },
		{ title: "责任人", dataIndex: "assignee", render: (value) => value || "-" },
		{ title: "风险等级", dataIndex: "riskLevel", render: (value) => riskTag(value) },
		{ title: "状态", dataIndex: "status", render: (value) => statusTag(value) },
		{
			title: "处理时间",
			dataIndex: "handledAt",
			render: (value) => formatDateTime(value) || "-",
		},
		{
			title: "操作",
			render: (_, row) => (
				<Space size={4}>
					<Button type="link" onClick={() => setSelected(row)}>
						查看
					</Button>
					{row.status === "PENDING" ? (
						<Button type="link" onClick={() => openActionModal(row, "SUBMIT")}>
							提交审批
						</Button>
					) : null}
					{row.status === "APPROVAL" ? (
						<>
							<Button type="link" onClick={() => openActionModal(row, "APPROVE")}>
								通过
							</Button>
							<Button type="link" danger onClick={() => openActionModal(row, "REJECT")}>
								驳回
							</Button>
						</>
					) : null}
				</Space>
			),
		},
	];

	const openModal = () => {
		form.resetFields();
		setModalOpen(true);
	};

	const openActionModal = (row: IngestionChangeLogDTO, action: "SUBMIT" | "APPROVE" | "REJECT") => {
		setActionTarget(row);
		setActionType(action);
		actionForm.setFieldsValue({
			assignee: row.assignee || "",
			approvalComment: "",
		});
		setActionModalOpen(true);
	};

	const submitChange = async () => {
		try {
			const values = await form.validateFields();
			await ingestionTaskAPI.createChangeLog({
				taskId: values.taskId,
				taskName: tasks.find((item) => item.id === values.taskId)?.name,
				changeType: values.changeType,
				summary: values.summary,
				detail: values.detail,
				riskLevel: values.riskLevel,
				status: "PENDING",
				assignee: values.assignee,
			});
			setModalOpen(false);
			toast.success("变更已登记");
			loadChanges(1, pageState.size);
		} catch (error) {
			if (error) {
				console.error(error);
				if (!(error as any)?.errorFields) {
					toast.error("登记变更失败");
				}
			}
		}
	};

	const submitTransition = async () => {
		if (!actionTarget?.id || !actionType) return;
		try {
			const values = await actionForm.validateFields();
			await ingestionTaskAPI.transitionChangeLog(actionTarget.id, {
				action: actionType,
				assignee: values.assignee,
				approvalComment: values.approvalComment,
			});
			toast.success(`${actionLabel[actionType]}成功`);
			setActionModalOpen(false);
			setActionTarget(null);
			setActionType(null);
			actionForm.resetFields();
			await loadChanges(pageState.page, pageState.size);
		} catch (error) {
			if ((error as any)?.errorFields) {
				return;
			}
			console.error(error);
			toast.error("流转失败");
		}
	};

	const impactView = selected ? (
			<Descriptions column={1} size="small" bordered>
			<Descriptions.Item label="变更任务">{selected.taskName || `任务 #${selected.taskId}`}</Descriptions.Item>
			<Descriptions.Item label="变更类型">
				{CHANGE_TYPE_LABELS[selected.changeType || ""] || selected.changeType || "-"}
			</Descriptions.Item>
			<Descriptions.Item label="风险等级">{riskTag(selected.riskLevel)}</Descriptions.Item>
			<Descriptions.Item label="处理状态">{statusTag(selected.status)}</Descriptions.Item>
			<Descriptions.Item label="责任人">{selected.assignee || "-"}</Descriptions.Item>
			<Descriptions.Item label="处理时间">{formatDateTime(selected.handledAt) || "-"}</Descriptions.Item>
			<Descriptions.Item label="处理人">{selected.handledBy || "-"}</Descriptions.Item>
			<Descriptions.Item label="变更摘要">{selected.summary || "-"}</Descriptions.Item>
			<Descriptions.Item label="变更详情">{selected.detail || "-"}</Descriptions.Item>
			<Descriptions.Item label="审批意见">{selected.approvalComment || "-"}</Descriptions.Item>
		</Descriptions>
	) : (
		<Text type="secondary">请选择一条变更记录查看详情。</Text>
	);

	return (
		<div className="space-y-4">
			<PageHeader
				title="接入变更记录"
				actions={
					<Space>
						<Button onClick={() => loadChanges()}>刷新</Button>
						<Button type="primary" onClick={openModal}>
							登记变更
						</Button>
					</Space>
				}
			/>

			<Alert
				type="info"
				showIcon
				message="记录连接参数变更、同步范围调整与调度配置变更，形成可追溯的接入变更闭环。"
			/>

			<Card
				title="变更查询"
				extra={
					<Space>
						<Button onClick={() => loadChanges()}>刷新</Button>
						<Button type="primary" onClick={openModal}>
							登记变更
						</Button>
					</Space>
				}
			>
				<Row gutter={12} className="mb-4">
					<Col span={5}>
						<Select value={taskId} onChange={setTaskId} options={taskOptions} />
					</Col>
					<Col span={5}>
						<Select value={changeType} onChange={setChangeType} options={changeTypeOptions} />
					</Col>
					<Col span={4}>
						<Select value={status} onChange={setStatus} options={statusOptions} />
					</Col>
					<Col span={5}>
						<Input value={assigneeKeyword} onChange={(e) => setAssigneeKeyword(e.target.value)} placeholder="责任人" />
					</Col>
					<Col span={5}>
						<Input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="关键字" />
					</Col>
				</Row>
				<Table
					rowKey={(row) => row.id ?? `${row.taskId}-${row.createdDate}`}
					columns={columns}
					dataSource={changes}
					loading={loading}
					pagination={{
						current: pageState.page,
						pageSize: pageState.size,
						total: pageState.total,
						showSizeChanger: true,
						onChange: (page, size) => loadChanges(page, size),
					}}
					onRow={(row) => ({
						onClick: () => setSelected(row),
					})}
				/>
			</Card>

			<Card title="影响分析与处置">{impactView}</Card>

			<Modal
				open={modalOpen}
				title="登记变更"
				onCancel={() => setModalOpen(false)}
				onOk={submitChange}
				okText="提交"
				cancelText="取消"
			>
				<Form form={form} layout="vertical">
					<Row gutter={12}>
						<Col span={12}>
							<Form.Item name="taskId" label="入湖任务" rules={[{ required: true, message: "请选择入湖任务" }]}>
								<Select options={taskOptions.filter((item) => item.value !== "ALL")} />
							</Form.Item>
						</Col>
						<Col span={12}>
							<Form.Item name="changeType" label="变更类型" rules={[{ required: true, message: "请选择变更类型" }]}>
								<Select options={changeTypeOptions.filter((item) => item.value !== "ALL")} />
							</Form.Item>
						</Col>
					</Row>
					<Row gutter={12}>
						<Col span={12}>
							<Form.Item name="riskLevel" label="风险等级" rules={[{ required: true, message: "请选择风险等级" }]}>
								<Select
									options={[
										{ label: "低", value: "L" },
										{ label: "中", value: "M" },
										{ label: "高", value: "H" },
									]}
								/>
							</Form.Item>
						</Col>
						<Col span={12}>
							<Form.Item name="summary" label="变更摘要" rules={[{ required: true, message: "请输入摘要" }]}>
								<Input placeholder="例如：同步范围调整、调度时间调整" />
							</Form.Item>
						</Col>
					</Row>
					<Form.Item name="detail" label="变更详情">
						<Input.TextArea rows={4} placeholder="补充说明、影响范围或处理建议" />
					</Form.Item>
					<Form.Item name="assignee" label="责任人">
						<Input placeholder="例如：opadmin" />
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={actionModalOpen}
				title={actionType ? actionLabel[actionType] : "变更流转"}
				onCancel={() => setActionModalOpen(false)}
				onOk={submitTransition}
				okText="提交"
				cancelText="取消"
			>
				<Form form={actionForm} layout="vertical">
					<Form.Item
						name="assignee"
						label="责任人"
						rules={
							actionType === "SUBMIT"
								? [{ required: true, message: "提交审批时必须指定责任人" }]
								: []
						}
					>
						<Input placeholder="例如：opadmin" />
					</Form.Item>
					<Form.Item
						name="approvalComment"
						label="审批意见"
						rules={
							actionType === "REJECT"
								? [{ required: true, message: "驳回时请填写审批意见" }]
								: []
						}
					>
						<Input.TextArea rows={4} placeholder="可填写审批说明" />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
