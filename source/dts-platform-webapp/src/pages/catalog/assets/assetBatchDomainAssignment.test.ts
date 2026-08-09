import { describe, expect, it, vi } from "vitest";
import { assignAssetsToDomain } from "./assetBatchDomainAssignment";

describe("assignAssetsToDomain", () => {
	it("keeps per-item failures visible while continuing the bounded batch", async () => {
		const update = vi.fn(async (id: string, domainId: string) => {
			if (id === "asset-2") throw new Error("无权维护该资产");
			return { id, domainId };
		});

		const result = await assignAssetsToDomain(["asset-1", "asset-2", "asset-1", "asset-3"], "domain-1", update, {
			concurrency: 2,
		});

		expect(update).toHaveBeenCalledTimes(3);
		expect(result.succeededIds).toEqual(["asset-1", "asset-3"]);
		expect(result.failures).toEqual([{ assetId: "asset-2", message: "无权维护该资产" }]);
	});

	it("fails closed before writing when the explicit selection exceeds 100 assets", async () => {
		const update = vi.fn();
		const ids = Array.from({ length: 101 }, (_, index) => `asset-${index}`);

		await expect(assignAssetsToDomain(ids, "domain-1", update)).rejects.toThrow("ASSET_BATCH_SELECTION_LIMIT_EXCEEDED");
		expect(update).not.toHaveBeenCalled();
	});

	it("requires an explicit target domain", async () => {
		const update = vi.fn();

		await expect(assignAssetsToDomain(["asset-1"], "", update)).rejects.toThrow("ASSET_BATCH_DOMAIN_REQUIRED");
		expect(update).not.toHaveBeenCalled();
	});
});
