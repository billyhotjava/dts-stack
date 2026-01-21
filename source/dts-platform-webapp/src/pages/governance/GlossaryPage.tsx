import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Button, Card, Form, Input, Modal, Space, Table, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import {
	createGlossaryTerm,
	deleteGlossaryTerm,
	listGlossaryTerms,
	updateGlossaryTerm,
} from "@/api/platformApi";

const { Text } = Typography;

const normalizeText = (value?: string) => String(value || "").trim();

type GlossaryTerm = {
	id?: string;
	name?: string;
	code?: string;
	aliases?: string;
	definition?: string;
	domain?: string;
	owner?: string;
	tags?: string;
};

export default function GlossaryPage() {
	const [keyword, setKeyword] = useState("");
	const [items, setItems] = useState<GlossaryTerm[]>([]);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [editing, setEditing] = useState<GlossaryTerm | null>(null);
	const [form] = Form.useForm();

	const loadGlossary = useCallback(async () => {
		setLoading(true);
		try {
			const resp = (await listGlossaryTerms({
				keyword: normalizeText(keyword) || undefined,
			})) as GlossaryTerm[];
			setItems(Array.isArray(resp) ? resp : []);
		} catch (err: any) {
			toast.error(err?.message || "加载术语失败");
		} finally {
			setLoading(false);
		}
	}, [keyword]);

	useEffect(() => {
		void loadGlossary();
	}, [loadGlossary]);

	const openModal = (row?: GlossaryTerm) => {
		setEditing(row || null);
		form.resetFields();
		form.setFieldsValue({
			name: row?.name,
			code: row?.code,
			aliases: row?.aliases,
			definition: row?.definition,
			domain: row?.domain,
			owner: row?.owner,
			tags: row?.tags,
		});
		setModalOpen(true);
	};

	const submit = async () => {
		setSaving(true);
		try {
			const values = await form.validateFields(["name"]);
			const payload = {
				name: normalizeText(values.name),
				code: normalizeText(form.getFieldValue("code")) || undefined,
				aliases: normalizeText(form.getFieldValue("aliases")) || undefined,
				definition: normalizeText(form.getFieldValue("definition")) || undefined,
				domain: normalizeText(form.getFieldValue("domain")) || undefined,
				owner: normalizeText(form.getFieldValue("owner")) || undefined,
				tags: normalizeText(form.getFieldValue("tags")) || undefined,
			};
			if (editing?.id) {
				await updateGlossaryTerm(editing.id, payload);
				toast.success("术语已更新");
			} else {
				await createGlossaryTerm(payload);
				toast.success("术语已创建");
			}
			setModalOpen(false);
			setEditing(null);
			await loadGlossary();
		} catch (err: any) {
			toast.error(err?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	};

	const removeGlossary = (row: GlossaryTerm) => {
		if (!row?.id) return;
		Modal.confirm({
			title: "删除术语？",
			content: "删除后无法恢复。",
			okText: "删除",
			cancelText: "取消",
			onOk: async () => {
				try {
					await deleteGlossaryTerm(row.id as string);
					toast.success("术语已删除");
					await loadGlossary();
				} catch (err: any) {
					toast.error(err?.message || "删除失败");
				}
			},
		});
	};

	const columns: ColumnsType<GlossaryTerm> = [
		{ title: "术语名称", dataIndex: "name", render: (t) => <Text strong className="text-blue-600">{t}</Text> },
		{ title: "标准编码", dataIndex: "code", render: (c) => <Text className="font-mono text-xs">{c || "-"}</Text> },
		{ title: "口径定义", dataIndex: "definition", ellipsis: true, render: (t) => t || "-" },
		{ title: "主题域", dataIndex: "domain", render: (t) => t || "-" },
		{ title: "负责人", dataIndex: "owner", render: (t) => t || "-" },
		{
			title: "操作",
			render: (_, row) => (
				<Space>
					<Button type="link" size="small" onClick={() => openModal(row)}>
						编辑
					</Button>
					<Button type="link" size="small" danger onClick={() => removeGlossary(row)}>
						删除
					</Button>
				</Space>
			),
		},
	];

	return (
		<div className="space-y-4">
			<PageHeader
				title="数据治理中心 · 标准管理 / 业务术语"
				description="维护业务术语口径与责任人，确保业务语义一致。"
				actions={
					<Space>
						<Button disabled>同步至 OpenMetadata</Button>
						<Button type="primary" onClick={() => openModal()}>
							+ 新增术语
						</Button>
					</Space>
				}
			/>

			<Card>
				<Space className="mb-4">
					<Input.Search
						placeholder="搜索术语名称..."
						style={{ width: 300 }}
						value={keyword}
						onChange={(e) => setKeyword(e.target.value)}
						onSearch={loadGlossary}
						allowClear
					/>
				</Space>
				{items.length === 0 && !loading ? (
					<EmptyState title="暂无术语" description="请先新增业务术语。" />
				) : (
					<Table
						rowKey={(row) => row.id || row.code || row.name || Math.random().toString(36)}
						dataSource={items}
						columns={columns}
						loading={loading}
						pagination={{ pageSize: 10 }}
					/>
				)}
			</Card>

			<Modal
				open={modalOpen}
				title={editing ? "编辑术语" : "新增术语"}
				onCancel={() => setModalOpen(false)}
				onOk={submit}
				okText="保存"
				cancelText="取消"
				confirmLoading={saving}
			>
				<Form layout="vertical" form={form}>
					<Form.Item name="name" label="术语名称" rules={[{ required: true, message: "请输入术语名称" }]}>
						<Input placeholder="例如：订单总额" />
					</Form.Item>
					<Form.Item name="code" label="标准编码">
						<Input placeholder="ORDER_AMT" />
					</Form.Item>
					<Form.Item name="definition" label="口径定义">
						<Input.TextArea rows={3} placeholder="说明业务口径" />
					</Form.Item>
					<Form.Item name="aliases" label="别名">
						<Input placeholder="可用逗号分隔" />
					</Form.Item>
					<Form.Item name="domain" label="主题域">
						<Input placeholder="交易域" />
					</Form.Item>
					<Form.Item name="owner" label="负责人">
						<Input placeholder="责任人" />
					</Form.Item>
					<Form.Item name="tags" label="标签">
						<Input placeholder="标签，逗号分隔" />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
