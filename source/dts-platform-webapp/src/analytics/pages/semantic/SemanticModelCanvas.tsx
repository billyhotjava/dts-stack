// Chrome 95 兼容性:@xyflow/react v12 内部使用 structuredClone(连接拖拽路径)。
// 入口 `polyfills/legacy-browser.ts` 已注入 polyfill;此处显式 import 以确保
// 即使 SemanticModelCanvas 被某个未来代码路径异步加载/单测使用,polyfill 也已就位。
import "@/polyfills/legacy-browser";
import { Background, Controls, type Edge, MarkerType, type Node, ReactFlow } from "@xyflow/react";
import { Alert, Empty, Space, Tag, Typography } from "antd";
import { useMemo } from "react";
import "@xyflow/react/dist/style.css";
import { toast } from "sonner";
import type { SemanticModelMeta } from "../../api/analyticsApi";
import { buildSemanticCanvasGraph, type SemanticJoinOption } from "./semanticCanvas.helpers";

type Props = {
	models: SemanticModelMeta[];
	baseModelId: string;
	selectedJoinTargets: string[];
	canEdit: boolean;
	onToggleJoin: (targetId: string) => void;
};

const NODE_WIDTH = 216;

function edgeColor(option: SemanticJoinOption, selected: boolean): string {
	if (option.fanoutWarning) {
		return selected ? "#d46b08" : "#f5c08a";
	}
	if (option.approvalRequired) {
		return selected ? "#7c3aed" : "#c4b5fd";
	}
	return selected ? "#1677ff" : "#cbd5e1";
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
		[models, baseModelId, selectedJoinTargets],
	);

	const joinOptionMap = useMemo(
		() => new Map(graph.joinOptions.map((item) => [item.targetId, item])),
		[graph.joinOptions],
	);

	const nodes = useMemo<Node[]>(
		() =>
			graph.nodes.map((node) => {
				const isBase = node.state === "base";
				const isSelected = node.state === "selected";
				const borderColor = isBase ? "#1677ff" : isSelected ? "#10b981" : "#94a3b8";
				const background = isBase ? "linear-gradient(135deg, #e0f2fe, #f8fbff)" : isSelected ? "#ecfdf5" : "#ffffff";

				return {
					id: node.id,
					position: { x: node.x, y: node.y },
					data: {
						label: (
							<div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
								<div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8 }}>
									<strong style={{ fontSize: 13, lineHeight: 1.2 }}>{node.label}</strong>
									<Tag color={isBase ? "blue" : isSelected ? "green" : "default"} style={{ marginInlineEnd: 0 }}>
										{isBase ? "Base" : isSelected ? "已选" : "候选"}
									</Tag>
								</div>
								<span style={{ fontSize: 11, color: "var(--ant-color-text-secondary)" }}>{node.subjectArea}</span>
								<div style={{ display: "flex", gap: 8, fontSize: 11, color: "var(--ant-color-text-secondary)" }}>
									<span>指标 {node.metricCount}</span>
									<span>维度 {node.dimensionCount}</span>
								</div>
								<Tag
									color={
										node.securityLevel === "CONFIDENTIAL" ? "red" : node.securityLevel === "SECRET" ? "orange" : "blue"
									}
									style={{ width: "fit-content", marginInlineEnd: 0 }}
								>
									{node.securityLevel}
								</Tag>
							</div>
						),
					},
					style: {
						width: NODE_WIDTH,
						padding: 12,
						borderRadius: 14,
						border: `1px solid ${borderColor}`,
						background,
						boxShadow: isBase ? "0 10px 30px rgba(22,119,255,0.12)" : "0 8px 24px rgba(15,23,42,0.08)",
						cursor: !isBase && canEdit ? "pointer" : "default",
					},
				};
			}),
		[canEdit, graph.nodes],
	);

	const edges = useMemo<Edge[]>(
		() =>
			graph.edges.map((edge) => {
				const option = joinOptionMap.get(edge.target);
				const stroke = option ? edgeColor(option, edge.selected) : "#cbd5e1";
				return {
					id: edge.id,
					source: edge.source,
					target: edge.target,
					type: "smoothstep",
					animated: edge.selected,
					label: edge.label,
					labelStyle: { fontSize: 11, fontWeight: 600, fill: stroke },
					labelBgStyle: { fill: "#ffffff", fillOpacity: 0.92 },
					markerEnd: { type: MarkerType.ArrowClosed, color: stroke },
					style: {
						stroke,
						strokeWidth: edge.selected ? 2.4 : 1.6,
						strokeDasharray: edge.selected ? undefined : "5 4",
					},
				};
			}),
		[graph.edges, joinOptionMap],
	);

	const selectedWarnings = useMemo(
		() =>
			graph.joinOptions.filter(
				(item) => selectedJoinTargets.includes(item.targetId) && (item.fanoutWarning || item.approvalRequired),
			),
		[graph.joinOptions, selectedJoinTargets],
	);

	if (!baseModelId) {
		return <Empty description="请选择基础模型后开始可视化建模" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
	}

	return (
		<div className="space-y-3">
			<div style={{ border: "1px solid var(--ant-color-border-secondary)", borderRadius: 16, overflow: "hidden" }}>
				<div
					style={{
						display: "flex",
						alignItems: "center",
						justifyContent: "space-between",
						padding: "10px 14px",
						borderBottom: "1px solid var(--ant-color-border-secondary)",
						background: "linear-gradient(180deg, rgba(248,250,252,0.9), rgba(255,255,255,0.95))",
					}}
				>
					<div>
						<Typography.Text strong>关系画布</Typography.Text>
						<div className="text-xs text-secondary">点击候选模型加入 Join，再点已选模型可移除</div>
					</div>
					<Space size={6}>
						<Tag color="blue">Base</Tag>
						<Tag color="green">已选</Tag>
						<Tag>候选</Tag>
					</Space>
				</div>
				<div style={{ height: 360 }}>
					<ReactFlow
						nodes={nodes}
						edges={edges}
						fitView
						nodesDraggable={false}
						nodesConnectable={false}
						elementsSelectable={false}
						onNodeClick={(_event, node) => {
							const targetId = String(node.id);
							const nodeState = graph.nodes.find((item) => item.id === targetId)?.state;
							if (!canEdit || nodeState === "base") {
								return;
							}
							const option = joinOptionMap.get(targetId);
							if (option?.approvalRequired) {
								toast.info(
									`Join ${option.sourceLabel} -> ${option.targetLabel} 标记为需要审批，当前仍可先做建模预览。`,
								);
							}
							if (option?.fanoutWarning) {
								toast.warning(
									`Join ${option.sourceLabel} -> ${option.targetLabel} 存在 fanout 风险，某些查询会被后端拒绝。`,
								);
							}
							onToggleJoin(targetId);
						}}
						onEdgeClick={(_event, edge) => {
							if (!canEdit) {
								return;
							}
							const targetId = String(edge.target);
							if (selectedJoinTargets.includes(targetId)) {
								onToggleJoin(targetId);
							}
						}}
					>
						<Background gap={20} size={1} color="#e2e8f0" />
						<Controls showInteractive={false} />
					</ReactFlow>
				</div>
			</div>

			{selectedJoinTargets.length > 0 && (
				<Space wrap>
					{selectedJoinTargets.map((targetId) => {
						const option = joinOptionMap.get(targetId);
						const color = option?.fanoutWarning ? "orange" : option?.approvalRequired ? "purple" : "green";
						const label = option ? `${option.sourceLabel} -> ${option.targetLabel}` : targetId;
						return (
							<Tag
								key={targetId}
								color={color}
								closable={canEdit}
								onClose={(event) => {
									event.preventDefault();
									onToggleJoin(targetId);
								}}
							>
								{label}
							</Tag>
						);
					})}
				</Space>
			)}

			{selectedWarnings.map((item) => (
				<Alert
					key={`${item.sourceId}-${item.targetId}`}
					type={item.fanoutWarning ? "warning" : "info"}
					showIcon
					message={`${item.sourceLabel} -> ${item.targetLabel}`}
					description={
						item.fanoutWarning
							? "该 Join 标记为 fanout 风险，关联维度扩展后可能被后端拒绝执行。"
							: "该 Join 标记为需要审批，当前可预览但正式发布前应补审批记录。"
					}
				/>
			))}
		</div>
	);
}
