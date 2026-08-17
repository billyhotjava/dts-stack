import { describe, expect, it } from "vitest";

import { buildAssetGrantUrl, resolveAssetReadiness } from "./assetPortalUx.helpers";

describe("assetPortalUx helpers", () => {
	it("marks assets without required governance fields as blocked", () => {
		const readiness = resolveAssetReadiness({
			classification: "",
			domainId: undefined,
			ownerDept: "",
			governanceStatus: "PENDING_GOVERNANCE",
		});

		expect(readiness.state).toBe("BLOCKED");
		expect(readiness.reasons).toContain("缺少密级");
		expect(readiness.reasons).toContain("缺少业务归属数据域");
		expect(readiness.reasons).toContain("缺少归属部门");
	});

	it("keeps OpenMetadata-only assets visible as fallback instead of ready", () => {
		const readiness = resolveAssetReadiness({
			classification: "INTERNAL",
			domainId: "domain-1",
			ownerDept: "D001",
			governanceStatus: "GOVERNED",
			matchStatus: "MATCHED",
			metadataSource: "openmetadata-cache",
		});

		expect(readiness.state).toBe("FALLBACK");
		expect(readiness.reasons).toContain("仅有主目录缓存，尚未沉淀为 DTS 治理资产");
	});

	it("treats DTS-native assets as resolved without an OpenMetadata mapping", () => {
		const readiness = resolveAssetReadiness({
			classification: "INTERNAL",
			domainId: "domain-1",
			ownerDept: "D001",
			governanceStatus: "GOVERNED",
			matchStatus: "DTS_NATIVE",
			metadataSource: "dts-catalog",
		});

		expect(readiness).toEqual({ state: "READY", label: "可引用", color: "green", reasons: [] });
	});

	it("builds an asset grant URL that deep-links into the new-request flow", () => {
		expect(buildAssetGrantUrl({ assetType: "TABLE", assetId: "dwd.project/detail" })).toBe(
			"/security/dataset-access-approval?action=new&assetType=TABLE&assetId=dwd.project%2Fdetail",
		);
	});
});

describe("失效生命周期判定", () => {
	it.each(["DEPRECATED", "ARCHIVED", "BLOCKED"])("%s 判为阻断态", (status) => {
		const readiness = resolveAssetReadiness({
			classification: "INTERNAL",
			domainId: "d1",
			ownerDept: "dept",
			lifecycleStatus: status,
			governanceStatus: "GOVERNED",
			matchStatus: "MATCHED",
		});
		expect(readiness.state).toBe("BLOCKED");
	});

	it("不再比较不可达的 DISABLED 生命周期值", () => {
		const readiness = resolveAssetReadiness({
			classification: "INTERNAL",
			domainId: "d1",
			ownerDept: "dept",
			lifecycleStatus: "DISABLED",
			governanceStatus: "GOVERNED",
			matchStatus: "MATCHED",
		});
		expect(readiness.state).toBe("READY");
	});
});
