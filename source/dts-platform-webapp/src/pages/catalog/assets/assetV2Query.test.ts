import { describe, expect, it } from "vitest";

import { buildAssetV2Query } from "./assetV2Query";

describe("buildAssetV2Query", () => {
	it("translates the unassigned navigation sentinel instead of sending it as a UUID", () => {
		expect(buildAssetV2Query({ domainId: "__UNASSIGNED__" })).toMatchObject({
			domainId: undefined,
			domainUnassigned: true,
		});
		expect(buildAssetV2Query({ domainId: "domain-1" })).toMatchObject({
			domainId: "domain-1",
			domainUnassigned: undefined,
		});
	});

	it("maps ALL selectors to undefined and keeps page/size", () => {
		const query = buildAssetV2Query(
			{ assetType: "ALL", classification: "ALL", warehouseLayer: "ALL", governanceStatus: "ALL", matchStatus: "ALL" },
			2,
			10,
		);
		expect(query).toEqual({
			page: 2,
			size: 10,
			keyword: undefined,
			assetFamily: undefined,
			domainId: undefined,
			domainUnassigned: undefined,
			type: undefined,
			classification: undefined,
			warehouseLayer: undefined,
			governanceStatus: undefined,
			matchStatus: undefined,
			unclassified: undefined,
			stale: undefined,
			eligibility: undefined,
			servingStatus: undefined,
			qualityStatus: undefined,
			tagIds: undefined,
		});
	});

	it("passes concrete filters through and trims keyword", () => {
		const query = buildAssetV2Query({
			keyword: "  客户  ",
			assetFamily: "SEMANTIC_MODEL",
			domainId: "dom-1",
			assetType: "DATASET",
			classification: "PUBLIC",
			warehouseLayer: "DWD",
			governanceStatus: "UNDER_GOVERNANCE",
			matchStatus: "MATCHED",
			tagIds: ["t1", "t2"],
		});
		expect(query.keyword).toBe("客户");
		expect(query.assetFamily).toBe("SEMANTIC_MODEL");
		expect(query.type).toBe("DATASET");
		expect(query.classification).toBe("PUBLIC");
		expect(query.warehouseLayer).toBe("DWD");
		expect(query.governanceStatus).toBe("UNDER_GOVERNANCE");
		expect(query.matchStatus).toBe("MATCHED");
		expect(query.tagIds).toEqual(["t1", "t2"]);
	});

	it("honors unclassified/stale/domainUnassigned flags and drops empty tagIds", () => {
		expect(
			buildAssetV2Query({ unclassified: true, stale: false, domainUnassigned: true, tagIds: [] }).unclassified,
		).toBe(true);
		expect(buildAssetV2Query({ stale: true }).stale).toBe(true);
		expect(buildAssetV2Query({ domainUnassigned: true }).domainUnassigned).toBe(true);
		expect(
			buildAssetV2Query({ unclassified: false, stale: false, domainUnassigned: false }).unclassified,
		).toBeUndefined();
		expect(buildAssetV2Query({ tagIds: [] }).tagIds).toBeUndefined();
	});

	it("defaults page to 0 and size to the ledger page size", () => {
		const query = buildAssetV2Query({});
		expect(query.page).toBe(0);
		expect(query.size).toBe(10);
	});
});
