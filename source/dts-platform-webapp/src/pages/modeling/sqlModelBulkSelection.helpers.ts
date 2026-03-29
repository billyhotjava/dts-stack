export type BulkSelectionSource = "tree" | "governance" | "list" | "mixed";

export type BulkSelectionState = {
	selectedIds: string[];
	sourceSelections: Record<Exclude<BulkSelectionSource, "mixed">, string[]>;
	selectedSource: BulkSelectionSource | null;
	lastChangedAt: number | null;
};

export const EMPTY_BULK_SELECTION: BulkSelectionState = {
	selectedIds: [],
	sourceSelections: {
		tree: [],
		governance: [],
		list: [],
	},
	selectedSource: null,
	lastChangedAt: null,
};

const normalizeIds = (ids: string[]) =>
	Array.from(new Set(ids.map((id) => String(id || "").trim()).filter(Boolean))).sort();

export function deriveSelectedModelIdsFromCheckedKeys(checkedKeys: string[]) {
	return normalizeIds(
		checkedKeys
			.map((key) => String(key))
			.filter((key) => key.startsWith("model:"))
			.map((key) => key.slice("model:".length)),
	);
}

export function applyBulkSelectionChange(
	current: BulkSelectionState,
	selectedIds: string[],
	source: Exclude<BulkSelectionSource, "mixed">,
	changedAt = Date.now(),
): BulkSelectionState {
	const nextSourceSelections = {
		tree: normalizeIds(current.sourceSelections?.tree || []),
		governance: normalizeIds(current.sourceSelections?.governance || []),
		list: normalizeIds(current.sourceSelections?.list || []),
	};
	nextSourceSelections[source] = normalizeIds(selectedIds);
	const nextIds = normalizeIds([
		...nextSourceSelections.tree,
		...nextSourceSelections.governance,
		...nextSourceSelections.list,
	]);
	const activeSources = (Object.entries(nextSourceSelections) as Array<
		[Exclude<BulkSelectionSource, "mixed">, string[]]
	>).filter(([, ids]) => ids.length > 0);
	const nextSource =
		activeSources.length === 0 ? null : activeSources.length === 1 ? activeSources[0][0] : "mixed";
	return {
		selectedIds: nextIds,
		sourceSelections: nextSourceSelections,
		selectedSource: nextSource,
		lastChangedAt: changedAt,
	};
}

export function clearBulkSelectionSource(
	current: BulkSelectionState,
	source: Exclude<BulkSelectionSource, "mixed">,
	changedAt = Date.now(),
): BulkSelectionState {
	return applyBulkSelectionChange(current, [], source, changedAt);
}

export function summarizeBulkSelection(current: BulkSelectionState) {
	return {
		total: current.selectedIds.length,
		tree: normalizeIds(current.sourceSelections?.tree || []).length,
		governance: normalizeIds(current.sourceSelections?.governance || []).length,
		list: normalizeIds(current.sourceSelections?.list || []).length,
	};
}

export function clearDeletedBulkSelection(current: BulkSelectionState, deletedIds: string[]): BulkSelectionState {
	if (!current.selectedIds.length || !deletedIds.length) {
		return current;
	}
	const deleted = new Set(normalizeIds(deletedIds));
	const nextSourceSelections = {
		tree: (current.sourceSelections?.tree || []).filter((id) => !deleted.has(id)),
		governance: (current.sourceSelections?.governance || []).filter((id) => !deleted.has(id)),
		list: (current.sourceSelections?.list || []).filter((id) => !deleted.has(id)),
	};
	const nextIds = normalizeIds([
		...nextSourceSelections.tree,
		...nextSourceSelections.governance,
		...nextSourceSelections.list,
	]);
	const activeSources = (Object.entries(nextSourceSelections) as Array<
		[Exclude<BulkSelectionSource, "mixed">, string[]]
	>).filter(([, ids]) => ids.length > 0);
	const nextSource =
		activeSources.length === 0 ? null : activeSources.length === 1 ? activeSources[0][0] : "mixed";
	return {
		selectedIds: nextIds,
		sourceSelections: nextSourceSelections,
		selectedSource: nextSource,
		lastChangedAt: current.lastChangedAt,
	};
}
