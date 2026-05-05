import type { XYPosition } from "@xyflow/react";
import type { WorkflowNode } from "../store/types";
import type { DraggedBlockPayload } from "./blocks.config";

let nodeSeq = 0;

export function createWorkflowNodeFromBlock(block: DraggedBlockPayload, position: XYPosition): WorkflowNode {
	nodeSeq += 1;
	const id = `node-${block.kind}-${Date.now()}-${nodeSeq}`;
	return {
		id,
		type: block.kind,
		position: { ...position },
		data: {
			...block.defaultData,
			kind: block.kind,
			title: block.label,
			description: block.label,
			blockColor: block.color,
		},
	};
}

export function resetWorkflowNodeFactoryForTest(): void {
	nodeSeq = 0;
}
