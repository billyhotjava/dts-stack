import { useEffect, useMemo, useState } from "react";
import type React from "react";
import { Alert, Button, Card, Col, Empty, Form, Input, Modal, Row, Select, Space, Table, Tag, Typography, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { DatabaseOutlined, DeploymentUnitOutlined, EditOutlined, PlusOutlined, SaveOutlined } from "@ant-design/icons";
import { MarkerType, type Edge, type Node } from "@xyflow/react";
import { PageHeader } from "@/components/page-header";
import { VisualFlowCanvas, type VisualFlowDropEvent } from "@/components/visual-canvas/VisualFlowCanvas";
import { listDatasets } from "@/api/platformApi";
import {
	createSemanticBusinessObject,
	listSemanticBusinessObjects,
	listSemanticObjectTableMappings,
	listSemanticSubjectDomains,
	saveSemanticObjectTableMappings,
	type SemanticBusinessObject,
	type SemanticObjectTableMapping,
	type SemanticSubjectDomain,
	updateSemanticBusinessObject,
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

type JoinTable = {
	id: string;
	name: string;
};

type JoinRule = {
	leftTable?: string;
	leftField?: string;
	rightTable?: string;
	rightField?: string;
	joinType?: string;
};

const normalizeDataset = (item: any): DatasetOption => ({
	id: String(item.id || item.key || item.name || item.tableName || item.hiveTable),
	name: String(item.name || item.displayName || item.hiveTable || item.tableName || item.id || ""),
	table: String(item.hiveTable || item.tableName || item.name || ""),
	layer: String(item.warehouseLayer || item.layer || ""),
	database: String(item.databaseName || item.database || ""),
	schema: String(item.schemaName || item.schema || ""),
});

const splitQualifiedField = (value?: string) => {
	const parts = String(value || "").split(".");
	if (parts.length < 2) return { table: undefined, field: value };
	return { table: parts.slice(0, -1).join("."), field: parts[parts.length - 1] };
};

export default function SemanticObjectsPage() {
	const [form] = Form.useForm();
	const [domains, setDomains] = useState<SemanticSubjectDomain[]>([]);
	const [objects, setObjects] = useState<SemanticBusinessObject[]>([]);
	const [datasets, setDatasets] = useState<DatasetOption[]>([]);
	const [selectedObjectId, setSelectedObjectId] = useState<string>();
	const [selectedDatasetKey, setSelectedDatasetKey] = useState<string>();
	const [joinTables, setJoinTables] = useState<JoinTable[]>([]);
	const [joinRules, setJoinRules] = useState<JoinRule[]>([]);
	const [objectsLoading, setObjectsLoading] = useState(false);
	const [datasetsLoading, setDatasetsLoading] = useState(false);
	const [mappingsLoading, setMappingsLoading] = useState(false);
	const [saving, setSaving] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [editingObject, setEditingObject] = useState<SemanticBusinessObject | null>(null);

	const loadDomains = () => {
		listSemanticSubjectDomains()
			.then((resp) => setDomains(asArray<SemanticSubjectDomain>(resp)))
			.catch(() => setDomains([]));
	};

	const loadObjects = () => {
		setObjectsLoading(true);
		listSemanticBusinessObjects()
			.then((resp) => setObjects(asArray<SemanticBusinessObject>(resp)))
			.catch(() => setObjects([]))
			.finally(() => setObjectsLoading(false));
	};

	const loadDatasets = () => {
		setDatasetsLoading(true);
		listDatasets({ page: 0, size: 120 })
			.then((resp: any) => setDatasets(asArray<any>(resp).map(normalizeDataset)))
			.catch(() => setDatasets([]))
			.finally(() => setDatasetsLoading(false));
	};

	useEffect(() => {
		loadDomains();
		loadObjects();
		loadDatasets();
	}, []);

	const dwdDatasets = useMemo(
		() => datasets.filter(isDwdSemanticInput),
		[datasets],
	);

	const selectedObject = useMemo(
		() => objects.find((item) => item.id === selectedObjectId),
		[objects, selectedObjectId],
	);

	const selectedDataset = useMemo(
		() => dwdDatasets.find((item) => item.id === selectedDatasetKey || (item.table || item.name) === selectedDatasetKey),
		[dwdDatasets, selectedDatasetKey],
	);

	useEffect(() => {
		if (!selectedObjectId) {
			setJoinTables([]);
			setJoinRules([]);
			return;
		}
		let ignore = false;
		setMappingsLoading(true);
		listSemanticObjectTableMappings(selectedObjectId)
			.then((resp) => {
				if (ignore) return;
				const rows = asArray<SemanticObjectTableMapping>(resp);
				setJoinTables(rows.map((item) => ({ id: String(item.id || item.tableName), name: String(item.tableName || "") })).filter((item) => item.name));
				setJoinRules(
					rows
						.filter((item) => String(item.tableRole || "").toUpperCase() !== "PRIMARY")
						.map((item) => {
							const [left, right] = String(item.joinExpression || "").split("=");
							const leftSide = splitQualifiedField(left?.trim());
							const rightSide = splitQualifiedField(right?.trim());
							return {
								joinType: String(item.tableRole || "left").toLowerCase(),
								leftTable: leftSide.table,
								leftField: leftSide.field,
								rightTable: rightSide.table || item.tableName,
								rightField: rightSide.field || item.joinExpression,
							};
						}),
				);
			})
			.catch(() => {
				if (!ignore) {
					setJoinTables([]);
					setJoinRules([]);
				}
			})
			.finally(() => {
				if (!ignore) setMappingsLoading(false);
			});
		return () => {
			ignore = true;
		};
	}, [selectedObjectId]);

	const objectColumns: ColumnsType<SemanticBusinessObject> = [
		{ title: "业务对象", dataIndex: "name" },
		{ title: "编码", dataIndex: "code", render: (value) => value || "-" },
		{ title: "主表", dataIndex: "mainTable", render: (value) => value || "-" },
		{ title: "主键", dataIndex: "primaryKey", width: 140, render: (value) => value || "-" },
		{
			title: "操作",
			width: 90,
			render: (_, row) => (
				<Button size="small" icon={<EditOutlined />} onClick={() => openModal(row)}>
					编辑
				</Button>
			),
		},
	];

	const addDatasetToJoinCanvas = (dataset: DatasetOption) => {
		const name = dataset.table || dataset.name;
		setSelectedDatasetKey(dataset.id);
		setJoinTables((current) => current.some((item) => item.name === name) ? current : [...current, { id: dataset.id, name }]);
	};

	const dragDataset = (event: React.DragEvent<HTMLElement>, dataset: DatasetOption) => {
		event.dataTransfer.setData("application/json", JSON.stringify({ kind: "dataset", value: dataset }));
		event.dataTransfer.effectAllowed = "copy";
	};

	const dropDatasetToJoinCanvas = ({ payload }: VisualFlowDropEvent) => {
		if (payload.kind !== "dataset" || !payload.value || typeof payload.value !== "object") return;
		addDatasetToJoinCanvas(payload.value as DatasetOption);
	};

	const addSelectedTableToCanvas = () => {
		if (!selectedDataset) {
			message.warning("请选择 DWD 明细模型");
			return;
		}
		addDatasetToJoinCanvas(selectedDataset);
	};

	const addJoinRule = () => {
		setJoinRules((current) => [...current, { joinType: "left" }]);
	};

	const removeJoinTable = (tableName: string) => {
		setJoinTables((current) => current.filter((item) => item.name !== tableName));
		setJoinRules((current) => current.filter((item) => item.leftTable !== tableName && item.rightTable !== tableName));
	};

	const findJoinTableId = (tableName?: string) => {
		if (!tableName) return undefined;
		const normalized = tableName.trim();
		return joinTables.find((item) => item.name === normalized || item.name.endsWith(`.${normalized}`))?.id;
	};

	const joinCanvasNodes: Node[] = useMemo(() =>
		joinTables.map((table, index) => ({
			id: table.id,
			position: {
				x: (index % 2) * 260,
				y: Math.floor(index / 2) * 130,
			},
			data: {
				label: (
					<Space direction="vertical" size={2}>
						<Tag color={index === 0 ? "blue" : "default"}>{index === 0 ? "主表" : "关联表"}</Tag>
						<Text strong>{table.name}</Text>
						<Button size="small" type="link" danger onClick={() => removeJoinTable(table.name)}>移除</Button>
					</Space>
				),
			},
			style: {
				width: 210,
				minHeight: 96,
				background: "#fff",
				border: `1px solid ${index === 0 ? "#1677ff" : "#d9d9d9"}`,
				borderRadius: 6,
				padding: "10px 12px",
				boxShadow: index === 0 ? "0 0 0 2px rgba(22, 119, 255, 0.08)" : "0 1px 3px rgba(15, 23, 42, 0.08)",
			},
		})),
		[joinTables],
	);

	const joinCanvasEdges: Edge[] = useMemo(() =>
		joinRules
			.map((rule, index): Edge | null => {
				const sourceName = rule.leftTable || String(rule.leftField || "").split(".")[0];
				const targetName = rule.rightTable || String(rule.rightField || "").split(".")[0];
				const source = findJoinTableId(sourceName) || joinTables[0]?.id;
				const target = findJoinTableId(targetName) || joinTables[index + 1]?.id;
				if (!source || !target || source === target) return null;
				return {
					id: `join-${index}`,
					source,
					target,
					type: "smoothstep",
					label: `${String(rule.joinType || "left").toUpperCase()} JOIN`,
					labelStyle: { fontSize: 10, fill: "#475569", fontWeight: 600 },
					labelBgPadding: [6, 3] as [number, number],
					labelBgBorderRadius: 4,
					style: { stroke: "#1677ff", strokeWidth: 1.6 },
					markerEnd: { type: MarkerType.ArrowClosed, width: 16, height: 16 },
				};
			})
			.filter((edge): edge is Edge => Boolean(edge)),
		[joinRules, joinTables],
	);

	const saveJoinCanvas = async () => {
		if (!selectedObjectId) {
			message.warning("请选择业务对象");
			return;
		}
		if (!joinTables.length) {
			message.warning("请至少加入一个 DWD 明细模型");
			return;
		}
		const mappings = joinTables.map((table, index) => {
			const rule = joinRules[index - 1];
			return {
				tableName: table.name,
				tableRole: index === 0 ? "PRIMARY" : (rule?.joinType || "left").toUpperCase(),
				joinExpression: index === 0 ? undefined : [rule?.leftField, rule?.rightField].filter(Boolean).join(" = "),
				sortOrder: index,
			};
		});
		setSaving(true);
		try {
			await saveSemanticObjectTableMappings(selectedObjectId, mappings);
			message.success("Join 画布已保存");
		} finally {
			setSaving(false);
		}
	};

	const openModal = (object?: SemanticBusinessObject) => {
		form.resetFields();
		setEditingObject(object || null);
		if (object) {
			form.setFieldsValue({
				domainId: object.domainId,
				code: object.code,
				name: object.name,
				primaryKey: object.primaryKey,
				mainTable: object.mainTable,
				description: object.description,
			});
		} else {
			form.setFieldsValue({ mainTable: selectedDataset?.table || selectedDataset?.name });
		}
		setModalOpen(true);
	};

	const closeModal = () => {
		setModalOpen(false);
		setEditingObject(null);
		form.resetFields();
	};

	const submitObject = async () => {
		const values = await form.validateFields();
		setSaving(true);
		try {
			const saved = editingObject?.id
				? await updateSemanticBusinessObject(editingObject.id, values)
				: await createSemanticBusinessObject(values);
			message.success("业务对象已保存");
			closeModal();
			await loadObjects();
			if ((saved as any)?.id) setSelectedObjectId((saved as any).id);
		} finally {
			setSaving(false);
		}
	};

	return (
		<div className="space-y-5 p-5" data-testid="semantic-objects-page">
			<PageHeader
				title={semanticSectionMeta.objects.title}
				actions={(
					<Space wrap>
						<Button icon={<PlusOutlined />} onClick={() => openModal()}>新建业务对象</Button>
						<Button type="primary" icon={<SaveOutlined />} loading={saving} disabled={!selectedObjectId} onClick={saveJoinCanvas}>保存 Join</Button>
					</Space>
				)}
			/>

			<SemanticSectionNav activeSection="objects" />

			<Alert
				type="info"
				showIcon
				message="业务对象 Join 边界"
				description="业务对象从 DWD 明细模型开始设计，Join 画布只负责沉淀对象与物理表映射；指标、DWS/ADS 输出在后续页面配置。"
			/>

			<Row gutter={[16, 16]}>
				<Col xs={24} xl={8}>
					<Card title="业务对象">
						<Table<SemanticBusinessObject>
							rowKey="id"
							size="small"
							loading={objectsLoading}
							pagination={{ pageSize: 8 }}
							rowSelection={{
								type: "radio",
								selectedRowKeys: selectedObjectId ? [selectedObjectId] : [],
								onChange: (keys) => setSelectedObjectId(String(keys[0] || "")),
							}}
							columns={objectColumns}
							dataSource={objects}
						/>
					</Card>

					<Card className="mt-4" title="DWD 明细模型">
						<Select
							className="mb-3 w-full"
							allowClear
							showSearch
							loading={datasetsLoading}
							value={selectedDatasetKey}
							placeholder="选择 DWD 明细模型"
							optionFilterProp="label"
							options={dwdDatasets.map((item) => ({
								label: item.table || item.name,
								value: item.id,
							}))}
							onChange={setSelectedDatasetKey}
						/>
						<Button className="mb-3" icon={<DeploymentUnitOutlined />} onClick={addSelectedTableToCanvas}>加入画布</Button>
						{dwdDatasets.length ? (
							<div className="max-h-72 space-y-2 overflow-auto rounded border border-dashed border-slate-200 p-3">
								{dwdDatasets.map((item) => (
									<div
										key={item.id}
										draggable
										className="cursor-grab rounded border border-slate-200 bg-white p-2 active:cursor-grabbing"
										onDragStart={(event) => dragDataset(event, item)}
										onDoubleClick={() => addDatasetToJoinCanvas(item)}
									>
										<Text strong>{item.table || item.name}</Text>
										<div><Text type="secondary" className="text-xs">{item.database || "-"} / {item.schema || "-"}</Text></div>
									</div>
								))}
							</div>
						) : (
							<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={datasetsLoading ? "正在读取资产目录" : "资产目录暂无 DWD 明细模型"} />
						)}
					</Card>
				</Col>

				<Col xs={24} xl={16}>
					<Card
						title={selectedObject ? `${selectedObject.name} Join 画布` : "Join 画布"}
						extra={<Button icon={<PlusOutlined />} onClick={addJoinRule}>新增 Join 条件</Button>}
						loading={mappingsLoading}
					>
						<Row gutter={[16, 16]}>
							<Col xs={24} lg={15}>
								<VisualFlowCanvas
									nodes={joinCanvasNodes}
									edges={joinCanvasEdges}
									height={430}
									emptyText="从左侧拖入 DWD 明细模型"
									onDropItem={dropDatasetToJoinCanvas}
								/>
							</Col>
							<Col xs={24} lg={9}>
								<Space direction="vertical" className="w-full">
									<Text strong>Join 条件</Text>
									{joinRules.length ? joinRules.map((rule, index) => (
										<Card key={index} size="small">
											<Space direction="vertical" className="w-full">
												<Select
													size="small"
													value={rule.joinType}
													options={[
														{ label: "Left Join", value: "left" },
														{ label: "Inner Join", value: "inner" },
														{ label: "Full Join", value: "full" },
													]}
													onChange={(value) => setJoinRules((current) => current.map((item, i) => i === index ? { ...item, joinType: value } : item))}
												/>
												<Select
													size="small"
													allowClear
													placeholder="左表"
													value={rule.leftTable}
													options={joinTables.map((item) => ({ label: item.name, value: item.name }))}
													onChange={(value) => setJoinRules((current) => current.map((item, i) => i === index ? { ...item, leftTable: value } : item))}
												/>
												<Input
													size="small"
													placeholder="左字段"
													value={rule.leftField}
													onChange={(event) => setJoinRules((current) => current.map((item, i) => i === index ? { ...item, leftField: event.target.value } : item))}
												/>
												<Select
													size="small"
													allowClear
													placeholder="右表"
													value={rule.rightTable}
													options={joinTables.map((item) => ({ label: item.name, value: item.name }))}
													onChange={(value) => setJoinRules((current) => current.map((item, i) => i === index ? { ...item, rightTable: value } : item))}
												/>
												<Input
													size="small"
													placeholder="右字段"
													value={rule.rightField}
													onChange={(event) => setJoinRules((current) => current.map((item, i) => i === index ? { ...item, rightField: event.target.value } : item))}
												/>
											</Space>
										</Card>
									)) : (
										<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无 Join 条件" />
									)}
								</Space>
							</Col>
						</Row>
					</Card>
				</Col>
			</Row>

			<Modal
				open={modalOpen}
				title={editingObject ? "编辑业务对象" : "新建业务对象"}
				onCancel={closeModal}
				onOk={submitObject}
				confirmLoading={saving}
				destroyOnClose
			>
				<Form form={form} layout="vertical">
					<Form.Item name="domainId" label="主题域">
						<Select allowClear options={domains.map((item) => ({ label: item.name, value: item.id }))} />
					</Form.Item>
					<Form.Item name="code" label="编码" rules={[{ required: true, message: "请输入编码" }]}>
						<Input />
					</Form.Item>
					<Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
						<Input />
					</Form.Item>
					<Form.Item name="primaryKey" label="主键">
						<Input />
					</Form.Item>
					<Form.Item name="mainTable" label="主表">
						<Input prefix={<DatabaseOutlined />} />
					</Form.Item>
					<Form.Item name="description" label="说明">
						<Input.TextArea rows={3} />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
