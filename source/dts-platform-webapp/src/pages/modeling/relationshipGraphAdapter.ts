import type { WarehousePlanRelationshipGraph } from "@/api/warehousePlanApi";
import type { ImpactEdge, ImpactNode } from "@/pages/catalog/lineageShared";

export function filterRelationshipGraph(
	graph: WarehousePlanRelationshipGraph,
	keyword: string,
): WarehousePlanRelationshipGraph {
	const normalized = keyword.trim().toLocaleLowerCase();
	if (!normalized) return graph;
	const nodes = graph.nodes.filter((node) =>
		`${node.label} ${node.kind} ${node.status || ""}`.toLocaleLowerCase().includes(normalized),
	);
	const visibleIds = new Set(nodes.map((node) => node.id));
	return {
		...graph,
		nodes,
		edges: graph.edges.filter((edge) => visibleIds.has(edge.source) && visibleIds.has(edge.target)),
	};
}

export function toLineageGraph(graph: WarehousePlanRelationshipGraph): {
	nodes: ImpactNode[];
	edges: ImpactEdge[];
} {
	return {
		nodes: graph.nodes.map((node) => ({
			id: node.id,
			name: node.label,
			kind: node.kind,
			type: node.kind,
			status: node.status || undefined,
		})),
		edges: graph.edges.map((edge, index) => ({
			id: `${edge.kind}:${edge.source}:${edge.target}:${index}`,
			fromId: edge.source,
			toId: edge.target,
			relationType: edge.kind,
			notes: edge.label || undefined,
		})),
	};
}
