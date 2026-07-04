import { BaseEdge, EdgeLabelRenderer, getStraightPath, type EdgeProps } from "@xyflow/react";

export function MetricBindingEdge({
    sourceX, sourceY, targetX, targetY, data, selected,
}: EdgeProps) {
    const [edgePath] = getStraightPath({ sourceX, sourceY, targetX, targetY });
    const relationType = data?.relationType === "METRIC_DERIVES" ? "METRIC_DERIVES" : "OBJECT_METRIC";
    const label = relationType === "METRIC_DERIVES" ? "派生" : "绑定";
    const stroke = relationType === "METRIC_DERIVES" ? "hsl(217,75%,55%)" : "hsl(142,50%,50%)";
    const labelX = (sourceX + targetX) / 2;
    const labelY = (sourceY + targetY) / 2;

    return (
        <>
            <BaseEdge
                path={edgePath}
                style={{
                    stroke,
                    strokeWidth: selected ? 2.5 : 1.5,
                    strokeDasharray: relationType === "METRIC_DERIVES" ? "0" : "5,3",
                }}
            />
            <EdgeLabelRenderer>
                <div
                    className="nodrag nopan rounded border border-gray-200 bg-white px-1.5 py-0.5 text-[10px] text-gray-600 shadow-sm"
                    style={{
                        position: "absolute",
                        transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
                        pointerEvents: "none",
                    }}
                    data-relation-type={relationType}
                >
                    {label}
                </div>
            </EdgeLabelRenderer>
        </>
    );
}
