import { Tag } from "antd";
import type { TransformNodeKind, TransformNodeStatus } from "@/types/transform";
import { CompactTable, StatusDot } from "@/ui/components";
import type { CompactColumn, DotTone } from "@/ui/components";
import { type TransformNode, useTransformGraphStore } from "./transformGraphStore";

const KIND_LABEL: Record<TransformNodeKind, string> = {
	source: "源表",
	clean: "清洗",
	join: "连接",
	aggregate: "聚合",
	output: "输出",
};
const STATUS_TONE: Record<TransformNodeStatus, DotTone> = { ok: "success", running: "active", error: "error", idle: "muted" };
const STATUS_LABEL: Record<TransformNodeStatus, string> = { ok: "就绪", running: "运行中", error: "异常", idle: "待运行" };

/** 画布的列表视图（同一份转换作业数据，收编 Transform/Orchestration 表格）。 */
export function CanvasListView() {
	const nodes = useTransformGraphStore((s) => s.nodes);
	const edges = useTransformGraphStore((s) => s.edges);
	const setSelected = useTransformGraphStore((s) => s.setSelected);

	const upstreamLabels = (id: string) =>
		edges
			.filter((e) => e.target === id)
			.map((e) => nodes.find((n) => n.id === e.source)?.data.label ?? "?")
			.join(", ") || "—";

	const columns: CompactColumn<TransformNode>[] = [
		{
			key: "label",
			title: "节点",
			width: 200,
			render: (_v, r) => (
				<button type="button" onClick={() => setSelected(r.id)} style={{ all: "unset", cursor: "pointer", color: "var(--accent)", fontWeight: 600 }}>
					{r.data.label}
				</button>
			),
		},
		{ key: "kind", title: "类型", width: 90, render: (_v, r) => <Tag>{KIND_LABEL[r.data.kind]}</Tag> },
		{
			key: "status",
			title: "状态",
			width: 100,
			render: (_v, r) => <StatusDot tone={STATUS_TONE[r.data.status]} label={STATUS_LABEL[r.data.status]} />,
		},
		{ key: "rows", title: "行数", width: 110, align: "right", render: (_v, r) => (typeof r.data.rowCount === "number" ? r.data.rowCount.toLocaleString() : "—") },
		{ key: "up", title: "上游", render: (_v, r) => upstreamLabels(r.id) },
	];

	return <CompactTable<TransformNode> columns={columns} data={nodes} rowKey="id" />;
}
