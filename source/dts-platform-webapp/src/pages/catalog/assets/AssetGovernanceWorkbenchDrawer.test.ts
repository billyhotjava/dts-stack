// @vitest-environment jsdom

import { describe, expect, it } from "vitest";
import type { ClassificationFactView } from "@/api/platformApi";
import { resolveAssetGovernanceTask } from "./AssetGovernanceWorkbenchDrawer";
import type { AssetRow } from "./assetPageShared";

const classificationFact: ClassificationFactView = {
	subjectType: "ASSET",
	subjectKey: "source:erp/schema:public/table:orders",
	sealed: true,
	effectiveLevel: "INTERNAL",
};

const readyAsset = (overrides: Partial<AssetRow> = {}): AssetRow => ({
	id: "asset-1",
	name: "orders",
	type: "POSTGRESQL",
	assetType: "DATASET",
	assetKey: classificationFact.subjectKey,
	classification: "INTERNAL",
	domainId: "domain-sales",
	domain: "销售域",
	owner: "owner-a",
	lifecycleStatus: "ACTIVE",
	governanceStatus: "GOVERNED",
	matchStatus: "MATCHED",
	metadataSource: "DTS-CATALOG",
	assetTags: [
		{ id: "tag-1", categoryId: "cat-1", code: "ORDER", name: "订单", builtin: false, enabled: true, usageCount: 1 },
	],
	...overrides,
});

describe("resolveAssetGovernanceTask", () => {
	it("fails closed when the canonical asset identity is incomplete", () => {
		const task = resolveAssetGovernanceTask(readyAsset({ assetKey: undefined }), classificationFact);
		expect(task.title).toBe("修复资产身份映射");
		expect(task.target).toBe("DETAIL");
	});

	it("routes ownership and domain gaps to governance responsibility", () => {
		const task = resolveAssetGovernanceTask(
			readyAsset({ domain: undefined, domainId: undefined, owner: undefined, ownerDept: undefined }),
			classificationFact,
		);
		expect(task.title).toBe("补齐治理责任");
		expect(task.detailTab).toBe("governance");
	});

	it("routes a missing classification to the immutable lifecycle workbench", () => {
		const task = resolveAssetGovernanceTask(readyAsset({ classification: undefined }), {
			...classificationFact,
			effectiveLevel: null,
		});
		expect(task.target).toBe("LIFECYCLE");
	});

	it("prioritizes an invalid lifecycle over ordinary responsibility gaps", () => {
		const task = resolveAssetGovernanceTask(
			readyAsset({ lifecycleStatus: "ARCHIVED", domain: undefined, domainId: undefined }),
			classificationFact,
		);
		expect(task.target).toBe("LIFECYCLE");
	});

	it("prompts for business tags before the access step", () => {
		const task = resolveAssetGovernanceTask(readyAsset({ assetTags: [] }), classificationFact);
		expect(task.target).toBe("TAGS");
	});

	it("routes a governed and tagged asset to access verification", () => {
		const task = resolveAssetGovernanceTask(readyAsset(), classificationFact);
		expect(task.target).toBe("DETAIL");
		expect(task.detailTab).toBe("access");
	});
});
