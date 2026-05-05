import type { Viewport, WorkflowEdge, WorkflowNode } from "../../store/types";
import type { SerializedNode, WorkflowDsl } from "./schema";
import { CURRENT_DSL_VERSION } from "./version";

export interface WorkflowDslState {
	nodes: WorkflowNode[];
	edges: WorkflowEdge[];
	viewport: Viewport;
	metadata?: WorkflowDsl["metadata"];
}

const RUNTIME_DATA_KEYS = new Set(["isDragging", "status", "error"]);
const SUBFLOW_CONFIG_KEYS = new Set(["children", "childEdges"]);

function stripSubflowConfig(config: Record<string, unknown>): Record<string, unknown> {
	const result: Record<string, unknown> = {};
	for (const key of Object.keys(config)) {
		if (!SUBFLOW_CONFIG_KEYS.has(key)) {
			result[key] = config[key];
		}
	}
	return result;
}

function stripRuntimeData(data: Record<string, unknown>): Record<string, unknown> {
	const result: Record<string, unknown> = {};
	for (const key of Object.keys(data)) {
		if (!RUNTIME_DATA_KEYS.has(key)) {
			result[key] =
				key === "config" && data[key] && typeof data[key] === "object"
					? stripSubflowConfig(data[key] as Record<string, unknown>)
					: data[key];
		}
	}
	return result;
}

function asWorkflowNodes(value: unknown): WorkflowNode[] {
	return Array.isArray(value) ? (value as WorkflowNode[]) : [];
}

function asWorkflowEdges(value: unknown): WorkflowEdge[] {
	return Array.isArray(value) ? (value as WorkflowEdge[]) : [];
}

function serializeEdges(edges: WorkflowEdge[]) {
	return edges.map((edge) => ({
		id: edge.id,
		source: edge.source,
		target: edge.target,
		sourceHandle: edge.sourceHandle ?? undefined,
		targetHandle: edge.targetHandle ?? undefined,
	}));
}

export function serializeNode(node: WorkflowNode): SerializedNode {
	const config = (node.data.config ?? {}) as Record<string, unknown>;
	const children = asWorkflowNodes(config.children);
	const childEdges = asWorkflowEdges(config.childEdges);
	return {
		id: node.id,
		type: String(node.type ?? node.data.kind),
		position: { x: node.position.x, y: node.position.y },
		data: stripRuntimeData(node.data),
		children: children.length > 0 ? children.map(serializeNode) : undefined,
		childEdges: childEdges.length > 0 ? serializeEdges(childEdges) : undefined,
	};
}

export function serializeDsl(state: WorkflowDslState): WorkflowDsl {
	const now = new Date().toISOString();
	return {
		dslVersion: CURRENT_DSL_VERSION,
		metadata: {
			createdAt: state.metadata?.createdAt ?? now,
			updatedAt: now,
		},
		nodes: state.nodes.map(serializeNode),
		edges: serializeEdges(state.edges),
		viewport: { x: state.viewport.x, y: state.viewport.y, zoom: state.viewport.zoom },
	};
}
