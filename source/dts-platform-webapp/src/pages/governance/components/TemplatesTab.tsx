import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Button, Form, Input, Modal, Select, Space, Tag } from "antd";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { } from "@ant-design/icons";
import {
	listQualityTemplates,
	createQualityTemplate,
	updateQualityTemplate,
	deleteQualityTemplate,
	previewTemplateSQL,
} from "@/api/platformApi";

type Template = {
	id?: string;
	code?: string;
	name?: string;
	category?: string;
	severityDefault?: string;
	actionDefault?: string;
	builtin?: boolean;
	enabled?: boolean;
	sqlTemplate?: string;
	paramSchema?: string;
};

type TemplateForm = {
	code?: string;
	name?: string;
	category?: string;
	severityDefault?: string;
	actionDefault?: string;
	sqlTemplate?: string;
	paramSchema?: string;
};

const CATEGORY_OPTIONS = [
	{ label: "完整性", value: "COMPLETENESS" },
	{ label: "一致性", value: "CONSISTENCY" },
	{ label: "准确性", value: "ACCURACY" },
	{ label: "唯一性", value: "UNIQUENESS" },
	{ label: "及时性", value: "TIMELINESS" },
];

const SEVERITY_OPTIONS = [
	{ label: "低", value: "LOW" },
	{ label: "中", value: "MEDIUM" },
	{ label: "高", value: "HIGH" },
	{ label: "致命", value: "CRITICAL" },
];

const ACTION_OPTIONS = [
	{ label: "告警", value: "WARN" },
	{ label: "阻断", value: "BLOCK" },
];

export default function TemplatesTab({ canManage }: { canManage: boolean }) {
	const [templates, setTemplates] = useState<Template[]>([]);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [editing, setEditing] = useState<Template | null>(null);
	const [previewSql, setPreviewSql] = useState<string>("");
	const [previewOpen, setPreviewOpen] = useState(false);
	const [form] = Form.useForm<TemplateForm>();

	const load = async () => {
		setLoading(true);
		try {
			const list = await listQualityTemplates();
			setTemplates(Array.isArray(list) ? (list as Template[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "模板加载失败");
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void load();
	}, []);

	const openModal = (tpl?: Template) => {
		setEditing(tpl || null);
		form.setFieldsValue({
			code: tpl?.code || "",
			name: tpl?.name || "",
			category: tpl?.category || "COMPLETENESS",
			severityDefault: tpl?.severityDefault || "MEDIUM",
			actionDefault: tpl?.actionDefault || "WARN",
			sqlTemplate: tpl?.sqlTemplate || "",
			paramSchema: tpl?.paramSchema || "",
		});
		setModalOpen(true);
	};

	const save = async () => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		try {
			const values = await form.validateFields();
			const payload = { ...values };
			if (editing?.id) {
				await updateQualityTemplate(editing.id, payload);
				toast.success("模板已更新");
			} else {
				await createQualityTemplate(payload);
				toast.success("模板已新增");
			}
			setModalOpen(false);
			await load();
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "保存失败");
		}
	};

	const remove = async (id?: string) => {
		if (!canManage || !id) return;
		try {
			await deleteQualityTemplate(id);
			toast.success("模板已删除");
			await load();
		} catch (error: any) {
			toast.error(error?.message || "删除失败");
		}
	};

	const preview = async (tpl: Template) => {
		if (!tpl?.id) return;
		try {
			const result: any = await previewTemplateSQL(tpl.id, {});
			setPreviewSql(typeof result === "string" ? result : (result?.sql || JSON.stringify(result, null, 2)));
			setPreviewOpen(true);
		} catch (error: any) {
			toast.error(error?.message || "预览失败");
		}
	};

	const columns: ColumnsType<Template> = [
		{ title: "编码", dataIndex: "code", width: 160, render: (v) => v || "-" , sorter: (a, b) => (a.code || "").localeCompare(b.code || "") },
		{ title: "名称", dataIndex: "name", render: (v) => v || "-" , sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
		{ title: "分类", dataIndex: "category", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "默认严重性", dataIndex: "severityDefault", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{
			title: "默认策略",
			dataIndex: "actionDefault",
			width: 100,
			render: (v) => (
				<Tag color={v === "BLOCK" ? "red" : "blue"}>
					{v === "BLOCK" ? "阻断" : "告警"}
				</Tag>
			),
		},
		{
			title: "内置",
			dataIndex: "builtin",
			width: 80,
			render: (v) => (v ? <Tag color="purple">内置</Tag> : <Tag>自定义</Tag>),
		},
		{
			title: "操作",
			width: 220,
			render: (_, record) => (
				<Space>
					<Button size="small" onClick={() => preview(record)}>
						预览SQL
					</Button>
					<Button size="small" onClick={() => openModal(record)} disabled={!canManage}>
						编辑
					</Button>
					<Button
						size="small"
						danger
						onClick={() => remove(record.id)}
						disabled={!canManage || !!record.builtin}
					>
						删除
					</Button>
				</Space>
			),
		},
	];

	return (
		<>
			<div className="mb-3 flex justify-end">
				<Button type="primary" onClick={() => openModal()} disabled={!canManage}>
					新增模板
				</Button>
			</div>
			<CompactTable
				rowKey={(r) => r.id || r.code || Math.random().toString(36)}
				columns={columns}
				dataSource={templates}
				loading={loading}
				pagination={{ showSizeChanger: true }}
			/>

			<Modal
				open={modalOpen}
				title={editing ? "编辑模板" : "新增模板"}
				onCancel={() => setModalOpen(false)}
				onOk={save}
				okText="保存"
				destroyOnClose
				width={720}
			>
				<Form form={form} layout="vertical">
					<Form.Item label="模板名称" name="name" rules={[{ required: true, message: "请输入模板名称" }]}>
						<Input placeholder="例如：非空检查模板" />
					</Form.Item>
					<Form.Item label="模板编码" name="code">
						<Input placeholder="可选，如 TPL_NOT_NULL" />
					</Form.Item>
					<Form.Item label="分类" name="category">
						<Select options={CATEGORY_OPTIONS} />
					</Form.Item>
					<Form.Item label="默认严重性" name="severityDefault">
						<Select options={SEVERITY_OPTIONS} />
					</Form.Item>
					<Form.Item label="默认策略" name="actionDefault">
						<Select options={ACTION_OPTIONS} />
					</Form.Item>
					<Form.Item label="SQL 模板" name="sqlTemplate">
						<Input.TextArea rows={6} placeholder="SELECT ... WHERE {{column}} IS NULL" />
					</Form.Item>
					<Form.Item label="参数定义 (JSON)" name="paramSchema">
						<Input.TextArea rows={4} placeholder='[{"name":"column","type":"string","required":true}]' />
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={previewOpen}
				title="SQL 预览"
				onCancel={() => setPreviewOpen(false)}
				footer={null}
				width={720}
			>
				<pre style={{ whiteSpace: "pre-wrap", margin: 0, maxHeight: 400, overflow: "auto" }}>
					{previewSql || "（空）"}
				</pre>
			</Modal>
		</>
	);
}
