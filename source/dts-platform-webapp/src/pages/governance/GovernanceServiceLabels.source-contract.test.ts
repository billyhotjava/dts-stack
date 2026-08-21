import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const source = (relativePath: string) => readFileSync(resolve(process.cwd(), relativePath), "utf8");

describe("data governance and service customer-facing labels", () => {
	it("keeps backend enum values while showing Chinese governance labels", () => {
		const ownership = source("src/pages/governance/AssetOwnershipPage.tsx");
		const grants = source("src/pages/governance/AssetGrantPage.tsx");
		const audit = source("src/pages/governance/PermissionAuditPage.tsx");

		expect(ownership).toContain('{ label: "数据表", value: "TABLE" }');
		expect(grants).toContain('{ label: "查看", value: "READ" }');
		expect(audit).toContain('{ label: "变更所有者", value: "CHANGE_OWNERSHIP" }');
		expect(ownership).toContain("assetTypeLabel(v)");
		expect(grants).toContain("permissionLabel(v)");
		expect(audit).toContain("auditActionLabel(v)");
	});

	it("localizes service status and classification without changing request values", () => {
		const apis = source("src/pages/services/ApiServicesPage.tsx");
		const products = source("src/pages/services/DataProductsPage.tsx");
		const links = source("src/pages/services/BiLinksPage.tsx");

		expect(apis).toContain('{ label: "公开", value: "PUBLIC" }');
		expect(apis).toContain("classificationLabel(v)");
		expect(products).toContain('{ label: "已发布", value: "PUBLISHED" }');
		expect(products).toContain("statusLabel(v");
		expect(links).toContain("reportTypeLabel(value)");
		expect(links).toContain('statusLabel(value || "PENDING")');
	});

	it("localizes quality, indicator, approval and catalog lifecycle enums", () => {
		const qualityRuns = source("src/features/data-quality/RunPages.tsx");
		const indicators = source("src/pages/governance/IndicatorListPage.tsx");
		const approvals = source("src/pages/security/DatasetAccessApprovalPage.tsx");
		const lifecycle = source("src/pages/catalog/assets/AssetLifecycleWorkbenchDrawer.tsx");

		expect(qualityRuns).toContain("displayName(value)");
		expect(qualityRuns).toContain('"SUCCEEDED"');
		expect(indicators).toContain("indicatorDomainLabel(v)");
		expect(indicators).toContain("aggregationLabel(v)");
		expect(approvals).toContain('statusLabel(v, "-")');
		expect(lifecycle).toContain("governanceEventLabel(value");
		expect(lifecycle).toContain("migrationDecisionLabel(value)");
		expect(lifecycle).toContain("新建试跑");
	});
});
