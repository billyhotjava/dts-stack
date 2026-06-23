/** ELT 画布节点类型。 */
export type TransformNodeKind = "source" | "clean" | "join" | "aggregate" | "output";

export type TransformNodeStatus = "ok" | "running" | "error" | "idle";

/** 节点业务数据（挂在 reactflow Node.data 上）。 */
export interface TransformNodeData {
	kind: TransformNodeKind;
	label: string;
	/** 副标题（如来源系统、策略） */
	sub?: string;
	/** 行数徽标 */
	rowCount?: number;
	status: TransformNodeStatus;
	[key: string]: unknown;
}

/** 序列化的图（mock 持久化形状）。 */
export interface TransformGraphDTO {
	projectSpaceId: string;
	nodes: Array<{ id: string; kind: TransformNodeKind; label: string; sub?: string; rowCount?: number; status: TransformNodeStatus; x: number; y: number }>;
	edges: Array<{ id: string; source: string; target: string }>;
}
