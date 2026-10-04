import type { WorkflowEdge, WorkflowNode, WorkflowNodeData, WorkflowNodeKind } from "../../store/types";
import { type SerializedEdge, type SerializedNode, type WorkflowDsl, workflowDslSchema } from "./schema";
import { CURRENT_DSL_VERSION } from "./version";

export type DeserializeResult =
	| {
			success: true;
			state: {
				nodes: WorkflowNode[];
				edges: WorkflowEdge[];
				viewport: WorkflowDsl["viewport"];
				metadata?: WorkflowDsl["metadata"];
			};
	  }
	| { success: false; error: string };

function rebuildEdge(edge: SerializedEdge): WorkflowEdge {
	return {
		id: edge.id,
		source: edge.source,
		target: edge.target,
		sourceHandle: edge.sourceHandle,
		targetHandle: edge.targetHandle,
	};
}

function rebuildNode(node: SerializedNode): WorkflowNode {
	const data = node.data as WorkflowNodeData;
	const children = (node.children ?? []).map(rebuildNode);
	const childEdges = (node.childEdges ?? []).map(rebuildEdge);
	const config = {
		...(data.config ?? {}),
		...(children.length > 0 ? { children } : {}),
		...(childEdges.length > 0 ? { childEdges } : {}),
	};
	return {
		id: node.id,
		type: node.type,
		position: { x: node.position.x, y: node.position.y },
		data: {
			...data,
			kind: (data.kind ?? node.type) as WorkflowNodeKind,
			title: typeof data.title === "string" ? data.title : node.type,
			config,
			isDragging: false,
		},
	};
}

export function deserializeDsl(input: unknown): DeserializeResult {
	const parsed = workflowDslSchema.safeParse(input);
	if (!parsed.success) {
		return {
			success: false,
			error: parsed.error.issues.map((issue) => `${issue.path.join(".")}: ${issue.message}`).join("; "),
		};
	}
	const dsl = parsed.data;
	if (dsl.dslVersion !== CURRENT_DSL_VERSION) {
		return { success: false, error: `Unsupported DSL version: ${dsl.dslVersion}` };
	}
	return {
		success: true,
		state: {
			nodes: dsl.nodes.map(rebuildNode),
			edges: dsl.edges.map(rebuildEdge),
			viewport: dsl.viewport,
			metadata: dsl.metadata,
		},
	};
}
