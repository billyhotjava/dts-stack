import { useCallback, useEffect, useMemo, useState } from "react";
import { Button, Drawer, Empty, Form, Input, InputNumber, Select, Space } from "antd";
import { Plus } from "lucide-react";
import { toast } from "sonner";
import { CompactTable } from "@/components/table";
import { VisualFlowCanvas } from "@/components/visual-canvas/VisualFlowCanvas";
import type { ColumnsType } from "antd/es/table";
import {
	listSemanticSubjectDomains,
	listSemanticBusinessObjects,
	listSemanticObjectTableMappings,
	createSemanticBusinessObject,
	saveSemanticObjectTableMappings,
	type SemanticSubjectDomain,
	type SemanticBusinessObject,
	type SemanticObjectTableMapping,
} from "@/api/semanticModelingApi";
import { SemanticWorkspaceFrame } from "./semantic-workspace/SemanticWorkspaceFrame";
import { buildSemanticObjectJoinGraph } from "./semanticObjectMappings.helpers";

const TABLE_ROLE_OPTIONS = [
	{ label: "主表", value: "main" },
	{ label: "关联表", value: "join" },
	{ label: "维表", value: "dimension" },
	{ label: "事实表", value: "fact" },
];

type MappingFormValues = {
	mappings?: SemanticObjectTableMapping[];
};

