export type BulkSelectionSource = "tree" | "governance" | "list" | "mixed";

export type BulkSelectionState = {
	selectedIds: string[];
	sourceSelections: Record<Exclude<BulkSelectionSource, "mixed">, string[]>;
	selectedSource: BulkSelectionSource | null;
	lastChangedAt: number | null;
};

export type TreeSelectableModel = {
	id?: string;
	planId?: string;
	layer?: string;
	name?: string;
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

export function resolveSelectedModelIdsFromTreeKeys(
	checkedKeys: string[],
	options: {
		models: TreeSelectableModel[];
		activeSpaceModels: TreeSelectableModel[];
		unassignedModels: TreeSelectableModel[];
		spaceKeyToPlanId: Record<string, string>;
		unassignedSpaceKey: string;
		inferLayer: (name?: string) => string;
	},
) {
	const result = new Set<string>();
	const addModels = (models: TreeSelectableModel[]) => {
		models.forEach((model) => {
			const id = String(model.id || "").trim();
			if (id) {
				result.add(id);
			}
		});
	};
	const checked = checkedKeys.map((key) => String(key || "").trim()).filter(Boolean);
	checked.forEach((key) => {
		if (key.startsWith("model:")) {
			const id = key.slice("model:".length).trim();
			if (id) {
				result.add(id);
			}
			return;
		}
		if (key === options.unassignedSpaceKey) {
			addModels(options.unassignedModels);
			return;
		}
		if (key.startsWith("space-")) {
			const planId = String(options.spaceKeyToPlanId[key] || "").trim();
			if (planId) {
				addModels(options.models.filter((model) => String(model.planId || "").trim() === planId));
			}
			return;
		}
		if (key.startsWith("layer-")) {
			const layer = key.slice("layer-".length).trim();
			if (!layer) {
				return;
			}
			addModels(
				options.activeSpaceModels.filter((model) => {
					const modelLayer = String(model.layer || options.inferLayer(model.name)).trim();
					return modelLayer === layer;
				}),
			);
		}
	});
	return normalizeIds(Array.from(result));
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
