import { Handle, Position, type NodeProps } from "@xyflow/react";

export type BizObjectNodeData = {
    objectId: string;
    name: string;
    code: string;
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
                cursor: "move",
                boxShadow: selected ? "0 8px 18px rgba(37, 99, 235, 0.16)" : "0 2px 8px rgba(15, 23, 42, 0.06)",
            }}
        >
            <div style={{ fontWeight: 600, color: "hsl(220,30%,25%)" }}>{d.name}</div>
            <div style={{ color: "hsl(220,20%,55%)", fontSize: 11, marginTop: 2 }}>
                {d.code}
            </div>
            <Handle
                type="source"
                position={Position.Right}
                style={{
                    width: 10,
                    height: 10,
                    border: "2px solid white",
                    background: "hsl(220,80%,55%)",
                    boxShadow: "0 0 0 2px hsla(220,80%,55%,0.22)",
                }}
            />
        </div>
    );
}
