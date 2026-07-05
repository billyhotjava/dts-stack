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

export type MetricCanvasRelationType = "OBJECT_METRIC" | "METRIC_DERIVES";

export type MetricCanvasRelation =
	| {
			relationType: "OBJECT_METRIC";
			objectId: string;
			metricId: string;
	  }
	| {
			relationType: "METRIC_DERIVES";
			sourceMetricId: string;
			targetMetricId: string;
	  };

export type SemanticMetricUpdatePayload = Pick<
	SemanticMetric,
	"objectId" | "code" | "name" | "formulaType" | "formulaJson" | "format" | "unit" | "status"
>;

export type MetricFormulaUpdateResult =
	| { ok: true; formulaJson: string }
	| { ok: false; error: "INVALID_FORMULA_JSON" };

export type MetricCanvasPreflightIssue = {
	code:
		| "ACTIVE_DEPENDS_ON_DRAFT"
		| "CYCLIC_METRIC_DEPENDENCY"
		| "DERIVATION_COMPILE_FAILED"
		| "INVALID_FORMULA_JSON"
		| "ISOLATED_METRIC"
		| "MISSING_OBJECT_BINDING";
	severity: "error" | "warning";
	message: string;
	metricId?: string;
	nodeId?: string;
	edgeId?: string;
};

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

function metricNodeId(metricId: string): string {
	return `${METRIC_NODE_PREFIX}${metricId}`;
}

function objectNodeId(objectId: string): string {
	return `${OBJECT_NODE_PREFIX}${objectId}`;
}

function objectMetricEdgeId(metricId: string): string {
	return `edge-${metricId}`;
}

function metricDerivesEdgeId(sourceMetricId: string, targetMetricId: string): string {
	return `edge-derives-${sourceMetricId}-${targetMetricId}`;
}

function parseFormulaObject(formulaJson: string | null | undefined): { ok: true; value: Record<string, unknown> } | { ok: false } {
	if (!formulaJson || !formulaJson.trim()) {
		return { ok: true, value: {} };
	}
	try {
		const value = JSON.parse(formulaJson) as unknown;
		if (!value || typeof value !== "object" || Array.isArray(value)) {
			return { ok: false };
		}
		return { ok: true, value: value as Record<string, unknown> };
	} catch {
		return { ok: false };
	}
}

function normalizeMetricIds(value: unknown): string[] {
	if (!Array.isArray(value)) {
		return [];
	}
	return Array.from(
		new Set(
			value.filter((item): item is string => typeof item === "string" && item.trim().length > 0),
		),
	);
}

export function getMetricDependencyIds(metric: Pick<SemanticMetric, "formulaJson">): string[] {
	const parsed = parseFormulaObject(metric.formulaJson);
	return parsed.ok ? normalizeMetricIds(parsed.value.dependsOnMetricIds) : [];
}

export function insertMetricDslToken(
	expression: string,
	token: string,
	selectionStart?: number,
	selectionEnd?: number,
): string {
	const source = expression ?? "";
	const start = Math.max(0, Math.min(selectionStart ?? source.length, source.length));
	const end = Math.max(start, Math.min(selectionEnd ?? start, source.length));
	return `${source.slice(0, start)}${token}${source.slice(end)}`;
}

export function addMetricDependencyToFormulaJson(
	formulaJson: string | null | undefined,
	dependencyMetricId: string,
): MetricFormulaUpdateResult {
	const parsed = parseFormulaObject(formulaJson);
	if (!parsed.ok) {
		return { ok: false, error: "INVALID_FORMULA_JSON" };
	}
	const dependsOnMetricIds = normalizeMetricIds(parsed.value.dependsOnMetricIds);
	if (!dependsOnMetricIds.includes(dependencyMetricId)) {
		dependsOnMetricIds.push(dependencyMetricId);
	}
	return {
		ok: true,
		formulaJson: JSON.stringify({
			...parsed.value,
			dependsOnMetricIds,
		}),
	};
}

