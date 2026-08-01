import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const packageUrl = new URL("./dataProductAcceptancePackage.ts", import.meta.url);
const indexUrl = new URL("./index.ts", import.meta.url);
const workbenchUrl = new URL("../../pages/workbench/DataManagementWorkbenchPage.tsx", import.meta.url);
const staticRoutes = readFileSync(
	new URL("../../routes/sections/dashboard/static-routes.tsx", import.meta.url),
	"utf8",
);
const dynamicResolver = readFileSync(
	new URL("../../routes/sections/dashboard/dynamic-resolver.tsx", import.meta.url),
	"utf8",
);

const routes = [
	"/foundation/data-sources",
	"/governance/standards/elements",
	"/data-modeling/dimensions/workbench",
	"/data-modeling/metrics/atomic",
	"/services/apis",
	"/governance/rules/runs",
	"/governance/asset-grants",
	"/ops/instances",
	"/ops/audit-evidence",
] as const;

test("acceptance package model defines nine evidence groups and export builders", () => {
	assert.equal(existsSync(packageUrl), true, `${packageUrl.pathname} should exist`);
	const source = readFileSync(packageUrl, "utf8");

	assert.match(source, /DataProductAcceptancePackage/);
	assert.match(source, /DataProductAcceptanceEvidenceGroupKey/);
	assert.match(source, /DataProductAcceptanceEvidenceGroup/);
	assert.match(source, /DataProductAcceptanceEvidenceStatus/);
	assert.match(source, /buildDataProductAcceptancePackage/);
	assert.match(source, /buildAcceptancePackageMarkdown/);
	assert.match(source, /buildAcceptancePackageJson/);
	assert.match(source, /ACCEPTANCE_EVIDENCE_GROUPS/);
	for (const key of [
		"source",
		"standards",
		"model",
		"metrics",
		"service",
		"quality",
		"permission",
		"operation",
		"audit",
	]) {
		assert.match(source, new RegExp(key));
	}
	for (const label of ["来源", "标准", "模型", "指标", "服务", "质量", "权限", "运行", "审计"]) {
		assert.match(source, new RegExp(label));
	}
	for (const status of ["ready", "missing", "blocked"]) {
		assert.match(source, new RegExp(status));
	}
	for (const param of ["sourceId", "standardDraftId", "modelSpecId", "metricId", "serviceId", "runId", "auditId"]) {
		assert.match(source, new RegExp(param));
	}
});

test("acceptance package evidence links preserve journey context and point to registered pages", () => {
	const source = readFileSync(packageUrl, "utf8");

	for (const route of routes) {
		assert.match(source, new RegExp(route.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
		assert.ok(
			(route.startsWith("/data-modeling/") && staticRoutes.includes('path: "data-modeling/*"')) ||
				dynamicResolver.includes(`"${route}"`) ||
				staticRoutes.includes(`path: "${route.slice(1)}"`),
			`${route} should resolve to a registered page`,
		);
	}
	assert.match(source, /buildJourneyUrl/);
	assert.match(source, /journey=e2e-data-product|E2E_DATA_PRODUCT_JOURNEY/);
	assert.match(source, /missingReason/);
	assert.match(source, /补齐入口/);
});

test("workbench renders acceptance package with copy and JSON export actions", () => {
	const source = readFileSync(workbenchUrl, "utf8");

	assert.match(source, /buildDataProductAcceptancePackage/);
	assert.match(source, /buildAcceptancePackageMarkdown/);
	assert.match(source, /buildAcceptancePackageJson/);
	assert.match(source, /acceptancePackage/);
	assert.match(source, /data-testid="data-product-acceptance-package"/);
	assert.match(source, /data-testid=\{`acceptance-package-group-\$\{group\.key\}`\}/);
	assert.match(source, /客户验收包/);
	assert.match(source, /查看证据/);
	assert.match(source, /缺失项/);
	assert.match(source, /复制验收摘要/);
	assert.match(source, /下载 JSON/);
	assert.match(source, /navigator\.clipboard/);
	assert.match(source, /Blob/);
});

test("journey barrel exports acceptance package helpers", () => {
	const source = readFileSync(indexUrl, "utf8");

	assert.match(source, /dataProductAcceptancePackage/);
	assert.match(source, /buildDataProductAcceptancePackage/);
	assert.match(source, /buildAcceptancePackageMarkdown/);
	assert.match(source, /buildAcceptancePackageJson/);
});

test("acceptance package aggregates structured gate evidence", () => {
	const packageSource = readFileSync(new URL("./dataProductAcceptancePackage.ts", import.meta.url), "utf8");

	assert.match(packageSource, /buildGateEvidence/);
	assert.match(packageSource, /gateEvidence/);
	assert.match(packageSource, /发布门禁/);
	assert.match(packageSource, /门禁未齐/);
	assert.match(packageSource, /发布门禁明细/);

	const behaviorTestUrl = new URL("./dataProductAcceptancePackage.test.ts", import.meta.url);
	assert.equal(existsSync(behaviorTestUrl), true, "dataProductAcceptancePackage.test.ts (vitest) should exist");
});

test("workbench provides a chrome95-safe print view for the acceptance package", () => {
	const workbenchSource = readFileSync(
		new URL("../../pages/workbench/DataManagementWorkbenchPage.tsx", import.meta.url),
		"utf8",
	);

	assert.match(workbenchSource, /data-print-root/);
	assert.match(workbenchSource, /acceptance-print-view/);
	assert.match(workbenchSource, /acceptance-print-header/);
	assert.match(workbenchSource, /acceptance-print-signoff/);
	assert.match(workbenchSource, /@media print/);
	assert.match(workbenchSource, /journey-print-only/);
	assert.match(workbenchSource, /window\.print\(\)/);

	const packageSource = readFileSync(new URL("./dataProductAcceptancePackage.ts", import.meta.url), "utf8");
	assert.match(packageSource, /ACCEPTANCE_SIGN_COLUMNS/);
	assert.match(packageSource, /buildAcceptancePrintMeta/);
});
