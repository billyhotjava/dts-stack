export type DatasetOption = {
	id: string;
	name: string;
};

export type ImpactNode = {
	kind?: "dataset" | "job" | "source" | string;
	id?: string;
	name?: string;
	db?: string;
	table?: string;
	type?: string;
	assetType?: string;
	lineageJobId?: string;
	jobKey?: string;
	jobType?: string;
	engine?: string;
	relationType?: string;
	sourceName?: string;
	projectName?: string;
	layer?: string;
	ownerDept?: string;
	owner?: string;
	sourceId?: string;
	status?: string;
	lastExecutionId?: string;
	lastExecutionStatus?: string;
	lastObservedAt?: string;
	lastVerifiedAt?: string;
	lastModifiedAt?: string;
	snapshotTime?: string;
};

export type ImpactEdge = {
	id?: string;
	kind?: string;
	fromId?: string;
	toId?: string;
	relationType?: string;
	lineageJobId?: string;
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
	verificationStatus?: string;
	lastExecutionId?: string;
	lastExecutionStatus?: string;
	lastObservedAt?: string;
	lastVerifiedAt?: string;
	validFrom?: string;
	validTo?: string;
	lastModifiedAt?: string;
};

export type ColumnLineage = {
	id?: string;
	datasetLineageId?: string;
	upstreamDatasetId?: string;
	downstreamDatasetId?: string;
	upstreamColumnId?: string;
	downstreamColumnId?: string;
	upstreamColumn?: string;
	downstreamColumn?: string;
	relationType?: string;
	lineageType?: string;
	expression?: string;
	confidence?: string;
	projectName?: string;
	lineageJobId?: string;
	lastObservedAt?: string;
	lastModifiedAt?: string;
};

export type ImpactStats = {
	layerNodeCounts?: Record<string, number>;
	relationTypeCounts?: Record<string, number>;
	verificationStatusCounts?: Record<string, number>;
	kindNodeCounts?: Record<string, number>;
	changedNodeCount?: number;
	columnLineageCount?: number;
};

export type ImpactResult = {
	datasetId?: string;
	direction?: string;
	depth?: number;
	/** 字段血缘取自的表级血缘快照时刻（与 snapshotAt 同值），用于自证表级/字段级时间语义一致 */
	columnLineageSnapshotAt?: string;
	projectName?: string;
	nodeCount?: number;
	edgeCount?: number;
	layers?: string[];
	changedWithinHours?: number;
	sourceId?: string;
	withColumns?: boolean;
	snapshotAt?: string;
	timeTravel?: boolean;
	impactStats?: ImpactStats;
	nodes?: ImpactNode[];
	edges?: ImpactEdge[];
	columnLineages?: ColumnLineage[];
};

export type LineageDiffResult = {
	from?: string;
	to?: string;
	addedCount?: number;
	removedCount?: number;
	changedCount?: number;
	unchangedCount?: number;
	addedEdges?: ImpactEdge[];
	removedEdges?: ImpactEdge[];
	unchangedEdges?: ImpactEdge[];
};

export type LineageSection = "impact" | "graph" | "columns" | "import" | "diff";
export type LineageDirection = "UPSTREAM" | "DOWNSTREAM" | "BOTH";
export type LayoutDirection = "LR" | "TB";

export const lineageSectionMeta: Record<LineageSection, { title: string; path: string }> = {
	impact: { title: "影响分析", path: "/catalog/lineage/impact" },
	graph: { title: "血缘图谱", path: "/catalog/lineage/graph" },
	columns: { title: "字段血缘", path: "/catalog/lineage/columns" },
	import: { title: "血缘导入", path: "/catalog/lineage/import" },
	diff: { title: "对比", path: "/catalog/lineage/diff" },
};

export const lineageSections = Object.keys(lineageSectionMeta) as LineageSection[];
export const NODE_SIZE = { width: 190, height: 64 };

