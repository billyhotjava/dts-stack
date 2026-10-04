export function resolveRequestedModelSelection<T extends { id: string }>(
	models: readonly T[],
	requestedModelId: string,
) {
	const requestedModel = requestedModelId ? models.find((model) => model.id === requestedModelId) : undefined;
	return {
		selectedModel: requestedModel || null,
		normalizedModelId: requestedModel?.id || "",
	};
}

export function shouldBlockWorkbenchNavigation(dirty: boolean, currentPathname: string, nextPathname: string): boolean {
	return dirty && currentPathname !== nextPathname;
}
