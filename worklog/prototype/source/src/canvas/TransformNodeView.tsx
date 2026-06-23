import {
	DatabaseOutlined,
	ClearOutlined,
	MergeCellsOutlined,
	FunctionOutlined,
	ExportOutlined,
} from "@ant-design/icons";
import { Handle, Position } from "@xyflow/react";
import type { NodeProps } from "@xyflow/react";
import type { ReactNode } from "react";
import type { TransformNodeKind, TransformNodeStatus } from "@/types/transform";
import { StatusDot } from "@/ui/components";
import type { DotTone } from "@/ui/components";
import type { TransformNode } from "./transformGraphStore";

const KIND_META: Record<TransformNodeKind, { icon: ReactNode; tag: string }> = {
	source: { icon: <DatabaseOutlined />, tag: "源表" },
	clean: { icon: <ClearOutlined />, tag: "清洗" },
	join: { icon: <MergeCellsOutlined />, tag: "连接" },
	aggregate: { icon: <FunctionOutlined />, tag: "聚合" },
	output: { icon: <ExportOutlined />, tag: "输出" },
};

const STATUS_TONE: Record<TransformNodeStatus, DotTone> = {
	ok: "success",
	running: "active",
	error: "error",
	idle: "muted",
};
const STATUS_LABEL: Record<TransformNodeStatus, string> = { ok: "就绪", running: "运行中", error: "异常", idle: "待运行" };

const handleStyle = { width: 9, height: 9, background: "var(--accent)", border: "2px solid var(--surface)" };

/** 单一 transform 节点视图，按 data.kind 区分外观与 handle。 */
export function TransformNodeView({ data, selected }: NodeProps<TransformNode>) {
	const meta = KIND_META[data.kind];
	const hasInput = data.kind !== "source";
	const hasOutput = data.kind !== "output";

	return (
		<div
			style={{
				width: 184,
				background: "var(--surface)",
				border: `1.5px solid ${selected ? "var(--accent)" : "var(--hairline-strong)"}`,
				borderRadius: "var(--radius-md)",
				boxShadow: selected ? "0 0 0 3px var(--accent-soft)" : "0 1px 2px rgba(16,24,40,0.06)",
				overflow: "hidden",
			}}
		>
			{hasInput ? <Handle type="target" position={Position.Left} style={handleStyle} /> : null}

			<div
				style={{
					display: "flex",
					alignItems: "center",
					gap: 6,
					padding: "6px 10px",
					background: "var(--surface-sunken)",
					borderBottom: "1px solid var(--hairline)",
					fontSize: 11,
					color: "var(--ink-muted)",
				}}
			>
				<span style={{ color: "var(--accent)" }}>{meta.icon}</span>
				<span style={{ fontWeight: 600 }}>{meta.tag}</span>
				<span style={{ marginLeft: "auto" }}>
					<StatusDot tone={STATUS_TONE[data.status]} size={7} />
				</span>
			</div>

			<div style={{ padding: "8px 10px" }}>
				<div style={{ fontWeight: 650, fontSize: "var(--text-base)", color: "var(--ink)", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
					{data.label}
				</div>
				{data.sub ? (
					<div style={{ fontSize: 11, color: "var(--ink-subtle)", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
						{data.sub}
					</div>
				) : null}
				<div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginTop: 6 }}>
					<span style={{ fontSize: 10, color: "var(--ink-subtle)" }}>{STATUS_LABEL[data.status]}</span>
					{typeof data.rowCount === "number" ? (
						<span
							className="tnum"
							style={{ fontSize: 11, color: "var(--ink-muted)", background: "var(--surface-sunken)", padding: "1px 6px", borderRadius: 999 }}
						>
							{data.rowCount.toLocaleString()} 行
						</span>
					) : null}
				</div>
			</div>

			{hasOutput ? <Handle type="source" position={Position.Right} style={handleStyle} /> : null}
		</div>
	);
}
