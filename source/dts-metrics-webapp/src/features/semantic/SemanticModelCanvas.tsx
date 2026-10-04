import "../../polyfills/legacyBrowser";
import { Background, Controls, type Edge, MarkerType, type Node, ReactFlow, useNodesState } from "@xyflow/react";
import { type DragEvent, type ReactNode, useEffect, useMemo } from "react";
import "@xyflow/react/dist/style.css";
import { buildSemanticCanvasGraph, type SemanticJoinOption } from "./semanticCanvas.helpers";
import type { SemanticModelMeta } from "./semanticTypes";

const FIELD_DRAG_MIME = "application/vnd.dts-metrics-field";
const FIELD_METRIC_NODE_PREFIX = "field:metric:";
const FIELD_DIMENSION_NODE_PREFIX = "field:dimension:";

type Props = {
	models: SemanticModelMeta[];
	baseModelId: string;
	selectedJoinTargets: string[];
	selectedMeasures: string[];
	selectedDimensions: string[];
	canEdit: boolean;
	onToggleJoin: (targetId: string) => void;
	onDropField: (fieldId: string, fieldKind: "metric" | "dimension") => void;
};

const NODE_WIDTH = 216;

type SemanticFlowNode = Node<{ label: ReactNode }>;

function edgeTone(option: SemanticJoinOption, selected: boolean): string {
	if (option.fanoutWarning) return selected ? "#d97706" : "#f2c078";
	if (option.approvalRequired) return selected ? "#6d28d9" : "#c4b5fd";
	return selected ? "#0f766e" : "#94a3b8";
}

function layerClass(layer: string): string {
	const value = layer.toLowerCase();
	return value === "dwd" || value === "dws" || value === "ads" ? value : "unknown";
}

function readId(value: unknown): string {
	if (typeof value === "string") return value;
	if (typeof value === "number") return String(value);
	return "";
}

function readLabel(value: Record<string, unknown> | undefined, fallback: string): string {
	return String(value?.label ?? value?.display_name ?? value?.name ?? value?.id ?? fallback);
}

function readDroppedField(event: DragEvent<HTMLElement>): { fieldId: string; fieldKind: "metric" | "dimension" } | null {
	const raw = event.dataTransfer.getData(FIELD_DRAG_MIME) || event.dataTransfer.getData("application/json");
	if (!raw) return null;
	try {
		const parsed = JSON.parse(raw) as Record<string, unknown>;
		const fieldId = readId(parsed.fieldId ?? parsed.name);
		const kind = parsed.fieldKind ?? parsed.role;
		if (fieldId && (kind === "metric" || kind === "dimension")) {
			return { fieldId, fieldKind: kind };
		}
	} catch {
		return null;
	}
	return null;
}

