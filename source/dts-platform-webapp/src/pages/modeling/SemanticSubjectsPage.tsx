import { useCallback, useEffect, useState } from "react";
import { Button, Form, Input, Modal, Tag } from "antd";
import { toast } from "sonner";
import { CompactTable } from "@/components/table";
import { PageHeader } from "@/components/page-header";
import type { ColumnsType } from "antd/es/table";
import {
	listSemanticSubjectDomains,
	createSemanticSubjectDomain,
	updateSemanticSubjectDomain,
	type SemanticSubjectDomain,
} from "@/api/semanticModelingApi";

const columns = (onEdit: (row: SemanticSubjectDomain) => void): ColumnsType<SemanticSubjectDomain> => [
	{ title: "编码", dataIndex: "code", key: "code", width: 120 },
	{ title: "名称", dataIndex: "name", key: "name" },
	{ title: "描述", dataIndex: "description", key: "description", ellipsis: true },
	{
		title: "治理域", dataIndex: "governanceDomainName", key: "governanceDomainName",
		render: (v?: string) => v ? <Tag>{v}</Tag> : <span style={{ color: "#aaa" }}>未映射</span>,
	},
	{
		title: "操作", key: "actions", width: 80,
		render: (_: unknown, row: SemanticSubjectDomain) => (
			<Button type="link" size="small" onClick={() => onEdit(row)}>编辑</Button>
		),
	},
];

export default function SemanticSubjectsPage() {
	const [domains, setDomains] = useState<SemanticSubjectDomain[]>([]);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [editRow, setEditRow] = useState<SemanticSubjectDomain | null>(null);
	const [form] = Form.useForm();

	const load = useCallback(async () => {
		setLoading(true);
		try {
			const list = await listSemanticSubjectDomains();
			setDomains(Array.isArray(list) ? (list as SemanticSubjectDomain[]) : []);
		} catch {
			/* global interceptor */
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => { void load(); }, [load]);

	const handleOpen = (row?: SemanticSubjectDomain) => {
		setEditRow(row ?? null);
		form.setFieldsValue(row ?? { code: "", name: "", description: "" });
		setModalOpen(true);
	};

	const handleSubmit = async () => {
		try {
			const values = await form.validateFields();
			if (editRow) {
				await updateSemanticSubjectDomain(editRow.id, values);
				toast.success("主题域已更新");
			} else {
				await createSemanticSubjectDomain(values);
				toast.success("主题域已创建");
			}
			setModalOpen(false);
			void load();
		} catch (err: unknown) {
			if (err && typeof err === "object" && "errorFields" in err) return;
			/* global interceptor */
		}
	};

	return (
		<div className="space-y-4" data-testid="semantic-subjects-page">
			<PageHeader
				title="语义建模 · 主题域"
				actions={
					<Button
						type="primary"
						data-testid="semantic-subjects-create"
						onClick={() => handleOpen()}
					>
						+ 新建主题域
					</Button>
				}
			/>
			<CompactTable<SemanticSubjectDomain>
				rowKey="id"
				columns={columns(handleOpen)}
				dataSource={domains}
				loading={loading}
			/>
			<Modal
				title={editRow ? "编辑主题域" : "新建主题域"}
				open={modalOpen}
				onCancel={() => setModalOpen(false)}
				onOk={handleSubmit}
				destroyOnClose
			>
				<Form form={form} layout="vertical" className="pt-4">
					<Form.Item name="code" label="编码" rules={[{ required: true, message: "必填" }]}>
						<Input placeholder="SALES" disabled={!!editRow} />
					</Form.Item>
					<Form.Item name="name" label="名称" rules={[{ required: true, message: "必填" }]}>
						<Input placeholder="销售域" />
					</Form.Item>
					<Form.Item name="description" label="描述">
						<Input.TextArea rows={2} />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
