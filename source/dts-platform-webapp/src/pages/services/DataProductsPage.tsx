import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Alert, Button, Card, Form, Input, Modal, Select, Tag, Typography } from "antd";
import { actionColumn, CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import {} from "@ant-design/icons";
import { useSearchParams } from "react-router";
import { PageHeader } from "@/components/page-header";
import { JourneyContextBar } from "@/components/journey";
import dataProductsService, {
	type DataProductDetail,
	type DataProductSummary,
} from "@/api/services/dataProductsService";
import { listDatasets } from "@/api/platformApi";
import { statusLabel } from "@/utils/customerDisplayLabels";

const { Text } = Typography;

const STATUS_OPTIONS = [
	{ label: "草稿", value: "DRAFT" },
	{ label: "已发布", value: "PUBLISHED" },
	{ label: "已归档", value: "ARCHIVED" },
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
	const [searchParams] = useSearchParams();
	const journeyModelId = searchParams.get("modelId") || "";
	const journeyMetricId = searchParams.get("metricId") || "";
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

	const datasetOptions = useMemo(() => datasets.map((item) => ({ label: item.name, value: item.id })), [datasets]);

	const loadProducts = async () => {
		setLoading(true);
		try {
			const list = await dataProductsService.list();
			setProducts(Array.isArray(list) ? (list as DataProductSummary[]) : []);
		} catch {
			// global interceptor handles the error toast
		} finally {
			setLoading(false);
		}
	};

	const loadDatasets = async () => {
		try {
			const resp: any = await listDatasets({ page: 0, size: 200 });
			const list = Array.isArray(resp?.content) ? resp.content : [];
			setDatasets(list.map((item: any) => ({ id: String(item.id), name: item.name || item.id })));
		} catch {
			// global interceptor handles the error toast
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
			datasets: searchParams.get("datasetId") ? [searchParams.get("datasetId")] : [],
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
				toast.success("数据产品已新建");
			}
			setModalOpen(false);
			await loadProducts();
		} catch (error: any) {
			if (error?.errorFields) return;
		}
	};

	const removeProduct = async (id?: string) => {
		if (!id) return;
		try {
			await dataProductsService.remove(id);
			toast.success("已删除数据产品");
			await loadProducts();
		} catch {
			// global interceptor handles the error toast
		}
	};

	const openDetail = async (item: DataProductSummary) => {
		try {
			const detail = await dataProductsService.detail(item.id);
			setDetailModal(detail as DataProductDetail);
		} catch {
			// global interceptor handles the error toast
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
		}
	};

	const columns: ColumnsType<DataProductSummary> = [
		{
			title: "名称",
			dataIndex: "name",
			render: (v) => v || "-",
			sorter: (a, b) => (a.name || "").localeCompare(b.name || ""),
		},
		{ title: "类型", dataIndex: "productType", render: (v) => v || "-" },
		{ title: "状态", dataIndex: "status", render: (v) => <Tag>{statusLabel(v, "-")}</Tag> },
		{ title: "版本", dataIndex: "currentVersion", width: 120, render: (v) => v || "-" },
		{ title: "数据集", dataIndex: "datasets", render: (v: string[]) => (v?.length ? v.join(", ") : "-") },
		actionColumn<DataProductSummary>(
			(record) => [
				{ key: "source-asset", label: "查看来源资产", onClick: () => openDetail(record) },
				{ key: "consume", label: "配置消费方式", onClick: () => openVersionModal(record) },
				{ key: "new-version", label: "新增版本", onClick: () => openVersionModal(record) },
				{
					key: "publish",
					label: "发布",
					disabled: record.status === "PUBLISHED",
					tooltip: "通过版本配置保存发布状态",
				},
				{
					key: "offline",
					label: "下线",
					disabled: record.status !== "PUBLISHED",
					tooltip: "当前后端未开放独立下线接口，请通过产品状态归档",
				},
				{ key: "edit", label: "编辑", onClick: () => openModal(record) },
				{ key: "delete", label: "删除", danger: true, onClick: () => removeProduct(record.id) },
			],
			{ width: 260 },
		),
	];

	return (
		<div className="space-y-6">
			<JourneyContextBar stage="service" />
			<PageHeader
				title="数据服务中心 / 数据产品"
				actions={
					<Button type="primary" onClick={() => openModal()}>
						新建数据产品
					</Button>
				}
			/>
			<Card>
				{journeyModelId || journeyMetricId ? (
					<Alert
						className="mb-4"
						type="info"
						showIcon
						message="来自模型与指标旅程"
						description={`来源模型：${journeyModelId || "待绑定"} · 来源指标：${journeyMetricId || "待绑定"}；数据产品会优先带入当前数据集。`}
					/>
				) : null}
				<CompactTable rowKey={(record) => record.id} columns={columns} dataSource={products} loading={loading} />
			</Card>

			<Modal
				open={modalOpen}
				title={editing ? "编辑数据产品" : "新建数据产品"}
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