export const layerColor = (layer?: string) => {
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

export const formatTs = (value?: string) => {
	if (!value) return "-";
	try {
		return new Date(value).toLocaleString();
	} catch {
		return value;
	}
};

export const toIsoInstant = (value?: string) => {
	const text = String(value || "").trim();
	if (!text) return undefined;
	const parsed = new Date(text);
	if (Number.isNaN(parsed.getTime())) return undefined;
	return parsed.toISOString();
};

export const verificationColor = (status?: string) => {
	const key = String(status || "").toUpperCase();
	if (key === "VERIFIED") return "green";
	if (key === "KNOWN_UNVERIFIED") return "gold";
	if (key === "DECLARED") return "default";
	return "default";
};

export const edgeEndpoint = (edge: ImpactEdge, side: "from" | "to") =>
	side === "from" ? edge.fromId || edge.upstreamDatasetId : edge.toId || edge.downstreamDatasetId;

export const edgeLabel = (edge: ImpactEdge) => {
	const relation = edge.relationType || "";
	const status = String(edge.verificationStatus || "").toUpperCase();
	if (!status || status === "VERIFIED") return relation;
	return relation ? `${relation}/${status}` : status;
};

const LAYER_RANK: Record<string, number> = { SOURCE: 0, JOB: 1, ODS: 2, STG: 3, DWD: 4, DIM: 4, DWS: 5, ADS: 6 };
const RELATION_STROKE: Record<string, string> = {
	ADDAX: "#389e0d",
	DBT: "#d46b08",
	DBT_MODEL: "#d46b08",
	AIRFLOW: "#08979c",
	AUTO_VIEW: "#1677ff",
	MANUAL: "#8c8c8c",
};

export const relationStroke = (relation?: string) => RELATION_STROKE[String(relation || "").toUpperCase()] ?? "#bfbfbf";

export const nodeTone = (node: ImpactNode) => {
	const kind = String(node.kind || "").toLowerCase();
	if (kind === "source") return { bg: "#fff0f6", border: "#ffadd2", color: "#9e1068" };
	if (kind === "job") return { bg: "#fff7e6", border: "#ffd591", color: "#ad4e00" };
	const layer = String(node.layer || "").toUpperCase();
	if (layer === "SOURCE") return { bg: "#fff0f6", border: "#ffadd2", color: "#9e1068" };
	if (layer === "ADS") return { bg: "#f6ffed", border: "#b7eb8f", color: "#237804" };
	if (layer === "DWS") return { bg: "#e6fffb", border: "#87e8de", color: "#006d75" };
	if (layer === "DWD") return { bg: "#e6f4ff", border: "#91caff", color: "#0958d9" };
	if (layer === "DIM") return { bg: "#f9f0ff", border: "#d3adf7", color: "#531dab" };
	return { bg: "#ffffff", border: "#d9d9d9", color: "#262626" };
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

export const applyLayeredLayout = (nodes: ImpactNode[], edges: ImpactEdge[], direction: LayoutDirection) => {
	const rankById = new Map<string, number>();
	for (const node of nodes) if (node.id) rankById.set(node.id, nodeRank(node));
	for (let i = 0; i < 8; i += 1) {
		let changed = false;
		for (const edge of edges) {
			const from = edgeEndpoint(edge, "from");
			const to = edgeEndpoint(edge, "to");
			const fromRank = from ? rankById.get(from) : undefined;
			const toRank = to ? rankById.get(to) : undefined;
			if (fromRank == null || toRank == null || toRank > fromRank) continue;
			if (to) rankById.set(to, fromRank + 1);
			changed = true;
		}
		if (!changed) break;
	}
	const groups = new Map<number, ImpactNode[]>();
	for (const node of nodes) {
		const rank = node.id ? (rankById.get(node.id) ?? nodeRank(node)) : nodeRank(node);
		groups.set(rank, [...(groups.get(rank) ?? []), node]);
	}
	const positions = new Map<string, { x: number; y: number }>();
	[...groups.keys()]
		.sort((a, b) => a - b)
		.forEach((rank) => {
			(groups.get(rank) ?? [])
				.sort((a, b) => String(a.name || a.table || a.id).localeCompare(String(b.name || b.table || b.id)))
				.forEach((node, index) => {
					if (!node.id) return;
					const main = rank * 260;
					const cross = index * 96;
					positions.set(node.id, direction === "LR" ? { x: main, y: cross } : { x: cross, y: main });
				});
		});
	return positions;
};

export const normalizeColumnNodeId = (datasetId?: string, columnName?: string) =>
	datasetId && columnName ? `${datasetId}:${columnName}` : undefined;

export const isColumnNodeId = (id?: string) => Boolean(id?.includes(":"));
