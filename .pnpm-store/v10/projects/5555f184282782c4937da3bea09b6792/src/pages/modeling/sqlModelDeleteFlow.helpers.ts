export type DeleteModelSelectionInput = {
	activeModelKey: string | null;
	deletedModelKey: string | null;
};

export type BatchDeleteModelSelectionInput = {
	activeModelKey: string | null;
	deletedModelKeys: string[];
};

export type DeleteModelSelectionState = {
	nextActiveModelKey: string | null;
	suppressAutoSelect: boolean;
};

export type RunsRequestSelectionState = {
	shouldLoadRuns: boolean;
	selector: string | undefined;
};

export function applyDeletedModelSelection(input: DeleteModelSelectionInput): DeleteModelSelectionState {
	const activeModelKey = input.activeModelKey ?? null;
	const deletedModelKey = input.deletedModelKey ?? null;
	if (!activeModelKey || !deletedModelKey || activeModelKey !== deletedModelKey) {
		return {
			nextActiveModelKey: activeModelKey,
			suppressAutoSelect: false,
		};
	}
	return {
		nextActiveModelKey: null,
		suppressAutoSelect: true,
	};
}

export function applyManualModelSelection(nextActiveModelKey: string | null): DeleteModelSelectionState {
	return {
		nextActiveModelKey,
		suppressAutoSelect: false,
	};
}

export function applyBatchDeletedModelSelection(input: BatchDeleteModelSelectionInput): DeleteModelSelectionState {
	const activeModelKey = input.activeModelKey ?? null;
	if (!activeModelKey) {
		return {
			nextActiveModelKey: null,
			suppressAutoSelect: false,
		};
	}
	const deletedSet = new Set((input.deletedModelKeys || []).map((key) => String(key || "").trim()).filter(Boolean));
	if (!deletedSet.has(activeModelKey)) {
		return {
			nextActiveModelKey: activeModelKey,
			suppressAutoSelect: false,
		};
	}
	return {
		nextActiveModelKey: null,
		suppressAutoSelect: true,
	};
}

export function resolveRunsRequestAfterSelection(selector?: string | null): RunsRequestSelectionState {
	const nextSelector = typeof selector === "string" ? selector.trim() || undefined : undefined;
	return {
		shouldLoadRuns: !!nextSelector,
		selector: nextSelector,
	};
}
