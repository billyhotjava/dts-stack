import type { WarehousePlanSourceBindingView } from "@/api/warehousePlanApi";
/** Only unique, confirmed exact table-name matches are proposed; explicit mappings are retained. */
export function suggestImportSourceMappings(
	relations: Array<readonly [string, string]>,
	sources: WarehousePlanSourceBindingView[],
	existing: Record<string, string>,
) {
	const mappings = { ...existing };
	for (const [id, name] of relations) {
		if (mappings[id]) continue;
		const tableName = name.split(".").pop() || name;
		const names = new Set([tableName, `ods_${tableName}`]);
		const matches = sources
			.filter(
				(source) =>
					source.confirmationStatus === "CONFIRMED" &&
					source.resolutionStatus === "AVAILABLE" &&
					source.freshness === "CURRENT",
			)
			.filter((source) =>
				[source.locator?.objectName, source.displayName?.split(/[\s/.]+/).pop()].some((value) =>
					Boolean(value && names.has(value)),
				),
			);
		if (matches.length === 1) mappings[id] = matches[0].bindingId;
	}
	return mappings;
}
