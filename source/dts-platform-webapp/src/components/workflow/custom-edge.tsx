import { memo, useCallback, useMemo, useState, type CSSProperties } from "react";
import { BaseEdge, EdgeLabelRenderer, getBezierPath, type EdgeProps, type EdgeTypes } from "@xyflow/react";
import { useWorkflowStore } from "./store/workflow-store";
import type { WorkflowEdge, WorkflowEdgeData } from "./store/types";

const DEFAULT_STROKE = "#94a3b8";
const SELECTED_STROKE = "#3b82f6";
const HOVER_STROKE = "#64748b";

type ColorFromData = WorkflowEdgeData & { sourceColor?: string; targetColor?: string };

export function pickEdgeColor(data: WorkflowEdgeData | undefined, key: "sourceColor" | "targetColor"): string {
	const candidate = (data as ColorFromData | undefined)?.[key];
	return typeof candidate === "string" && candidate.length > 0 ? candidate : DEFAULT_STROKE;
}

export function resolveEdgeStroke(opts: {
	gradientId: string;
	selected: boolean;
	hover: boolean;
}): string {
	if (opts.selected) return SELECTED_STROKE;
	if (opts.hover) return HOVER_STROKE;
	return `url(#${opts.gradientId})`;
}

export function shouldShowRemoveButton(opts: { selected: boolean; hover: boolean; data: WorkflowEdgeData | undefined }): boolean {
	if (opts.data?.condition) return false;
	return Boolean(opts.selected || opts.hover);
}

function CustomEdgeComponent(props: EdgeProps<WorkflowEdge>) {
	const { id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, selected, data } = props;
	const removeEdge = useWorkflowStore((s) => s.removeEdge);
	const [hover, setHover] = useState(false);

	const [edgePath, labelX, labelY] = getBezierPath({
		sourceX,
		sourceY,
		targetX,
		targetY,
		sourcePosition,
		targetPosition,
	});

	const gradientId = `wf-edge-${id}`;
	const sourceColor = pickEdgeColor(data, "sourceColor");
	const targetColor = pickEdgeColor(data, "targetColor");

	const stroke = useMemo(
		() => resolveEdgeStroke({ gradientId, selected: Boolean(selected), hover }),
		[selected, hover, gradientId],
	);

	const strokeWidth = selected ? 2 : 1.5;
	const onEnter = useCallback(() => setHover(true), []);
	const onLeave = useCallback(() => setHover(false), []);

	const onRemove = useCallback(() => {
		removeEdge(id);
	}, [removeEdge, id]);

	const labelStyle: CSSProperties = {
		position: "absolute",
		transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
		pointerEvents: "all",
	};

	return (
		<>
			<defs>
				<linearGradient id={gradientId} gradientUnits="userSpaceOnUse" x1={sourceX} y1={sourceY} x2={targetX} y2={targetY}>
					<stop offset="0%" stopColor={sourceColor} />
					<stop offset="100%" stopColor={targetColor} />
				</linearGradient>
			</defs>
			<BaseEdge
				id={id}
				path={edgePath}
				style={{ stroke, strokeWidth, fill: "none" }}
				interactionWidth={20}
			/>
			<g onMouseEnter={onEnter} onMouseLeave={onLeave}>
				{data?.label ? (
					<EdgeLabelRenderer>
						<div style={labelStyle} className="workflow-edge-label">
							{data.label}
						</div>
					</EdgeLabelRenderer>
				) : null}
				{shouldShowRemoveButton({ selected: Boolean(selected), hover, data }) ? (
					<EdgeLabelRenderer>
						<div style={labelStyle}>
							<button
								type="button"
								onClick={onRemove}
								className="workflow-edge-remove"
								aria-label={`删除连线 ${id}`}
							>
								×
							</button>
						</div>
					</EdgeLabelRenderer>
				) : null}
			</g>
		</>
	);
}

export const CustomEdge = memo(CustomEdgeComponent);

export const workflowEdgeTypes: EdgeTypes = {
	custom: CustomEdge as unknown as EdgeTypes["custom"],
};

export const DEFAULT_EDGE_TYPE = "custom";
