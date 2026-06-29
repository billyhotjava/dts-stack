import { Handle, Position, type NodeProps } from "@xyflow/react";

export type BizObjectNodeData = {
    objectId: string;
    name: string;
    code: string;
    tableCount: number;
};

export function BizObjectNode({ data, selected }: NodeProps) {
    const d = data as BizObjectNodeData;
    return (
        <div
            style={{
                border: selected ? "2px solid hsl(220,80%,40%)" : "2px solid hsl(220,80%,55%)",
                background: "hsl(220,95%,97%)",
                borderRadius: 8,
                padding: "10px 14px",
                minWidth: 160,
                fontSize: 13,
            }}
        >
            <div style={{ fontWeight: 600, color: "hsl(220,30%,25%)" }}>{d.name}</div>
            <div style={{ color: "hsl(220,20%,55%)", fontSize: 11, marginTop: 2 }}>
                {d.code} · {d.tableCount} 张表
            </div>
            <Handle type="source" position={Position.Right} />
            <Handle type="target" position={Position.Left} />
        </div>
    );
}
