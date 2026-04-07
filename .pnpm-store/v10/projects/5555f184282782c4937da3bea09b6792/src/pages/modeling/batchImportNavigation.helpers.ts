type BatchImportDetailLike = {
	name?: string;
	status?: string;
};

type BatchImportedSqlModel = {
	id?: string;
	name?: string;
	planId?: string;
};

export function extractImportedModelNames(details: BatchImportDetailLike[]): string[] {
	if (!Array.isArray(details)) {
		return [];
	}
	return details
		.filter((item) => item?.status === "imported" && !!item?.name)
		.map((item) => String(item.name).trim())
		.filter(Boolean);
}

export function resolveBatchImportNavigation(
	planId: string | undefined,
	importedModelNames: string[],
	models: BatchImportedSqlModel[],
): { activeSpaceKey: string | null; activeModelKey: string | null } {
	const normalizedPlanId = String(planId || "").trim();
	if (!normalizedPlanId) {
		return { activeSpaceKey: null, activeModelKey: null };
	}

	const nameOrder = new Map<string, number>();
	(importedModelNames || []).forEach((name, index) => {
		const normalized = String(name || "").trim().toLowerCase();
		if (normalized && !nameOrder.has(normalized)) {
			nameOrder.set(normalized, index);
		}
	});

	const matchedModel =
		(models || [])
			.filter((model) => String(model?.planId || "").trim() === normalizedPlanId)
			.map((model) => ({
				model,
				order: nameOrder.get(String(model?.name || "").trim().toLowerCase()),
			}))
			.filter((item) => item.order != null)
			.sort((a, b) => Number(a.order) - Number(b.order))[0]?.model || null;

	return {
		activeSpaceKey: `space-${normalizedPlanId}`,
		activeModelKey: matchedModel?.id || null,
	};
}
