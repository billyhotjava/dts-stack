export type TargetedCrossFilter = {
	sourceCardId: number;
	column: string;
	value: string;
	targetCardIds: number[];
};

export function buildTargetedCrossFilterParams(
	activeFilter: TargetedCrossFilter | null,
	dashcardId: number,
	baseParams: unknown[],
): unknown[] {
	if (!activeFilter || dashcardId === activeFilter.sourceCardId) return baseParams;
	if (!activeFilter.targetCardIds.includes(dashcardId)) return baseParams;
	return [
		...baseParams,
		{
			type: "category",
			value: activeFilter.value,
			target: ["dimension", ["field", activeFilter.column, null]],
		},
	];
}

export async function mapWithConcurrency<T, R>(
	items: T[],
	limit: number,
	mapper: (item: T, index: number) => Promise<R>,
): Promise<R[]> {
	if (items.length === 0) return [];
	const concurrency = Math.max(1, Math.min(items.length, Math.floor(limit) || 1));
	const results = new Array<R>(items.length);
	let cursor = 0;
	await Promise.all(
		Array.from({ length: concurrency }, async () => {
			while (cursor < items.length) {
				const index = cursor;
				cursor += 1;
				results[index] = await mapper(items[index], index);
			}
		}),
	);
	return results;
}
