import type { NodeProps } from "@xyflow/react";
import { BLOCKS, type BlockDef } from "../block-selector";
import type { WorkflowNode, WorkflowNodeData, WorkflowNodeKind } from "../store/types";

export type WorkflowNodeProps = NodeProps<WorkflowNode>;

export function getWorkflowBlock(kind: WorkflowNodeKind): BlockDef {
	const block = BLOCKS.find((item) => item.kind === kind);
	if (!block) {
		throw new Error(`Unknown workflow block kind: ${kind}`);
	}
	return block;
}

export function getNodeConfig(data: WorkflowNodeData): Record<string, unknown> {
	return data.config ?? {};
}

export function stringValue(value: unknown, fallback = ""): string {
	return typeof value === "string" && value.length > 0 ? value : fallback;
}

export function numberValue(value: unknown): number | null {
	return typeof value === "number" && Number.isFinite(value) ? value : null;
}
