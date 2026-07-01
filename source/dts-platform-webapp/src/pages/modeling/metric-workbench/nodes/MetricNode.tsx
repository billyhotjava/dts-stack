import { Handle, Position, type NodeProps } from "@xyflow/react";

export type MetricNodeData = {
    metricId: string;
    name: string;
    formulaType?: string;
    status?: string;
};

const FORMULA_COLOR: Record<string, string> = {
    "aggregation/sum": "hsl(220,80%,55%)",
    "aggregation/count_distinct": "hsl(142,60%,45%)",
    "aggregation/avg": "hsl(35,85%,50%)",
};

export function MetricNode({ data, selected }: NodeProps) {
    const d = data as MetricNodeData;
    const isDraft = !d.status || d.status === "DRAFT";
    const borderColor = isDraft ? "hsl(0,0%,70%)" : "hsl(142,60%,45%)";
    const bg = isDraft ? "hsl(0,0%,98%)" : "hsl(142,80%,96%)";
    const formulaColor = d.formulaType ? (FORMULA_COLOR[d.formulaType] ?? "hsl(0,0%,60%)") : "hsl(0,0%,60%)";

    return (
        <div
            style={{
                border: selected ? `2px solid hsl(142,60%,30%)` : `2px solid ${borderColor}`,
                background: bg,
                borderRadius: 8,
                padding: "10px 14px",
                minWidth: 160,
                fontSize: 13,
                cursor: "move",
                boxShadow: selected ? "0 8px 18px rgba(22, 163, 74, 0.16)" : "0 2px 8px rgba(15, 23, 42, 0.06)",
            }}
        >
            <div style={{ fontWeight: 600, color: "hsl(142,30%,20%)" }}>📈 {d.name}</div>
            {d.formulaType && (
                <div
                    style={{
                        marginTop: 4,
                        fontSize: 10,
                        color: formulaColor,
                        background: "rgba(0,0,0,0.04)",
                        borderRadius: 4,
                        padding: "1px 6px",
                        display: "inline-block",
                    }}
                >
                    {d.formulaType}
                </div>
            )}
            {isDraft && (
                <div style={{ fontSize: 10, color: "hsl(0,0%,60%)", marginTop: 2 }}>DRAFT</div>
            )}
            <Handle
                type="target"
                position={Position.Left}
                style={{
                    width: 10,
                    height: 10,
                    border: "2px solid white",
                    background: "hsl(142,60%,45%)",
                    boxShadow: "0 0 0 2px hsla(142,60%,45%,0.22)",
                }}
            />
        </div>
    );
}
