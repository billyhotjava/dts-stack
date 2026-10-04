import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const PAGE_PATH = new URL("./AssetDetailPage.tsx", import.meta.url).pathname;
const REDIRECT = readFileSync(new URL("./LegacyAssetDetailRedirect.tsx", import.meta.url), "utf8");
const RESOLVER = readFileSync(new URL("../../routes/sections/dashboard/dynamic-resolver.tsx", import.meta.url), "utf8");
const GOVERNANCE = readFileSync(new URL("./AssetGovernanceOverview.tsx", import.meta.url), "utf8");

test("ADR-85-09: the legacy asset detail page is retired and its route converges", () => {
	assert.ok(!existsSync(PAGE_PATH), "AssetDetailPage.tsx must be deleted (ADR-85-09)");
	assert.match(RESOLVER, /"\/catalog\/asset-detail": "\/pages\/catalog\/LegacyAssetDetailRedirect"/);
	assert.doesNotMatch(RESOLVER, /"\/catalog\/asset-detail": "\/pages\/catalog\/AssetDetailPage"/);
});

test("legacy asset-detail deep links redirect to the unified detail page when the id resolves", () => {
	assert.match(REDIRECT, /Navigate to=\{`\/catalog\/datasets\/\$\{encodeURIComponent\(id\)\}`\} replace \/>/);
	assert.match(REDIRECT, /UUID_PATTERN\.test\(id\)/);
	assert.match(REDIRECT, /Navigate to="\/catalog\/assets\/ledger" replace \/>/);
});

test("governance overview panel survives as the shared presentational component", () => {
	assert.match(GOVERNANCE, /权限授权/);
	assert.match(GOVERNANCE, /治理健康/);
	assert.match(GOVERNANCE, /查看质量报告/);
});
