import type { SemanticModelMeta } from "./semanticTypes";

export type SemanticFieldKind = "metric" | "dimension";
export type SemanticFieldExplorerNodeType = "subject" | "model" | "group" | "field";

export type SemanticFieldExplorerNode = {
	key: string;
	type: SemanticFieldExplorerNodeType;
	label: string;
	searchText: string;
	children?: SemanticFieldExplorerNode[];
	count?: number;
	selectedCount?: number;
	modelId?: string;
	fieldId?: string;
	fieldKind?: SemanticFieldKind;
	description?: string;
	standardCodeField?: string;
	labelField?: string;
	securityLevel?: string;
	isBase?: boolean;
	metricCount?: number;
	dimensionCount?: number;
	selected?: boolean;
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

function readString(value: unknown): string | undefined {
	return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function inferStandardCodeField(fieldId: string, field: Record<string, unknown>, fieldKind: SemanticFieldKind): string | undefined {
	return (
		readString(field.standardCodeField) ??
		readString(field.standard_code_field) ??
		readString(field.codeField) ??
		readString(field.code_field) ??
		(fieldKind === "dimension" && fieldId.endsWith("_name") ? fieldId.replace(/_name$/, "_code") : undefined) ??
		(fieldKind === "dimension" && fieldId.endsWith("_label") ? fieldId.replace(/_label$/, "_code") : undefined) ??
		(fieldKind === "dimension" && fieldId.toLowerCase().includes("code") ? fieldId : undefined)
	);
}

function inferLabelField(fieldId: string, field: Record<string, unknown>): string | undefined {
	return readString(field.labelField) ?? readString(field.label_field) ?? readString(field.nameField) ?? readString(field.name_field) ?? fieldId;
}

function normalizeSearchText(value: string): string {
	return value.trim().toLowerCase();
}

function buildFieldKey(kind: SemanticFieldKind, fieldId: string): string {
	return `${kind}:${fieldId}`;
}

function buildFieldNodes(
	model: SemanticModelMeta,
	fieldKind: SemanticFieldKind,
	selectedSet: Set<string>,
): SemanticFieldExplorerNode[] {
	const modelId = readId(model.id);
	const modelLabel = String(model.label ?? model.id ?? modelId);
	const subjectArea = String(model.subject_area ?? "未分域");
	const source = fieldKind === "metric" ? model.metrics : model.dimensions;
	const fields = asArray<Record<string, unknown>>(source);
	const nodes: SemanticFieldExplorerNode[] = [];

	for (const field of fields) {
		const fieldId = readId(field.id);
		if (!fieldId) {
			continue;
		}
		const label = readLabel(field, fieldId);
		const description = typeof field.description === "string" ? field.description : undefined;
		const standardCodeField = inferStandardCodeField(fieldId, field, fieldKind);
		const labelField = inferLabelField(fieldId, field);
		nodes.push({
			key: buildFieldKey(fieldKind, fieldId),
			type: "field",
			label,
			searchText: normalizeSearchText([label, fieldId, standardCodeField, labelField, description, modelLabel, subjectArea].filter(Boolean).join(" ")),
			modelId,
			fieldId,
			fieldKind,
			description,
			standardCodeField,
			labelField,
			selected: selectedSet.has(fieldId),
		});
	}

	return nodes.sort((left, right) => {
		if (left.selected !== right.selected) {
			return left.selected ? -1 : 1;
		}
		return left.label.localeCompare(right.label, "zh-CN");
	});
}

export function buildSemanticFieldExplorerTree(
	models: SemanticModelMeta[],
	selectedModelIds: string[],
	baseModelId: string,
	selectedMeasures: string[],
	selectedDimensions: string[],
): SemanticFieldExplorerNode[] {
	const visibleModelIds = new Set(selectedModelIds.filter(Boolean));
	if (visibleModelIds.size === 0) {
		return [];
	}

	const selectedMeasureSet = new Set(selectedMeasures);
	const selectedDimensionSet = new Set(selectedDimensions);
	const subjectMap = new Map<string, SemanticFieldExplorerNode[]>();

	for (const model of models) {
		const modelId = readId(model.id);
		if (!modelId || !visibleModelIds.has(modelId)) {
			continue;
		}

		const metricNodes = buildFieldNodes(model, "metric", selectedMeasureSet);
		const dimensionNodes = buildFieldNodes(model, "dimension", selectedDimensionSet);
		const modelChildren: SemanticFieldExplorerNode[] = [];

		if (metricNodes.length > 0) {
			modelChildren.push({
				key: `group:${modelId}:metric`,
				type: "group",
				label: "指标",
				searchText: "指标 metric",
				modelId,
				fieldKind: "metric",
				count: metricNodes.length,
				selectedCount: metricNodes.filter((item) => item.selected).length,
				children: metricNodes,
			});
		}
		if (dimensionNodes.length > 0) {
			modelChildren.push({
				key: `group:${modelId}:dimension`,
				type: "group",
				label: "维度",
				searchText: "维度 dimension",
				modelId,
				fieldKind: "dimension",
				count: dimensionNodes.length,
				selectedCount: dimensionNodes.filter((item) => item.selected).length,
				children: dimensionNodes,
			});
		}

		const subjectArea = String(model.subject_area ?? "未分域");
		const modelLabel = String(model.label ?? model.id ?? modelId);
		const modelNode: SemanticFieldExplorerNode = {
			key: `model:${modelId}`,
			type: "model",
			label: modelLabel,
			searchText: normalizeSearchText([modelLabel, modelId, subjectArea, model.description].filter(Boolean).join(" ")),
			modelId,
			securityLevel: String(model.security_level ?? "INTERNAL"),
			isBase: modelId === baseModelId,
			metricCount: metricNodes.length,
			dimensionCount: dimensionNodes.length,
			selectedCount:
				metricNodes.filter((item) => item.selected).length + dimensionNodes.filter((item) => item.selected).length,
			children: modelChildren,
		};

		const subjectNodes = subjectMap.get(subjectArea) ?? [];
		subjectNodes.push(modelNode);
		subjectMap.set(subjectArea, subjectNodes);
	}

	return [...subjectMap.entries()]
		.sort((left, right) => left[0].localeCompare(right[0], "zh-CN"))
		.map(([subjectArea, subjectNodes]) => {
			const sortedChildren = [...subjectNodes].sort((left, right) => {
				if (left.isBase !== right.isBase) {
					return left.isBase ? -1 : 1;
				}
				return left.label.localeCompare(right.label, "zh-CN");
			});
			return {
				key: `subject:${subjectArea}`,
				type: "subject" as const,
				label: subjectArea,
				searchText: normalizeSearchText(subjectArea),
				count: sortedChildren.reduce((total, node) => total + (node.metricCount ?? 0) + (node.dimensionCount ?? 0), 0),
				selectedCount: sortedChildren.reduce((total, node) => total + (node.selectedCount ?? 0), 0),
				children: sortedChildren,
			};
		});
}

export function filterSemanticFieldExplorerTree(
	nodes: SemanticFieldExplorerNode[],
	keyword: string,
): SemanticFieldExplorerNode[] {
	const normalizedKeyword = normalizeSearchText(keyword);
	if (!normalizedKeyword) {
		return nodes;
	}

	const visit = (node: SemanticFieldExplorerNode): SemanticFieldExplorerNode | null => {
		const children = node.children?.map(visit).filter((item): item is SemanticFieldExplorerNode => Boolean(item)) ?? [];
		const matchedSelf = node.searchText.includes(normalizedKeyword);
		if (!matchedSelf && children.length === 0) {
			return null;
		}
		return {
			...node,
			children: children.length > 0 ? children : undefined,
		};
	};

	return nodes.map(visit).filter((item): item is SemanticFieldExplorerNode => Boolean(item));
}

export function collectSemanticFieldExplorerExpandedKeys(nodes: SemanticFieldExplorerNode[]): string[] {
	const keys: string[] = [];
	const visit = (node: SemanticFieldExplorerNode) => {
		if (node.type !== "field") {
			keys.push(node.key);
		}
		for (const child of node.children ?? []) {
			visit(child);
		}
	};

	for (const node of nodes) {
		visit(node);
	}
	return keys;
}
