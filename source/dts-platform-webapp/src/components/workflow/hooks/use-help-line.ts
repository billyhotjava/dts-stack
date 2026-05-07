import { useEffect } from "react";
import { useWorkflowStore } from "../store/workflow-store";
import type { HelpLineState, WorkflowNode } from "../store/types";

const DEFAULT_THRESHOLD = 4;

interface DetectInput {
	draggingNode: WorkflowNode;
	otherNodes: WorkflowNode[];
	threshold?: number;
}

/**
 * 纯函数：基于左上角坐标比较，命中阈值则返回对应轴的辅助线坐标。
 * 选择最接近的一个，避免多重虚线噪声。
 */
export function detectAlignment({
	draggingNode,
	otherNodes,
	threshold = DEFAULT_THRESHOLD,
}: DetectInput): HelpLineState {
	const { x: dx, y: dy } = draggingNode.position;
	let vertical: number | null = null;
	let horizontal: number | null = null;
	let bestVerticalDelta = threshold + 1;
	let bestHorizontalDelta = threshold + 1;

	for (const other of otherNodes) {
		if (other.id === draggingNode.id) continue;
		const deltaX = Math.abs(other.position.x - dx);
		if (deltaX <= threshold && deltaX < bestVerticalDelta) {
			vertical = other.position.x;
			bestVerticalDelta = deltaX;
		}
		const deltaY = Math.abs(other.position.y - dy);
		if (deltaY <= threshold && deltaY < bestHorizontalDelta) {
			horizontal = other.position.y;
			bestHorizontalDelta = deltaY;
		}
	}

	return { vertical, horizontal };
}

/**
 * useHelpLine — 节点拖拽期间，把对齐结果写入 ui-slice.helpLine。
 *
 * 性能：依赖 React 的 batching + 节点列表 reference 比较；同一帧多次 setNodePosition 只会触发一次效果。
 */
export function useHelpLine(threshold = DEFAULT_THRESHOLD): void {
	const draggingNode = useWorkflowStore((s) => s.nodes.find((n) => n.data?.isDragging === true) ?? null);
	const otherNodes = useWorkflowStore((s) => s.nodes);
	const setHelpLine = useWorkflowStore((s) => s.setHelpLine);

	useEffect(() => {
		if (!draggingNode) {
			setHelpLine({ vertical: null, horizontal: null });
			return;
		}
		const next = detectAlignment({ draggingNode, otherNodes, threshold });
		setHelpLine(next);
	}, [draggingNode, otherNodes, threshold, setHelpLine]);
}
