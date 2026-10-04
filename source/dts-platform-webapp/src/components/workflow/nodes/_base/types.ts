import type { ReactNode } from "react";
import type { BlockDef } from "../../block-selector";

export interface HandleDef {
	id: string;
	label?: string;
	color?: string;
}

export type WorkflowNodeStatus = "idle" | "running" | "success" | "error";

export interface BaseNodeProps {
	nodeId: string;
	block: BlockDef;
	selected?: boolean;
	dragging?: boolean;
	data?: Record<string, unknown>;
	inputs?: HandleDef[];
	outputs?: HandleDef[];
	children?: ReactNode;
}
