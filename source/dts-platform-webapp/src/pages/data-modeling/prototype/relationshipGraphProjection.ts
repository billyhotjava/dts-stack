import type {
	ModelingRelationshipGraph,
	ModelingRelationshipGraphEdge,
	ModelingRelationshipGraphKind,
} from "@/api/services/modelingRelationshipGraphService";
import type { ImpactEdge, ImpactNode } from "@/features/catalog/lineageContracts";
import { statusLabel } from "@/utils/customerDisplayLabels";

const MAX_VISIBLE_NODES = 80;

const kindsByView: Record<string, Set<ModelingRelationshipGraphKind>> = {
	models: new Set(["DIMENSION", "MODEL"]),
	standards: new Set(["MODEL", "STANDARD"]),
	metrics: new Set(["MODEL", "INDICATOR"]),
};

const edgeKindsByView: Record<string, Set<string>> = {
	models: new Set(["DEPENDS_ON", "DIMENSION_REFERENCE", "DIMENSION_DEFINITION_REFERENCE"]),
	standards: new Set(["STANDARD_BINDING"]),
	metrics: new Set(["INDICATOR_REFERENCE", "INDICATOR_DEPENDS_ON"]),
};

const kindLabel: Record<ModelingRelationshipGraphKind, string> = {
	PLAN: "规划",
	DIMENSION: "维度",
	MODEL: "模型",
	STANDARD: "数据标准",
	INDICATOR: "数据指标",
};

const relationLabel: Record<string, string> = {
	CONTAINS: "包含",
	DEPENDS_ON: "依赖",
	DIMENSION_REFERENCE: "引用维度",
	DIMENSION_DEFINITION_REFERENCE: "引用维度定义",
	STANDARD_BINDING: "绑定标准",
	INDICATOR_REFERENCE: "引用指标",
	INDICATOR_DEPENDS_ON: "指标依赖",
};

export type ProjectedRelationshipNode = ImpactNode & { id: string };
export type ProjectedRelationshipEdge = ImpactEdge & { fromId: string; toId: string };

export type ProjectedRelationshipGraph = {
	nodes: ProjectedRelationshipNode[];
	edges: ProjectedRelationshipEdge[];
	limited: boolean;
	totalNodeCount: number;
	totalEdgeCount: number;
};

const normalizedText = (value: unknown) =>
	String(value || "")
		.normalize("NFKC")
		.trim()
		.toLocaleLowerCase();

const matches = (node: ModelingRelationshipGraph["nodes"][number], query: string) =>
	normalizedText(`${node.label} ${node.status || ""} ${kindLabel[node.kind]}`).includes(query);

const stableEdgeKey = (edge: ModelingRelationshipGraphEdge) => `${edge.kind}:${edge.source}:${edge.target}`;

export function projectModelingRelationshipGraph(
	graph: ModelingRelationshipGraph,
	view: string,
	query: string,
): ProjectedRelationshipGraph {
	const allowedKinds = kindsByView[view] || kindsByView.models;
	const allowedEdgeKinds = edgeKindsByView[view] || edgeKindsByView.models;
	const nodesById = new Map(graph.nodes.filter((node) => allowedKinds.has(node.kind)).map((node) => [node.id, node]));
	const relevantEdges = graph.edges
		.filter((edge) => allowedEdgeKinds.has(edge.kind) && nodesById.has(edge.source) && nodesById.has(edge.target))
		.sort((left, right) => stableEdgeKey(left).localeCompare(stableEdgeKey(right)));
	const relatedIds = new Set(relevantEdges.flatMap((edge) => [edge.source, edge.target]));
	const normalizedQuery = normalizedText(query);
	let visibleIds = relatedIds;
	let visibleEdges = relevantEdges;
	if (normalizedQuery) {
		const matchedIds = new Set(
			[...relatedIds].filter((id) => {
				const node = nodesById.get(id);
				return node ? matches(node, normalizedQuery) : false;
			}),
		);
		visibleEdges = relevantEdges.filter((edge) => matchedIds.has(edge.source) || matchedIds.has(edge.target));
		visibleIds = new Set(visibleEdges.flatMap((edge) => [edge.source, edge.target]));
	}

	const totalNodeCount = visibleIds.size;
	const totalEdgeCount = visibleEdges.length;
	const selectedIds = new Set([...visibleIds].sort().slice(0, MAX_VISIBLE_NODES));
	visibleEdges = visibleEdges.filter((edge) => selectedIds.has(edge.source) && selectedIds.has(edge.target));

	const nodes = [...selectedIds]
		.map((id): ProjectedRelationshipNode | null => {
			const node = nodesById.get(id);
			if (!node) return null;
			return {
				id: node.id,
				name: node.label,
				kind: kindLabel[node.kind],
				type: node.status ? statusLabel(node.status) : "状态未提供",
				status: node.status || undefined,
			};
		})
		.filter((node): node is ProjectedRelationshipNode => node !== null);
	const edges = visibleEdges.map(
		(edge): ProjectedRelationshipEdge => ({
			id: stableEdgeKey(edge),
			fromId: edge.source,
			toId: edge.target,
			relationType: relationLabel[edge.kind] || "关联",
		}),
	);

	return {
		nodes,
		edges,
		limited: totalNodeCount > MAX_VISIBLE_NODES,
		totalNodeCount,
		totalEdgeCount,
	};
}