export default function SemanticObjectsPage() {
	const [domains, setDomains] = useState<SemanticSubjectDomain[]>([]);
	const [objects, setObjects] = useState<SemanticBusinessObject[]>([]);
	const [loading, setLoading] = useState(false);
	const [domainsLoading, setDomainsLoading] = useState(false);
	const [selected, setSelected] = useState<SemanticBusinessObject | null>(null);
	const [mappings, setMappings] = useState<SemanticObjectTableMapping[]>([]);
	const [mappingLoading, setMappingLoading] = useState(false);
	const [mappingSaving, setMappingSaving] = useState(false);
	const [mappingDrawerOpen, setMappingDrawerOpen] = useState(false);
	const [createOpen, setCreateOpen] = useState(false);
	const [form] = Form.useForm();
	const [mappingForm] = Form.useForm<MappingFormValues>();

	const loadDomains = useCallback(async () => {
		setDomainsLoading(true);
		try {
			const list = await listSemanticSubjectDomains();
			setDomains(Array.isArray(list) ? (list as SemanticSubjectDomain[]) : []);
		} catch {
			setDomains([]);
		} finally {
			setDomainsLoading(false);
		}
	}, []);

	const load = useCallback(async () => {
		setLoading(true);
		try {
			const list = await listSemanticBusinessObjects();
			setObjects(Array.isArray(list) ? (list as SemanticBusinessObject[]) : []);
		} catch {
			/* global interceptor */
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		void load();
	}, [load]);

	useEffect(() => {
		void loadDomains();
	}, [loadDomains]);

	const domainNameById = useMemo(
		() => new Map(domains.map((domain) => [domain.id, domain.name || domain.code || domain.id])),
		[domains],
	);

	const domainOptions = useMemo(
		() =>
			domains.map((domain) => ({
				value: domain.id,
				label: domain.name ? (domain.code ? `${domain.name} (${domain.code})` : domain.name) : domain.code || domain.id,
			})),
		[domains],
	);

	const loadMappings = useCallback(async (obj: SemanticBusinessObject) => {
		setMappingLoading(true);
		try {
			const maps = await listSemanticObjectTableMappings(obj.id);
			setMappings(Array.isArray(maps) ? (maps as SemanticObjectTableMapping[]) : []);
		} catch {
			setMappings([]);
		} finally {
			setMappingLoading(false);
		}
	}, []);

	const handleSelect = async (obj: SemanticBusinessObject) => {
		setSelected(obj);
		await loadMappings(obj);
	};

	const handleCreate = async () => {
		try {
			const values = await form.validateFields();
			await createSemanticBusinessObject(values);
			toast.success("业务对象已创建");
			setCreateOpen(false);
			void load();
		} catch (err: unknown) {
			if (err && typeof err === "object" && "errorFields" in err) return;
		}
	};

	const openMappingDrawer = () => {
		if (!selected) return;
		const initialMappings = mappings.length > 0
			? mappings
			: [{
				objectId: selected.id,
				tableName: selected.mainTable ?? "",
				tableRole: "main",
				joinExpression: "",
				sortOrder: 0,
			}];
		mappingForm.setFieldsValue({ mappings: initialMappings });
		setMappingDrawerOpen(true);
	};

	const handleSaveMappings = async () => {
		if (!selected) return;
		try {
			const values = await mappingForm.validateFields();
			const nextMappings = (values.mappings ?? [])
				.map((mapping, index) => ({
					...mapping,
					objectId: selected.id,
					tableName: String(mapping.tableName ?? "").trim(),
					tableRole: mapping.tableRole || (index === 0 ? "main" : "join"),
					joinExpression: mapping.joinExpression?.trim(),
					sortOrder: typeof mapping.sortOrder === "number" ? mapping.sortOrder : index,
				}))
				.filter((mapping) => mapping.tableName);
			setMappingSaving(true);
			await saveSemanticObjectTableMappings(selected.id, nextMappings);
			toast.success("表映射已保存");
			setMappingDrawerOpen(false);
			await loadMappings(selected);
		} catch (err: unknown) {
			if (err && typeof err === "object" && "errorFields" in err) return;
		} finally {
			setMappingSaving(false);
		}
	};

	const columns: ColumnsType<SemanticBusinessObject> = [
		{ title: "编码", dataIndex: "code", width: 120 },
		{ title: "名称", dataIndex: "name" },
		{
			title: "治理主题域",
			dataIndex: "domainId",
			width: 180,
			render: (value?: string) =>
				value ? domainNameById.get(value) || <span style={{ color: "#aaa" }}>未匹配治理域</span> : <span style={{ color: "#aaa" }}>未归属</span>,
		},
		{
			title: "主表",
			dataIndex: "mainTable",
			render: (v?: string) => v ?? <span style={{ color: "#aaa" }}>-</span>,
		},
		{
			title: "操作",
			key: "actions",
			width: 100,
			render: (_: unknown, row: SemanticBusinessObject) => (
				<Button type="link" size="small" onClick={() => void handleSelect(row)}>
					维护映射
				</Button>
			),
		},
	];

	const { nodes, edges } = buildSemanticObjectJoinGraph(mappings);
	const objectsWithMainTable = objects.filter((item) => item.mainTable).length;
	const objectsWithDomain = objects.filter((item) => item.domainId).length;

	return (
		<SemanticWorkspaceFrame
			activeKey="objects"
			title="业务对象"
			description="维护业务对象的主表、主键和关联表。"
			stats={[
				{ label: "业务对象", value: objects.length, tone: "blue" },
				{ label: "治理主题域", value: domains.length, tone: "green" },
				{ label: "已归属主题域", value: objectsWithDomain, tone: objectsWithDomain > 0 ? "green" : "amber" },
				{ label: "已配置主表", value: objectsWithMainTable, tone: objectsWithMainTable > 0 ? "green" : "amber" },
				{ label: "当前映射表", value: selected ? mappings.length : "-", tone: "gray" },
			]}
			actions={
				<Button
					type="primary"
					data-testid="semantic-objects-create"
					onClick={() => {
						form.resetFields();
						setCreateOpen(true);
					}}
				>
					<Plus size={16} />
					新建业务对象
				</Button>
			}
		>
			<div className="grid gap-4 xl:grid-cols-[minmax(520px,1fr)_520px]" data-testid="semantic-objects-page">
				<div className="rounded-lg border border-gray-200 bg-white p-3 shadow-sm">
					<CompactTable<SemanticBusinessObject>
						rowKey="id"
						columns={columns}
						dataSource={objects}
						loading={loading}
					/>
				</div>
				{selected && (
					<div
							style={{
								border: "1px solid #e5e7eb",
								borderRadius: 6,
								overflow: "hidden",
								background: "#fff",
							}}
					>
						<div className="p-2 border-b border-gray-200 flex items-center justify-between gap-2">
							<div>
								<div className="text-sm font-medium text-gray-600">{selected.name} — join 关系图</div>
								<div className="text-xs text-gray-400">{mappings.length} 张表映射</div>
							</div>
							<Space size="small">
								<Button size="small" onClick={() => void loadMappings(selected)} loading={mappingLoading}>
									刷新
								</Button>
								<Button size="small" type="primary" onClick={openMappingDrawer}>
									编辑表映射
								</Button>
							</Space>
						</div>
						{mappingLoading ? (
							<div className="p-6 text-sm text-gray-400">加载表映射...</div>
						) : mappings.length === 0 ? (
							<div className="p-6">
								<Empty description="暂无表映射">
									<Button type="primary" onClick={openMappingDrawer}>
										去编辑
									</Button>
								</Empty>
							</div>
						) : (
							<VisualFlowCanvas nodes={nodes} edges={edges} height={300} />
						)}
					</div>
				)}
			</div>
			<Drawer
				title="新建业务对象"
				open={createOpen}
				onClose={() => setCreateOpen(false)}
				footer={
					<Button type="primary" onClick={() => void handleCreate()} block>
						创建
					</Button>
				}
			>
				<Form form={form} layout="vertical">
					<Form.Item name="code" label="编码" rules={[{ required: true }]}>
						<Input placeholder="ORDER" />
					</Form.Item>
					<Form.Item name="name" label="名称" rules={[{ required: true }]}>
						<Input placeholder="订单" />
					</Form.Item>
					<Form.Item name="domainId" label="治理主题域" rules={[{ required: true, message: "请选择治理主题域" }]}>
						<Select
							showSearch
							optionFilterProp="label"
							loading={domainsLoading}
							options={domainOptions}
							placeholder="选择数据治理中心主题域"
						/>
					</Form.Item>
					<Form.Item name="mainTable" label="主表">
						<Input placeholder="dwd_order_detail" />
					</Form.Item>
					<Form.Item name="primaryKey" label="主键">
						<Input placeholder="order_id" />
					</Form.Item>
				</Form>
			</Drawer>
			<Drawer
				title={selected ? `${selected.name} · 编辑表映射` : "编辑表映射"}
				open={mappingDrawerOpen}
				onClose={() => setMappingDrawerOpen(false)}
				width={620}
				data-testid="semantic-object-mappings-drawer"
				footer={
					<Space className="w-full justify-end">
						<Button onClick={() => setMappingDrawerOpen(false)}>取消</Button>
						<Button type="primary" loading={mappingSaving} onClick={() => void handleSaveMappings()}>
							保存映射
						</Button>
					</Space>
				}
			>
				<Form form={mappingForm} layout="vertical">
					<Form.List name="mappings">
						{(fields, { add, remove }) => (
							<div className="space-y-3">
								{fields.map(({ key, name, ...restField }) => (
									<div key={key} className="border border-gray-200 rounded-md p-3">
										<Form.Item {...restField} name={[name, "id"]} hidden>
											<Input />
										</Form.Item>
										<Form.Item {...restField} name={[name, "objectId"]} hidden>
											<Input />
										</Form.Item>
										<div className="grid grid-cols-2 gap-3">
											<Form.Item
												{...restField}
												name={[name, "tableName"]}
												label="表名"
												rules={[{ required: true, message: "请输入表名" }]}
											>
												<Input placeholder="dwd_order_detail" />
											</Form.Item>
											<Form.Item {...restField} name={[name, "tableRole"]} label="表角色">
												<Select options={TABLE_ROLE_OPTIONS} placeholder="选择角色" />
											</Form.Item>
										</div>
										<Form.Item {...restField} name={[name, "joinExpression"]} label="关联表达式">
											<Input.TextArea
												rows={2}
												placeholder="fact.customer_id = dim_customer.customer_id"
												style={{ fontFamily: "monospace", fontSize: 12 }}
											/>
										</Form.Item>
										<div className="flex items-end justify-between gap-3">
											<Form.Item {...restField} name={[name, "sortOrder"]} label="排序" className="mb-0">
												<InputNumber min={0} style={{ width: 120 }} />
											</Form.Item>
											<Button danger size="small" onClick={() => remove(name)} disabled={fields.length <= 1}>
												删除
											</Button>
										</div>
									</div>
								))}
								<Button
									block
									onClick={() =>
										add({
											objectId: selected?.id,
											tableRole: fields.length === 0 ? "main" : "join",
											sortOrder: fields.length,
										})
									}
								>
									新增表映射
								</Button>
							</div>
						)}
					</Form.List>
				</Form>
			</Drawer>
		</SemanticWorkspaceFrame>
	);
}
