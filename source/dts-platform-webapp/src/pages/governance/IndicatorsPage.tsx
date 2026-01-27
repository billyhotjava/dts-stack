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
	Tabs,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { PlusOutlined, EditOutlined, DeleteOutlined, UploadOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";
import {
	listIndicators,
	createIndicator,
	updateIndicator,
	deleteIndicator,
	publishIndicator,
	archiveIndicator,
	validateIndicator,
	listDimensions,
	createDimension,
	updateDimension,
	deleteDimension,
	publishDimension,
	archiveDimension,
	listDatasets,
} from "@/api/platformApi";

const STATUS_OPTIONS = [
	{ label: "草稿", value: "DRAFT" },
	{ label: "发布", value: "PUBLISHED" },
	{ label: "废止", value: "ARCHIVED" },
];

type Indicator = any;
type Dimension = any;

export default function Page() {
	const [tabKey, setTabKey] = useState("indicators");
	const [indicators, setIndicators] = useState<Indicator[]>([]);
	const [indicatorPage, setIndicatorPage] = useState({ page: 1, size: 10, total: 0 });
	const [dimensions, setDimensions] = useState<Dimension[]>([]);
	const [dimensionPage, setDimensionPage] = useState({ page: 1, size: 10, total: 0 });
	const [loading, setLoading] = useState(false);
	const [indicatorModal, setIndicatorModal] = useState(false);
	const [dimensionModal, setDimensionModal] = useState(false);
	const [editingIndicator, setEditingIndicator] = useState<Indicator | null>(null);
	const [editingDimension, setEditingDimension] = useState<Dimension | null>(null);
	const [indicatorForm] = Form.useForm();
	const [dimensionForm] = Form.useForm();
	const [datasets, setDatasets] = useState<{ id: string; name: string }[]>([]);

	const datasetOptions = useMemo(
		() => datasets.map((item) => ({ label: item.name, value: item.id })),
		[datasets],
	);

	const loadIndicators = async (page = indicatorPage.page, size = indicatorPage.size) => {
		setLoading(true);
		try {
			const resp: any = await listIndicators({ page: page - 1, size });
			setIndicators(Array.isArray(resp?.content) ? resp.content : []);
			setIndicatorPage({
				page: Number(resp?.page ?? page - 1) + 1,
				size: Number(resp?.size ?? size),
				total: Number(resp?.total ?? 0),
			});
		} catch (error: any) {
			toast.error(error?.message || "指标加载失败");
		} finally {
			setLoading(false);
		}
	};

	const loadDimensions = async (page = dimensionPage.page, size = dimensionPage.size) => {
		setLoading(true);
		try {
			const resp: any = await listDimensions({ page: page - 1, size });
			setDimensions(Array.isArray(resp?.content) ? resp.content : []);
			setDimensionPage({
				page: Number(resp?.page ?? page - 1) + 1,
				size: Number(resp?.size ?? size),
				total: Number(resp?.total ?? 0),
			});
		} catch (error: any) {
			toast.error(error?.message || "维度加载失败");
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
		void loadIndicators(1, indicatorPage.size);
		void loadDimensions(1, dimensionPage.size);
		void loadDatasets();
	}, []);

	const openIndicatorModal = (row?: Indicator) => {
		setEditingIndicator(row || null);
		indicatorForm.setFieldsValue({
			code: row?.code || "",
			name: row?.name || "",
			category: row?.category || "",
			definition: row?.definition || "",
			expressionSql: row?.expressionSql || "",
			datasetId: row?.datasetId || undefined,
			status: row?.status || "DRAFT",
			versionNotes: row?.versionNotes || "",
			tags: row?.tags || "",
		});
		setIndicatorModal(true);
	};

	const saveIndicator = async () => {
		try {
			const values = await indicatorForm.validateFields();
			if (editingIndicator?.id) {
				await updateIndicator(editingIndicator.id, values);
				toast.success("指标已更新");
			} else {
				await createIndicator(values);
				toast.success("指标已新增");
			}
			setIndicatorModal(false);
			await loadIndicators();
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "保存失败");
		}
	};

	const openDimensionModal = (row?: Dimension) => {
		setEditingDimension(row || null);
		dimensionForm.setFieldsValue({
			code: row?.code || "",
			name: row?.name || "",
			description: row?.description || "",
			status: row?.status || "DRAFT",
			tags: row?.tags || "",
		});
		setDimensionModal(true);
	};

	const saveDimension = async () => {
		try {
			const values = await dimensionForm.validateFields();
			if (editingDimension?.id) {
				await updateDimension(editingDimension.id, values);
				toast.success("维度已更新");
			} else {
				await createDimension(values);
				toast.success("维度已新增");
			}
			setDimensionModal(false);
			await loadDimensions();
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "保存失败");
		}
	};

	const indicatorColumns: ColumnsType<Indicator> = [
		{ title: "名称", dataIndex: "name", render: (v) => v || "-" },
		{ title: "编码", dataIndex: "code", width: 140, render: (v) => v || "-" },
		{ title: "状态", dataIndex: "status", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "数据集", dataIndex: "datasetId", render: (v) => datasets.find((d) => d.id === v)?.name || v || "-" },
		{
			title: "操作",
			width: 260,
			render: (_, record) => (
				<Space>
					<Button size="small" icon={<EditOutlined />} onClick={() => openIndicatorModal(record)}>
						编辑
					</Button>
					<Button
						size="small"
						icon={<UploadOutlined />}
						onClick={async () => {
							await publishIndicator(record.id);
							toast.success("指标已发布");
							await loadIndicators();
						}}
					>
						发布
					</Button>
					<Button
						size="small"
						onClick={async () => {
							await archiveIndicator(record.id);
							toast.success("指标已废止");
							await loadIndicators();
						}}
					>
						废止
					</Button>
					<Button
						size="small"
						onClick={async () => {
							await validateIndicator(record.id);
							toast.success("已触发校验");
						}}
					>
						校验
					</Button>
					<Button
						size="small"
						danger
						icon={<DeleteOutlined />}
						onClick={async () => {
							await deleteIndicator(record.id);
							toast.success("指标已删除");
							await loadIndicators();
						}}
					>
						删除
					</Button>
				</Space>
			),
		},
	];

	const dimensionColumns: ColumnsType<Dimension> = [
		{ title: "名称", dataIndex: "name", render: (v) => v || "-" },
		{ title: "编码", dataIndex: "code", width: 140, render: (v) => v || "-" },
		{ title: "状态", dataIndex: "status", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "描述", dataIndex: "description", render: (v) => v || "-" },
		{
			title: "操作",
			width: 220,
			render: (_, record) => (
				<Space>
					<Button size="small" icon={<EditOutlined />} onClick={() => openDimensionModal(record)}>
						编辑
					</Button>
					<Button
						size="small"
						onClick={async () => {
							await publishDimension(record.id);
							toast.success("维度已发布");
							await loadDimensions();
						}}
					>
						发布
					</Button>
					<Button
						size="small"
						onClick={async () => {
							await archiveDimension(record.id);
							toast.success("维度已废止");
							await loadDimensions();
						}}
					>
						废止
					</Button>
					<Button
						size="small"
						danger
						icon={<DeleteOutlined />}
						onClick={async () => {
							await deleteDimension(record.id);
							toast.success("维度已删除");
							await loadDimensions();
						}}
					>
						删除
					</Button>
				</Space>
			),
		},
	];

	return (
		<div className="space-y-6">
			<PageHeader title="数据治理中心 / 指标中心" description="指标口径与指标库。" />
			<Card>
				<Tabs
					activeKey={tabKey}
					onChange={setTabKey}
					items={[
						{
							key: "indicators",
							label: "指标字典",
							children: (
								<>
									<Space className="mb-3">
										<Button type="primary" icon={<PlusOutlined />} onClick={() => openIndicatorModal()}>
											新增指标
										</Button>
									</Space>
									<Table
										rowKey={(record) => record.id}
										columns={indicatorColumns}
										dataSource={indicators}
										loading={loading}
										pagination={{
											current: indicatorPage.page,
											pageSize: indicatorPage.size,
											total: indicatorPage.total,
											onChange: (page, size) => loadIndicators(page, size),
										}}
									/>
								</>
							),
						},
						{
							key: "dimensions",
							label: "维度字典",
							children: (
								<>
									<Space className="mb-3">
										<Button type="primary" icon={<PlusOutlined />} onClick={() => openDimensionModal()}>
											新增维度
										</Button>
									</Space>
									<Table
										rowKey={(record) => record.id}
										columns={dimensionColumns}
										dataSource={dimensions}
										loading={loading}
										pagination={{
											current: dimensionPage.page,
											pageSize: dimensionPage.size,
											total: dimensionPage.total,
											onChange: (page, size) => loadDimensions(page, size),
										}}
									/>
								</>
							),
						},
					]}
				/>
			</Card>

			<Modal
				open={indicatorModal}
				title={editingIndicator ? "编辑指标" : "新增指标"}
				onCancel={() => setIndicatorModal(false)}
				onOk={saveIndicator}
				okText="保存"
				width={720}
				destroyOnClose
			>
				<Form form={indicatorForm} layout="vertical">
					<Form.Item label="指标名称" name="name" rules={[{ required: true, message: "请输入名称" }]}>
						<Input />
					</Form.Item>
					<Form.Item label="指标编码" name="code">
						<Input />
					</Form.Item>
					<Form.Item label="分类" name="category">
						<Input />
					</Form.Item>
					<Form.Item label="定义" name="definition">
						<Input.TextArea rows={3} />
					</Form.Item>
					<Form.Item label="计算SQL" name="expressionSql">
						<Input.TextArea rows={4} />
					</Form.Item>
					<Form.Item label="数据集" name="datasetId">
						<Select options={datasetOptions} allowClear />
					</Form.Item>
					<Form.Item label="状态" name="status">
						<Select options={STATUS_OPTIONS} />
					</Form.Item>
					<Form.Item label="版本说明" name="versionNotes">
						<Input.TextArea rows={2} />
					</Form.Item>
					<Form.Item label="标签" name="tags">
						<Input placeholder="逗号分隔" />
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={dimensionModal}
				title={editingDimension ? "编辑维度" : "新增维度"}
				onCancel={() => setDimensionModal(false)}
				onOk={saveDimension}
				okText="保存"
				destroyOnClose
			>
				<Form form={dimensionForm} layout="vertical">
					<Form.Item label="维度名称" name="name" rules={[{ required: true, message: "请输入名称" }]}>
						<Input />
					</Form.Item>
					<Form.Item label="维度编码" name="code">
						<Input />
					</Form.Item>
					<Form.Item label="描述" name="description">
						<Input.TextArea rows={3} />
					</Form.Item>
					<Form.Item label="状态" name="status">
						<Select options={STATUS_OPTIONS} />
					</Form.Item>
					<Form.Item label="标签" name="tags">
						<Input placeholder="逗号分隔" />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
