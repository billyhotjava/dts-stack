import { useEffect, useMemo, useState } from "react";
import type React from "react";
import { Alert, Button, Card, Col, Empty, Form, Input, Modal, Row, Segmented, Select, Space, Tag, Typography, message } from "antd";
import { CompactTable, RecordDetailDrawer, appendDetailAction } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { CodeOutlined, DashboardOutlined, EditOutlined, PartitionOutlined, PlayCircleOutlined, PlusOutlined, SaveOutlined } from "@ant-design/icons";
import type { Node } from "@xyflow/react";
import { PageHeader } from "@/components/page-header";
import { VisualFlowCanvas, type VisualFlowDropEvent } from "@/components/visual-canvas/VisualFlowCanvas";
import {
	createSemanticModel,
	generateSemanticModelArtifacts,
	getSemanticModelBindings,
	listSemanticBusinessObjects,
	listSemanticDimensions,
	listSemanticGeneratedArtifacts,
	listSemanticMetrics,
	listSemanticModels,
	previewSemanticModelData,
	saveSemanticModelBindings,
	type SemanticBusinessObject,
	type SemanticDimension,
	type SemanticGeneratedArtifact,
	type SemanticMetric,
	type SemanticModel,
	type SemanticModelPreview,
	updateSemanticModel,
} from "@/api/semanticModelingApi";
import { SemanticSectionNav } from "./SemanticSectionNav";
import { asArray, semanticSectionMeta } from "./semanticModelingShared";

const { Text } = Typography;

type DragFieldPayload = {
	id?: string;
	name: string;
	dataType?: string;
	kind?: "dimension" | "metric";
};

