import type { Edge, Node, Viewport, XYPosition } from "@xyflow/react";

/**
 * Workflow 节点 / 连线统一以 xyflow 为底，data 字段在不同业务节点上结构不同，
 * 这里仅约束最小骨架；具体节点类型在 F3 BaseNode 系列里再细化。
 */

export type WorkflowNodeKind =
	| "start"
	| "end"
	| "source"
	| "transform"
	| "validate"
	| "sink"
	| "iteration"
	| "loop"
	| "note"
	| "candidate";

export type WorkflowNodeData = {
	kind: WorkflowNodeKind;
	title: string;
	description?: string;
	config?: Record<string, unknown>;
	isDragging?: boolean;
	[key: string]: unknown;
};

export type WorkflowNode = Node<WorkflowNodeData>;

export type WorkflowEdgeData = {
	label?: string;
	condition?: string;
	[key: string]: unknown;
};

export type WorkflowEdge = Edge<WorkflowEdgeData>;

export interface HelpLineState {
	vertical: number | null;
	horizontal: number | null;
}

export const EMPTY_HELP_LINE: Readonly<HelpLineState> = Object.freeze({
	vertical: null,
	horizontal: null,
});

export interface UiState {
	selectedNodeId: string | null;
	selectedEdgeId: string | null;
	viewport: Viewport;
	panelOpen: boolean;
	helpLine: HelpLineState;
}

export const DEFAULT_VIEWPORT: Readonly<Viewport> = Object.freeze({ x: 0, y: 0, zoom: 1 });

export type { XYPosition, Viewport };
