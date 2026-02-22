import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
	Button,
	Card,
	Drawer,
	Form,
	Input,
	Modal,
	Select,
	Space,
	Table,
	Tag,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { EditOutlined, FileTextOutlined, PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import {
	appendIssueAction,
	closeIssue,
	createIssue,
	getIssue,
	listIssues,
	updateIssue,
} from "@/api/platformApi";
import { useActiveDept } from "@/store/contextStore";
import { useUserRoles } from "@/store/userStore";

type IssueAction = {
	id?: string;
	actionType?: string;
	notes?: string;
	operator?: string;
	createdDate?: string;
	attachments?: string[];
};

type IssueTicket = {
	id?: string;
	sourceType?: string;
	sourceId?: string;
	datasetId?: string;
	title?: string;
	summary?: string;
	status?: string;
	severity?: string;
	priority?: string;
	dataLevel?: string;
	ownerDept?: string;
	assignedTo?: string;
	dueAt?: string;
	resolvedAt?: string;
	resolution?: string;
	owner?: string;
	tags?: string[];
	lastModifiedDate?: string;
	actions?: IssueAction[];
};

type IssueForm = {
	title: string;
	summary?: string;
	sourceType?: string;
	sourceId?: string;
	datasetId?: string;
	status?: string;
	severity?: string;
	priority?: string;
	dataLevel?: string;
	owner?: string;
	assignedTo?: string;
	tagsRaw?: string;
};

type ActionForm = {
	actionType: string;
	notes?: string;
	attachmentsRaw?: string;
};

const MAINTAINER_ROLES = new Set([
	"ADMIN",
	"OP_ADMIN",
	"INST_DATA_OWNER",
	"DEPT_DATA_OWNER",
	"INST_LEADER",
	"DEPT_LEADER",
]);

const normalizeRole = (raw: unknown) =>
	String(raw || "")
		.trim()
		.toUpperCase()
		.replace(/^ROLE_/, "");

const normalizeStatus = (raw: unknown) => String(raw || "").trim().toUpperCase();

const splitCsv = (raw?: string) =>
	String(raw || "")
		.split(",")
		.map((item) => item.trim())
		.filter(Boolean);

const statusColor = (status?: string) => {
	const normalized = normalizeStatus(status);
	if (normalized === "OPEN" || normalized === "NEW") return "blue";
	if (normalized === "IN_PROGRESS" || normalized === "PROCESSING") return "gold";
	if (normalized === "CLOSED" || normalized === "RESOLVED") return "green";
	if (normalized === "REJECTED" || normalized === "FAILED") return "red";
	return "default";
};

export default function IssueWorkflowPanel() {
	const [issues, setIssues] = useState<IssueTicket[]>([]);
	const [loading, setLoading] = useState(false);
	const [statusFilter, setStatusFilter] = useState<string>("ALL");
	const [keyword, setKeyword] = useState("");
	const [editing, setEditing] = useState<IssueTicket | null>(null);
	const [modalOpen, setModalOpen] = useState(false);
	const [detailOpen, setDetailOpen] = useState(false);
	const [detailIssue, setDetailIssue] = useState<IssueTicket | null>(null);
	const [closeModalOpen, setCloseModalOpen] = useState(false);
	const [closeIssueId, setCloseIssueId] = useState<string>("");
	const [resolution, setResolution] = useState("");
	const [actionModalOpen, setActionModalOpen] = useState(false);
	const [actionIssueId, setActionIssueId] = useState<string>("");
	const [readOnly, setReadOnly] = useState(false);
	const [saving, setSaving] = useState(false);
	const [form] = Form.useForm<IssueForm>();
	const [actionForm] = Form.useForm<ActionForm>();

	const activeDept = useActiveDept();
	const userRoles = useUserRoles();

	const canManage = useMemo(() => {
		if (readOnly) return false;
		const normalized = new Set((userRoles || []).map(normalizeRole));
		for (const role of normalized) {
			if (MAINTAINER_ROLES.has(role)) return true;
		}
		return false;
	}, [readOnly, userRoles]);

	const parseWriteError = (error: any, fallback: string) => {
		const message = String(error?.message || fallback);
		if (message.toLowerCase().includes("access denied") || message.includes("403")) {
			setReadOnly(true);
		}
		return message;
	};

	const loadIssues = async () => {
		setLoading(true);
		try {
			const params: any = { limit: 100 };
			if (statusFilter !== "ALL") {
				params.status = statusFilter;
			}
			if (keyword.trim()) {
				params.keyword = keyword.trim();
			}
			const list = await listIssues(params);
			setIssues(Array.isArray(list) ? (list as IssueTicket[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "问题单加载失败");
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadIssues();
	}, [statusFilter]);

	const openCreate = () => {
		setEditing(null);
		form.setFieldsValue({
			title: "",
			summary: "",
			sourceType: "QUALITY_RULE",
			status: "OPEN",
			severity: "MEDIUM",
			priority: "MEDIUM",
			dataLevel: "DATA_INTERNAL",
			owner: "",
			assignedTo: "",
			tagsRaw: "",
		});
		setModalOpen(true);
	};

	const openEdit = (issue: IssueTicket) => {
		setEditing(issue);
		form.setFieldsValue({
			title: issue.title || "",
			summary: issue.summary || "",
			sourceType: issue.sourceType || "QUALITY_RULE",
			sourceId: issue.sourceId || "",
			datasetId: issue.datasetId || "",
			status: issue.status || "OPEN",
			severity: issue.severity || "MEDIUM",
			priority: issue.priority || "MEDIUM",
			dataLevel: issue.dataLevel || "DATA_INTERNAL",
			owner: issue.owner || "",
			assignedTo: issue.assignedTo || "",
			tagsRaw: Array.isArray(issue.tags) ? issue.tags.join(",") : "",
		});
		setModalOpen(true);
	};

	const saveIssue = async () => {
		try {
			const values = await form.validateFields();
			setSaving(true);
			const payload = {
				title: values.title,
				summary: values.summary || undefined,
				sourceType: values.sourceType || undefined,
				sourceId: values.sourceId || undefined,
				datasetId: values.datasetId || undefined,
				status: values.status || undefined,
				severity: values.severity || undefined,
				priority: values.priority || undefined,
				dataLevel: values.dataLevel || undefined,
				owner: values.owner || undefined,
				assignedTo: values.assignedTo || undefined,
				tags: splitCsv(values.tagsRaw),
			};
			if (editing?.id) {
				await updateIssue(editing.id, payload);
				toast.success("问题单已更新");
			} else {
				await createIssue(payload);
				toast.success("问题单已创建");
			}
			setModalOpen(false);
			await loadIssues();
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(parseWriteError(error, "问题单保存失败"));
		} finally {
			setSaving(false);
		}
	};

	const openDetail = async (id?: string) => {
		if (!id) return;
		try {
			const detail = (await getIssue(id)) as IssueTicket;
			setDetailIssue(detail || null);
			setDetailOpen(true);
		} catch (error: any) {
			toast.error(error?.message || "问题单详情加载失败");
		}
	};

	const openClose = (id?: string) => {
		if (!id) return;
		setCloseIssueId(id);
		setResolution("");
		setCloseModalOpen(true);
	};

	const submitClose = async () => {
		if (!closeIssueId) return;
		try {
			setSaving(true);
			await closeIssue(closeIssueId, resolution || undefined);
			toast.success("问题单已关闭");
			setCloseModalOpen(false);
			await loadIssues();
			if (detailIssue?.id === closeIssueId) {
				await openDetail(closeIssueId);
			}
		} catch (error: any) {
			toast.error(parseWriteError(error, "关闭问题单失败"));
		} finally {
			setSaving(false);
		}
	};

	const openAction = (id?: string) => {
		if (!id) return;
		setActionIssueId(id);
		actionForm.setFieldsValue({ actionType: "COMMENT", notes: "", attachmentsRaw: "" });
		setActionModalOpen(true);
	};

	const submitAction = async () => {
		if (!actionIssueId) return;
		try {
			const values = await actionForm.validateFields();
			setSaving(true);
			await appendIssueAction(actionIssueId, {
				actionType: values.actionType || "COMMENT",
				notes: values.notes || undefined,
				attachments: splitCsv(values.attachmentsRaw),
			});
			toast.success("处理记录已追加");
			setActionModalOpen(false);
			await loadIssues();
			if (detailIssue?.id === actionIssueId) {
				await openDetail(actionIssueId);
			}
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(parseWriteError(error, "追加处理记录失败"));
		} finally {
			setSaving(false);
		}
	};

	const columns: ColumnsType<IssueTicket> = [
		{ title: "标题", dataIndex: "title", render: (value) => value || "-" },
		{
			title: "状态",
			dataIndex: "status",
			width: 120,
			render: (value) => <Tag color={statusColor(value)}>{normalizeStatus(value) || "-"}</Tag>,
		},
		{ title: "严重性", dataIndex: "severity", width: 100, render: (value) => value || "-" },
		{ title: "优先级", dataIndex: "priority", width: 100, render: (value) => value || "-" },
		{ title: "责任人", dataIndex: "assignedTo", width: 120, render: (value) => value || "-" },
		{ title: "归属部门", dataIndex: "ownerDept", width: 140, render: (value) => value || "-" },
		{ title: "来源", dataIndex: "sourceType", width: 120, render: (value) => value || "-" },
		{ title: "最后更新", dataIndex: "lastModifiedDate", width: 200, render: (value) => value || "-" },
		{
			title: "操作",
			width: 260,
			render: (_, record) => (
				<Space wrap>
					<Button size="small" icon={<FileTextOutlined />} onClick={() => void openDetail(record.id)}>
						详情
					</Button>
					<Button size="small" icon={<EditOutlined />} disabled={!canManage} onClick={() => openEdit(record)}>
						编辑
					</Button>
					<Button size="small" disabled={!canManage} onClick={() => openAction(record.id)}>
						追加记录
					</Button>
					<Button size="small" disabled={!canManage} onClick={() => openClose(record.id)}>
						关闭
					</Button>
				</Space>
			),
		},
	];

	return (
		<Card
			title="问题单闭环"
			extra={
				<Space>
					<Input.Search
						allowClear
						placeholder="按标题/描述搜索"
						value={keyword}
						onChange={(event) => setKeyword(event.target.value)}
						onSearch={() => void loadIssues()}
						style={{ width: 220 }}
					/>
					<Select
						value={statusFilter}
						onChange={(value) => setStatusFilter(value)}
						options={[
							{ label: "全部状态", value: "ALL" },
							{ label: "OPEN", value: "OPEN" },
							{ label: "IN_PROGRESS", value: "IN_PROGRESS" },
							{ label: "CLOSED", value: "CLOSED" },
						]}
						style={{ width: 140 }}
					/>
					<Button icon={<ReloadOutlined />} onClick={() => void loadIssues()}>
						刷新
					</Button>
					<Button type="primary" icon={<PlusOutlined />} disabled={!canManage} onClick={openCreate}>
						新建问题单
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
							? `当前部门上下文：${activeDept || "未设置"}，可创建/更新/关闭问题单。`
							: "当前账号为只读模式，仅可查看问题单。"
					}
				/>
				<Table rowKey={(record) => record.id || record.title || "issue"} columns={columns} dataSource={issues} loading={loading} />
			</Space>

			<Modal
				open={modalOpen}
				title={editing ? "编辑问题单" : "新建问题单"}
				onCancel={() => setModalOpen(false)}
				onOk={saveIssue}
				okText="保存"
				confirmLoading={saving}
				width={760}
				destroyOnClose
			>
				<Form form={form} layout="vertical">
					<Form.Item label="标题" name="title" rules={[{ required: true, message: "请输入标题" }]}>
						<Input placeholder="例如：客户维度校验失败需要处理" />
					</Form.Item>
					<Form.Item label="描述" name="summary">
						<Input.TextArea rows={4} placeholder="请输入问题描述" />
					</Form.Item>
					<Form.Item label="来源类型" name="sourceType">
						<Select
							options={[
								{ label: "质量规则", value: "QUALITY_RULE" },
								{ label: "质量任务", value: "QUALITY_TASK" },
								{ label: "手工创建", value: "MANUAL" },
							]}
						/>
					</Form.Item>
					<Form.Item label="来源ID" name="sourceId">
						<Input placeholder="可选，关联规则/任务ID" />
					</Form.Item>
					<Form.Item label="数据集ID" name="datasetId">
						<Input placeholder="可选" />
					</Form.Item>
					<Form.Item label="状态" name="status">
						<Select
							options={[
								{ label: "OPEN", value: "OPEN" },
								{ label: "IN_PROGRESS", value: "IN_PROGRESS" },
								{ label: "CLOSED", value: "CLOSED" },
							]}
						/>
					</Form.Item>
					<Form.Item label="严重性" name="severity">
						<Select
							options={[
								{ label: "LOW", value: "LOW" },
								{ label: "MEDIUM", value: "MEDIUM" },
								{ label: "HIGH", value: "HIGH" },
								{ label: "CRITICAL", value: "CRITICAL" },
							]}
						/>
					</Form.Item>
					<Form.Item label="优先级" name="priority">
						<Select
							options={[
								{ label: "LOW", value: "LOW" },
								{ label: "MEDIUM", value: "MEDIUM" },
								{ label: "HIGH", value: "HIGH" },
								{ label: "URGENT", value: "URGENT" },
							]}
						/>
					</Form.Item>
					<Form.Item label="数据密级" name="dataLevel">
						<Select
							options={[
								{ label: "DATA_PUBLIC", value: "DATA_PUBLIC" },
								{ label: "DATA_INTERNAL", value: "DATA_INTERNAL" },
								{ label: "DATA_SECRET", value: "DATA_SECRET" },
								{ label: "DATA_CONFIDENTIAL", value: "DATA_CONFIDENTIAL" },
							]}
						/>
					</Form.Item>
					<Form.Item label="Owner" name="owner">
						<Input placeholder="可选" />
					</Form.Item>
					<Form.Item label="责任人" name="assignedTo">
						<Input placeholder="用户名" />
					</Form.Item>
					<Form.Item label="标签(逗号分隔)" name="tagsRaw">
						<Input placeholder="QUALITY,ODS,PATENT" />
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={closeModalOpen}
				title="关闭问题单"
				onCancel={() => setCloseModalOpen(false)}
				onOk={submitClose}
				okText="确认关闭"
				confirmLoading={saving}
				destroyOnClose
			>
				<Input.TextArea value={resolution} onChange={(event) => setResolution(event.target.value)} rows={4} placeholder="请输入关闭说明" />
			</Modal>

			<Modal
				open={actionModalOpen}
				title="追加处理记录"
				onCancel={() => setActionModalOpen(false)}
				onOk={submitAction}
				okText="提交"
				confirmLoading={saving}
				destroyOnClose
			>
				<Form form={actionForm} layout="vertical">
					<Form.Item label="动作类型" name="actionType" rules={[{ required: true, message: "请选择动作类型" }]}>
						<Select
							options={[
								{ label: "COMMENT", value: "COMMENT" },
								{ label: "ASSIGN", value: "ASSIGN" },
								{ label: "STATUS_CHANGE", value: "STATUS_CHANGE" },
								{ label: "RESOLUTION", value: "RESOLUTION" },
							]}
						/>
					</Form.Item>
					<Form.Item label="备注" name="notes">
						<Input.TextArea rows={4} />
					</Form.Item>
					<Form.Item label="附件(逗号分隔)" name="attachmentsRaw">
						<Input placeholder="http://a, http://b" />
					</Form.Item>
				</Form>
			</Modal>

			<Drawer open={detailOpen} onClose={() => setDetailOpen(false)} title={`问题单详情：${detailIssue?.title || "-"}`} width={680}>
				<Space direction="vertical" className="w-full" size={12}>
					<Alert
						type="info"
						showIcon
						message={`状态 ${normalizeStatus(detailIssue?.status)} / 严重性 ${detailIssue?.severity || "-"} / 责任人 ${
							detailIssue?.assignedTo || "-"
						}`}
					/>
					<div>
						<div className="font-medium">描述</div>
						<div className="whitespace-pre-wrap">{detailIssue?.summary || "-"}</div>
					</div>
					<div>
						<div className="font-medium">处理记录</div>
						<Table
							size="small"
							rowKey={(item) => item.id || `${item.createdDate}-${item.actionType}`}
							columns={[
								{ title: "动作", dataIndex: "actionType", width: 120 },
								{ title: "备注", dataIndex: "notes", render: (value) => value || "-" },
								{ title: "执行人", dataIndex: "operator", width: 140, render: (value) => value || "-" },
								{ title: "时间", dataIndex: "createdDate", width: 200, render: (value) => value || "-" },
							]}
							dataSource={Array.isArray(detailIssue?.actions) ? detailIssue?.actions : []}
							pagination={false}
						/>
					</div>
				</Space>
			</Drawer>
		</Card>
	);
}
