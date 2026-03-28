export type BulkSelectionSource = "tree" | "governance" | "list" | "mixed";

export type BulkSelectionState = {
	selectedIds: string[];
	selectedSource: BulkSelectionSource | null;
	lastChangedAt: number | null;
};

export const EMPTY_BULK_SELECTION: BulkSelectionState = {
	selectedIds: [],
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
	const nextIds = normalizeIds(selectedIds);
	if (!nextIds.length) {
		return {
			selectedIds: [],
			selectedSource: null,
			lastChangedAt: changedAt,
		};
	}
	const nextSource =
		!current.selectedSource || current.selectedSource === source || current.selectedSource === "mixed"
			? current.selectedSource === "mixed"
				? "mixed"
				: source
			: "mixed";
	return {
		selectedIds: nextIds,
		selectedSource: nextSource,
		lastChangedAt: changedAt,
	};
}

export function clearDeletedBulkSelection(current: BulkSelectionState, deletedIds: string[]): BulkSelectionState {
	if (!current.selectedIds.length || !deletedIds.length) {
		return current;
	}
	const deleted = new Set(normalizeIds(deletedIds));
	const nextIds = current.selectedIds.filter((id) => !deleted.has(id));
	if (!nextIds.length) {
		return {
			selectedIds: [],
			selectedSource: null,
			lastChangedAt: current.lastChangedAt,
		};
	}
	return {
		...current,
		selectedIds: nextIds,
	};
}