export default function SemanticDatasetsPage() {
	const [form] = Form.useForm();
	const [objects, setObjects] = useState<SemanticBusinessObject[]>([]);
	const [dimensions, setDimensions] = useState<SemanticDimension[]>([]);
	const [metrics, setMetrics] = useState<SemanticMetric[]>([]);
	const [models, setModels] = useState<SemanticModel[]>([]);
	const [artifacts, setArtifacts] = useState<SemanticGeneratedArtifact[]>([]);
	const [selectedObjectId, setSelectedObjectId] = useState<string>();
	const [selectedModelId, setSelectedModelId] = useState<string>();
	const [modelType, setModelType] = useState<"DWS" | "ADS">("DWS");
	const [modelDimensions, setModelDimensions] = useState<string[]>([]);
	const [modelMetrics, setModelMetrics] = useState<string[]>([]);
	const [loading, setLoading] = useState(false);
	const [artifactLoading, setArtifactLoading] = useState(false);
	const [detailRow, setDetailRow] = useState<SemanticModel | null>(null);
	const [saving, setSaving] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [previewLoading, setPreviewLoading] = useState(false);
	const [previewOpen, setPreviewOpen] = useState(false);
	const [previewResult, setPreviewResult] = useState<SemanticModelPreview | null>(null);
	const [editingModel, setEditingModel] = useState<SemanticModel | null>(null);

	const loadSemanticData = () => {
		setLoading(true);
		Promise.allSettled([
			listSemanticBusinessObjects(),
			listSemanticDimensions(),
			listSemanticMetrics(),
			listSemanticModels(),
		]).then((results) => {
			const valueAt = (index: number) => (results[index]?.status === "fulfilled" ? (results[index] as PromiseFulfilledResult<any>).value : undefined);
			setObjects(asArray<SemanticBusinessObject>(valueAt(0)));
			setDimensions(asArray<SemanticDimension>(valueAt(1)));
			setMetrics(asArray<SemanticMetric>(valueAt(2)));
			setModels(asArray<SemanticModel>(valueAt(3)));
		}).finally(() => setLoading(false));
	};

	useEffect(() => {
		loadSemanticData();
	}, []);

	const selectedModel = useMemo(
		() => models.find((item) => item.id === selectedModelId),
		[models, selectedModelId],
	);

	const activeObjectId = selectedModel?.objectId || selectedObjectId;

	const visibleDimensions = useMemo(
		() => activeObjectId ? dimensions.filter((item) => item.objectId === activeObjectId) : dimensions,
		[activeObjectId, dimensions],
	);

	const visibleMetrics = useMemo(
		() => activeObjectId ? metrics.filter((item) => item.objectId === activeObjectId) : metrics,
		[activeObjectId, metrics],
	);

	const dimensionNameById = useMemo(
		() => new Map(dimensions.map((item) => [item.id, item.name || item.code])),
		[dimensions],
	);

	const metricNameById = useMemo(
		() => new Map(metrics.map((item) => [item.id, item.name || item.code])),
		[metrics],
	);

	useEffect(() => {
		if (!selectedModelId) {
			setArtifacts([]);
			setModelDimensions([]);
			setModelMetrics([]);
			return;
		}
		let ignore = false;
		setArtifactLoading(true);
		Promise.allSettled([
			listSemanticGeneratedArtifacts({ modelId: selectedModelId }),
			getSemanticModelBindings(selectedModelId),
		]).then((results) => {
			if (ignore) return;
			const artifactsResp = results[0].status === "fulfilled" ? results[0].value : [];
			const bindingsResp = results[1].status === "fulfilled" ? results[1].value : undefined;
			setArtifacts(asArray<SemanticGeneratedArtifact>(artifactsResp));
			setModelDimensions(Array.isArray((bindingsResp as any)?.dimensionIds) ? (bindingsResp as any).dimensionIds : []);
			setModelMetrics(Array.isArray((bindingsResp as any)?.metricIds) ? (bindingsResp as any).metricIds : []);
		}).finally(() => {
			if (!ignore) setArtifactLoading(false);
		});
		return () => {
			ignore = true;
		};
	}, [selectedModelId]);

	const dragField = (event: React.DragEvent<HTMLElement>, field: DragFieldPayload) => {
		event.dataTransfer.setData("application/json", JSON.stringify(field));
		event.dataTransfer.effectAllowed = "copy";
	};

	const fieldFromDropPayload = ({ payload }: VisualFlowDropEvent): DragFieldPayload | null => {
		if (payload.value && typeof payload.value === "object") return payload.value as DragFieldPayload;
		return payload as DragFieldPayload;
	};

	const addModelDimension = (field?: DragFieldPayload | null) => {
		const value = field?.id || field?.name;
		if (value) setModelDimensions((current) => (current.includes(value) ? current : [...current, value]));
	};

	const addModelMetric = (field?: DragFieldPayload | null) => {
		const value = field?.id || field?.name;
		if (value) setModelMetrics((current) => (current.includes(value) ? current : [...current, value]));
	};

	const modelDimensionNodes: Node[] = useMemo(() =>
		modelDimensions.map((id, index) => ({
			id: `model-dimension-${id}`,
			position: { x: (index % 3) * 170, y: Math.floor(index / 3) * 78 },
			data: {
				label: (
					<Tag
						closable
						className="nodrag"
						onClose={() => setModelDimensions((current) => current.filter((value) => value !== id))}
					>
						{dimensionNameById.get(id) || id}
					</Tag>
				),
			},
			style: {
				width: 150,
				minHeight: 50,
				background: "#fff",
				border: "1px solid #91caff",
				borderRadius: 6,
				padding: "9px",
			},
		})),
		[dimensionNameById, modelDimensions],
	);

	const modelMetricNodes: Node[] = useMemo(() =>
		modelMetrics.map((id, index) => ({
			id: `model-metric-${id}`,
			position: { x: (index % 3) * 170, y: Math.floor(index / 3) * 78 },
			data: {
				label: (
					<Tag
						color="green"
						closable
						className="nodrag"
						onClose={() => setModelMetrics((current) => current.filter((value) => value !== id))}
					>
						{metricNameById.get(id) || id}
					</Tag>
				),
			},
			style: {
				width: 150,
				minHeight: 50,
				background: "#fff",
				border: "1px solid #95de64",
				borderRadius: 6,
				padding: "9px",
			},
		})),
		[metricNameById, modelMetrics],
	);

	const modelBaseColumns: ColumnsType<SemanticModel> = [
		{ title: "模型", dataIndex: "name" , sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
		{ title: "类型", dataIndex: "type", width: 90, render: (value) => value || "-" },
		{ title: "表名", dataIndex: "tableName", render: (value) => value || "-" , sorter: (a, b) => (a.tableName || "").localeCompare(b.tableName || "") },
		{ title: "状态", dataIndex: "status", width: 110, render: (value) => value || "-" },
		{
			title: "操作",
			dataIndex: "actions",
			width: 160,
			fixed: "right",
			render: (_, row) => (
				<Button size="small" icon={<EditOutlined />} onClick={() => openModelModal(row)}>
					编辑
				</Button>
			),
		},
	];

	const modelColumns = useMemo(
		() => appendDetailAction(modelBaseColumns, (row) => setDetailRow(row)),
		// eslint-disable-next-line react-hooks/exhaustive-deps
		[],
	);

	const artifactColumns: ColumnsType<SemanticGeneratedArtifact> = [
		{ title: "类型", dataIndex: "artifactType", width: 130, render: (value) => value || "-" },
		{ title: "路径", dataIndex: "path", render: (value) => value || "-" },
		{ title: "状态", dataIndex: "status", width: 110, render: (value) => value || "-" },
	];

	const previewColumns = useMemo<ColumnsType<Record<string, any>>>(
		() => (previewResult?.headers || []).map((name) => ({
			title: name,
			dataIndex: name,
			key: name,
			ellipsis: true,
			render: (value) => value == null ? <Text type="secondary">NULL</Text> : String(value),
		})),
		[previewResult?.headers],
	);

	const openModelModal = (model?: SemanticModel) => {
		form.resetFields();
		setEditingModel(model || null);
		form.setFieldsValue(model ? {
			objectId: model.objectId,
			type: model.type,
			name: model.name,
			tableName: model.tableName,
			description: model.description,
			grain: model.grain,
			materialization: model.materialization,
			refreshCycle: model.refreshCycle,
			status: model.status,
		} : { type: modelType, objectId: selectedObjectId });
		setModalOpen(true);
	};

	const closeModelModal = () => {
		setModalOpen(false);
		setEditingModel(null);
		form.resetFields();
	};

	const submitModel = async () => {
		const values = await form.validateFields();
		setSaving(true);
		try {
			const saved = editingModel?.id
				? await updateSemanticModel(editingModel.id, values)
				: await createSemanticModel(values);
			message.success("语义模型已保存");
			closeModelModal();
			loadSemanticData();
			if ((saved as any)?.id) setSelectedModelId((saved as any).id);
		} finally {
			setSaving(false);
		}
	};

	const saveModelCanvas = async () => {
		if (!selectedModelId) {
			message.warning("请选择语义模型");
			return;
		}
		const dimensionIds = modelDimensions.filter((id) => dimensions.some((item) => item.id === id));
		const metricIds = modelMetrics.filter((id) => metrics.some((item) => item.id === id));
		setSaving(true);
		try {
			await saveSemanticModelBindings(selectedModelId, { dimensionIds, metricIds });
			message.success("模型画布已保存");
		} finally {
			setSaving(false);
		}
	};

	const generateArtifacts = async () => {
		if (!selectedModelId) {
			message.warning("请选择一个语义模型");
			return;
		}
		setArtifactLoading(true);
		try {
			const result = await generateSemanticModelArtifacts(selectedModelId);
			setArtifacts((result as any)?.artifacts || []);
			message.success("已生成 SQL/dbt 产物");
		} finally {
			setArtifactLoading(false);
		}
	};

	const previewModelData = async () => {
		if (!selectedModelId) {
			message.warning("请选择一个语义模型");
			return;
		}
		setPreviewLoading(true);
		try {
			const result = await previewSemanticModelData(selectedModelId, 100);
			setPreviewResult(result);
			setPreviewOpen(true);
			if ((result as any)?.success === false) {
				message.warning((result as any)?.errorMessage || "预览执行失败，请检查模型 SQL");
			}
		} finally {
			setPreviewLoading(false);
		}
	};

	return (
		<div className="space-y-5 p-5" data-testid="semantic-datasets-page">
			<PageHeader
				title={semanticSectionMeta.models.title}
				actions={(
					<Space wrap>
						<Button icon={<PlusOutlined />} onClick={() => openModelModal()}>定义模型</Button>
						<Button icon={<SaveOutlined />} loading={saving} disabled={!selectedModelId} onClick={saveModelCanvas}>保存画布</Button>
						<Button icon={<PlayCircleOutlined />} loading={previewLoading} disabled={!selectedModelId} onClick={previewModelData}>预览数据</Button>
						<Button type="primary" icon={<CodeOutlined />} loading={artifactLoading} disabled={!selectedModelId} onClick={generateArtifacts}>生成 dbt</Button>
					</Space>
				)}
			/>

			<SemanticSectionNav activeSection="models" />

			<Alert
				type="info"
				showIcon
				message="DWS/ADS 输出边界"
				description="DWS 是可复用公共汇总模型，ADS 是面向具体看板、大屏或 API 的应用数据集；这里负责组合维度和指标并生成 SQL/dbt，发布审核放在下一步。"
			/>

			<Row gutter={[16, 16]}>
				<Col xs={24} xl={8}>
					<Card
						title="模型选择"
						extra={(
							<Segmented
								size="small"
								value={modelType}
								onChange={(value) => setModelType(value as "DWS" | "ADS")}
								options={[
									{ label: "公共汇总模型", value: "DWS", icon: <PartitionOutlined /> },
									{ label: "应用数据集", value: "ADS", icon: <DashboardOutlined /> },
								]}
							/>
						)}
					>
						<Form layout="vertical">
							<Form.Item label="业务对象">
								<Select
									allowClear
									loading={loading}
									value={selectedObjectId}
									placeholder="选择业务对象过滤维度和指标"
									options={objects.map((item) => ({ label: item.name, value: item.id }))}
									onChange={setSelectedObjectId}
								/>
							</Form.Item>
						</Form>
						<CompactTable<SemanticModel>
							rowKey="id"
							size="small"
							loading={loading}
							pagination={{ pageSize: 6 }}
							rowSelection={{
								type: "radio",
								selectedRowKeys: selectedModelId ? [selectedModelId] : [],
								onChange: (keys) => setSelectedModelId(String(keys[0] || "")),
							}}
							columns={modelColumns}
							dataSource={models.filter((item) => !modelType || item.type === modelType)}
						/>
					</Card>

					<Card className="mt-4" title="可用维度 / 指标">
						<Space direction="vertical" className="w-full">
							<div>
								<Text type="secondary">维度</Text>
								<div className="mt-2 flex flex-wrap gap-2">
									{visibleDimensions.length ? visibleDimensions.map((item) => (
										<Tag
											key={item.id}
											draggable
											onDragStart={(event) => dragField(event, { id: item.id, name: item.code || item.name, dataType: item.dataType, kind: "dimension" })}
											className="cursor-grab"
										>
											{item.name}
										</Tag>
									)) : <Text type="secondary">暂无已保存维度</Text>}
								</div>
							</div>
							<div>
								<Text type="secondary">指标</Text>
								<div className="mt-2 flex flex-wrap gap-2">
									{visibleMetrics.length ? visibleMetrics.map((item) => (
										<Tag
											key={item.id}
											color="green"
											draggable
											onDragStart={(event) => dragField(event, { id: item.id, name: item.code || item.name, dataType: item.formulaType, kind: "metric" })}
											className="cursor-grab"
										>
											{item.name}
										</Tag>
									)) : <Text type="secondary">暂无已保存指标</Text>}
								</div>
							</div>
						</Space>
					</Card>
				</Col>

				<Col xs={24} xl={16}>
					<Card title={`${modelType} 输出结构`}>
						<Row gutter={[16, 16]}>
							<Col xs={24} lg={12}>
								<Space direction="vertical" className="w-full">
									<Text strong>统计粒度</Text>
									<VisualFlowCanvas
										nodes={modelDimensionNodes}
										height={240}
										emptyText="拖入维度字段"
										onDropItem={(event) => addModelDimension(fieldFromDropPayload(event))}
									/>
								</Space>
							</Col>
							<Col xs={24} lg={12}>
								<Space direction="vertical" className="w-full">
									<Text strong>输出指标</Text>
									<VisualFlowCanvas
										nodes={modelMetricNodes}
										height={240}
										emptyText="拖入指标字段"
										onDropItem={(event) => addModelMetric(fieldFromDropPayload(event))}
									/>
								</Space>
							</Col>
						</Row>
					</Card>

					<Card className="mt-4" title="生成物">
						{artifacts.length ? (
							<CompactTable<SemanticGeneratedArtifact>
								rowKey="id"
								size="small"
								pagination={{ pageSize: 4 }}
								loading={artifactLoading}
								columns={artifactColumns}
								expandable={{
									expandedRowRender: (row) => (
										<Input.TextArea readOnly rows={10} value={row.content || ""} />
									),
								}}
								dataSource={artifacts}
							/>
						) : (
							<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="请选择模型后生成 SQL / dbt 产物" />
						)}
					</Card>
				</Col>
			</Row>

			<Modal
				open={modalOpen}
				title={editingModel ? "编辑模型" : "定义模型"}
				onCancel={closeModelModal}
				onOk={submitModel}
				confirmLoading={saving}
				destroyOnClose
			>
				<Form form={form} layout="vertical">
					<Form.Item name="objectId" label="业务对象">
						<Select allowClear options={objects.map((item) => ({ label: item.name, value: item.id }))} />
					</Form.Item>
					<Form.Item name="type" label="类型" rules={[{ required: true, message: "请选择类型" }]}>
						<Select options={[{ label: "DWS 公共汇总模型", value: "DWS" }, { label: "ADS 应用数据集", value: "ADS" }]} />
					</Form.Item>
					<Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
						<Input />
					</Form.Item>
					<Form.Item name="tableName" label="目标表名">
						<Input />
					</Form.Item>
					<Form.Item name="grain" label="统计粒度">
						<Input />
					</Form.Item>
					<Form.Item name="materialization" label="物化方式">
						<Select allowClear options={["view", "table", "incremental"].map((item) => ({ label: item, value: item }))} />
					</Form.Item>
					<Form.Item name="refreshCycle" label="刷新周期">
						<Select allowClear options={["manual", "hourly", "daily", "weekly"].map((item) => ({ label: item, value: item }))} />
					</Form.Item>
					<Form.Item name="status" label="状态">
						<Select allowClear options={["DRAFT", "ACTIVE", "DISABLED"].map((item) => ({ label: item, value: item }))} />
					</Form.Item>
					<Form.Item name="description" label="说明">
						<Input.TextArea rows={3} />
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={previewOpen}
				title="模型数据预览"
				onCancel={() => setPreviewOpen(false)}
				footer={<Button onClick={() => setPreviewOpen(false)}>关闭</Button>}
				width={960}
			>
				<Space direction="vertical" className="w-full">
					{previewResult?.success === false ? (
						<Alert type="warning" showIcon message="预览执行失败" description={previewResult.errorMessage || "请检查来源表、Join 条件和字段口径。"} />
					) : null}
					<Input.TextArea readOnly rows={8} value={previewResult?.sql || ""} />
					<CompactTable<Record<string, any>>
						size="small"
						rowKey={(_, index) => String(index)}
						columns={previewColumns}
						dataSource={(previewResult?.rows || []).map((row, index) => ({ ...row, __rowIndex: index }))}
						pagination={{ pageSize: 10 }}
						scroll={{ x: true }}
					/>
					<Text type="secondary">
						行数：{previewResult?.rowCount ?? 0}，耗时：{previewResult?.durationMs ?? 0} ms
					</Text>
				</Space>
			</Modal>
			<RecordDetailDrawer<SemanticModel>
				open={detailRow !== null}
				onClose={() => setDetailRow(null)}
				record={detailRow}
				columns={modelBaseColumns}
				title="模型详情"
			/>
		</div>
	);
}
