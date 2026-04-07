import { useEffect, useState } from "react";
import { Button, Card, Form, Input, Modal, Space, Tag, Typography } from "antd";
import { PlusOutlined, DeleteOutlined, EditOutlined } from "@ant-design/icons";
import { toast } from "sonner";
import { EmptyState } from "@/components/empty-state";
import {
	type DataProduct,
	createDataProduct,
	deleteDataProduct,
	listDataProducts,
	updateDataProduct,
} from "@/api/platformApi";

const { Text } = Typography;

const STATUS_CONFIG = {
	DRAFT: { label: "草稿", color: "default" },
	PUBLISHED: { label: "已发布", color: "green" },
	OFFLINE: { label: "已下线", color: "orange" },
} as const;

export default function DataProductsPage() {
	const [products, setProducts] = useState<DataProduct[]>([]);
	const [total, setTotal] = useState(0);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [editTarget, setEditTarget] = useState<DataProduct | null>(null);
	const [saving, setSaving] = useState(false);
	const [form] = Form.useForm();

	useEffect(() => {
		void load();
	}, []);

	const load = async () => {
		setLoading(true);
		try {
			const resp: any = await listDataProducts(0, 50);
			const content = Array.isArray(resp?.content) ? resp.content : [];
			setProducts(content);
			setTotal(Number(resp?.total ?? content.length));
		} catch {
			// global interceptor
		} finally {
			setLoading(false);
		}
	};

	const openCreate = () => {
		setEditTarget(null);
		form.resetFields();
		setModalOpen(true);
	};

	const openEdit = (product: DataProduct) => {
		setEditTarget(product);
		form.setFieldsValue({
			name: product.name,
			code: product.code,
			ownerDept: product.ownerDept,
			description: product.description,
			status: product.status ?? "DRAFT",
		});
		setModalOpen(true);
	};

	const handleSave = async () => {
		const values = await form.validateFields();
		setSaving(true);
		try {
			if (editTarget?.id) {
				await updateDataProduct(editTarget.id, values);
				toast.success("数据产品已更新");
			} else {
				await createDataProduct(values);
				toast.success("数据产品已创建");
			}
			setModalOpen(false);
			void load();
		} catch {
			// global interceptor
		} finally {
			setSaving(false);
		}
	};

	const handleDelete = (product: DataProduct) => {
		if (!product.id) return;
		Modal.confirm({
			title: "确认删除",
			content: `确定要删除数据产品「${product.name || product.code || ""}」吗？此操作不可撤销。`,
			okText: "删除",
			okType: "danger",
			cancelText: "取消",
			onOk: async () => {
				try {
					await deleteDataProduct(product.id!);
					toast.success("已删除");
					void load();
				} catch {
					// global interceptor
				}
			},
		});
	};

	return (
		<div className="space-y-4">
			<Card
				title={`数据产品（${total}）`}
				extra={
					<Button
						type="primary"
						icon={<PlusOutlined />}
						className="rounded-2xl"
						onClick={openCreate}
					>
						新建数据产品
					</Button>
				}
				loading={loading}
			>
				{products.length === 0 ? (
					<EmptyState
						title="暂无数据产品"
						description="将一组数据集和指标打包为数据产品，支持跨部门共享。"
					/>
				) : (
					<div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
						{products.map((product) => {
							const status = STATUS_CONFIG[product.status as keyof typeof STATUS_CONFIG] ?? STATUS_CONFIG.DRAFT;
							return (
								<div
									key={product.id}
									className="rounded-[20px] border border-slate-200 bg-white p-4 space-y-2 hover:border-blue-200 hover:shadow-sm transition-all"
								>
									<div className="flex items-start justify-between gap-2">
										<div>
											<div className="font-semibold text-sm text-slate-900">{product.name}</div>
											{product.code && (
												<Text code className="text-xs">{product.code}</Text>
											)}
										</div>
										<Tag color={status.color}>{status.label}</Tag>
									</div>
									{product.ownerDept && (
										<div className="text-xs text-slate-500">负责部门：{product.ownerDept}</div>
									)}
									{product.description && (
										<div className="text-xs text-slate-600 line-clamp-2">{product.description}</div>
									)}
									<div className="flex items-center justify-end gap-2 pt-1 border-t border-slate-100">
										<Button
											size="small"
											icon={<EditOutlined />}
											onClick={() => openEdit(product)}
										>
											编辑
										</Button>
										<Button
											size="small"
											danger
											icon={<DeleteOutlined />}
											onClick={() => handleDelete(product)}
										>
											删除
										</Button>
									</div>
								</div>
							);
						})}
					</div>
				)}
			</Card>

			<Modal
				title={editTarget ? "编辑数据产品" : "新建数据产品"}
				open={modalOpen}
				onOk={() => void handleSave()}
				onCancel={() => setModalOpen(false)}
				confirmLoading={saving}
				width={560}
				destroyOnClose
			>
				<Form form={form} layout="vertical" className="mt-4">
					<Form.Item
						name="name"
						label="产品名称"
						rules={[{ required: true, message: "请输入产品名称" }]}
					>
						<Input placeholder="例：项目进度数据产品" />
					</Form.Item>
					<Form.Item name="code" label="产品代码">
						<Input placeholder="例：pjm_progress_product（全局唯一）" />
					</Form.Item>
					<Form.Item name="ownerDept" label="负责部门">
						<Input placeholder="例：数字化部门" />
					</Form.Item>
					<Form.Item name="description" label="描述">
						<Input.TextArea rows={3} placeholder="数据产品的用途和包含内容" />
					</Form.Item>
					<Form.Item name="status" label="状态" initialValue="DRAFT">
						<Space>
							{(["DRAFT", "PUBLISHED", "OFFLINE"] as const).map((s) => (
								<Button
									key={s}
									size="small"
									type={form.getFieldValue("status") === s ? "primary" : "default"}
									onClick={() => form.setFieldValue("status", s)}
								>
									{STATUS_CONFIG[s].label}
								</Button>
							))}
						</Space>
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
