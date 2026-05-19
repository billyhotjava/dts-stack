import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Button, Form, Input, InputNumber, Modal, Space, Tag } from "antd";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { } from "@ant-design/icons";
import {
	listCleansingFunctions,
	createCleansingFunction,
	updateCleansingFunction,
	deleteCleansingFunction,
} from "@/api/platformApi";

type CleansingFn = {
	id?: string;
	code?: string;
	name?: string;
	sqlExpression?: string;
	displayOrder?: number;
	builtin?: boolean;
	description?: string;
};

type CleansingForm = {
	code?: string;
	name?: string;
	sqlExpression?: string;
	displayOrder?: number;
	description?: string;
};

export default function CleansingTab({ canManage }: { canManage: boolean }) {
	const [items, setItems] = useState<CleansingFn[]>([]);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [editing, setEditing] = useState<CleansingFn | null>(null);
	const [form] = Form.useForm<CleansingForm>();

	const load = async () => {
		setLoading(true);
		try {
			const list = await listCleansingFunctions();
			setItems(Array.isArray(list) ? (list as CleansingFn[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "清洗函数加载失败");
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void load();
	}, []);

	const openModal = (item?: CleansingFn) => {
		setEditing(item || null);
		form.setFieldsValue({
			code: item?.code || "",
			name: item?.name || "",
			sqlExpression: item?.sqlExpression || "",
			displayOrder: item?.displayOrder ?? 0,
			description: item?.description || "",
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
			if (editing?.id) {
				await updateCleansingFunction(editing.id, values);
				toast.success("清洗函数已更新");
			} else {
				await createCleansingFunction(values);
				toast.success("清洗函数已新增");
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
			await deleteCleansingFunction(id);
			toast.success("清洗函数已删除");
			await load();
		} catch (error: any) {
			toast.error(error?.message || "删除失败");
		}
	};

	const columns: ColumnsType<CleansingFn> = [
		{ title: "编码", dataIndex: "code", width: 160, render: (v) => v || "-" , sorter: (a, b) => (a.code || "").localeCompare(b.code || "") },
		{ title: "名称", dataIndex: "name", render: (v) => v || "-" , sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
		{ title: "SQL 表达式", dataIndex: "sqlExpression", ellipsis: true, render: (v) => v || "-" },
		{ title: "执行顺序", dataIndex: "displayOrder", width: 100, render: (v) => v ?? "-" },
		{
			title: "内置",
			dataIndex: "builtin",
			width: 80,
			render: (v) => (v ? <Tag color="purple">内置</Tag> : <Tag>自定义</Tag>),
		},
		{
			title: "操作",
			width: 160,
			render: (_, record) => (
				<Space>
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
					新增清洗函数
				</Button>
			</div>
			<CompactTable
				rowKey={(r) => r.id || r.code || Math.random().toString(36)}
				columns={columns}
				dataSource={items}
				loading={loading}
				pagination={{ showSizeChanger: true }}
			/>

			<Modal
				open={modalOpen}
				title={editing ? "编辑清洗函数" : "新增清洗函数"}
				onCancel={() => setModalOpen(false)}
				onOk={save}
				okText="保存"
				destroyOnClose
				width={680}
			>
				<Form form={form} layout="vertical">
					<Form.Item label="函数名称" name="name" rules={[{ required: true, message: "请输入函数名称" }]}>
						<Input placeholder="例如：去除前后空格" />
					</Form.Item>
					<Form.Item label="函数编码" name="code">
						<Input placeholder="可选，如 FN_TRIM" />
					</Form.Item>
					<Form.Item label="SQL 表达式" name="sqlExpression" rules={[{ required: true, message: "请输入 SQL 表达式" }]}>
						<Input.TextArea rows={4} placeholder="TRIM({{column}})" />
					</Form.Item>
					<Form.Item label="执行顺序" name="displayOrder">
						<InputNumber min={0} style={{ width: "100%" }} />
					</Form.Item>
					<Form.Item label="说明" name="description">
						<Input.TextArea rows={2} placeholder="可选描述" />
					</Form.Item>
				</Form>
			</Modal>
		</>
	);
}
