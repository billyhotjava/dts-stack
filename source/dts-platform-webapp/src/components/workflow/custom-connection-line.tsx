import { memo } from "react";
import type { ConnectionLineComponentProps, Connection, IsValidConnection } from "@xyflow/react";
import { canConnect } from "./store/edges-slice";
import { useWorkflowStore } from "./store/workflow-store";
import type { WorkflowEdge } from "./store/types";

export const CONNECTION_VALID_COLOR = "#10b981";
export const CONNECTION_INVALID_COLOR = "#ef4444";
export const CONNECTION_PENDING_COLOR = "#94a3b8";

export type ConnectionStatus = "valid" | "invalid" | null;

export function pickConnectionStroke(status: ConnectionStatus): string {
	if (status === "valid") return CONNECTION_VALID_COLOR;
	if (status === "invalid") return CONNECTION_INVALID_COLOR;
	return CONNECTION_PENDING_COLOR;
}

function CustomConnectionLineComponent(props: ConnectionLineComponentProps) {
	const { fromX, fromY, toX, toY, connectionStatus } = props;
	const stroke = pickConnectionStroke(connectionStatus);
	return (
		<g className="workflow-connection-line">
			<path
				d={`M${fromX},${fromY} L${toX},${toY}`}
				stroke={stroke}
				strokeWidth={1.5}
				strokeDasharray="6 4"
				fill="none"
			/>
			<circle cx={fromX} cy={fromY} r={3} fill={stroke} />
			<circle cx={toX} cy={toY} r={4} fill={stroke} stroke="#ffffff" strokeWidth={1.5} />
		</g>
	);
}

export const CustomConnectionLine = memo(CustomConnectionLineComponent);

/**
 * useIsValidWorkflowConnection — 把 store 中现有 edges 喂给 canConnect，作为 ReactFlow 的 isValidConnection。
 *
 * 注意：拖拽过程中 ReactFlow 需要每次实时查询，因此返回函数捕获当前 edges 引用。
 */
export function useIsValidWorkflowConnection(): IsValidConnection<WorkflowEdge> {
	const edges = useWorkflowStore((s) => s.edges);
	return (edge) => {
		const connection = edge as Connection;
		return canConnect(edges, {
			source: connection.source ?? "",
			target: connection.target ?? "",
			sourceHandle: connection.sourceHandle,
			targetHandle: connection.targetHandle,
		});
	};
}
