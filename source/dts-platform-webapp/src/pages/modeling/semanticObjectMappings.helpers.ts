import type { Edge, Node } from "@xyflow/react";
import type { SemanticObjectTableMapping } from "@/api/semanticModelingApi";

const PRIMARY_TABLE_ROLES = new Set(["main", "primary", "fact"]);

export function isPrimaryTableRole(tableRole?: string) {
	return PRIMARY_TABLE_ROLES.has(String(tableRole ?? "").trim().toLowerCase());
}

function mappingNodeId(mapping: SemanticObjectTableMapping, index: number) {
	return mapping.id || `${mapping.tableName || "table"}-${index}`;
}

export function buildSemanticObjectJoinGraph(mappings: SemanticObjectTableMapping[]): { nodes: Node[]; edges: Edge[] } {
	const nodes: Node[] = mappings.map((mapping, index) => ({
		id: mappingNodeId(mapping, index),
		position: { x: index * 220, y: 60 },
		data: { label: `${mapping.tableName}\n[${mapping.tableRole ?? "main"}]` },
	}));
	const primaryIndex = mappings.findIndex((mapping) => isPrimaryTableRole(mapping.tableRole));
	const mainNode = nodes[primaryIndex >= 0 ? primaryIndex : 0];
	const edges: Edge[] = mappings
		.map((mapping, index) => ({ mapping, nodeId: mappingNodeId(mapping, index) }))
		.filter(({ mapping, nodeId }) => mapping.joinExpression && mainNode && nodeId !== mainNode.id)
		.map(({ mapping, nodeId }) => ({
			id: `edge-${nodeId}`,
			source: mainNode!.id,
			target: nodeId,
			label: mapping.joinExpression?.slice(0, 20),
		}));
	return { nodes, edges };
}
