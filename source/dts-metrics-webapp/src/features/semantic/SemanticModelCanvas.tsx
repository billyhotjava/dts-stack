import { useMemo } from "react";
import { buildSemanticCanvasGraph, type SemanticJoinOption } from "./semanticCanvas.helpers";
import type { SemanticModelMeta } from "./semanticTypes";

type Props = {
	models: SemanticModelMeta[];
	baseModelId: string;
	selectedJoinTargets: string[];
	canEdit: boolean;
	onToggleJoin: (targetId: string) => void;
};

const NODE_WIDTH = 216;
const NODE_HEIGHT = 108;
const PADDING_X = 44;
const PADDING_Y = 210;

function edgeTone(option: SemanticJoinOption | undefined, selected: boolean): string {
	if (option?.fanoutWarning) return selected ? "#d97706" : "#f2c078";
	if (option?.approvalRequired) return selected ? "#6d28d9" : "#c4b5fd";
	return selected ? "#0f766e" : "#94a3b8";
}

export default function SemanticModelCanvas({
	models,
	baseModelId,
	selectedJoinTargets,
	canEdit,
	onToggleJoin,
}: Props) {
	const graph = useMemo(
		() => buildSemanticCanvasGraph(models, baseModelId, selectedJoinTargets),
		[baseModelId, models, selectedJoinTargets],
	);
	const optionMap = useMemo(() => new Map(graph.joinOptions.map((item) => [item.targetId, item])), [graph.joinOptions]);
	const maxX = Math.max(760, ...graph.nodes.map((node) => node.x + NODE_WIDTH + PADDING_X * 2));
	const maxY = Math.max(420, ...graph.nodes.map((node) => Math.abs(node.y) + NODE_HEIGHT + PADDING_Y + 40));

	if (!baseModelId) {
		return <div className="semantic-empty">请选择基础模型后开始可视化建模</div>;
	}

	return (
		<div className="semantic-canvas" style={{ minHeight: maxY }}>
			<svg className="semantic-canvas-edges" viewBox={`0 0 ${maxX} ${maxY}`} aria-hidden="true">
				{graph.edges.map((edge) => {
					const source = graph.nodes.find((node) => node.id === edge.source);
					const target = graph.nodes.find((node) => node.id === edge.target);
					if (!source || !target) return null;
					const option = optionMap.get(edge.target);
					const stroke = edgeTone(option, edge.selected);
					const startX = source.x + PADDING_X + NODE_WIDTH;
					const startY = source.y + PADDING_Y + NODE_HEIGHT / 2;
					const endX = target.x + PADDING_X;
					const endY = target.y + PADDING_Y + NODE_HEIGHT / 2;
					const midX = startX + Math.max(64, (endX - startX) / 2);
					return (
						<g key={edge.id}>
							<path
								d={`M ${startX} ${startY} C ${midX} ${startY}, ${midX} ${endY}, ${endX} ${endY}`}
								fill="none"
								stroke={stroke}
								strokeDasharray={edge.selected ? undefined : "7 6"}
								strokeWidth={edge.selected ? 2.6 : 1.6}
							/>
							<text x={(startX + endX) / 2} y={(startY + endY) / 2 - 8} fill={stroke}>
								{edge.label}
							</text>
						</g>
					);
				})}
			</svg>
			{graph.nodes.map((node) => {
				const isBase = node.state === "base";
				const isSelected = node.state === "selected";
				return (
					<button
						type="button"
						className={`semantic-node ${isBase ? "base" : ""} ${isSelected ? "selected" : ""}`}
						key={node.id}
						style={{ left: node.x + PADDING_X, top: node.y + PADDING_Y, width: NODE_WIDTH }}
						disabled={!canEdit || isBase}
						onClick={() => onToggleJoin(node.id)}
					>
						<span className="semantic-node-head">
							<strong>{node.label}</strong>
							<em>{isBase ? "Base" : isSelected ? "已选" : "候选"}</em>
						</span>
						<span>{node.subjectArea}</span>
						<span className="semantic-node-meta">
							指标 {node.metricCount} / 维度 {node.dimensionCount}
						</span>
						<span className="semantic-node-security">{node.securityLevel}</span>
					</button>
				);
			})}
			{selectedJoinTargets.length > 0 ? (
				<div className="semantic-canvas-warnings">
					{selectedJoinTargets.map((targetId) => {
						const option = optionMap.get(targetId);
						if (!option?.fanoutWarning && !option?.approvalRequired) return null;
						return (
							<span key={targetId}>
								{option.sourceLabel} -> {option.targetLabel}: {option.fanoutWarning ? "fanout 风险" : "需要审批"}
							</span>
						);
					})}
				</div>
			) : null}
		</div>
	);
}