export function removeMetricDependencyFromFormulaJson(
	formulaJson: string | null | undefined,
	dependencyMetricId: string,
): MetricFormulaUpdateResult {
	const parsed = parseFormulaObject(formulaJson);
	if (!parsed.ok) {
		return { ok: false, error: "INVALID_FORMULA_JSON" };
	}
	const dependsOnMetricIds = normalizeMetricIds(parsed.value.dependsOnMetricIds).filter(
		(metricId) => metricId !== dependencyMetricId,
	);
	const next = { ...parsed.value };
	if (dependsOnMetricIds.length > 0) {
		next.dependsOnMetricIds = dependsOnMetricIds;
	} else {
		delete next.dependsOnMetricIds;
	}
	return { ok: true, formulaJson: JSON.stringify(next) };
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
	metrics: Array<Pick<SemanticMetric, "id" | "objectId" | "formulaJson">>,
): Edge[] {
	const metricIds = new Set(metrics.map((metric) => metric.id));
	const objectMetricEdges = metrics
		.filter((metric) => metric.objectId)
		.map((metric) => ({
			id: objectMetricEdgeId(metric.id),
			source: objectNodeId(metric.objectId!),
			target: metricNodeId(metric.id),
			type: "binding",
			data: { relationType: "OBJECT_METRIC" satisfies MetricCanvasRelationType },
		}));
	const derivedMetricEdges = metrics.flatMap((targetMetric) =>
		getMetricDependencyIds(targetMetric)
			.filter((sourceMetricId) => sourceMetricId !== targetMetric.id && metricIds.has(sourceMetricId))
			.map((sourceMetricId) => ({
				id: metricDerivesEdgeId(sourceMetricId, targetMetric.id),
				source: metricNodeId(sourceMetricId),
				target: metricNodeId(targetMetric.id),
				type: "binding",
				data: {
					relationType: "METRIC_DERIVES" satisfies MetricCanvasRelationType,
					sourceMetricId,
					targetMetricId: targetMetric.id,
				},
			})),
	);
	return [...objectMetricEdges, ...derivedMetricEdges];
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

export function resolveMetricConnection(connection: MetricCanvasConnection): MetricCanvasRelation | null {
	const binding = resolveMetricBinding(connection);
	if (binding) {
		return {
			relationType: "OBJECT_METRIC",
			...binding,
		};
	}
	const sourceMetricId = stripNodePrefix(connection.source, METRIC_NODE_PREFIX);
	const targetMetricId = stripNodePrefix(connection.target, METRIC_NODE_PREFIX);
	if (sourceMetricId && targetMetricId && sourceMetricId !== targetMetricId) {
		return {
			relationType: "METRIC_DERIVES",
			sourceMetricId,
			targetMetricId,
		};
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

export function findMetricCanvasRelationByEdgeId(
	edgeId: string,
	metrics: Array<Pick<SemanticMetric, "id" | "objectId" | "formulaJson">>,
): MetricCanvasRelation | null {
	for (const metric of metrics) {
		if (metric.objectId && objectMetricEdgeId(metric.id) === edgeId) {
			return {
				relationType: "OBJECT_METRIC",
				objectId: metric.objectId,
				metricId: metric.id,
			};
		}
		for (const sourceMetricId of getMetricDependencyIds(metric)) {
			if (metricDerivesEdgeId(sourceMetricId, metric.id) === edgeId) {
				return {
					relationType: "METRIC_DERIVES",
					sourceMetricId,
					targetMetricId: metric.id,
				};
			}
		}
	}
	return null;
}

export function buildMetricCanvasPreflightIssues(
	objects: Array<Pick<SemanticBusinessObject, "id">>,
	metrics: Array<Pick<SemanticMetric, "id" | "objectId" | "formulaJson" | "status" | "name" | "code">>,
): MetricCanvasPreflightIssue[] {
	const objectIds = new Set(objects.map((object) => object.id));
	const metricById = new Map(metrics.map((metric) => [metric.id, metric]));
	const targetsBySource = new Map<string, string[]>();
	const sourceByTarget = new Map<string, string[]>();
	const issues: MetricCanvasPreflightIssue[] = [];

	for (const metric of metrics) {
		const parsed = parseFormulaObject(metric.formulaJson);
		if (!parsed.ok) {
			issues.push({
				code: "INVALID_FORMULA_JSON",
				severity: "error",
				message: `${metric.name || metric.code} 的公式 JSON 不是合法对象`,
				metricId: metric.id,
				nodeId: metricNodeId(metric.id),
			});
			continue;
		}
		const dependencies = normalizeMetricIds(parsed.value.dependsOnMetricIds).filter(
			(sourceMetricId) => sourceMetricId !== metric.id && metricById.has(sourceMetricId),
		);
		sourceByTarget.set(metric.id, dependencies);
		for (const sourceMetricId of dependencies) {
			const currentTargets = targetsBySource.get(sourceMetricId) ?? [];
			currentTargets.push(metric.id);
			targetsBySource.set(sourceMetricId, currentTargets);
		}
	}

	for (const metric of metrics) {
		if (metric.objectId && !objectIds.has(metric.objectId)) {
			issues.push({
				code: "MISSING_OBJECT_BINDING",
				severity: "error",
				message: `${metric.name || metric.code} 绑定的业务对象不存在`,
				metricId: metric.id,
				nodeId: metricNodeId(metric.id),
			});
		}
		const dependencies = sourceByTarget.get(metric.id) ?? [];
		for (const sourceMetricId of dependencies) {
			const sourceMetric = metricById.get(sourceMetricId);
			if (metric.status === "ACTIVE" && sourceMetric?.status === "DRAFT") {
				issues.push({
					code: "ACTIVE_DEPENDS_ON_DRAFT",
					severity: "warning",
					message: `${metric.name || metric.code} 依赖草稿指标 ${sourceMetric.name || sourceMetric.code}`,
					metricId: metric.id,
					edgeId: metricDerivesEdgeId(sourceMetricId, metric.id),
				});
			}
		}
	}

	for (const metric of metrics) {
		const dependencies = sourceByTarget.get(metric.id) ?? [];
		const dependents = targetsBySource.get(metric.id) ?? [];
		if (!metric.objectId && dependencies.length === 0 && dependents.length === 0) {
			issues.push({
				code: "ISOLATED_METRIC",
				severity: "warning",
				message: `${metric.name || metric.code} 尚未绑定对象或派生关系`,
				metricId: metric.id,
				nodeId: metricNodeId(metric.id),
			});
		}
	}

	const visitedCycleEdges = new Set<string>();
	const hasPath = (fromMetricId: string, toMetricId: string, seen = new Set<string>()): boolean => {
		if (fromMetricId === toMetricId) {
			return true;
		}
		if (seen.has(fromMetricId)) {
			return false;
		}
		seen.add(fromMetricId);
		return (targetsBySource.get(fromMetricId) ?? []).some((nextMetricId) =>
			hasPath(nextMetricId, toMetricId, seen),
		);
	};

	for (const [targetMetricId, sourceMetricIds] of sourceByTarget.entries()) {
		for (const sourceMetricId of sourceMetricIds) {
			const edgeId = metricDerivesEdgeId(sourceMetricId, targetMetricId);
			if (visitedCycleEdges.has(edgeId) || !hasPath(targetMetricId, sourceMetricId)) {
				continue;
			}
			visitedCycleEdges.add(edgeId);
			const targetMetric = metricById.get(targetMetricId);
			issues.push({
				code: "CYCLIC_METRIC_DEPENDENCY",
				severity: "error",
				message: `${targetMetric?.name || targetMetric?.code || targetMetricId} 存在环形派生依赖`,
				metricId: targetMetricId,
				edgeId,
			});
		}
	}

	return issues;
}
