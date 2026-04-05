import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Alert, Button, Card, Collapse, Descriptions, Drawer, Input, Select, Space, Statistic, Table, Tabs, Tag, Upload } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { UploadProps } from "antd";
import { DownloadOutlined, UploadOutlined } from "@ant-design/icons";
import { ReactFlow, Background, Controls, type Node, type Edge } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { EmptyState } from "@/components/empty-state";
import { getCatalogLineageImpact, importDbtManifest, listDatasets } from "@/api/platformApi";

type DatasetOption = {
	id: string;
	name: string;
};

type ImpactNode = {
	id?: string;
	name?: string;
	db?: string;
	table?: string;
	type?: string;
	layer?: string;
	ownerDept?: string;
	owner?: string;
	sourceId?: string;
	lastModifiedAt?: string;
	snapshotTime?: string;
};

type ImpactEdge = {
	id?: string;
	relationType?: string;
	upstreamDatasetId?: string;
	downstreamDatasetId?: string;
	upstreamName?: string;
	downstreamName?: string;
	upstreamLayer?: string;
	downstreamLayer?: string;
	upstreamAssetType?: string;
	downstreamAssetType?: string;
	direction?: string;
	projectName?: string;
	notes?: string;
	lastModifiedAt?: string;
};

type ImpactStats = {
	layerNodeCounts?: Record<string, number>;
	relationTypeCounts?: Record<string, number>;
	changedNodeCount?: number;
};

type ImpactResult = {
	datasetId?: string;
	direction?: string;
	depth?: number;
	projectName?: string;
	nodeCount?: number;
	edgeCount?: number;
	layers?: string[];
	changedWithinHours?: number;
	sourceId?: string;
	impactStats?: ImpactStats;
	nodes?: ImpactNode[];
	edges?: ImpactEdge[];
};

const layerColor = (layer?: string) => {
	const key = String(layer || "").toUpperCase();
	if (key === "ODS") return "default";
	if (key === "DWD") return "blue";
	if (key === "DWS") return "cyan";
	if (key === "ADS") return "green";
	if (key === "DIM") return "purple";
	return "processing";
};

const formatTs = (value?: string) => {
	if (!value) return "-";
	try {
		return new Date(value).toLocaleString();
	} catch {
		return value;
	}
};

const csvEscape = (value: unknown) => {
	const text = value == null ? "" : String(value);
	if (text.includes(",") || text.includes("\"") || text.includes("\n")) {
		return `"${text.replaceAll("\"", "\"\"")}"`;
	}
	return text;
};

const downloadCsv = (name: string, rows: Array<Record<string, unknown>>) => {
	if (!rows.length) {
		return;
	}
	const keys = Object.keys(rows[0] || {});
	const header = keys.join(",");
	const body = rows
		.map((row) => keys.map((key) => csvEscape(row[key])).join(","))
		.join("\n");
	const blob = new Blob([`${header}\n${body}`], { type: "text/csv;charset=utf-8;" });
	const url = URL.createObjectURL(blob);
	const anchor = document.createElement("a");
	anchor.href = url;
	anchor.download = name;
	anchor.click();
	URL.revokeObjectURL(url);
};

const LAYER_BG: Record<string, string> = {
	ODS: "#f5f5f5",
	DWD: "#e6f4ff",
	DWS: "#e6fffb",
	ADS: "#f6ffed",
	DIM: "#f9f0ff",
};

