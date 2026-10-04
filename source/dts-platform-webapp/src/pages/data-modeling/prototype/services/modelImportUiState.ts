import type { ModelSpecImportConflictResolution, ModelSpecImportPreview } from "@/api/modelSpecImportApi";

export type RenameMapping = { _clientId: string; oldUniqueId: string; newUniqueId: string };

export const createRenameMapping = (): RenameMapping => ({
	_clientId: crypto.randomUUID(),
	oldUniqueId: "",
	newUniqueId: "",
});

export const renameMappingRequests = (mappings: RenameMapping[]) =>
	mappings
		.filter((item) => item.oldUniqueId.trim() && item.newUniqueId.trim())
		.map(({ oldUniqueId, newUniqueId }) => ({ oldUniqueId: oldUniqueId.trim(), newUniqueId: newUniqueId.trim() }));

export const defaultImportConflictResolutions = (
	preview: Pick<ModelSpecImportPreview, "items">,
): Record<string, ModelSpecImportConflictResolution> =>
	Object.fromEntries(
		preview.items.filter((item) => item.action === "CONFLICT").map((item) => [item.dbtUniqueId, "KEEP_CURRENT"]),
	);
