import type { Edge, Node } from "@xyflow/react";
import type { SemanticBusinessObject, SemanticMetric } from "@/api/semanticModelingApi";
import type { BizObjectNodeData } from "./nodes/BizObjectNode";
import type { MetricNodeData } from "./nodes/MetricNode";

export type MetricCanvasNodePosition = {
	x: number;
	y: number;
};

export type MetricCanvasNodePositionMap = Record<string, MetricCanvasNodePosition>;

export type MetricCanvasConnection = {
	source?: string | null;
	target?: string | null;
};

export type MetricBinding = {
	objectId: string;
	metricId: string;
};

export type SemanticMetricUpdatePayload = Pick<
	SemanticMetric,
	"objectId" | "code" | "name" | "formulaType" | "formulaJson" | "format" | "unit" | "status"
>;

const OBJECT_NODE_PREFIX = "obj-";
const METRIC_NODE_PREFIX = "metric-";
const METRIC_DRAG_KIND = "metric-workbench/metric";
const OBJECT_DROP_TARGET_WIDTH = 260;
const OBJECT_DROP_TARGET_HEIGHT = 120;

function stripNodePrefix(id: string | null | undefined, prefix: string): string | null {
	if (!id || !id.startsWith(prefix)) {
		return null;
	}
	return id.slice(prefix.length);
}

function defaultObjectPosition(index: number): MetricCanvasNodePosition {
	return { x: 0, y: index * 180 };
}

function defaultMetricPosition(index: number): MetricCanvasNodePosition {
	return { x: 420, y: index * 140 };
}

export function buildMetricCanvasNodes(
	objects: Array<Pick<SemanticBusinessObject, "id" | "name" | "code">>,
	metrics: Array<Pick<SemanticMetric, "id" | "name" | "formulaType" | "status">>,
	selectedId: string | null,
	positions: MetricCanvasNodePositionMap = {},
): Node[] {
	const objectNodes: Node[] = objects.map((o, index) => {
		const id = `${OBJECT_NODE_PREFIX}${o.id}`;
		return {
			id,
			type: "bizObject",
			position: positions[id] ?? defaultObjectPosition(index),
			selected: selectedId === id,
			data: {
				objectId: o.id,
				name: o.name,
				code: o.code,
			} satisfies BizObjectNodeData,
		};
	});
	const metricNodes: Node[] = metrics.map((m, index) => {
		const id = `${METRIC_NODE_PREFIX}${m.id}`;
		return {
			id,
			type: "metric",
			position: positions[id] ?? defaultMetricPosition(index),
			selected: selectedId === id,
			data: {
				metricId: m.id,
				name: m.name,
				formulaType: m.formulaType,
				status: m.status,
			} satisfies MetricNodeData,
		};
	});
	return [...objectNodes, ...metricNodes];
}

export function buildMetricCanvasEdges(
	metrics: Array<Pick<SemanticMetric, "id" | "objectId">>,
): Edge[] {
	return metrics
		.filter((metric) => metric.objectId)
		.map((metric) => ({
			id: `edge-${metric.id}`,
			source: `${OBJECT_NODE_PREFIX}${metric.objectId}`,
			target: `${METRIC_NODE_PREFIX}${metric.id}`,
			type: "binding",
		}));
}

export function resolveMetricBinding(connection: MetricCanvasConnection): MetricBinding | null {
	const sourceObjectId = stripNodePrefix(connection.source, OBJECT_NODE_PREFIX);
	const sourceMetricId = stripNodePrefix(connection.source, METRIC_NODE_PREFIX);
	const targetObjectId = stripNodePrefix(connection.target, OBJECT_NODE_PREFIX);
	const targetMetricId = stripNodePrefix(connection.target, METRIC_NODE_PREFIX);

	if (sourceObjectId && targetMetricId) {
		return { objectId: sourceObjectId, metricId: targetMetricId };
	}
	if (targetObjectId && sourceMetricId) {
		return { objectId: targetObjectId, metricId: sourceMetricId };
	}
	return null;
}

export function serializeMetricDragPayload(metricId: string): string {
	return JSON.stringify({ kind: METRIC_DRAG_KIND, metricId });
}

export function parseMetricDragPayload(payload: string): string | null {
	try {
		const data = JSON.parse(payload) as { kind?: unknown; metricId?: unknown };
		return data.kind === METRIC_DRAG_KIND && typeof data.metricId === "string" && data.metricId
			? data.metricId
			: null;
	} catch {
		return null;
	}
}

export function findMetricDropTargetObject(
	nodes: Array<Pick<Node, "id" | "type" | "position">>,
	position: MetricCanvasNodePosition,
): string | null {
	const target = nodes.find((node) => {
		if (node.type !== "bizObject") {
			return false;
		}
		const withinX =
			position.x >= node.position.x - 24 &&
			position.x <= node.position.x + OBJECT_DROP_TARGET_WIDTH;
		const withinY =
			position.y >= node.position.y - 24 &&
			position.y <= node.position.y + OBJECT_DROP_TARGET_HEIGHT;
		return withinX && withinY;
	});
	return stripNodePrefix(target?.id, OBJECT_NODE_PREFIX);
}

export function resolveMetricNodeDropBinding(
	node: Pick<Node, "id" | "type" | "position">,
	nodes: Array<Pick<Node, "id" | "type" | "position">>,
): MetricBinding | null {
	if (node.type !== "metric") {
		return null;
	}
	const metricId = stripNodePrefix(node.id, METRIC_NODE_PREFIX);
	const objectId = findMetricDropTargetObject(
		nodes.filter((item) => item.id !== node.id),
		node.position,
	);
	return metricId && objectId ? { metricId, objectId } : null;
}

export function buildSemanticMetricUpdatePayload(
	metric: Pick<SemanticMetric, "objectId" | "code" | "name" | "formulaType" | "formulaJson" | "format" | "unit" | "status">,
	patch: Partial<SemanticMetricUpdatePayload>,
): SemanticMetricUpdatePayload {
	return {
		objectId: metric.objectId,
		code: metric.code,
		name: metric.name,
		formulaType: metric.formulaType,
		formulaJson: metric.formulaJson,
		format: metric.format,
		unit: metric.unit,
		status: metric.status,
		...patch,
	};
}
