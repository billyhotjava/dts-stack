import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Alert, Button, Card, Collapse, Descriptions, Drawer, Input, Select, Space, Statistic, Table, Tabs, Tag, Upload } from "antd";
import type { ColumnsType } from "antd/es/table";
import type { UploadProps } from "antd";
import {
	ApiOutlined,
	CodeOutlined,
	DatabaseOutlined,
	DownloadOutlined,
	EyeOutlined,
	FunctionOutlined,
	UploadOutlined,
} from "@ant-design/icons";
import { ReactFlow, Background, Controls, MiniMap, MarkerType, type Node, type Edge } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { EmptyState } from "@/components/empty-state";
import { getCatalogLineageImpact, importDbtManifest, listDatasets } from "@/api/platformApi";

type DatasetOption = {
	id: string;
	name: string;
};

type ImpactNode = {
	kind?: "dataset" | "job" | "source" | string;
	id?: string;
	name?: string;
	db?: string;
	table?: string;
	type?: string;
	assetType?: string;
	jobType?: string;
	relationType?: string;
	sourceName?: string;
	projectName?: string;
	layer?: string;
	ownerDept?: string;
	owner?: string;
	sourceId?: string;
	lastModifiedAt?: string;
	snapshotTime?: string;
};

type ImpactEdge = {
	id?: string;
	kind?: string;
	fromId?: string;
	toId?: string;
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
	kindNodeCounts?: Record<string, number>;
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
	if (key === "SOURCE") return "magenta";
	if (key === "JOB") return "orange";
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

const NODE_SIZE = { width: 190, height: 64 };
const LAYER_RANK: Record<string, number> = {
	SOURCE: 0,
	JOB: 1,
	ODS: 2,
	STG: 3,
	DWD: 4,
	DIM: 4,
	DWS: 5,
	ADS: 6,
};
const RELATION_STROKE: Record<string, string> = {
	ADDAX: "#389e0d",
	DBT: "#d46b08",
	DBT_MODEL: "#d46b08",
	AIRFLOW: "#08979c",
	AUTO_VIEW: "#1677ff",
	MANUAL: "#8c8c8c",
};

const nodeIcon = (node: ImpactNode) => {
	const kind = String(node.kind || "").toLowerCase();
	const jobType = String(node.jobType || "").toUpperCase();
	const assetType = String(node.assetType || node.type || "").toUpperCase();
	if (kind === "source") return <ApiOutlined />;
	if (kind === "job" && jobType.includes("ADDAX")) return <UploadOutlined />;
	if (kind === "job" && jobType.includes("DBT")) return <CodeOutlined />;
	if (kind === "job") return <FunctionOutlined />;
	if (assetType.includes("VIEW")) return <EyeOutlined />;
	return <DatabaseOutlined />;
};

const nodeTone = (node: ImpactNode) => {
	const kind = String(node.kind || "").toLowerCase();
	if (kind === "source") return { bg: "#fff0f6", border: "#ffadd2", color: "#9e1068" };
	if (kind === "job") return { bg: "#fff7e6", border: "#ffd591", color: "#ad4e00" };
	const layer = String(node.layer || "").toUpperCase();
	if (layer === "ADS") return { bg: "#f6ffed", border: "#b7eb8f", color: "#237804" };
	if (layer === "DWS") return { bg: "#e6fffb", border: "#87e8de", color: "#006d75" };
	if (layer === "DWD") return { bg: "#e6f4ff", border: "#91caff", color: "#0958d9" };
	if (layer === "DIM") return { bg: "#f9f0ff", border: "#d3adf7", color: "#531dab" };
	return { bg: "#ffffff", border: "#d9d9d9", color: "#262626" };
};

const edgeEndpoint = (edge: ImpactEdge, side: "from" | "to") => {
	if (side === "from") return edge.fromId || edge.upstreamDatasetId;
	return edge.toId || edge.downstreamDatasetId;
};

const nodeRank = (node: ImpactNode) => {
	const kind = String(node.kind || "").toLowerCase();
	const jobType = String(node.jobType || "").toUpperCase();
	const layer = String(node.layer || "").toUpperCase();
	if (kind === "source") return 0;
	if (kind === "job" && jobType.includes("ADDAX")) return 1;
	if (kind === "job") return Math.max(3, LAYER_RANK[layer] ?? 3);
	return LAYER_RANK[layer] ?? 7;
};

const applyLayeredLayout = (nodes: ImpactNode[], edges: ImpactEdge[], direction: "LR" | "TB") => {
	const rankById = new Map<string, number>();
	for (const node of nodes) {
		if (node.id) rankById.set(node.id, nodeRank(node));
	}
	for (let i = 0; i < 8; i += 1) {
		let changed = false;
		for (const edge of edges) {
			const from = edgeEndpoint(edge, "from");
			const to = edgeEndpoint(edge, "to");
			if (!from || !to) continue;
			const fromRank = rankById.get(from);
			const toRank = rankById.get(to);
			if (fromRank == null || toRank == null) continue;
			if (toRank <= fromRank) {
				rankById.set(to, fromRank + 1);
				changed = true;
			}
		}
		if (!changed) break;
	}
	const groups = new Map<number, ImpactNode[]>();
	for (const node of nodes) {
		const rank = node.id ? (rankById.get(node.id) ?? nodeRank(node)) : nodeRank(node);
		groups.set(rank, [...(groups.get(rank) ?? []), node]);
	}
	const sortedRanks = [...groups.keys()].sort((a, b) => a - b);
	const positions = new Map<string, { x: number; y: number }>();
	for (const rank of sortedRanks) {
		const group = groups.get(rank) ?? [];
		group
			.sort((a, b) => String(a.name || a.table || a.id).localeCompare(String(b.name || b.table || b.id)))
			.forEach((node, index) => {
				if (!node.id) return;
				const main = rank * 260;
				const cross = index * 96;
				positions.set(node.id, direction === "LR" ? { x: main, y: cross } : { x: cross, y: main });
			});
	}
	return positions;
};

const renderNodeLabel = (node: ImpactNode) => {
	const tone = nodeTone(node);
	const title = node.name || node.table || "未知节点";
	const subTitle = String(node.kind || "dataset").toUpperCase();
	const detail = node.kind === "job"
		? node.jobType || node.relationType || "JOB"
		: node.kind === "source"
			? node.type || "SOURCE"
			: node.layer || node.assetType || node.type || "DATASET";
	return (
		<div className="flex w-[170px] items-center gap-2 overflow-hidden text-left">
			<span style={{ color: tone.color }} className="shrink-0 text-base">{nodeIcon(node)}</span>
			<span className="min-w-0 flex-1">
				<span className="block text-[10px] uppercase leading-4 text-slate-500">{subTitle} · {detail}</span>
				<span className="block truncate text-xs font-semibold leading-5 text-slate-900">{title}</span>
			</span>
		</div>
	);
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
	const [layoutDirection, setLayoutDirection] = useState<"LR" | "TB">("LR");

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
				withJobs: true,
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
				(edgeEndpoint(edge, "from") && nodeIdSet.has(edgeEndpoint(edge, "from")!)) ||
				(edgeEndpoint(edge, "to") && nodeIdSet.has(edgeEndpoint(edge, "to")!));
			return Boolean(byText || byNode);
		});
	}, [edgesRaw, keywordLower, nodeIdSet]);

	const rfNodes: Node[] = useMemo(() => {
		if (!nodes.length) return [];
		const positions = applyLayeredLayout(nodes, edges, layoutDirection);
		const selectedId = selectedNode?.id;
		const adjacent = new Set<string>();
		if (selectedId) {
			for (const edge of edges) {
				const from = edgeEndpoint(edge, "from");
				const to = edgeEndpoint(edge, "to");
				if (from === selectedId && to) adjacent.add(to);
				if (to === selectedId && from) adjacent.add(from);
			}
		}
		return nodes.map((n, idx) => {
			const position = n.id ? positions.get(n.id) : undefined;
			const tone = nodeTone(n);
			const isDimmed = Boolean(selectedId) && n.id !== selectedId && !adjacent.has(n.id || "");
			return {
				id: n.id ?? (n.db && n.table ? `${n.db}.${n.table}` : `node-${idx}`),
				position: position ?? { x: idx * 220, y: 0 },
				data: { label: renderNodeLabel(n) },
				style: {
					width: NODE_SIZE.width,
					minHeight: NODE_SIZE.height,
					background: tone.bg,
					border: `1px solid ${tone.border}`,
					borderRadius: n.kind === "job" ? 12 : 6,
					padding: "6px 8px",
					opacity: isDimmed ? 0.35 : 1,
					boxShadow: n.id === selectedId ? `0 0 0 2px ${tone.border}` : "none",
				},
			};
		});
	}, [edges, layoutDirection, nodes, selectedNode?.id]);

	const rfEdges: Edge[] = useMemo(() =>
		edges
			.filter((e) => edgeEndpoint(e, "from") && edgeEndpoint(e, "to"))
			.map((e, i) => ({
				id: e.id ?? `e-${i}`,
				source: edgeEndpoint(e, "from")!,
				target: edgeEndpoint(e, "to")!,
				type: "smoothstep",
				animated: false,
				label: e.relationType,
				labelStyle: { fontSize: 10, fill: "#595959", fontWeight: 600 },
				labelBgPadding: [6, 3] as [number, number],
				labelBgBorderRadius: 4,
				style: {
					stroke: RELATION_STROKE[String(e.relationType || "").toUpperCase()] ?? "#bfbfbf",
					strokeWidth: e.relationType === "MANUAL" ? 1.2 : 1.8,
					strokeDasharray: e.relationType === "MANUAL" ? "4 4" : undefined,
				},
				markerEnd: { type: MarkerType.ArrowClosed, width: 16, height: 16 },
			})),
		[edges],
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
			title: "类型",
			key: "kind",
			width: 120,
			render: (_, row) => {
				const kind = String(row.kind || "dataset").toUpperCase();
				const detail = row.jobType || row.assetType || row.type || "-";
				return (
					<Space size={4}>
						<Tag color={row.kind === "job" ? "orange" : row.kind === "source" ? "magenta" : "blue"}>{kind}</Tag>
						<span className="text-xs text-slate-500">{detail}</span>
					</Space>
				);
			},
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
							{ label: "STG", value: "STG" },
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
					<Select
						style={{ width: 140 }}
						value={layoutDirection}
						options={[
							{ label: "横向布局", value: "LR" },
							{ label: "纵向布局", value: "TB" },
						]}
						onChange={(value) => setLayoutDirection(value)}
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
											<Statistic title="数据源" value={Number(impact?.impactStats?.kindNodeCounts?.source || 0)} />
											<Statistic title="任务节点" value={Number(impact?.impactStats?.kindNodeCounts?.job || 0)} />
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
									<ReactFlow
										nodes={rfNodes}
										edges={rfEdges}
										fitView
										onNodeClick={(_, node) => {
											const matched = nodes.find((item) => (item.id || "") === node.id);
											if (matched) setSelectedNode(matched);
										}}
									>
										<Background />
										<Controls />
										<MiniMap
											position="bottom-right"
											nodeStrokeWidth={2}
											nodeColor={(node) => {
												const matched = nodes.find((item) => (item.id || "") === node.id);
												return nodeTone(matched || {}).bg;
											}}
										/>
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
