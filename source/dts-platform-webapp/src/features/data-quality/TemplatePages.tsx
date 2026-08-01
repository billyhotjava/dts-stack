import { DeleteOutlined, PlusOutlined, ReloadOutlined } from "@ant-design/icons";
import { Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { toast } from "sonner";
import {
	createQualityTemplate,
	deleteQualityTemplate,
	listQualityTemplates,
	previewTemplateSQL,
	updateQualityTemplate,
} from "@/api/platformApi";
import { ManagePermissionHint, QualityEmpty, QualityPageHeading } from "./QualityShared";
import { qualityPath } from "./qualityRoutes";
import { displayName, type QualityTemplate, toList } from "./qualityTypes";
import { useQualityMaintainerAccess } from "./useQualityAccess";

type TemplateForm = {
	name: string;
	code: string;
	description?: string;
	category?: string;
	severityDefault?: string;
	actionDefault?: string;
	sqlTemplate: string;
	paramSchema?: string;
};

const initialValues: Partial<TemplateForm> = {
	category: "CUSTOM",
	severityDefault: "MEDIUM",
	actionDefault: "WARN",
	paramSchema: "[]",
};

function templatePayload(values: TemplateForm) {
	let paramSchema: unknown;
	if (values.paramSchema?.trim()) {
		paramSchema = JSON.parse(values.paramSchema);
		if (!Array.isArray(paramSchema)) throw new Error("参数定义必须是 JSON 数组");
	}
	return {
		...values,
		name: values.name.trim(),
		code: values.code.trim(),
		description: values.description?.trim() || undefined,
		sqlTemplate: values.sqlTemplate.trim(),
		paramSchema,
	};
}

export function TemplateListPage() {
	const navigate = useNavigate();
	const canManage = useQualityMaintainerAccess();
	const [templates, setTemplates] = useState<QualityTemplate[]>([]);
	const [loading, setLoading] = useState(true);
	const [open, setOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [form] = Form.useForm<TemplateForm>();

	const load = useCallback(async () => {
		setLoading(true);
		try {
			setTemplates(toList<QualityTemplate>(await listQualityTemplates()));
		} catch (error) {
			toast.error(error instanceof Error ? error.message : "规则模板加载失败");
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		void load();
	}, [load]);

	const save = async () => {
		try {
			const values = await form.validateFields();
			setSaving(true);
			await createQualityTemplate(templatePayload(values));
			toast.success("模板已创建");
			setOpen(false);
			form.resetFields();
			await load();
		} catch (error) {
			if ((error as { errorFields?: unknown })?.errorFields) return;
			toast.error(
				error instanceof SyntaxError
					? "参数定义必须是有效 JSON"
					: error instanceof Error
						? error.message
						: "模板保存失败",
			);
		} finally {
			setSaving(false);
		}
	};

	const remove = async (id: string) => {
		try {
			await deleteQualityTemplate(id);
			toast.success("模板已删除");
			await load();
		} catch (error) {
			toast.error(error instanceof Error ? error.message : "模板删除失败");
		}
	};

	const columns: ColumnsType<QualityTemplate> = [
		{
			title: "模板名称",
			dataIndex: "name",
			render: (value, row) => (
				<Button type="link" onClick={() => navigate(qualityPath("template-detail", { templateId: row.id }))}>
					{displayName(value)}
				</Button>
			),
		},
		{ title: "编码", dataIndex: "code", width: 180, ellipsis: true },
		{
			title: "分类",
			dataIndex: "category",
			width: 130,
			render: (value) => <Tag color="blue">{displayName(value)}</Tag>,
		},
		{ title: "默认严重性", dataIndex: "severityDefault", width: 130 },
		{ title: "默认策略", dataIndex: "actionDefault", width: 120 },
		{ title: "说明", dataIndex: "description", ellipsis: true },
		{
			title: "操作",
			width: 100,
			render: (_, row) =>
				row.builtin ? (
					<Tag>内置模板</Tag>
				) : (
					<Popconfirm title="确认删除该模板？" disabled={!canManage} onConfirm={() => void remove(row.id)}>
						<Button size="small" danger disabled={!canManage} icon={<DeleteOutlined />} />
					</Popconfirm>
				),
		},
	];

	return (
		<div className="dq-page">
			<QualityPageHeading
				title="规则模板库"
				description="沉淀可复用 SQL 检查模板，通过参数化预览验证后再生成质量规则。"
				actions={[
					<ManagePermissionHint key="permission" canManage={canManage} />,
					<Button key="reload" icon={<ReloadOutlined />} onClick={() => void load()}>
						刷新
					</Button>,
					<Button key="new" type="primary" icon={<PlusOutlined />} disabled={!canManage} onClick={() => setOpen(true)}>
						新建模板
					</Button>,
				]}
			/>
			<Table
				rowKey="id"
				loading={loading}
				columns={columns}
				dataSource={templates}
				pagination={{ pageSize: 10 }}
				size="small"
			/>
			<Modal
				title="新建质量规则模板"
				open={open}
				width={780}
				okText="创建模板"
				confirmLoading={saving}
				onOk={() => void save()}
				onCancel={() => setOpen(false)}
				destroyOnClose
			>
				<TemplateFormBody form={form} />
			</Modal>
		</div>
	);
}

function TemplateFormBody({ form }: { form: ReturnType<typeof Form.useForm<TemplateForm>>[0] }) {
	return (
		<Form form={form} layout="vertical" initialValues={initialValues}>
			<Space align="start" size={16} style={{ width: "100%" }}>
				<Form.Item name="name" label="模板名称" rules={[{ required: true }]} style={{ width: 320 }}>
					<Input />
				</Form.Item>
				<Form.Item name="code" label="模板编码" rules={[{ required: true }]} style={{ width: 260 }}>
					<Input />
				</Form.Item>
			</Space>
			<Space align="start" size={16} wrap>
				<Form.Item
					name="category"
					label="分类"
					rules={[{ required: true, message: "请输入模板分类" }]}
					style={{ width: 180 }}
				>
					<Input placeholder="完整性 / 唯一性" />
				</Form.Item>
				<Form.Item name="severityDefault" label="默认严重性" style={{ width: 180 }}>
					<Select options={["LOW", "MEDIUM", "HIGH", "CRITICAL"].map((value) => ({ value, label: value }))} />
				</Form.Item>
				<Form.Item name="actionDefault" label="默认失败策略" style={{ width: 180 }}>
					<Select
						options={[
							{ value: "WARN", label: "告警" },
							{ value: "BLOCK", label: "阻断" },
						]}
					/>
				</Form.Item>
			</Space>
			<Form.Item name="description" label="说明">
				<Input.TextArea rows={2} />
			</Form.Item>
			<Form.Item name="sqlTemplate" label="SQL 模板" rules={[{ required: true }]}>
				<Input.TextArea
					rows={7}
					className="dq-code-block"
					placeholder="SELECT * FROM {{table}} WHERE {{column}} IS NULL"
				/>
			</Form.Item>
			<Form.Item name="paramSchema" label="参数定义（JSON）">
				<Input.TextArea rows={6} className="dq-code-block" />
			</Form.Item>
		</Form>
	);
}

export function TemplateDetailPage() {
	const { templateId = "" } = useParams();
	const navigate = useNavigate();
	const canManage = useQualityMaintainerAccess();
	const [template, setTemplate] = useState<QualityTemplate>();
	const [loading, setLoading] = useState(true);
	const [editing, setEditing] = useState(false);
	const [saving, setSaving] = useState(false);
	const [previewParams, setPreviewParams] = useState("{}");
	const [previewSql, setPreviewSql] = useState("");
	const [form] = Form.useForm<TemplateForm>();

	const load = useCallback(async () => {
		setLoading(true);
		try {
			const next = toList<QualityTemplate>(await listQualityTemplates()).find((item) => String(item.id) === templateId);
			setTemplate(next);
			if (next)
				form.setFieldsValue({
					name: next.name || "",
					code: next.code || "",
					description: next.description,
					category: next.category,
					severityDefault: next.severityDefault || "MEDIUM",
					actionDefault: next.actionDefault || "WARN",
					sqlTemplate: next.sqlTemplate || "",
					paramSchema:
						typeof next.paramSchema === "string"
							? next.paramSchema
							: JSON.stringify(next.paramSchema || { params: [] }, null, 2),
				});
		} catch (error) {
			toast.error(error instanceof Error ? error.message : "模板详情加载失败");
		} finally {
			setLoading(false);
		}
	}, [form, templateId]);

	useEffect(() => {
		void load();
	}, [load]);

	const save = async () => {
		try {
			const values = await form.validateFields();
			setSaving(true);
			await updateQualityTemplate(templateId, templatePayload(values));
			toast.success("模板已更新");
			setEditing(false);
			await load();
		} catch (error) {
			if ((error as { errorFields?: unknown })?.errorFields) return;
			toast.error(
				error instanceof SyntaxError
					? "参数定义必须是有效 JSON"
					: error instanceof Error
						? error.message
						: "模板更新失败",
			);
		} finally {
			setSaving(false);
		}
	};

	const preview = async () => {
		try {
			const result = await previewTemplateSQL(templateId, JSON.parse(previewParams || "{}"));
			setPreviewSql(
				typeof result === "string"
					? result
					: String((result as { sql?: string })?.sql || JSON.stringify(result, null, 2)),
			);
		} catch (error) {
			toast.error(
				error instanceof SyntaxError
					? "预览参数必须是有效 JSON"
					: error instanceof Error
						? error.message
						: "SQL 预览失败",
			);
		}
	};

	if (!loading && !template) return <QualityEmpty description="未找到该规则模板。" />;
	return (
		<div className="dq-page">
			<QualityPageHeading
				title={template?.name || "模板详情"}
				description="维护模板 SQL 与参数结构，并使用真实后端模板渲染接口进行预览。"
				actions={[
					<Button key="back" onClick={() => navigate(qualityPath("rule-template"))}>
						返回模板库
					</Button>,
					<Button key="edit" type="primary" disabled={!canManage} onClick={() => setEditing((value) => !value)}>
						{editing ? "退出编辑" : "编辑模板"}
					</Button>,
				]}
			/>
			<Card loading={loading} title="模板定义">
				{editing ? (
					<>
						<TemplateFormBody form={form} />
						<Button type="primary" loading={saving} disabled={!canManage} onClick={() => void save()}>
							保存模板
						</Button>
					</>
				) : (
					<Space direction="vertical" size={14} style={{ width: "100%" }}>
						<div>
							<Tag color="blue">{displayName(template?.category)}</Tag>
							<Tag>{displayName(template?.code)}</Tag>
							<Tag>{displayName(template?.severityDefault)}</Tag>
						</div>
						<div>{displayName(template?.description, "暂无说明")}</div>
						<pre className="dq-code-block">{displayName(template?.sqlTemplate)}</pre>
					</Space>
				)}
			</Card>
			<Card title="SQL 渲染预览">
				<Space direction="vertical" size={12} style={{ width: "100%" }}>
					<Input.TextArea
						rows={4}
						value={previewParams}
						onChange={(event) => setPreviewParams(event.target.value)}
						className="dq-code-block"
					/>
					<Button type="primary" disabled={!canManage} onClick={() => void preview()}>
						调用后端预览
					</Button>
					{previewSql ? <pre className="dq-code-block">{previewSql}</pre> : null}
				</Space>
			</Card>
		</div>
	);
}
