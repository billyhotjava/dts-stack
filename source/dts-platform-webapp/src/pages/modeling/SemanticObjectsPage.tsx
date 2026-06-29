import { useCallback, useEffect, useState } from "react";
import { Button, Drawer, Form, Input } from "antd";
import { toast } from "sonner";
import { CompactTable } from "@/components/table";
import { PageHeader } from "@/components/page-header";
import { VisualFlowCanvas } from "@/components/visual-canvas/VisualFlowCanvas";
import type { ColumnsType } from "antd/es/table";
import type { Node, Edge } from "@xyflow/react";
import {
	listSemanticBusinessObjects,
	listSemanticObjectTableMappings,
	createSemanticBusinessObject,
	type SemanticBusinessObject,
	type SemanticObjectTableMapping,
} from "@/api/semanticModelingApi";

function buildJoinGraph(mappings: SemanticObjectTableMapping[]): { nodes: Node[]; edges: Edge[] } {
	const nodes: Node[] = mappings.map((m, i) => ({
		id: m.id ?? `tmp-${i}`,
		position: { x: i * 220, y: 60 },
		data: { label: `${m.tableName}\n[${m.tableRole ?? "main"}]` },
	}));
	const mainNode = nodes.find((_, i) => mappings[i]?.tableRole === "main" || i === 0);
	const edges: Edge[] = mappings
		.filter((m) => m.joinExpression && mainNode && m.id !== mainNode.id)
		.map((m) => ({
			id: `edge-${m.id}`,
			source: mainNode!.id,
			target: m.id ?? "",
			label: m.joinExpression?.slice(0, 20),
		}));
	return { nodes, edges };
}

export default function SemanticObjectsPage() {
	const [objects, setObjects] = useState<SemanticBusinessObject[]>([]);
	const [loading, setLoading] = useState(false);
	const [selected, setSelected] = useState<SemanticBusinessObject | null>(null);
	const [mappings, setMappings] = useState<SemanticObjectTableMapping[]>([]);
	const [createOpen, setCreateOpen] = useState(false);
	const [form] = Form.useForm();

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

	const handleSelect = async (obj: SemanticBusinessObject) => {
		setSelected(obj);
		try {
			const maps = await listSemanticObjectTableMappings(obj.id);
			setMappings(Array.isArray(maps) ? (maps as SemanticObjectTableMapping[]) : []);
		} catch {
			setMappings([]);
		}
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

	const columns: ColumnsType<SemanticBusinessObject> = [
		{ title: "编码", dataIndex: "code", width: 120 },
		{ title: "名称", dataIndex: "name" },
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
					查看 join 图
				</Button>
			),
		},
	];

	const { nodes, edges } = buildJoinGraph(mappings);

	return (
		<div className="space-y-4" data-testid="semantic-objects-page">
			<PageHeader
				title="语义建模 · 业务对象"
				actions={
					<Button
						type="primary"
						data-testid="semantic-objects-create"
						onClick={() => {
							form.resetFields();
							setCreateOpen(true);
						}}
					>
						+ 新建业务对象
					</Button>
				}
			/>
			<div className="flex gap-4">
				<div style={{ flex: 1 }}>
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
							width: 480,
							border: "1px solid #e5e7eb",
							borderRadius: 6,
							overflow: "hidden",
						}}
					>
						<div className="p-2 text-sm font-medium text-gray-600 border-b border-gray-200">
							{selected.name} — join 关系图
						</div>
						<VisualFlowCanvas nodes={nodes} edges={edges} height={300} />
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
					<Form.Item name="mainTable" label="主表">
						<Input placeholder="dwd_order_detail" />
					</Form.Item>
					<Form.Item name="primaryKey" label="主键">
						<Input placeholder="order_id" />
					</Form.Item>
				</Form>
			</Drawer>
		</div>
	);
}
