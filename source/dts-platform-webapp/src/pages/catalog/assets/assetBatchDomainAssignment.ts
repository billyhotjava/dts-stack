const MAX_SELECTION = 100;
const DEFAULT_CONCURRENCY = 5;

export type AssetDomainUpdate = (assetId: string, domainId: string) => Promise<unknown>;

export type AssetDomainAssignmentFailure = {
	assetId: string;
	message: string;
};

export type AssetDomainAssignmentResult = {
	succeededIds: string[];
	failures: AssetDomainAssignmentFailure[];
};

const failureMessage = (error: unknown) => (error instanceof Error && error.message ? error.message : "资产归域失败");

/** Execute a bounded, explicit per-asset batch so one denial never hides the other item results. */
export async function assignAssetsToDomain(
	assetIds: string[],
	domainId: string,
	update: AssetDomainUpdate,
	options: { concurrency?: number } = {},
): Promise<AssetDomainAssignmentResult> {
	const normalizedDomainId = domainId.trim();
	if (!normalizedDomainId) throw new Error("ASSET_BATCH_DOMAIN_REQUIRED");
	const uniqueIds = [...new Set(assetIds.map((id) => id.trim()).filter(Boolean))];
	if (uniqueIds.length > MAX_SELECTION) throw new Error("ASSET_BATCH_SELECTION_LIMIT_EXCEEDED");
	if (uniqueIds.length === 0) return { succeededIds: [], failures: [] };

	const requestedConcurrency = Math.trunc(options.concurrency ?? DEFAULT_CONCURRENCY);
	const concurrency = Math.max(1, Math.min(10, requestedConcurrency || DEFAULT_CONCURRENCY, uniqueIds.length));
	const outcomes: Array<{ succeeded: boolean; message?: string } | undefined> = new Array(uniqueIds.length);
	let cursor = 0;
	const worker = async () => {
		while (cursor < uniqueIds.length) {
			const index = cursor;
			cursor += 1;
			try {
				await update(uniqueIds[index], normalizedDomainId);
				outcomes[index] = { succeeded: true };
			} catch (error: unknown) {
				outcomes[index] = { succeeded: false, message: failureMessage(error) };
			}
		}
	};
	await Promise.all(Array.from({ length: concurrency }, () => worker()));

	return uniqueIds.reduce<AssetDomainAssignmentResult>(
		(result, assetId, index) => {
			const outcome = outcomes[index];
			if (outcome?.succeeded) result.succeededIds.push(assetId);
			else result.failures.push({ assetId, message: outcome?.message || "资产归域失败" });
			return result;
		},
		{ succeededIds: [], failures: [] },
	);
}