export default function LineagePage() {
	const [datasets, setDatasets] = useState<DatasetOption[]>([]);
	const [selectedId, setSelectedId] = useState<string | undefined>();
	const [loading, setLoading] = useState(false);
	const [impact, setImpact] = useState<ImpactResult | null>(null);
	const [selectedNode, setSelectedNode] = useState<ImpactNode | null>(null);
	const [direction, setDirection] = useState<"UPSTREAM" | "DOWNSTREAM" | "BOTH">("BOTH");
	const [depth, setDepth] = useState<number>(3);
	const [projectName, setProjectName] = useState<string>("");
	const [layerFilters, setLayerFilters] = useState<string[]>([]);
	const [changedWithinHours, setChangedWithinHours] = useState<number>(0);
	const [keyword, setKeyword] = useState<string>("");

	useEffect(() => {
		void loadDatasets();
	}, []);

	useEffect(() => {
		if (!selectedId) {
			setImpact(null);
			setSelectedNode(null);
			return;
		}
		void loadImpact(selectedId, direction, depth, projectName, layerFilters, changedWithinHours);
	}, [selectedId, direction, depth, projectName, layerFilters, changedWithinHours]);

	const datasetOptions = useMemo(() => datasets.map((item) => ({ label: item.name, value: item.id })), [datasets]);

	const loadDatasets = async () => {
		try {
			const resp: any = await listDatasets({ page: 0, size: 300, enabledOnly: true });
			const content = Array.isArray(resp?.content) ? resp.content : [];
			const options = content
				.map((item: any) => ({ id: String(item.id || ""), name: String(item.name || "").trim() }))
				.filter((item: DatasetOption) => item.id && item.name);
			setDatasets(options);
			if (!selectedId && options.length) {
				setSelectedId(options[0].id);
			}
		} catch {
			// error toast handled by global interceptor
		}
	};

	const loadImpact = async (
		datasetId: string,
		dir: "UPSTREAM" | "DOWNSTREAM" | "BOTH",
		depthValue: number,
		project: string,
		layers: string[],
		changedHours: number,
	) => {
		setLoading(true);
		try {
			const resp: any = await getCatalogLineageImpact(datasetId, {
				direction: dir,
				depth: depthValue,
				projectName: project.trim() || undefined,
				layers: layers.length ? layers.join(",") : undefined,
				changedWithinHours: changedHours > 0 ? changedHours : undefined,
			});
			setImpact(resp || null);
		} catch {
			// error toast handled by global interceptor
			setImpact(null);
		} finally {
			setLoading(false);
		}
	};

	const nodesRaw = useMemo(() => (Array.isArray(impact?.nodes) ? (impact?.nodes ?? []) : []), [impact?.nodes]);
	const edgesRaw = useMemo(() => (Array.isArray(impact?.edges) ? (impact?.edges ?? []) : []), [impact?.edges]);

	const keywordLower = keyword.trim().toLowerCase();
	const nodes = useMemo(() => {
		if (!keywordLower) {
			return nodesRaw;
		}
		return nodesRaw.filter((node) => {
			const text = `${node.name || ""} ${node.db || ""}.${node.table || ""} ${node.owner || ""} ${node.ownerDept || ""}`.toLowerCase();
			return text.includes(keywordLower);
		});
	}, [nodesRaw, keywordLower]);

	const nodeIdSet = useMemo(() => new Set(nodes.map((node) => node.id).filter(Boolean)), [nodes]);
	const edges = useMemo(() => {
		if (!keywordLower) {
			return edgesRaw;
		}
		return edgesRaw.filter((edge) => {
			const byText =
				`${edge.upstreamName || ""} ${edge.downstreamName || ""} ${edge.relationType || ""} ${edge.notes || ""}`.toLowerCase().includes(keywordLower);
			const byNode =
				(edge.upstreamDatasetId && nodeIdSet.has(edge.upstreamDatasetId)) ||
				(edge.downstreamDatasetId && nodeIdSet.has(edge.downstreamDatasetId));
			return Boolean(byText || byNode);
		});
	}, [edgesRaw, keywordLower, nodeIdSet]);

	const rfNodes: Node[] = useMemo(() => {
		if (!impact?.nodes) return [];
		const layerX: Record<string, number> = { ODS: 0, DWD: 250, DWS: 500, ADS: 750, DIM: 1000 };
		const layerCount: Record<string, number> = {};
		return impact.nodes.map((n, idx) => {
			const layer = n.layer?.toUpperCase() ?? "UNKNOWN";
			const x = layerX[layer] ?? 1100;
			layerCount[layer] = (layerCount[layer] ?? 0) + 1;
			const y = (layerCount[layer] - 1) * 80;
			return {
				id: n.id ?? (n.db && n.table ? `${n.db}.${n.table}` : `node-${idx}`),
				position: { x, y },
				data: { label: n.name ?? n.table ?? "未知" },
				style: {
					background: LAYER_BG[layer] ?? "#fff",
					border: "1px solid #d9d9d9",
					borderRadius: 6,
					fontSize: 11,
					padding: "4px 8px",
				},
			};
		});
	}, [impact?.nodes]);

	const rfEdges: Edge[] = useMemo(() =>
		(impact?.edges ?? [])
			.filter((e) => e.upstreamDatasetId && e.downstreamDatasetId)
			.map((e, i) => ({
				id: e.id ?? `e-${i}`,
				source: e.upstreamDatasetId!,
				target: e.downstreamDatasetId!,
				animated: false,
				style: { stroke: "#bfbfbf" },
			})),
		[impact?.edges],
	);

	const dbtUploadProps: UploadProps = {
		accept: ".json",
		showUploadList: false,
		beforeUpload: async (file) => {
			try {
				const result = await importDbtManifest(file as File);
				toast.success(`dbt 血缘导入成功：新建 ${result.created} 条，跳过 ${result.skipped} 条`);
				if (selectedId) {
					void loadImpact(selectedId, direction, depth, projectName, layerFilters, changedWithinHours);
				}
			} catch {
				// global interceptor handles error toast
			}
			return false;
		},
	};

	const layerGroupItems = useMemo(() => {
		const groups = new Map<string, ImpactNode[]>();
		for (const node of nodes) {
			const key = String(node.layer || "UNKNOWN").toUpperCase();
			if (!groups.has(key)) {
				groups.set(key, []);
			}
			groups.get(key)?.push(node);
		}
		return [...groups.entries()].map(([layer, list]) => ({
			key: layer,
			label: (
				<Space>
					<Tag color={layerColor(layer)}>{layer}</Tag>
					<span>{list.length} 个节点</span>
				</Space>
			),
			children: (
				<Table
					size="small"
					rowKey={(row, idx) => row.id || `${row.db || "db"}.${row.table || "tb"}-${idx}`}
					columns={[
						{ title: "节点", dataIndex: "name", render: (v, r) => v || `${r.db || "-"}.${r.table || "-"}` },
						{ title: "模式.表", render: (_, r) => `${r.db || "-"}.${r.table || "-"}` },
						{ title: "负责人", render: (_, r) => r.owner || r.ownerDept || "-" },
						{ title: "最近变更", render: (_, r) => formatTs(r.lastModifiedAt) },
					]}
					dataSource={list}
					pagination={false}
					onRow={(record) => ({
						onClick: () => setSelectedNode(record),
					})}
				/>
			),
		}));
	}, [nodes]);

	const nodeColumns: ColumnsType<ImpactNode> = [
		{
			title: "节点",
			dataIndex: "name",
			render: (value, row) => value || `${row.db || "-"}.${row.table || "-"}`,
		},
		{
			title: "分层",
			dataIndex: "layer",
			width: 120,
			render: (value) => {
				if (!value) return "-";
				return <Tag color={layerColor(value)}>{String(value).toUpperCase()}</Tag>;
			},
		},
		{
			title: "类型",
			dataIndex: "type",
			width: 130,
			render: (value) => (value ? <Tag>{String(value).toUpperCase()}</Tag> : "-"),
		},
		{
			title: "模式.表",
			key: "table",
			render: (_, row) => `${row.db || "-"}.${row.table || "-"}`,
		},
		{
			title: "负责人",
			key: "owner",
			render: (_, row) => row.owner || row.ownerDept || "-",
		},
		{
			title: "最近变更",
			key: "lastModifiedAt",
			render: (_, row) => formatTs(row.lastModifiedAt),
		},
	];

	const edgeColumns: ColumnsType<ImpactEdge> = [
		{
			title: "上游",
			key: "upstream",
			render: (_, row) => row.upstreamName || row.upstreamDatasetId || "-",
		},
		{
			title: "下游",
			key: "downstream",
			render: (_, row) => row.downstreamName || row.downstreamDatasetId || "-",
		},
		{
			title: "关系",
			dataIndex: "relationType",
			width: 140,
			render: (value) => (value ? <Tag color="blue">{value}</Tag> : "-"),
		},
		{
			title: "分层",
			key: "layerFlow",
			width: 180,
			render: (_, row) => `${row.upstreamLayer || "-"} -> ${row.downstreamLayer || "-"}`,
		},
		{
			title: "项目",
			dataIndex: "projectName",
			width: 150,
			render: (value) => value || "-",
		},
		{
			title: "最近变更",
			dataIndex: "lastModifiedAt",
			width: 190,
			render: (value) => formatTs(value),
		},
	];

	const handleExport = () => {
		if (!impact) {
			toast.warning("当前无可导出的数据");
			return;
		}
		const stamp = new Date().toISOString().slice(0, 19).replaceAll(":", "-");
		downloadCsv(
			`lineage-nodes-${stamp}.csv`,
			nodes.map((row) => ({
				id: row.id,
				name: row.name,
				layer: row.layer,
				type: row.type,
				schema_table: `${row.db || ""}.${row.table || ""}`,
				owner: row.owner || "",
				owner_dept: row.ownerDept || "",
				source_id: row.sourceId || "",
				last_modified_at: row.lastModifiedAt || "",
			})),
		);
		downloadCsv(
			`lineage-edges-${stamp}.csv`,
			edges.map((row) => ({
				id: row.id,
				upstream: row.upstreamName || row.upstreamDatasetId || "",
				downstream: row.downstreamName || row.downstreamDatasetId || "",
				relation_type: row.relationType || "",
				layer_flow: `${row.upstreamLayer || ""}->${row.downstreamLayer || ""}`,
				project_name: row.projectName || "",
				last_modified_at: row.lastModifiedAt || "",
			})),
		);
		toast.success("已导出节点与关系 CSV");
	};

	return (
		<div className="space-y-4">
			<Card
				title="血缘影响分析"
				extra={
					<Space>
						<Upload {...dbtUploadProps}>
							<Button className="rounded-2xl" icon={<UploadOutlined />}>
								导入 dbt 血缘
							</Button>
						</Upload>
						<Button className="rounded-2xl" icon={<DownloadOutlined />} onClick={handleExport} disabled={!nodes.length && !edges.length}>
							导出结果
						</Button>
					</Space>
				}
			>
				<div className="mb-3 flex flex-wrap items-center gap-2">
					<Select
						placeholder="选择数据集"
						style={{ minWidth: 320 }}
						value={selectedId}
						options={datasetOptions}
						onChange={(value) => setSelectedId(value)}
						showSearch
						optionFilterProp="label"
					/>
					<Select
						style={{ width: 150 }}
						value={direction}
						options={[
							{ label: "双向", value: "BOTH" },
							{ label: "仅上游", value: "UPSTREAM" },
							{ label: "仅下游", value: "DOWNSTREAM" },
						]}
						onChange={(value) => setDirection(value)}
					/>
					<Select
						style={{ width: 140 }}
						value={depth}
						options={[
							{ label: "深度 1", value: 1 },
							{ label: "深度 2", value: 2 },
							{ label: "深度 3", value: 3 },
							{ label: "深度 5", value: 5 },
						]}
						onChange={(value) => setDepth(value)}
					/>
					<Select
						mode="multiple"
						allowClear
						placeholder="层级过滤"
						style={{ minWidth: 220 }}
						value={layerFilters}
						options={[
							{ label: "ODS", value: "ODS" },
							{ label: "DWD", value: "DWD" },
							{ label: "DWS", value: "DWS" },
							{ label: "ADS", value: "ADS" },
							{ label: "DIM", value: "DIM" },
						]}
						onChange={(value) => setLayerFilters(value)}
					/>
					<Select
						style={{ width: 180 }}
						value={changedWithinHours}
						options={[
							{ label: "全部变更", value: 0 },
							{ label: "最近 24 小时", value: 24 },
							{ label: "最近 7 天", value: 24 * 7 },
							{ label: "最近 30 天", value: 24 * 30 },
						]}
						onChange={(value) => setChangedWithinHours(value)}
					/>
					<Input
						style={{ width: 220 }}
						placeholder="项目名过滤（可选）"
						value={projectName}
						onChange={(e) => setProjectName(e.target.value)}
						allowClear
					/>
					<Input
						style={{ width: 240 }}
						placeholder="链路快速搜索（节点/关系）"
						value={keyword}
						onChange={(e) => setKeyword(e.target.value)}
						allowClear
					/>
				</div>
			</Card>

			{!selectedId ? <Alert type="info" message="请选择一个数据集开始分析。" showIcon /> : null}

			{selectedId && (
				<Tabs
					defaultActiveKey="table"
					items={[
						{
							key: "table",
							label: "影响分析表格",
							children: (
								<div className="space-y-4">
									<Card title="影响概览">
										<Card bordered={false} loading={loading} bodyStyle={{ padding: 0 }}>
										<Space size={24} wrap>
											<Statistic title="节点数" value={Number(impact?.nodeCount || 0)} />
											<Statistic title="边数" value={Number(impact?.edgeCount || 0)} />
											<Statistic title="变更节点" value={Number(impact?.impactStats?.changedNodeCount || 0)} />
											<Statistic title="方向" value={impact?.direction || direction} />
											<Statistic title="深度" value={Number(impact?.depth || depth)} />
										</Space>
										</Card>
									</Card>

									<Card title="分层折叠视图">
										{layerGroupItems.length ? (
											<Collapse items={layerGroupItems} defaultActiveKey={layerGroupItems.map((item) => item.key)} />
										) : (
											<EmptyState title="暂无分层节点" description="当前筛选条件下无可展示节点。" />
										)}
									</Card>

									<Card title="节点列表">
										{nodes.length ? (
											<Table
												rowKey={(row, idx) => row.id || `${row.db || "db"}.${row.table || "tb"}-${idx}`}
												columns={nodeColumns}
												dataSource={nodes}
												loading={loading}
												scroll={{ x: 1200 }}
												pagination={{ pageSize: 10 }}
												onRow={(record) => ({
													onClick: () => setSelectedNode(record),
												})}
											/>
										) : (
											<EmptyState title="暂无节点" description="当前条件下未检索到血缘节点。" />
										)}
									</Card>

									<Card title="关系边列表">
										{edges.length ? (
											<Table
												rowKey={(row, idx) => row.id || `${row.upstreamDatasetId || "up"}-${row.downstreamDatasetId || "down"}-${idx}`}
												columns={edgeColumns}
												dataSource={edges}
												loading={loading}
												scroll={{ x: 1000 }}
												pagination={{ pageSize: 10 }}
											/>
										) : (
											<EmptyState title="暂无关系边" description="当前条件下未检索到血缘关系。" />
										)}
									</Card>
								</div>
							),
						},
						{
							key: "graph",
							label: "血缘图",
							children: (
								<div style={{ height: 500, border: "1px solid #e8e8e8", borderRadius: 8, overflow: "hidden" }}>
									<ReactFlow nodes={rfNodes} edges={rfEdges} fitView>
										<Background />
										<Controls />
									</ReactFlow>
								</div>
							),
						},
					]}
				/>
			)}

			<Drawer title="节点详情" open={Boolean(selectedNode)} width={520} onClose={() => setSelectedNode(null)} destroyOnClose>
				{selectedNode ? (
					<Descriptions column={1} bordered size="small">
						<Descriptions.Item label="节点名称">{selectedNode.name || "-"}</Descriptions.Item>
						<Descriptions.Item label="模式.表">{`${selectedNode.db || "-"}.${selectedNode.table || "-"}`}</Descriptions.Item>
						<Descriptions.Item label="分层">
							<Tag color={layerColor(selectedNode.layer)}>{String(selectedNode.layer || "UNKNOWN").toUpperCase()}</Tag>
						</Descriptions.Item>
						<Descriptions.Item label="负责人">{selectedNode.owner || "-"}</Descriptions.Item>
						<Descriptions.Item label="所属部门">{selectedNode.ownerDept || "-"}</Descriptions.Item>
						<Descriptions.Item label="来源数据源 ID">{selectedNode.sourceId || "-"}</Descriptions.Item>
						<Descriptions.Item label="最近变更">{formatTs(selectedNode.lastModifiedAt)}</Descriptions.Item>
						<Descriptions.Item label="最近采集">{formatTs(selectedNode.snapshotTime)}</Descriptions.Item>
					</Descriptions>
				) : null}
			</Drawer>
		</div>
	);
}
