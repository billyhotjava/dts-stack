import { describe, expect, it, vi } from "vitest";
import { createRenameMapping, defaultImportConflictResolutions, renameMappingRequests } from "./modelImportUiState";

describe("reverse modeling UI state", () => {
	it("restores an explicit default decision for every conflict", () => {
		expect(
			defaultImportConflictResolutions({
				items: [
					{ dbtUniqueId: "model.finance", action: "CONFLICT" },
					{ dbtUniqueId: "model.project", action: "CREATE" },
					{ dbtUniqueId: "model.risk", action: "CONFLICT" },
				] as never,
			}),
		).toEqual({ "model.finance": "KEEP_CURRENT", "model.risk": "KEEP_CURRENT" });
	});

	it("keeps a stable client key while stripping it from the apply request", () => {
		vi.stubGlobal("crypto", { randomUUID: () => "client-row-1" });
		const row = createRenameMapping();
		row.oldUniqueId = " model.old ";
		row.newUniqueId = " model.new ";

		expect(row._clientId).toBe("client-row-1");
		expect(renameMappingRequests([row])).toEqual([{ oldUniqueId: "model.old", newUniqueId: "model.new" }]);
	});
});
