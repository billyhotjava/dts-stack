import {
    ReactFlow,
    ReactFlowProvider,
    Background,
    Controls,
    MiniMap,
    type Node,
    type Edge,
    type NodeTypes,
    type EdgeTypes,
    type OnSelectionChangeFunc,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useCallback, useMemo } from "react";
import type { SemanticBusinessObject, SemanticMetric } from "@/api/semanticModelingApi";
import { BizObjectNode, type BizObjectNodeData } from "./nodes/BizObjectNode";
import { MetricNode, type MetricNodeData } from "./nodes/MetricNode";
import { MetricBindingEdge } from "./edges/MetricBindingEdge";

const NODE_TYPES: NodeTypes = {
    bizObject: BizObjectNode,
    metric: MetricNode,
};
const EDGE_TYPES: EdgeTypes = { binding: MetricBindingEdge };

interface MetricCanvasProps {
    objects: SemanticBusinessObject[];
    metrics: SemanticMetric[];
    selectedId: string | null;
    onNodeSelect: (id: string | null) => void;
    loading?: boolean;
}

function buildNodes(
    objects: SemanticBusinessObject[],
    metrics: SemanticMetric[],
    selectedId: string | null,
): Node[] {
    const objectNodes: Node[] = objects.map((o, i) => ({
        id: `obj-${o.id}`,
        type: "bizObject",
        position: { x: 0, y: i * 180 },
        selected: selectedId === `obj-${o.id}`,
        data: {
            objectId: o.id,
            name: o.name,
            code: o.code,
        } satisfies BizObjectNodeData,
    }));
    const metricNodes: Node[] = metrics.map((m, i) => ({
        id: `metric-${m.id}`,
        type: "metric",
        position: { x: 380, y: i * 140 },
        selected: selectedId === `metric-${m.id}`,
        data: {
            metricId: m.id,
            name: m.name,
            formulaType: m.formulaType,
            status: m.status,
        } satisfies MetricNodeData,
    }));
    return [...objectNodes, ...metricNodes];
}

function buildEdges(metrics: SemanticMetric[]): Edge[] {
    return metrics
        .filter((m) => m.objectId)
        .map((m) => ({
            id: `edge-${m.id}`,
            source: `obj-${m.objectId}`,
            target: `metric-${m.id}`,
            type: "binding",
        }));
}

function MetricCanvasInner({ objects, metrics, selectedId, onNodeSelect, loading }: MetricCanvasProps) {
    const nodes = useMemo(() => buildNodes(objects, metrics, selectedId), [objects, metrics, selectedId]);
    const edges = useMemo(() => buildEdges(metrics), [metrics]);

    const handleSelectionChange: OnSelectionChangeFunc = useCallback(
        ({ nodes: selected }) => {
            onNodeSelect(selected.length > 0 ? selected[0].id : null);
        },
        [onNodeSelect],
    );

    if (loading) {
        return (
            <div className="flex h-full items-center justify-center text-gray-400 text-sm">
                画布加载中...
            </div>
        );
    }

    if (objects.length === 0 && metrics.length === 0) {
        return (
            <div className="flex h-full items-center justify-center text-gray-400 text-sm">
                暂无数据，请先在主题域页创建业务对象
            </div>
        );
    }

    return (
        <ReactFlow
            nodes={nodes}
            edges={edges}
            nodeTypes={NODE_TYPES}
            edgeTypes={EDGE_TYPES}
            fitView
            onSelectionChange={handleSelectionChange}
        >
            <Background />
            <Controls />
            <MiniMap />
        </ReactFlow>
    );
}

export function MetricCanvas(props: MetricCanvasProps) {
    return (
        <ReactFlowProvider>
            <MetricCanvasInner {...props} />
        </ReactFlowProvider>
    );
}
