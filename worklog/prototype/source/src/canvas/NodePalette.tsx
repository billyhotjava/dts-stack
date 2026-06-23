import {
	DatabaseOutlined,
	ClearOutlined,
	MergeCellsOutlined,
	FunctionOutlined,
	ExportOutlined,
	PlusOutlined,
} from "@ant-design/icons";
import type { ReactNode } from "react";
import type { TransformNodeKind } from "@/types/transform";

export const DND_MIME = "application/dts-transform-kind";

const ITEMS: Array<{ kind: TransformNodeKind; label: string; icon: ReactNode }> = [
	{ kind: "source", label: "源表", icon: <DatabaseOutlined /> },
	{ kind: "clean", label: "清洗", icon: <ClearOutlined /> },
	{ kind: "join", label: "连接", icon: <MergeCellsOutlined /> },
	{ kind: "aggregate", label: "聚合", icon: <FunctionOutlined /> },
	{ kind: "output", label: "输出", icon: <ExportOutlined /> },
];

/**
 * 节点面板：原生拖拽到画布；并提供"添加"按钮作为键盘 a11y 兜底
 * （无鼠标用户可 Tab 到按钮后回车把节点加入画布）。
 */
export function NodePalette({ onAdd }: { onAdd: (kind: TransformNodeKind) => void }) {
	const onDragStart = (e: React.DragEvent, kind: TransformNodeKind) => {
		e.dataTransfer.setData(DND_MIME, kind);
		e.dataTransfer.effectAllowed = "move";
	};

	return (
		<div
			style={{
				width: 132,
				flex: "0 0 auto",
				borderRight: "1px solid var(--hairline)",
				background: "var(--surface)",
				padding: 10,
				display: "flex",
				flexDirection: "column",
				gap: 8,
			}}
		>
			<div style={{ fontSize: 11, fontWeight: 600, letterSpacing: "0.06em", color: "var(--ink-subtle)", textTransform: "uppercase" }}>
				节点
			</div>
			{ITEMS.map((it) => (
				<div
					key={it.kind}
					draggable
					onDragStart={(e) => onDragStart(e, it.kind)}
					style={{
						display: "flex",
						alignItems: "center",
						gap: 8,
						padding: "8px 10px",
						border: "1px solid var(--hairline)",
						borderRadius: "var(--radius-md)",
						background: "var(--surface)",
						cursor: "grab",
						fontSize: "var(--text-sm)",
					}}
				>
					<span style={{ color: "var(--accent)" }}>{it.icon}</span>
					<span style={{ fontWeight: 500 }}>{it.label}</span>
					<button
						type="button"
						aria-label={`添加${it.label}节点到画布`}
						onClick={() => onAdd(it.kind)}
						style={{
							marginLeft: "auto",
							display: "grid",
							placeItems: "center",
							width: 20,
							height: 20,
							border: "1px solid var(--hairline)",
							borderRadius: 4,
							background: "var(--surface-sunken)",
							cursor: "pointer",
							color: "var(--ink-muted)",
						}}
					>
						<PlusOutlined style={{ fontSize: 10 }} />
					</button>
				</div>
			))}
			<div style={{ marginTop: "auto", fontSize: 10, color: "var(--ink-subtle)", lineHeight: 1.5 }}>
				拖拽到画布，或点 + 添加（键盘可达）。
			</div>
		</div>
	);
}
