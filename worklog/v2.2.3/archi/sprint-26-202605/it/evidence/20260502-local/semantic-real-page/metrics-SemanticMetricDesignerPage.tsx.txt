import { useEffect, useMemo, useState } from "react";
import type React from "react";
import { Alert, Button, Card, Col, Empty, Form, Input, Modal, Row, Select, Space, Table, Tag, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { FunctionOutlined, SaveOutlined, TableOutlined } from "@ant-design/icons";
import type { Node } from "@xyflow/react";
import { PageHeader } from "@/components/page-header";
import { VisualFlowCanvas, type VisualFlowDropEvent } from "@/components/visual-canvas/VisualFlowCanvas";
import { getDatasetFields, listDatasets, type DatasetField } from "@/api/platformApi";
import {
	createSemanticDimension,
	createSemanticMetric,
	listSemanticBusinessObjects,
	listSemanticDimensions,
	listSemanticMetrics,
	type SemanticBusinessObject,
	type SemanticDimension,
	type SemanticMetric,
} from "@/api/semanticModelingApi";
import { SemanticSectionNav } from "./SemanticSectionNav";
import { asArray, isDwdSemanticInput, semanticSectionMeta } from "./semanticModelingShared";

const { Text } = Typography;

type DatasetOption = {
	id: string;
	name: string;
	table?: string;
	layer?: string;
	database?: string;
	schema?: string;
};

type DragFieldPayload = {
	id?: string;
	name: string;
	dataType?: string;
	tableName?: string;
};

const normalizeDataset = (item: any): DatasetOption => ({
	id: String(item.id || item.key || item.name || item.tableName || item.hiveTable),
	name: String(item.name || item.displayName || item.hiveTable || item.tableName || item.id || ""),
	table: String(item.hiveTable || item.tableName || item.name || ""),
	layer: String(item.warehouseLayer || item.layer || ""),
	database: String(item.databaseName || item.database || ""),
	schema: String(item.schemaName || item.schema || ""),
});

const safeCode = (value: string) =>
	value
		.trim()
		.replace(/([a-z0-9])([A-Z])/g, "$1_$2")
		.replace(/[^a-zA-Z0-9_]+/g, "_")
		.replace(/^_+|_+$/g, "")
		.toLowerCase();

const isNumericField = (field: DragFieldPayload) => {
	const dataType = (field.dataType || "").toLowerCase();
	return ["int", "integer", "bigint", "smallint", "decimal", "numeric", "number", "double", "float", "real"].some((item) =>
		dataType.includes(item),
	);
};

export default function SemanticMetricDesignerPage() {
	const [form] = Form.useForm();
	const [objects, setObjects] = useState<SemanticBusinessObject[]>([]);
	const [dimensions, setDimensions] = useState<SemanticDimension[]>([]);
	const [metrics, setMetrics] = useState<SemanticMetric[]>([]);
	const [datasets, setDatasets] = useState<DatasetOption[]>([]);
	const [datasetFields, setDatasetFields] = useState<DatasetField[]>([]);
	const [selectedObjectId, setSelectedObjectId] = useState<string>();
	const [selectedSource, setSelectedSource] = useState<string>();
	const [draftDimensions, setDraftDimensions] = useState<DragFieldPayload[]>([]);
	const [draftMetrics, setDraftMetrics] = useState<DragFieldPayload[]>([]);
	const [loading, setLoading] = useState(false);
	const [datasetsLoading, setDatasetsLoading] = useState(false);
	const [fieldsLoading, setFieldsLoading] = useState(false);
	const [saving, setSaving] = useState(false);
	const [modalType, setModalType] = useState<"dimension" | "metric" | null>(null);

	const loadSemanticData = () => {
		setLoading(true);
		Promise.allSettled([
			listSemanticBusinessObjects(),
			listSemanticDimensions(),
			listSemanticMetrics(),
		]).then((results) => {
			const valueAt = (index: number) => (results[index]?.status === "fulfilled" ? (results[index] as PromiseFulfilledResult<any>).value : undefined);
			setObjects(asArray<SemanticBusinessObject>(valueAt(0)));
			setDimensions(asArray<SemanticDimension>(valueAt(1)));
			setMetrics(asArray<SemanticMetric>(valueAt(2)));
		}).finally(() => setLoading(false));
	};

	const loadDatasets = () => {
		setDatasetsLoading(true);
		listDatasets({ page: 0, size: 120 })
			.then((resp: any) => setDatasets(asArray<any>(resp).map(normalizeDataset)))
			.catch(() => setDatasets([]))
			.finally(() => setDatasetsLoading(false));
	};

	useEffect(() => {
		loadSemanticData();
		loadDatasets();
	}, []);

	const dwdDatasets = useMemo(
		() => datasets.filter(isDwdSemanticInput),
		[datasets],
	);

	const selectedDataset = useMemo(
		() => dwdDatasets.find((item) => (item.table || item.name) === selectedSource),
		[dwdDatasets, selectedSource],
	);

	const selectedObject = useMemo(
		() => objects.find((item) => item.id === selectedObjectId),
		[objects, selectedObjectId],
	);

	const visibleDimensions = useMemo(
		() => selectedObjectId ? dimensions.filter((item) => item.objectId === selectedObjectId) : dimensions,
		[dimensions, selectedObjectId],
	);

	const visibleMetrics = useMemo(
		() => selectedObjectId ? metrics.filter((item) => item.objectId === selectedObjectId) : metrics,
		[metrics, selectedObjectId],
	);

	useEffect(() => {
		if (!selectedDataset?.id) {
			setDatasetFields([]);
			return;
		}
		let ignore = false;
		setFieldsLoading(true);
		getDatasetFields(selectedDataset.id)
			.then((fields) => {
				if (!ignore) setDatasetFields(Array.isArray(fields) ? fields : []);
			})
			.catch(() => {
				if (!ignore) setDatasetFields([]);
			})
			.finally(() => {
				if (!ignore) setFieldsLoading(false);
			});
		return () => {
			ignore = true;
		};
	}, [selectedDataset?.id]);

	const dragField = (event: React.DragEvent<HTMLElement>, field: DragFieldPayload) => {
		event.dataTransfer.setData("application/json", JSON.stringify(field));
		event.dataTransfer.effectAllowed = "copy";
	};

	const addUniqueField = (list: DragFieldPayload[], field: DragFieldPayload) =>
		list.some((item) => item.name === field.name && item.tableName === field.tableName) ? list : [...list, field];

	const fieldFromDropPayload = ({ payload }: VisualFlowDropEvent): DragFieldPayload | null => {
		if (payload.value && typeof payload.value === "object") return payload.value as DragFieldPayload;
		if (payload.kind === "dataset") return null;
		return payload as DragFieldPayload;
	};

	const saveDraftDimensions = async () => {
		if (!selectedObjectId) {
			message.warning("请选择业务对象");
			return;
		}
		if (!draftDimensions.length) {
			message.warning("请先拖入维度字段");
			return;
		}
		setSaving(true);
		try {
			for (const field of draftDimensions) {
				const code = safeCode(field.name);
				const duplicated = dimensions.some((item) => item.objectId === selectedObjectId && (item.fieldName === field.name || item.code === code));
				if (duplicated) continue;
				await createSemanticDimension({
					objectId: selectedObjectId,
					code,
					name: field.name,
					fieldName: field.name,
					dataType: field.dataType,
					semanticType: field.name.toLowerCase().includes("date") || field.name.includes("时间") ? "time" : "dimension",
				});
			}
			setDraftDimensions([]);
			loadSemanticData();
			message.success("维度已保存");
		} finally {
			setSaving(false);
		}
	};

	const saveDraftMetrics = async () => {
		if (!selectedObjectId) {
			message.warning("请选择业务对象");
			return;
		}
		if (!draftMetrics.length) {
			message.warning("请先拖入指标字段");
			return;
		}
		setSaving(true);
		try {
			for (const field of draftMetrics) {
				const code = safeCode(field.name);
				const duplicated = metrics.some((item) => item.objectId === selectedObjectId && (item.code === code || item.name === field.name));
				if (duplicated) continue;
				const formulaType = isNumericField(field) ? "sum" : "count_distinct";
				await createSemanticMetric({
					objectId: selectedObjectId,
					code,
					name: field.name,
					formulaType,
					formulaJson: JSON.stringify({ type: formulaType, field: field.name }),
					format: isNumericField(field) ? "number" : "integer",
				});
			}
			setDraftMetrics([]);
			loadSemanticData();
			message.success("指标已保存");
		} finally {
			setSaving(false);
		}
	};

	const draftDimensionNodes: Node[] = useMemo(() =>
		draftDimensions.map((field, index) => ({
			id: `draft-dimension-${field.tableName || "field"}-${field.name}`,
			position: { x: (index % 2) * 170, y: Math.floor(index / 2) * 82 },
			data: {
				label: (
					<Tag
						closable
						className="nodrag"
						onClose={() => setDraftDimensions((current) => current.filter((item) => item.name !== field.name || item.tableName !== field.tableName))}
					>
						{field.name}
					</Tag>
				),
			},
			style: {
				width: 150,
				minHeight: 52,
				background: "#eff6ff",
				border: "1px solid #91caff",
				borderRadius: 6,
				padding: "10px",
			},
		})),
		[draftDimensions],
	);

	const draftMetricNodes: Node[] = useMemo(() =>
		draftMetrics.map((field, index) => ({
			id: `draft-metric-${field.tableName || "field"}-${field.name}`,
			position: { x: (index % 2) * 170, y: Math.floor(index / 2) * 82 },
			data: {
				label: (
					<Tag
						color="green"
						closable
						className="nodrag"
						onClose={() => setDraftMetrics((current) => current.filter((item) => item.name !== field.name || item.tableName !== field.tableName))}
					>
						{field.name}
					</Tag>
				),
			},
			style: {
				width: 150,
				minHeight: 52,
				background: "#f6ffed",
				border: "1px solid #95de64",
				borderRadius: 6,
				padding: "10px",
			},
		})),
		[draftMetrics],
	);

	const dimensionColumns: ColumnsType<SemanticDimension> = [
		{ title: "维度", dataIndex: "name" },
		{ title: "字段", dataIndex: "fieldName", render: (value) => value || "-" },
		{ title: "类型", dataIndex: "semanticType", width: 120, render: (value) => value || "-" },
	];

	const metricColumns: ColumnsType<SemanticMetric> = [
		{ title: "指标", dataIndex: "name" },
		{ title: "公式类型", dataIndex: "formulaType", width: 130, render: (value) => value || "-" },
		{ title: "格式", dataIndex: "format", width: 110, render: (value) => value || "-" },
		{ title: "状态", dataIndex: "status", width: 100, render: (value) => value || "-" },
	];

	const openModal = (type: "dimension" | "metric") => {
		form.resetFields();
		form.setFieldsValue({ objectId: selectedObjectId });
		setModalType(type);
	};

	const closeModal = () => {
		setModalType(null);
		form.resetFields();
	};

	const submitModal = async () => {
		const values = await form.validateFields();
		setSaving(true);
		try {
			if (modalType === "dimension") await createSemanticDimension(values);
			if (modalType === "metric") await createSemanticMetric(values);
			message.success("已保存");
			closeModal();
			loadSemanticData();
		} finally {
			setSaving(false);
		}
	};

	return (
		<div className="space-y-5 p-5" data-testid="semantic-metric-designer-page">
			<PageHeader
				title={semanticSectionMeta.metrics.title}
				actions={(
					<Space wrap>
						<Button icon={<TableOutlined />} onClick={() => openModal("dimension")}>新增维度</Button>
						<Button type="primary" icon={<FunctionOutlined />} onClick={() => openModal("metric")}>新增指标</Button>
					</Space>
				)}
			/>

			<SemanticSectionNav activeSection="metrics" />

			<Alert
				type="info"
				showIcon
				message="指标配置边界"
				description="这里面向不写 SQL 的业务人员，通过字段拖拽定义维度和指标；DWS/ADS 模型组合、审核发布和 BI 注册放在后续页面处理。"
			/>

			<Row gutter={[16, 16]}>
				<Col xs={24} xl={8}>
					<Card title="业务对象与字段池">
						<Form layout="vertical">
							<Form.Item label="业务对象">
								<Select
									allowClear
									value={selectedObjectId}
									loading={loading}
									placeholder="请选择业务对象"
									options={objects.map((item) => ({ label: item.name, value: item.id }))}
									onChange={setSelectedObjectId}
								/>
							</Form.Item>
							<Form.Item label="来源 DWD 明细模型">
								<Select
									allowClear
									showSearch
									loading={datasetsLoading}
									value={selectedSource}
									placeholder="请选择真实 DWD 明细模型"
									optionFilterProp="label"
									options={dwdDatasets.map((item) => ({ label: item.table || item.name, value: item.table || item.name }))}
									onChange={setSelectedSource}
								/>
							</Form.Item>
							<Form.Item label="对象主键">
								<Input readOnly value={selectedObject?.primaryKey || ""} placeholder="业务对象未配置主键" />
							</Form.Item>
						</Form>

						<div className="max-h-96 space-y-2 overflow-auto rounded border border-dashed border-slate-200 p-3">
							{datasetFields.length ? datasetFields.map((field) => (
								<div
									key={`${field.tableName || selectedDataset?.table || ""}-${field.name}`}
									draggable
									onDragStart={(event) => dragField(event, { name: field.name, dataType: field.dataType, tableName: field.tableName || selectedDataset?.table })}
									className="cursor-grab rounded border border-slate-200 bg-white p-2 active:cursor-grabbing"
								>
									<Text>{field.name}</Text>
									<div><Text type="secondary" className="text-xs">{field.dataType || "-"}{field.comment ? ` / ${field.comment}` : ""}</Text></div>
								</div>
							)) : (
								<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={fieldsLoading ? "正在读取字段" : "暂无字段元数据"} />
							)}
						</div>
					</Card>
				</Col>

				<Col xs={24} xl={16}>
					<Row gutter={[16, 16]}>
						<Col xs={24} lg={12}>
							<Card
								title="拖拽到维度区"
								extra={<Button size="small" icon={<SaveOutlined />} loading={saving} disabled={!selectedObjectId || !draftDimensions.length} onClick={saveDraftDimensions}>保存维度</Button>}
							>
								<VisualFlowCanvas
									nodes={draftDimensionNodes}
									height={260}
									emptyText="放入时间、组织、状态等维度字段"
									onDropItem={(event) => {
										const field = fieldFromDropPayload(event);
										if (field) setDraftDimensions((current) => addUniqueField(current, field));
									}}
								/>
							</Card>
						</Col>
						<Col xs={24} lg={12}>
							<Card
								title="拖拽到指标区"
								extra={<Button size="small" icon={<SaveOutlined />} loading={saving} disabled={!selectedObjectId || !draftMetrics.length} onClick={saveDraftMetrics}>保存指标</Button>}
							>
								<VisualFlowCanvas
									nodes={draftMetricNodes}
									height={260}
									emptyText="放入金额、数量、状态判断等指标字段"
									onDropItem={(event) => {
										const field = fieldFromDropPayload(event);
										if (field) setDraftMetrics((current) => addUniqueField(current, field));
									}}
								/>
							</Card>
						</Col>
					</Row>

					<Row gutter={[16, 16]} className="mt-4">
						<Col xs={24} lg={12}>
							<Card title="已保存维度">
								<Table<SemanticDimension>
									rowKey="id"
									size="small"
									loading={loading}
									pagination={{ pageSize: 6 }}
									columns={dimensionColumns}
									dataSource={visibleDimensions}
								/>
							</Card>
						</Col>
						<Col xs={24} lg={12}>
							<Card title="已保存指标">
								<Table<SemanticMetric>
									rowKey="id"
									size="small"
									loading={loading}
									pagination={{ pageSize: 6 }}
									columns={metricColumns}
									dataSource={visibleMetrics}
								/>
							</Card>
						</Col>
					</Row>
				</Col>
			</Row>

			<Modal
				open={Boolean(modalType)}
				title={modalType === "dimension" ? "新增维度" : "新增指标"}
				onCancel={closeModal}
				onOk={submitModal}
				confirmLoading={saving}
				destroyOnClose
			>
				<Form form={form} layout="vertical">
					<Form.Item name="objectId" label="业务对象">
						<Select allowClear options={objects.map((item) => ({ label: item.name, value: item.id }))} />
					</Form.Item>
					<Form.Item name="code" label="编码" rules={[{ required: true, message: "请输入编码" }]}>
						<Input />
					</Form.Item>
					<Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
						<Input />
					</Form.Item>
					{modalType === "dimension" ? (
						<>
							<Form.Item name="fieldName" label="来源字段">
								<Input />
							</Form.Item>
							<Form.Item name="dataType" label="数据类型">
								<Input />
							</Form.Item>
						</>
					) : (
						<>
							<Form.Item name="formulaType" label="公式类型">
								<Select
									allowClear
									options={["sum", "count", "count_distinct", "avg", "count_if", "sum_if", "ratio"].map((item) => ({ label: item, value: item }))}
								/>
							</Form.Item>
							<Form.Item name="formulaJson" label="公式 JSON">
								<Input.TextArea rows={4} />
							</Form.Item>
							<Form.Item name="format" label="展示格式">
								<Input />
							</Form.Item>
						</>
					)}
				</Form>
			</Modal>
		</div>
	);
}
