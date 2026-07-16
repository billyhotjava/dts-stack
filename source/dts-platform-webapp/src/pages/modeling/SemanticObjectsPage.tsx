import { useCallback, useEffect, useMemo, useState } from "react";
import { Button, Drawer, Empty, Form, Input, InputNumber, Select, Space } from "antd";
import { Plus } from "lucide-react";
import { toast } from "sonner";
import { useRouter, useSearchParams } from "@/routes/hooks";
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
import { listModelingBusinessObjects } from "@/api/modelingApi";
import { SemanticWorkspaceFrame } from "./semantic-workspace/SemanticWorkspaceFrame";
import { buildSemanticObjectJoinGraph } from "./semanticObjectMappings.helpers";
import { buildBusinessObjectLedgerRows, toLegacyBusinessObject, type BusinessObjectLedgerRow } from "./modelingLedger";
import { generateBusinessObjectCode } from "./businessObjectCode";
import { resolveWarehousePlanningContext } from "../governance/warehousePlanningContext";
import { buildBusinessModelingRoute, resolveBusinessModelingContext } from "./businessModelingContext";

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
	const router = useRouter();
	const searchParams = useSearchParams();
	const planningResolution = useMemo(() => resolveWarehousePlanningContext(searchParams), [searchParams]);
	const context = useMemo(
		() => resolveBusinessModelingContext(searchParams, planningResolution.context),
		[planningResolution.context, searchParams],
	);
	const processId = context.processId;
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
	const [autoCode, setAutoCode] = useState("");
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
		if (!processId) {
			setObjects([]);
			setLoading(false);
			return;
		}
		try {
			try {
				const vnext = await listModelingBusinessObjects({ processId, status: "DRAFT" });
				if (Array.isArray(vnext) && vnext.length > 0) {
					setObjects(vnext.map(toLegacyBusinessObject));
					return;
				}
			} catch {
				// Keep the legacy read path available while a tenant is migrating.
			}
			const list = await listSemanticBusinessObjects({ processId });
			setObjects(Array.isArray(list) ? (list as SemanticBusinessObject[]) : []);
		} catch {
			/* global interceptor */
		} finally {
			setLoading(false);
		}
	}, [processId]);

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
	const ledgerRows = useMemo(() => buildBusinessObjectLedgerRows(objects), [objects]);
	const processRoute = buildBusinessModelingRoute("/governance/subjects?focus=business-processes&from=business-object-ledger", context);

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

	const openCreate = () => {
		if (!processId) {
			toast.info("请先选择业务过程，再登记业务对象");
			router.push(processRoute);
			return;
		}
		form.resetFields();
		form.setFieldsValue({ processId });
		// 编码由系统生成，手工编码会带来重复与写法漂移。
		setAutoCode(generateBusinessObjectCode(objects.map((item) => item.code || "")));
		setCreateOpen(true);
	};

	const handleCreate = async () => {
		try {
			const values = await form.validateFields();
			await createSemanticBusinessObject({ ...values, code: autoCode, processId });
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

	// 台账只呈现业务对象的关键点：是什么（名称/类型）、粒度是什么、归属哪个域、怎么实现、下一步做什么。
	// 业务过程列不再展示（列表已按当前业务过程过滤）；来源模型与标准状态细节移入详情维护。
	const columns: ColumnsType<BusinessObjectLedgerRow> = [
		{
			title: "业务对象",
			key: "name",
			width: 200,
			render: (_: unknown, row: BusinessObjectLedgerRow) => (
				<div>
					<div className="font-medium">{row.name}</div>
					<div style={{ color: "#9ca3af", fontSize: 12 }}>{row.code}</div>
				</div>
			),
		},
		{ title: "类型", dataIndex: "objectKind", width: 85 },
		{
			title: "业务键 / 粒度",
			key: "grain",
			width: 220,
			render: (_: unknown, row: BusinessObjectLedgerRow) => (
				<div>
					<div>{row.keyLabel}</div>
					<div style={{ color: row.grainStatus === "READY" ? "#16a34a" : "#d97706", fontSize: 12 }}>{row.grainLabel}</div>
				</div>
			),
		},
		{
			title: "治理主题域",
			dataIndex: "domainId",
			width: 180,
			render: (value?: string) =>
				value ? domainNameById.get(value) || <span style={{ color: "#aaa" }}>未匹配治理域</span> : <span style={{ color: "#aaa" }}>未归属</span>,
		},
		{
			title: "实现模式",
			dataIndex: "implementationMode",
			width: 110,
			render: (v?: string) => v === "DBT_MANAGED" ? "dbt 原生" : v === "LEGACY_READONLY" ? "兼容只读" : "设计器生成",
		},
		{
			title: "下一步",
			dataIndex: "nextAction",
			render: (v?: string, row?: BusinessObjectLedgerRow) => (
				<span style={{ color: row?.grainStatus === "BLOCKED" ? "#d97706" : "#0284c7" }}>{v}</span>
			),
		},
		{
			title: "操作",
			key: "actions",
			width: 100,
			render: (_: unknown, row: BusinessObjectLedgerRow) => (
				<Button type="link" size="small" onClick={() => void handleSelect(row)}>
					维护映射
				</Button>
			),
		},
	];

	const { nodes, edges } = buildSemanticObjectJoinGraph(mappings);
	const objectsWithMainTable = objects.filter((item) => item.mainTable).length;

	return (
		<SemanticWorkspaceFrame
			activeKey="objects"
			title="业务对象台账"
			description="业务对象是业务过程涉及的人和物，承载维度、指标与模型关系；编码由系统生成，物理映射在详情维护。"
			context={context}
			stats={[
				{ label: "业务对象", value: objects.length, tone: "blue" },
				{ label: "已配置主表", value: objectsWithMainTable, tone: objectsWithMainTable > 0 ? "green" : "amber" },
			]}
			actions={
				<Button
					type="primary"
					data-testid="semantic-objects-create"
					onClick={openCreate}
				>
					<Plus size={16} />
					{processId ? "新建业务对象" : "选择业务过程后新建"}
				</Button>
			}
		>
			<div className="grid gap-4 xl:grid-cols-[minmax(520px,1fr)_520px]" data-testid="semantic-objects-page">
				<div className="rounded-lg border border-gray-200 bg-white p-3 shadow-sm">
					<CompactTable<BusinessObjectLedgerRow>
						rowKey="id"
						columns={columns}
						dataSource={ledgerRows}
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
					<Form.Item name="processId" hidden>
						<Input />
					</Form.Item>
					<Form.Item name="name" label="名称" rules={[{ required: true }]}>
						<Input placeholder="订单" />
					</Form.Item>
					<Form.Item label="编码" extra="系统自动生成，无需填写">
						<Input value={autoCode} disabled data-testid="business-object-auto-code" />
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
