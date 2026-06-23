import { addEdge, applyEdgeChanges, applyNodeChanges } from "@xyflow/react";
import type { Connection, Edge, EdgeChange, Node, NodeChange } from "@xyflow/react";
import { create } from "zustand";
import type { TransformGraphDTO, TransformNodeData, TransformNodeKind } from "@/types/transform";

export type TransformNode = Node<TransformNodeData, "transform">;

export interface RunRecord {
	id: string;
	startedAt: string;
	status: "success" | "failed";
	durationMs: number;
	rows: number;
}

const KIND_LABEL: Record<TransformNodeKind, string> = {
	source: "源表",
	clean: "清洗",
	join: "连接",
	aggregate: "聚合",
	output: "输出",
};

let seq = 0;

interface GraphState {
	nodes: TransformNode[];
	edges: Edge[];
	selectedId: string | null;
	/** 最近一次非法连接的提示（供 UI 反馈） */
	lastRejection: string | null;
	running: boolean;
	logs: string[];
	runs: RunRecord[];
	load: (dto: TransformGraphDTO) => void;
	onNodesChange: (changes: NodeChange<TransformNode>[]) => void;
	onEdgesChange: (changes: EdgeChange[]) => void;
	onConnect: (conn: Connection) => void;
	addNode: (kind: TransformNodeKind, position: { x: number; y: number }) => void;
	updateNodeData: (id: string, patch: Partial<TransformNodeData>) => void;
	setSelected: (id: string | null) => void;
	clearRejection: () => void;
	run: () => void;
}

function toNode(n: TransformGraphDTO["nodes"][number]): TransformNode {
	return {
		id: n.id,
		type: "transform",
		position: { x: n.x, y: n.y },
		data: { kind: n.kind, label: n.label, sub: n.sub, rowCount: n.rowCount, status: n.status },
	};
}

/** 连接合法性校验。返回拒绝原因，null 表示允许。 */
function rejectReason(conn: Connection, nodes: TransformNode[], edges: Edge[]): string | null {
	if (conn.source === conn.target) return "不能连接到自身";
	const src = nodes.find((n) => n.id === conn.source);
	const tgt = nodes.find((n) => n.id === conn.target);
	if (!src || !tgt) return "节点不存在";
	if (tgt.data.kind === "source") return "源表节点不能有输入";
	if (src.data.kind === "output") return "输出节点不能有下游";
	if (edges.some((e) => e.source === conn.source && e.target === conn.target)) return "该连接已存在";
	return null;
}

export const useTransformGraphStore = create<GraphState>((set, get) => ({
	nodes: [],
	edges: [],
	selectedId: null,
	lastRejection: null,
	running: false,
	logs: [],
	runs: [],
	load(dto) {
		set({
			nodes: dto.nodes.map(toNode),
			edges: dto.edges.map((e) => ({ ...e })),
			selectedId: null,
			running: false,
			logs: [],
			runs: [],
		});
	},
	onNodesChange(changes) {
		set({ nodes: applyNodeChanges(changes, get().nodes) });
	},
	onEdgesChange(changes) {
		set({ edges: applyEdgeChanges(changes, get().edges) });
	},
	onConnect(conn) {
		const reason = rejectReason(conn, get().nodes, get().edges);
		if (reason) {
			set({ lastRejection: reason });
			return;
		}
		set({ edges: addEdge({ ...conn }, get().edges), lastRejection: null });
	},
	addNode(kind, position) {
		seq += 1;
		const node: TransformNode = {
			id: `n-${kind}-${seq}`,
			type: "transform",
			position,
			data: { kind, label: KIND_LABEL[kind], status: "idle" },
		};
		set({ nodes: [...get().nodes, node], selectedId: node.id });
	},
	updateNodeData(id, patch) {
		set({
			nodes: get().nodes.map((n) => (n.id === id ? { ...n, data: { ...n.data, ...patch } } : n)),
		});
	},
	setSelected(id) {
		set({ selectedId: id });
	},
	clearRejection() {
		set({ lastRejection: null });
	},
	run() {
		if (get().running) return;
		const startedAt = new Date().toISOString().slice(0, 19).replace("T", " ");
		// 标记全部为运行中
		set({
			running: true,
			logs: [`[${startedAt}] 开始运行转换图（${get().nodes.length} 节点）`],
			nodes: get().nodes.map((n) => ({ ...n, data: { ...n.data, status: "running" } })),
		});
		// 模拟异步执行：逐节点完成
		setTimeout(() => {
			const order = get().nodes;
			const logs = [...get().logs, ...order.map((n) => `  ✓ ${n.data.label} 执行完成`)];
			const rows = order.reduce((max, n) => Math.max(max, n.data.rowCount ?? 0), 0) || 31800;
			const record: RunRecord = {
				id: `run-${get().runs.length + 1}`,
				startedAt,
				status: "success",
				durationMs: 600 + order.length * 80,
				rows,
			};
			set({
				running: false,
				nodes: get().nodes.map((n) => ({ ...n, data: { ...n.data, status: "ok" } })),
				logs: [...logs, `[完成] 成功 · ${rows.toLocaleString()} 行 · ${record.durationMs}ms`],
				runs: [record, ...get().runs],
			});
		}, 700);
	},
}));

export { KIND_LABEL };
