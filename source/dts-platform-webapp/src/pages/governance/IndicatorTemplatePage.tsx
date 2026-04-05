import { useEffect, useState } from "react";
import { toast } from "sonner";
import {
	Button,
	Form,
	Input,
	Modal,
	Space,
	Table,
	Tabs,
	Tag,
	Tooltip,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { PlusOutlined, DeleteOutlined, EditOutlined, RocketOutlined } from "@ant-design/icons";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import {
	listIndicatorTemplates,
	getIndicatorTemplate,
	createIndicatorTemplate,
	updateIndicatorTemplate,
	deleteIndicatorTemplate,
} from "@/api/platformApi";
import IndicatorWizard from "./components/IndicatorWizard";

const DOMAIN_TABS = [
	{ key: "", label: "全部" },
	{ key: "FINANCE", label: "财务" },
	{ key: "OPERATION", label: "运营" },
	{ key: "QUALITY", label: "质量" },
	{ key: "COMPLIANCE", label: "合规" },
	{ key: "CUSTOM", label: "自定义" },
];

const DOMAIN_COLORS: Record<string, string> = {
	FINANCE: "blue",
	OPERATION: "green",
	QUALITY: "orange",
	COMPLIANCE: "red",
	CUSTOM: "purple",
};

type Template = any;

export default function Page() {
	const [templates, setTemplates] = useState<Template[]>([]);
	const [loading, setLoading] = useState(false);
	const [domain, setDomain] = useState("");
	const [modalOpen, setModalOpen] = useState(false);
	const [editing, setEditing] = useState<Template | null>(null);
	const [wizardOpen, setWizardOpen] = useState(false);
	const [wizardTemplate, setWizardTemplate] = useState<Template | null>(null);
	const [form] = Form.useForm();
	const canManage = useGovernanceManageAccess();

	const fetchList = async () => {
		setLoading(true);
		try {
			const res: any = await listIndicatorTemplates(domain ? { domain } : undefined);
			setTemplates(Array.isArray(res) ? res : res?.data ?? []);
		} catch {
			// interceptor handles error
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		fetchList();
	}, [domain]);

	const openCreate = () => {
		setEditing(null);
		form.resetFields();
		setModalOpen(true);
	};

	const openEdit = async (record: Template) => {
		try {
			const detail: any = await getIndicatorTemplate(record.id);
			const data = detail?.data ?? detail;
			setEditing(data);
			form.setFieldsValue({
				code: data.code,
				name: data.name,
				domain: data.domain,
				description: data.description,
				blueprintJson: typeof data.blueprintJson === "string" ? data.blueprintJson : JSON.stringify(data.blueprintJson ?? data.blueprints, null, 2),
				requiredFieldsJson: typeof data.requiredFieldsJson === "string" ? data.requiredFieldsJson : JSON.stringify(data.requiredFieldsJson ?? data.requiredFields, null, 2),
			});
			setModalOpen(true);
		} catch {
			// interceptor handles error
		}
	};

	const handleSave = async () => {
		try {
			const values = await form.validateFields();
			if (editing) {
				await updateIndicatorTemplate(editing.id, values);
				toast.success("模板已更新");
			} else {
				await createIndicatorTemplate(values);
				toast.success("模板已创建");
			}
			setModalOpen(false);
			fetchList();
		} catch {
			// validation or api error
		}
	};

	const handleDelete = (record: Template) => {
		Modal.confirm({
			title: "确认删除",
			content: `确定要删除模板「${record.name}」吗？`,
			okText: "删除",
			okType: "danger",
			cancelText: "取消",
			onOk: async () => {
				try {
					await deleteIndicatorTemplate(record.id);
					toast.success("模板已删除");
					fetchList();
				} catch {
					// interceptor handles error
				}
			},
		});
	};

	const openWizard = (record: Template) => {
		setWizardTemplate(record);
		setWizardOpen(true);
	};

	const columns: ColumnsType<Template> = [
		{
			title: "编码",
			dataIndex: "code",
			width: 160,
			ellipsis: true,
		},
		{
			title: "名称",
			dataIndex: "name",
			width: 200,
			ellipsis: true,
		},
		{
			title: "领域",
			dataIndex: "domain",
			width: 100,
			render: (v: string) => v ? <Tag color={DOMAIN_COLORS[v] ?? "default"}>{v}</Tag> : "-",
		},
		{
			title: "蓝图数量",
			dataIndex: "blueprintCount",
			width: 100,
			align: "center",
			render: (_: any, record: Template) => {
				try {
					const bp = typeof record.blueprintJson === "string" ? JSON.parse(record.blueprintJson) : (record.blueprintJson ?? record.blueprints);
					return Array.isArray(bp) ? bp.length : "-";
				} catch {
					return "-";
				}
			},
		},
		{
			title: "内置",
			dataIndex: "builtIn",
			width: 80,
			align: "center",
			render: (v: boolean) => v ? <Tag color="geekblue">内置</Tag> : null,
		},
		{
			title: "操作",
			key: "action",
			width: 200,
			render: (_: any, record: Template) => (
				<Space size="small">
					<Tooltip title="展开生成指标">
						<Button
							type="link"
							size="small"
							icon={<RocketOutlined />}
							onClick={() => openWizard(record)}
						>
							展开
						</Button>
					</Tooltip>
					{canManage && (
						<Button
							type="link"
							size="small"
							icon={<EditOutlined />}
							onClick={() => openEdit(record)}
						>
							编辑
						</Button>
					)}
					{canManage && !record.builtIn && (
						<Button
							type="link"
							size="small"
							danger
							icon={<DeleteOutlined />}
							onClick={() => handleDelete(record)}
						>
							删除
						</Button>
					)}
				</Space>
			),
		},
	];

	return (
		<div style={{ padding: 24 }}>
			<div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
				<Typography.Title level={4} style={{ margin: 0 }}>指标模板管理</Typography.Title>
				{canManage && (
					<Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
						新建模板
					</Button>
				)}
			</div>

			<Tabs
				activeKey={domain}
				onChange={setDomain}
				items={DOMAIN_TABS.map((t) => ({ key: t.key, label: t.label }))}
				style={{ marginBottom: 16 }}
			/>

			<Table
				rowKey="id"
				columns={columns}
				dataSource={templates}
				loading={loading}
				pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
				size="middle"
			/>

			{/* CRUD Modal */}
			<Modal
				title={editing ? "编辑模板" : "新建模板"}
				open={modalOpen}
				onOk={handleSave}
				onCancel={() => setModalOpen(false)}
				width={640}
				okText="保存"
				cancelText="取消"
				destroyOnClose
			>
				<Form form={form} layout="vertical" preserve={false}>
					<Form.Item name="code" label="编码" rules={[{ required: true, message: "请输入编码" }]}>
						<Input placeholder="如 TPL_FINANCE_001" disabled={!!editing?.builtIn} />
					</Form.Item>
					<Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
						<Input placeholder="模板名称" />
					</Form.Item>
					<Form.Item name="domain" label="领域">
						<Input placeholder="如 FINANCE / OPERATION / QUALITY" />
					</Form.Item>
					<Form.Item name="description" label="描述">
						<Input.TextArea rows={2} placeholder="模板描述" />
					</Form.Item>
					<Form.Item name="blueprintJson" label="蓝图 JSON" rules={[{ required: true, message: "请输入蓝图定义" }]}>
						<Input.TextArea rows={8} placeholder='[{"code":"...", "name":"...", "aggregation":"SUM", ...}]' />
					</Form.Item>
					<Form.Item name="requiredFieldsJson" label="所需字段 JSON">
						<Input.TextArea rows={4} placeholder='["amount", "date", "category"]' />
					</Form.Item>
				</Form>
			</Modal>

			{/* Wizard Drawer */}
			<IndicatorWizard
				open={wizardOpen}
				template={wizardTemplate}
				onClose={() => setWizardOpen(false)}
				onSuccess={() => {
					setWizardOpen(false);
					toast.success("指标已生成");
				}}
			/>
		</div>
	);
}
