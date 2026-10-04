import { memo } from "react";
import { useReactFlow } from "@xyflow/react";
import { useWorkflowStore } from "./store/workflow-store";
import { useHelpLine } from "./hooks/use-help-line";

const STROKE = "#3b82f6";
const DASHARRAY = "2 4";

/**
 * HelpLine — 在 ReactFlow viewport 坐标系内绘制水平/垂直辅助线。
 * 通过 useReactFlow().flowToScreenPosition 适配缩放/平移；当无对齐命中时不渲染。
 */
function HelpLineComponent() {
	useHelpLine();
	const { vertical, horizontal } = useWorkflowStore((s) => s.helpLine);
	const { flowToScreenPosition } = useReactFlow();

	if (vertical === null && horizontal === null) {
		return null;
	}

	// 把 flow 坐标转 screen 坐标（覆盖到画布容器顶部 SVG 上）
	const verticalScreenX = vertical !== null ? flowToScreenPosition({ x: vertical, y: 0 }).x : null;
	const horizontalScreenY = horizontal !== null ? flowToScreenPosition({ x: 0, y: horizontal }).y : null;

	return (
		<svg
			className="workflow-help-line"
			aria-hidden="true"
			style={{
				position: "absolute",
				left: 0,
				top: 0,
				width: "100%",
				height: "100%",
				pointerEvents: "none",
				zIndex: 10,
			}}
		>
			{verticalScreenX !== null ? (
				<line
					x1={verticalScreenX}
					y1={0}
					x2={verticalScreenX}
					y2="100%"
					stroke={STROKE}
					strokeWidth={1}
					strokeDasharray={DASHARRAY}
				/>
			) : null}
			{horizontalScreenY !== null ? (
				<line
					x1={0}
					y1={horizontalScreenY}
					x2="100%"
					y2={horizontalScreenY}
					stroke={STROKE}
					strokeWidth={1}
					strokeDasharray={DASHARRAY}
				/>
			) : null}
		</svg>
	);
}

export const HelpLine = memo(HelpLineComponent);
