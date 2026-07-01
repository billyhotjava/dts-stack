import {
    ReactFlow,
    ReactFlowProvider,
    Background,
    Controls,
    MiniMap,
    addEdge,
    useEdgesState,
    useNodesState,
    useReactFlow,
    type NodeTypes,
    type EdgeTypes,
    type OnSelectionChangeFunc,
    type Connection,
    type NodeChange,
    type OnNodeDrag,
    type Node,
    type Edge,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useCallback, useEffect, useState, type DragEvent } from "react";
import type { SemanticBusinessObject, SemanticMetric } from "@/api/semanticModelingApi";
import { BizObjectNode } from "./nodes/BizObjectNode";
import { MetricNode } from "./nodes/MetricNode";
import { MetricBindingEdge } from "./edges/MetricBindingEdge";
import {
    buildMetricCanvasEdges,
    buildMetricCanvasNodes,
    findMetricDropTargetObject,
    parseMetricDragPayload,
    resolveMetricBinding,
    resolveMetricNodeDropBinding,
    type MetricCanvasNodePositionMap,
} from "./metricCanvas.helpers";

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
    onMetricBound: (metricId: string, objectId: string) => Promise<void> | void;
    loading?: boolean;
}

const METRIC_DRAG_MIME = "application/x-dts-metric";
const NODE_POSITION_STORAGE_KEY = "dts.metricWorkbench.nodePositions.v1";

function readNodePositions(): MetricCanvasNodePositionMap {
    if (typeof window === "undefined") {
        return {};
    }
    try {
        const raw = window.localStorage.getItem(NODE_POSITION_STORAGE_KEY);
        return raw ? (JSON.parse(raw) as MetricCanvasNodePositionMap) : {};
    } catch {
        return {};
    }
}

function writeNodePositions(positions: MetricCanvasNodePositionMap) {
    if (typeof window === "undefined") {
        return;
    }
    try {
        window.localStorage.setItem(NODE_POSITION_STORAGE_KEY, JSON.stringify(positions));
    } catch {
        /* ignore browser storage failures */
    }
}

function MetricCanvasInner({
    objects,
    metrics,
    selectedId,
    onNodeSelect,
    onMetricBound,
    loading,
}: MetricCanvasProps) {
    const { screenToFlowPosition } = useReactFlow();
    const [nodePositions, setNodePositions] = useState<MetricCanvasNodePositionMap>(() => readNodePositions());
    const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
    const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);

    useEffect(() => {
        setNodes(buildMetricCanvasNodes(objects, metrics, selectedId, nodePositions));
    }, [objects, metrics, selectedId, nodePositions, setNodes]);

    useEffect(() => {
        setEdges(buildMetricCanvasEdges(metrics));
    }, [metrics, setEdges]);

    const handleSelectionChange: OnSelectionChangeFunc = useCallback(
        ({ nodes: selected }) => {
            onNodeSelect(selected.length > 0 ? selected[0].id : null);
        },
        [onNodeSelect],
    );

    const saveNodePosition = useCallback((nodeId: string, position: { x: number; y: number }) => {
        setNodePositions((current) => {
            const next = {
                ...current,
                [nodeId]: {
                    x: Math.round(position.x),
                    y: Math.round(position.y),
                },
            };
            writeNodePositions(next);
            return next;
        });
    }, []);

    const handleNodesChange = useCallback(
        (changes: NodeChange[]) => {
            onNodesChange(changes);
            for (const change of changes) {
                if (change.type === "position" && change.position && change.dragging === false) {
                    saveNodePosition(change.id, change.position);
                }
            }
        },
        [onNodesChange, saveNodePosition],
    );

    const bindMetricToObject = useCallback(
        async (metricId: string, objectId: string) => {
            await onMetricBound(metricId, objectId);
            setEdges((current) =>
                addEdge(
                    {
                        id: `edge-${metricId}`,
                        source: `obj-${objectId}`,
                        target: `metric-${metricId}`,
                        type: "binding",
                    },
                    current.filter((edge) => edge.target !== `metric-${metricId}`),
                ),
            );
            onNodeSelect(`metric-${metricId}`);
        },
        [onMetricBound, onNodeSelect, setEdges],
    );

    const handleConnect = useCallback(
        (connection: Connection) => {
            const binding = resolveMetricBinding(connection);
            if (!binding) {
                return;
            }
            void bindMetricToObject(binding.metricId, binding.objectId);
        },
        [bindMetricToObject],
    );

    const handleNodeDragStop: OnNodeDrag<Node> = useCallback(
        (_event, node) => {
            saveNodePosition(node.id, node.position);
            const latestNodes = nodes.map((item) =>
                item.id === node.id ? { ...item, position: node.position } : item,
            );
            const binding = resolveMetricNodeDropBinding(node, latestNodes);
            if (!binding) {
                return;
            }
            const metric = metrics.find((item) => item.id === binding.metricId);
            if (metric?.objectId === binding.objectId) {
                onNodeSelect(node.id);
                return;
            }
            void bindMetricToObject(binding.metricId, binding.objectId);
        },
        [bindMetricToObject, metrics, nodes, onNodeSelect, saveNodePosition],
    );

    const handleDragOver = useCallback((event: DragEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.dataTransfer.dropEffect = "link";
    }, []);

    const handleDrop = useCallback(
        (event: DragEvent<HTMLDivElement>) => {
            event.preventDefault();
            const metricId = parseMetricDragPayload(
                event.dataTransfer.getData(METRIC_DRAG_MIME) || event.dataTransfer.getData("text/plain"),
            );
            if (!metricId) {
                return;
            }
            const position = screenToFlowPosition({ x: event.clientX, y: event.clientY });
            const objectId = findMetricDropTargetObject(nodes, position);
            const nodeId = `metric-${metricId}`;

            saveNodePosition(nodeId, position);
            onNodeSelect(nodeId);
            if (objectId) {
                void bindMetricToObject(metricId, objectId);
            }
        },
        [bindMetricToObject, nodes, onNodeSelect, saveNodePosition, screenToFlowPosition],
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
                暂无数据，请先在业务对象页选择治理主题域并创建业务对象
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
            nodesDraggable
            nodesConnectable
            onNodesChange={handleNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={handleConnect}
            onNodeDragStop={handleNodeDragStop}
            onSelectionChange={handleSelectionChange}
            onDragOver={handleDragOver}
            onDrop={handleDrop}
            connectionRadius={36}
            aria-label="拖指标节点到业务对象节点上完成绑定"
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
