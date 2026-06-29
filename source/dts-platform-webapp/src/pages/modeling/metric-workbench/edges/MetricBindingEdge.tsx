import { BaseEdge, getStraightPath, type EdgeProps } from "@xyflow/react";

export function MetricBindingEdge({
    sourceX, sourceY, targetX, targetY,
}: EdgeProps) {
    const [edgePath] = getStraightPath({ sourceX, sourceY, targetX, targetY });
    return (
        <BaseEdge
            path={edgePath}
            style={{
                stroke: "hsl(142,50%,50%)",
                strokeWidth: 1.5,
                strokeDasharray: "5,3",
            }}
        />
    );
}
