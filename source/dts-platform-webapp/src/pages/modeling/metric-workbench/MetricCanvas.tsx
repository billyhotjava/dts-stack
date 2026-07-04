import {
    ReactFlow,
    ReactFlowProvider,
    Background,
    Controls,
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
import { useCallback, useEffect, useState, type DragEvent, type ReactNode } from "react";
import { Button, Space } from "antd";
import { AlertTriangle, GitBranch, MousePointer2, Save, Wand2 } from "lucide-react";
import type { SemanticBusinessObject, SemanticMetric } from "@/api/semanticModelingApi";
import { BizObjectNode } from "./nodes/BizObjectNode";
import { MetricNode } from "./nodes/MetricNode";
import { MetricBindingEdge } from "./edges/MetricBindingEdge";
import {
    buildMetricCanvasEdges,
    buildMetricCanvasNodes,
    findMetricDropTargetObject,
    getMetricDependencyIds,
    parseMetricDragPayload,
    resolveMetricConnection,
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
    onMetricDerived: (sourceMetricId: string, targetMetricId: string) => Promise<void> | void;
    onPreflight: () => void;
    onArrangementSave: () => void;
    loading?: boolean;
}

const METRIC_DRAG_MIME = "application/x-dts-metric";
const NODE_POSITION_STORAGE_KEY = "dts.metricWorkbench.nodePositions.v1";
type CanvasMode = "select" | "connect";

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
    onMetricDerived,
    onPreflight,
    onArrangementSave,
    loading,
}: MetricCanvasProps) {
    const { screenToFlowPosition } = useReactFlow();
    const [nodePositions, setNodePositions] = useState<MetricCanvasNodePositionMap>(() => readNodePositions());
    const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
    const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
    const [canvasMode, setCanvasMode] = useState<CanvasMode>("select");

    useEffect(() => {
        setNodes(buildMetricCanvasNodes(objects, metrics, null, nodePositions));
    }, [objects, metrics, nodePositions, setNodes]);

    useEffect(() => {
        setNodes((current) =>
            current.map((node) => ({
                ...node,
                selected: selectedId === node.id,
            })),
        );
    }, [selectedId, setNodes]);

    useEffect(() => {
        setEdges(buildMetricCanvasEdges(metrics));
    }, [metrics, setEdges]);

    const handleSelectionChange: OnSelectionChangeFunc = useCallback(
        ({ nodes: selectedNodes, edges: selectedEdges }) => {
            if (selectedEdges.length > 0) {
                onNodeSelect(selectedEdges[0].id);
                return;
            }
            onNodeSelect(selectedNodes.length > 0 ? selectedNodes[0].id : null);
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
                        data: { relationType: "OBJECT_METRIC" },
                    },
                    current.filter(
                        (edge) =>
                            edge.target !== `metric-${metricId}` ||
                            edge.data?.relationType !== "OBJECT_METRIC",
                    ),
                ),
            );
            onNodeSelect(`metric-${metricId}`);
        },
        [onMetricBound, onNodeSelect, setEdges],
    );

    const deriveMetricFromMetric = useCallback(
        async (sourceMetricId: string, targetMetricId: string) => {
            await onMetricDerived(sourceMetricId, targetMetricId);
            setEdges((current) =>
                addEdge(
                    {
                        id: `edge-derives-${sourceMetricId}-${targetMetricId}`,
                        source: `metric-${sourceMetricId}`,
                        target: `metric-${targetMetricId}`,
                        type: "binding",
                        data: {
                            relationType: "METRIC_DERIVES",
                            sourceMetricId,
                            targetMetricId,
                        },
                    },
                    current.filter((edge) => edge.id !== `edge-derives-${sourceMetricId}-${targetMetricId}`),
                ),
            );
            onNodeSelect(`edge-derives-${sourceMetricId}-${targetMetricId}`);
        },
        [onMetricDerived, onNodeSelect, setEdges],
    );

    const handleConnect = useCallback(
        (connection: Connection) => {
            const relation = resolveMetricConnection(connection);
            if (!relation) {
                return;
            }
            if (relation.relationType === "OBJECT_METRIC") {
                void bindMetricToObject(relation.metricId, relation.objectId);
                return;
            }
            void deriveMetricFromMetric(relation.sourceMetricId, relation.targetMetricId);
        },
        [bindMetricToObject, deriveMetricFromMetric],
    );

    const handleAutoLayout = useCallback(() => {
        const metricDepth = new Map<string, number>();
        const resolveDepth = (metric: SemanticMetric, trail = new Set<string>()): number => {
            if (metricDepth.has(metric.id)) {
                return metricDepth.get(metric.id)!;
            }
            if (trail.has(metric.id)) {
                return 0;
            }
            trail.add(metric.id);
            const dependencyDepths = getMetricDependencyIds(metric)
                .map((dependencyId) => metrics.find((item) => item.id === dependencyId))
                .filter((item): item is SemanticMetric => Boolean(item))
                .map((dependency) => resolveDepth(dependency, new Set(trail)));
            const depth = dependencyDepths.length > 0 ? Math.max(...dependencyDepths) + 1 : 0;
            metricDepth.set(metric.id, depth);
            return depth;
        };
        const nextPositions: MetricCanvasNodePositionMap = {};
        objects.forEach((object, index) => {
            nextPositions[`obj-${object.id}`] = { x: 0, y: index * 160 };
        });
        metrics.forEach((metric, index) => {
            const depth = resolveDepth(metric);
            nextPositions[`metric-${metric.id}`] = { x: 420 + depth * 280, y: index * 118 };
        });
        setNodePositions(nextPositions);
        writeNodePositions(nextPositions);
        setNodes(buildMetricCanvasNodes(objects, metrics, null, nextPositions));
    }, [metrics, objects, setNodes]);

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

    const toolbar = (
        <div
            className="flex flex-wrap items-center justify-between gap-2 border-b border-gray-100 bg-gray-50 px-3 py-2"
            data-testid="metric-canvas-toolbar"
        >
            <Space size={6} wrap>
                <Button
                    size="small"
                    type={canvasMode === "select" ? "primary" : "default"}
                    icon={<MousePointer2 size={14} />}
                    onClick={() => setCanvasMode("select")}
                >
                    选择
                </Button>
                <Button
                    size="small"
                    type={canvasMode === "connect" ? "primary" : "default"}
                    icon={<GitBranch size={14} />}
                    onClick={() => setCanvasMode("connect")}
                >
                    连线
                </Button>
                <Button size="small" icon={<Wand2 size={14} />} onClick={handleAutoLayout}>
                    自动布局
                </Button>
            </Space>
            <Space size={6} wrap>
                <Button size="small" icon={<AlertTriangle size={14} />} onClick={onPreflight}>
                    预检
                </Button>
                <Button size="small" type="primary" icon={<Save size={14} />} onClick={onArrangementSave}>
                    保存编排
                </Button>
            </Space>
        </div>
    );

    const renderShell = (content: ReactNode) => (
        <div className="flex h-full flex-col">
            {toolbar}
            <div className="min-h-0 flex-1">{content}</div>
        </div>
    );

    if (loading) {
        return renderShell(
            <div className="flex h-full items-center justify-center text-gray-400 text-sm">
                画布加载中...
            </div>,
        );
    }

    if (objects.length === 0 && metrics.length === 0) {
        return renderShell(
            <div className="flex h-full items-center justify-center text-gray-400 text-sm">
                暂无数据，请先在业务对象页选择治理主题域并创建业务对象
            </div>,
        );
    }

    return renderShell(
        <ReactFlow
            nodes={nodes}
            edges={edges}
            nodeTypes={NODE_TYPES}
            edgeTypes={EDGE_TYPES}
            fitView
            nodesDraggable
            nodesConnectable={canvasMode === "connect"}
            onNodesChange={handleNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={handleConnect}
            onNodeDragStop={handleNodeDragStop}
            onSelectionChange={handleSelectionChange}
            onDragOver={handleDragOver}
            onDrop={handleDrop}
            connectionRadius={36}
            aria-label="拖指标节点到业务对象节点上完成绑定 METRIC_DERIVES"
        >
            <Background />
            <Controls />
        </ReactFlow>,
    );
}

export function MetricCanvas(props: MetricCanvasProps) {
    return (
        <ReactFlowProvider>
            <MetricCanvasInner {...props} />
        </ReactFlowProvider>
    );
}
