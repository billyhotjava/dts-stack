import type { SemanticModelMeta } from "../../api/analyticsApi";

export type SemanticJoinOption = {
	sourceId: string;
	sourceLabel: string;
	targetId: string;
	targetLabel: string;
	joinType: string;
	relationship: string;
	path?: string;
	description?: string;
	approvalRequired: boolean;
	fanoutWarning: boolean;
};

export type SemanticCanvasNodeState = "base" | "selected" | "candidate";

export type SemanticCanvasNodeModel = {
	id: string;
	label: string;
	subjectArea: string;
	securityLevel: string;
	metricCount: number;
	dimensionCount: number;
	state: SemanticCanvasNodeState;
	x: number;
	y: number;
};

export type SemanticCanvasEdgeModel = {
	id: string;
	source: string;
	target: string;
	label: string;
	selected: boolean;
	approvalRequired: boolean;
	fanoutWarning: boolean;
};

export type SemanticCanvasGraph = {
	nodes: SemanticCanvasNodeModel[];
	edges: SemanticCanvasEdgeModel[];
	joinOptions: SemanticJoinOption[];
};

function asArray<T = Record<string, unknown>>(value: unknown): T[] {
	return Array.isArray(value) ? (value as T[]) : [];
}

function readId(value: unknown): string {
	if (typeof value === "string") return value;
	if (typeof value === "number") return String(value);
	return "";
}

function readLabel(value: Record<string, unknown>, fallback: string): string {
	return String(value.label ?? value.display_name ?? value.name ?? value.id ?? fallback);
}

function buildModelMap(models: SemanticModelMeta[]): Map<string, SemanticModelMeta> {
	const map = new Map<string, SemanticModelMeta>();
	for (const model of models) {
		const id = readId(model.id);
		if (id) {
			map.set(id, model);
		}
	}
	return map;
}

export function buildSemanticJoinOptions(
	models: SemanticModelMeta[],
	baseModelId: string,
	selectedJoinTargets: string[],
): SemanticJoinOption[] {
	if (!baseModelId) {
		return [];
	}
	const modelMap = buildModelMap(models);
	const sourceIds = [baseModelId, ...selectedJoinTargets].filter(Boolean);
	const seen = new Set<string>();
	const options: SemanticJoinOption[] = [];

	for (const sourceId of sourceIds) {
		const sourceModel = modelMap.get(sourceId);
		if (!sourceModel) {
			continue;
		}
		for (const join of asArray<Record<string, unknown>>(sourceModel.joins)) {
			const targetId = readId(join.to);
			if (!targetId || targetId === baseModelId) {
				continue;
			}
			const dedupeKey = `${sourceId}->${targetId}`;
			if (seen.has(dedupeKey)) {
				continue;
			}
			seen.add(dedupeKey);
			const targetModel = modelMap.get(targetId);
			options.push({
				sourceId,
				sourceLabel: String(sourceModel.label ?? sourceModel.id ?? sourceId),
				targetId,
				targetLabel: targetModel ? String(targetModel.label ?? targetModel.id ?? targetId) : readLabel(join, targetId),
				joinType: String(join.type ?? "many_to_one"),
				relationship: String(join.relationship ?? "left"),
				path: typeof join.path === "string" ? join.path : undefined,
				description: typeof join.description === "string" ? join.description : undefined,
				approvalRequired: Boolean(join.approval_required),
				fanoutWarning: Boolean(join.fanout_warning),
			});
		}
	}

	return options.sort((left, right) => {
		const bySource = left.sourceLabel.localeCompare(right.sourceLabel, "zh-CN");
		if (bySource !== 0) {
			return bySource;
		}
		return left.targetLabel.localeCompare(right.targetLabel, "zh-CN");
	});
}

export function buildSemanticCanvasGraph(
	models: SemanticModelMeta[],
	baseModelId: string,
	selectedJoinTargets: string[],
): SemanticCanvasGraph {
	if (!baseModelId) {
		return { nodes: [], edges: [], joinOptions: [] };
	}

	const modelMap = buildModelMap(models);
	const joinOptions = buildSemanticJoinOptions(models, baseModelId, selectedJoinTargets);
	const selectedSet = new Set(selectedJoinTargets);
	const visibleIds = new Set<string>([baseModelId]);
	const levelById = new Map<string, number>([[baseModelId, 0]]);

	for (const option of joinOptions) {
		visibleIds.add(option.sourceId);
		visibleIds.add(option.targetId);
	}

	let changed = true;
	while (changed) {
		changed = false;
		for (const option of joinOptions) {
			const sourceLevel = levelById.get(option.sourceId);
			if (sourceLevel == null) {
				continue;
			}
			const targetLevel = sourceLevel + 1;
			const current = levelById.get(option.targetId);
			if (current == null || targetLevel < current) {
				levelById.set(option.targetId, targetLevel);
				changed = true;
			}
		}
	}

	const levelGroups = new Map<number, string[]>();
	for (const nodeId of visibleIds) {
		const level = levelById.get(nodeId) ?? (nodeId === baseModelId ? 0 : 1);
		const group = levelGroups.get(level) ?? [];
		group.push(nodeId);
		levelGroups.set(level, group);
	}

	const nodes: SemanticCanvasNodeModel[] = [];
	for (const [level, ids] of [...levelGroups.entries()].sort((left, right) => left[0] - right[0])) {
		const sortedIds = [...ids].sort((left, right) => {
			const leftSelected = selectedSet.has(left);
			const rightSelected = selectedSet.has(right);
			if (leftSelected !== rightSelected) {
				return leftSelected ? -1 : 1;
			}
			const leftLabel = String(modelMap.get(left)?.label ?? left);
			const rightLabel = String(modelMap.get(right)?.label ?? right);
			return leftLabel.localeCompare(rightLabel, "zh-CN");
		});
		const topOffset = ((sortedIds.length - 1) * 132) / 2;
		sortedIds.forEach((id, index) => {
			const model = modelMap.get(id);
			nodes.push({
				id,
				label: String(model?.label ?? model?.id ?? id),
				subjectArea: String(model?.subject_area ?? "未分域"),
				securityLevel: String(model?.security_level ?? "INTERNAL"),
				metricCount: asArray(model?.metrics).length,
				dimensionCount: asArray(model?.dimensions).length,
				state: id === baseModelId ? "base" : selectedSet.has(id) ? "selected" : "candidate",
				x: level * 270,
				y: index * 132 - topOffset,
			});
		});
	}

	const edges: SemanticCanvasEdgeModel[] = joinOptions.map((option) => ({
		id: `${option.sourceId}->${option.targetId}`,
		source: option.sourceId,
		target: option.targetId,
		label: option.joinType,
		selected: selectedSet.has(option.targetId),
		approvalRequired: option.approvalRequired,
		fanoutWarning: option.fanoutWarning,
	}));

	return { nodes, edges, joinOptions };
}