export default function SemanticModelCanvas({
	models,
	baseModelId,
	selectedJoinTargets,
	selectedMeasures,
	selectedDimensions,
	canEdit,
	onToggleJoin,
	onDropField,
}: Props) {
	const graph = useMemo(
		() => buildSemanticCanvasGraph(models, baseModelId, selectedJoinTargets),
		[baseModelId, models, selectedJoinTargets],
	);
	const optionMap = useMemo(() => new Map(graph.joinOptions.map((item) => [item.targetId, item])), [graph.joinOptions]);
	const flowNodes = useMemo<SemanticFlowNode[]>(
		() => {
			const modelNodes = graph.nodes.map((node) => {
				const isBase = node.state === "base";
				const isSelected = node.state === "selected";
				const statusLabel = isBase ? "Base" : isSelected ? "已选" : "候选";

				return {
					id: node.id,
					position: { x: node.x, y: node.y + 180 },
					draggable: canEdit && !isBase,
					data: {
						label: (
							<div className="semantic-flow-node">
								<span className="semantic-node-head">
									<strong>{node.label}</strong>
									<em>{statusLabel}</em>
								</span>
								<span className={`semantic-layer-badge ${layerClass(node.warehouseLayer)}`}>{node.warehouseLayer}</span>
								<span className="semantic-node-key">{node.assetKey}</span>
								<span className="semantic-node-meta">
									指标 {node.metricCount} / 维度 {node.dimensionCount}
								</span>
								<span className="semantic-node-meta">粒度 {node.grain}</span>
								<span className="semantic-node-status-grid">
									<b title="治理状态">{node.governanceStatus}</b>
									<b title="权限决策">{node.permissionDecision}</b>
									<b title="血缘状态">{node.lineageStatus}</b>
								</span>
								<span className="semantic-node-security">{node.subjectArea} / {node.securityLevel}</span>
							</div>
						),
					},
					style: {
						width: NODE_WIDTH,
						borderColor: isBase ? "#288dd2" : isSelected ? "#0f9f83" : "#9fb1c8",
						background: isBase ? "#eef8ff" : isSelected ? "#effbf8" : "#ffffff",
						boxShadow: "0 8px 24px rgba(15, 23, 42, 0.08)",
					},
				};
			});

			const fieldsById = new Map<string, { fieldKind: "metric" | "dimension"; label: string; modelLabel: string }>();
			for (const model of models) {
				const modelLabel = String(model.label ?? model.id ?? "模型");
				for (const field of model.metrics ?? []) {
					const fieldId = readId(field.id);
					if (fieldId) fieldsById.set(fieldId, { fieldKind: "metric", label: readLabel(field, fieldId), modelLabel });
				}
				for (const field of model.dimensions ?? []) {
					const fieldId = readId(field.id);
					if (fieldId) fieldsById.set(fieldId, { fieldKind: "dimension", label: readLabel(field, fieldId), modelLabel });
				}
			}

			const fieldIds = [
				...selectedDimensions.map((fieldId) => ({ fieldId, fieldKind: "dimension" as const })),
				...selectedMeasures.map((fieldId) => ({ fieldId, fieldKind: "metric" as const })),
			];
			const fieldNodes = fieldIds.map(({ fieldId, fieldKind }, index): SemanticFlowNode => {
				const field = fieldsById.get(fieldId);
				const kindLabel = fieldKind === "metric" ? "指标" : "维度";
				const nodePrefix = fieldKind === "metric" ? FIELD_METRIC_NODE_PREFIX : FIELD_DIMENSION_NODE_PREFIX;
				return {
					id: `${nodePrefix}${fieldId}`,
					position: { x: 560, y: 120 + index * 92 },
					draggable: canEdit,
					data: {
						label: (
							<div className={`semantic-flow-node field-node ${fieldKind}`}>
								<span className="semantic-node-head">
									<strong>{field?.label ?? fieldId}</strong>
									<em>{kindLabel}</em>
								</span>
								<span>{field?.modelLabel ?? "当前模型"}</span>
								<span className="semantic-node-meta">{fieldId}</span>
							</div>
						),
					},
					style: {
						width: NODE_WIDTH,
						borderColor: fieldKind === "metric" ? "#0f9f83" : "#288dd2",
						background: fieldKind === "metric" ? "#effbf8" : "#eef8ff",
						boxShadow: "0 8px 24px rgba(15, 23, 42, 0.08)",
					},
				};
			});

			return [...modelNodes, ...fieldNodes];
		},
		[canEdit, graph.nodes, models, selectedDimensions, selectedMeasures],
	);
	const [nodes, setNodes, onNodesChange] = useNodesState(flowNodes);
	const edges = useMemo<Edge[]>(
		() => {
			const joinEdges = graph.edges.map((edge) => {
				const option = optionMap.get(edge.target);
				const stroke = option ? edgeTone(option, edge.selected) : "#94a3b8";

				return {
					id: edge.id,
					source: edge.source,
					target: edge.target,
					type: "smoothstep",
					animated: edge.selected,
					label: edge.label,
					labelStyle: { fontSize: 11, fontWeight: 700, fill: stroke },
					labelBgStyle: { fill: "#ffffff", fillOpacity: 0.92 },
					markerEnd: { type: MarkerType.ArrowClosed, color: stroke },
					style: {
						stroke,
						strokeDasharray: edge.selected ? undefined : "5 4",
						strokeWidth: edge.selected ? 2.4 : 1.6,
					},
				};
			});
			const fieldEdges = [...selectedDimensions, ...selectedMeasures].map((fieldId) => ({
				id: `${baseModelId}->field:${fieldId}`,
				source: baseModelId,
				target: `${selectedDimensions.includes(fieldId) ? FIELD_DIMENSION_NODE_PREFIX : FIELD_METRIC_NODE_PREFIX}${fieldId}`,
				type: "smoothstep",
				animated: true,
				label: selectedDimensions.includes(fieldId) ? "维度" : "指标",
				labelStyle: { fontSize: 11, fontWeight: 700, fill: "#526789" },
				labelBgStyle: { fill: "#ffffff", fillOpacity: 0.92 },
				markerEnd: { type: MarkerType.ArrowClosed, color: "#94a3b8" },
				style: { stroke: "#94a3b8", strokeDasharray: "4 4", strokeWidth: 1.4 },
			}));
			return [...joinEdges, ...fieldEdges];
		},
		[baseModelId, graph.edges, optionMap, selectedDimensions, selectedMeasures],
	);
	const selectedWarnings = useMemo(
		() =>
			graph.joinOptions.filter(
				(option) => selectedJoinTargets.includes(option.targetId) && (option.fanoutWarning || option.approvalRequired),
			),
		[graph.joinOptions, selectedJoinTargets],
	);

	useEffect(() => {
		setNodes(flowNodes);
	}, [flowNodes, setNodes]);

	if (!baseModelId) {
		return <div className="semantic-empty">请选择基础模型后开始可视化建模</div>;
	}

	const handleDrop = (event: DragEvent<HTMLDivElement>) => {
		event.preventDefault();
		const field = readDroppedField(event);
		if (field) onDropField(field.fieldId, field.fieldKind);
	};

	return (
		<div className="semantic-canvas">
			<div className="semantic-flow-toolbar">
				<div>
					<strong>关系画布</strong>
					<span>拖拽节点整理布局，点击候选模型加入 Join</span>
				</div>
				<div className="semantic-flow-legend" aria-label="画布节点状态">
					<span className="legend-base">Base</span>
					<span className="legend-selected">已选</span>
					<span>候选</span>
				</div>
			</div>
			<div
				className="semantic-flow-surface"
				onDragOver={(event) => {
					event.preventDefault();
					event.dataTransfer.dropEffect = "copy";
				}}
				onDrop={handleDrop}
			>
				<ReactFlow
					nodes={nodes}
					edges={edges}
					fitView
					nodesDraggable={canEdit}
					nodesConnectable={false}
					elementsSelectable={canEdit}
					onNodesChange={onNodesChange}
					onNodeClick={(_event, node) => {
						const targetId = String(node.id);
						if (targetId.startsWith("field:")) return;
						const nodeState = graph.nodes.find((item) => item.id === targetId)?.state;
						if (!canEdit || nodeState === "base") return;
						onToggleJoin(targetId);
					}}
					onEdgeClick={(_event, edge) => {
						if (!canEdit) return;
						const targetId = String(edge.target);
						if (targetId.startsWith("field:")) return;
						if (selectedJoinTargets.includes(targetId)) {
							onToggleJoin(targetId);
						}
					}}
				>
					<Background gap={20} size={1} color="#dce7f3" />
					<Controls showInteractive={false} />
				</ReactFlow>
			</div>
			{selectedJoinTargets.length > 0 ? (
				<div className="semantic-flow-selected" aria-label="已选 Join">
					{selectedJoinTargets.map((targetId) => {
						const option = optionMap.get(targetId);
						const warningClass = option?.fanoutWarning ? " warn" : option?.approvalRequired ? " approval" : "";
						return (
							<button className={`chip selected${warningClass}`} key={targetId} type="button" onClick={() => onToggleJoin(targetId)}>
								{option ? `${option.sourceLabel} -> ${option.targetLabel}` : targetId}
							</button>
						);
					})}
				</div>
			) : null}
			{selectedWarnings.length > 0 ? (
				<div className="semantic-canvas-warnings">
					{selectedWarnings.map((option) => (
						<span key={`${option.sourceId}->${option.targetId}`}>
							{option.sourceLabel}
							{" -> "}
							{option.targetLabel}: {option.fanoutWarning ? "fanout 风险" : "需要审批"}
						</span>
					))}
				</div>
			) : null}
		</div>
	);
}
