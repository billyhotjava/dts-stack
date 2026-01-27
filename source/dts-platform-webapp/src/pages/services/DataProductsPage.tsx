import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Button,
	Card,
	Form,
	Input,
	Modal,
	Select,
	Space,
	Table,
	Tag,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { PlusOutlined, EditOutlined, DeleteOutlined, FileAddOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";
import dataProductsService, {
	type DataProductDetail,
	type DataProductSummary,
} from "@/api/services/dataProductsService";
import { listDatasets } from "@/api/platformApi";

const { Text } = Typography;

const STATUS_OPTIONS = [
	{ label: "DRAFT", value: "DRAFT" },
	{ label: "PUBLISHED", value: "PUBLISHED" },
	{ label: "ARCHIVED", value: "ARCHIVED" },
];

const parseJson = (value?: string) => {
	if (!value) return undefined;
	try {
		return JSON.parse(value);
	} catch {
		return undefined;
	}
};

export default function Page() {
	const [products, setProducts] = useState<DataProductSummary[]>([]);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [editing, setEditing] = useState<DataProductSummary | null>(null);
	const [detailModal, setDetailModal] = useState<DataProductDetail | null>(null);
	const [versionModalOpen, setVersionModalOpen] = useState(false);
	const [versionTarget, setVersionTarget] = useState<DataProductSummary | null>(null);
	const [form] = Form.useForm();
	const [versionForm] = Form.useForm();
	const [datasets, setDatasets] = useState<{ id: string; name: string }[]>([]);

	const datasetOptions = useMemo(
		() => datasets.map((item) => ({ label: item.name, value: item.id })),
		[datasets],
	);

	const loadProducts = async () => {
		setLoading(true);
		try {
			const list = await dataProductsService.list();
			setProducts(Array.isArray(list) ? (list as DataProductSummary[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "数据产品加载失败");
		} finally {
			setLoading(false);
		}
	};

	const loadDatasets = async () => {
		try {
			const resp: any = await listDatasets({ page: 0, size: 200 });
			const list = Array.isArray(resp?.content) ? resp.content : [];
			setDatasets(list.map((item: any) => ({ id: String(item.id), name: item.name || item.id })));
		} catch (error: any) {
			toast.error(error?.message || "数据集加载失败");
		}
	};

	useEffect(() => {
		void loadProducts();
		void loadDatasets();
	}, []);

	const openModal = (item?: DataProductSummary) => {
		setEditing(item || null);
		form.setFieldsValue({
			code: item?.code || "",
			name: item?.name || "",
			productType: item?.productType || "",
			classification: item?.classification || "",
			status: item?.status || "DRAFT",
			sla: item?.sla || "",
			refreshFrequency: item?.refreshFrequency || "",
			description: "",
			datasets: [],
		});
		setModalOpen(true);
	};

	const saveProduct = async () => {
		try {
			const values = await form.validateFields();
			const payload = {
				...values,
				datasets: (values.datasets || []).map((id: string) => ({ datasetId: id })),
			};
			if (editing?.id) {
				await dataProductsService.update(editing.id, payload);
				toast.success("数据产品已更新");
			} else {
				await dataProductsService.create(payload);
				toast.success("数据产品已新增");
			}
			setModalOpen(false);
			await loadProducts();
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "保存失败");
		}
	};

	const removeProduct = async (id?: string) => {
		if (!id) return;
		try {
			await dataProductsService.remove(id);
			toast.success("已删除数据产品");
			await loadProducts();
		} catch (error: any) {
			toast.error(error?.message || "删除失败");
		}
	};

	const openDetail = async (item: DataProductSummary) => {
		try {
			const detail = await dataProductsService.detail(item.id);
			setDetailModal(detail as DataProductDetail);
		} catch (error: any) {
			toast.error(error?.message || "详情加载失败");
		}
	};

	const openVersionModal = (item: DataProductSummary) => {
		setVersionTarget(item);
		versionForm.resetFields();
		setVersionModalOpen(true);
	};

	const saveVersion = async () => {
		if (!versionTarget) return;
		try {
			const values = await versionForm.validateFields();
			const payload = {
				version: values.version,
				status: values.status,
				diffSummary: values.diffSummary,
				fields: parseJson(values.fieldsJson) || undefined,
				consumption: parseJson(values.consumptionJson) || undefined,
				metadata: parseJson(values.metadataJson) || undefined,
			};
			await dataProductsService.addVersion(versionTarget.id, payload);
			toast.success("版本已新增");
			setVersionModalOpen(false);
			await loadProducts();
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "保存失败");
		}
	};

	const columns: ColumnsType<DataProductSummary> = [
		{ title: "名称", dataIndex: "name", render: (v) => v || "-" },
		{ title: "类型", dataIndex: "productType", render: (v) => v || "-" },
		{ title: "状态", dataIndex: "status", render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "版本", dataIndex: "currentVersion", width: 120, render: (v) => v || "-" },
		{ title: "数据集", dataIndex: "datasets", render: (v: string[]) => (v?.length ? v.join(", ") : "-") },
		{
			title: "操作",
			width: 260,
			render: (_, record) => (
				<Space>
					<Button size="small" onClick={() => openDetail(record)}>详情</Button>
					<Button size="small" icon={<FileAddOutlined />} onClick={() => openVersionModal(record)}>
						新增版本
					</Button>
					<Button size="small" icon={<EditOutlined />} onClick={() => openModal(record)}>
						编辑
					</Button>
					<Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeProduct(record.id)}>
						删除
					</Button>
				</Space>
			),
		},
	];

	return (
		<div className="space-y-6">
			<PageHeader
				title="数据服务中心 / 数据产品"
				description="产品化包装数据服务并管理版本。"
				actions={
					<Button type="primary" icon={<PlusOutlined />} onClick={() => openModal()}>
						新增数据产品
					</Button>
				}
			/>
			<Card>
				<Table rowKey={(record) => record.id} columns={columns} dataSource={products} loading={loading} />
			</Card>

			<Modal
				open={modalOpen}
				title={editing ? "编辑数据产品" : "新增数据产品"}
				onCancel={() => setModalOpen(false)}
				onOk={saveProduct}
				okText="保存"
				destroyOnClose
				width={720}
			>
				<Form form={form} layout="vertical">
					<Form.Item label="产品名称" name="name" rules={[{ required: true, message: "请输入名称" }]}>
						<Input />
					</Form.Item>
					<Form.Item label="产品编码" name="code" rules={[{ required: true, message: "请输入编码" }]}>
						<Input />
					</Form.Item>
					<Form.Item label="类型" name="productType">
						<Input />
					</Form.Item>
					<Form.Item label="状态" name="status">
						<Select options={STATUS_OPTIONS} />
					</Form.Item>
					<Form.Item label="服务等级" name="sla">
						<Input />
					</Form.Item>
					<Form.Item label="刷新频率" name="refreshFrequency">
						<Input />
					</Form.Item>
					<Form.Item label="数据集" name="datasets">
						<Select mode="multiple" options={datasetOptions} />
					</Form.Item>
					<Form.Item label="描述" name="description">
						<Input.TextArea rows={3} />
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={versionModalOpen}
				title={`新增版本${versionTarget ? `: ${versionTarget.name}` : ""}`}
				onCancel={() => setVersionModalOpen(false)}
				onOk={saveVersion}
				okText="保存"
				destroyOnClose
				width={720}
			>
				<Form form={versionForm} layout="vertical">
					<Form.Item label="版本号" name="version" rules={[{ required: true, message: "请输入版本号" }]}>
						<Input placeholder="例如 v1" />
					</Form.Item>
					<Form.Item label="状态" name="status">
						<Select options={STATUS_OPTIONS} />
					</Form.Item>
					<Form.Item label="版本说明" name="diffSummary">
						<Input.TextArea rows={2} />
					</Form.Item>
					<Form.Item label="字段定义(JSON)" name="fieldsJson">
						<Input.TextArea rows={4} placeholder='例如: [{"name":"id","type":"string"}]' />
					</Form.Item>
					<Form.Item label="消费方式(JSON)" name="consumptionJson">
						<Input.TextArea rows={4} />
					</Form.Item>
					<Form.Item label="元数据(JSON)" name="metadataJson">
						<Input.TextArea rows={4} />
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={!!detailModal}
				onCancel={() => setDetailModal(null)}
				footer={null}
				title="数据产品详情"
				width={720}
				destroyOnClose
			>
				{detailModal ? (
					<div className="space-y-3">
						<Text strong>{detailModal.name}</Text>
						<div className="text-xs text-muted-foreground">{detailModal.description || "-"}</div>
						<div>版本数：{detailModal.versions?.length || 0}</div>
						<div>关联数据集：{detailModal.datasets?.join(", ") || "-"}</div>
					</div>
				) : null}
			</Modal>
		</div>
	);
}
