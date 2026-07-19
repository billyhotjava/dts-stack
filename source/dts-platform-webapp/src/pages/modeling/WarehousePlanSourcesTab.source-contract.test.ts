import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const readSource = (url: URL): string => (existsSync(url) ? readFileSync(url, "utf8") : "");

const component = readSource(new URL("./WarehousePlanSourcesTab.tsx", import.meta.url));
const parent = readSource(new URL("./WarehousePlanDetailPage.tsx", import.meta.url));
const api = readSource(new URL("../../api/warehousePlanApi.ts", import.meta.url));

test("source inventory API uses the canonical body version and a narrow PUT command", () => {
	assert.match(api, /type WarehousePlanSourceInventoryView[\s\S]*?version:\s*number[\s\S]*?etag:\s*string/);
	assert.match(api, /export const getWarehousePlanSources[\s\S]*?\/baseline\/sources/);
	assert.match(api, /type WarehousePlanSourceBindingInput[\s\S]*?sourceType\?:[\s\S]*?locator\?:/);
	assert.match(api, /type WarehousePlanSourceBindingView[\s\S]*?locator:\s*WarehousePlanSourceLocator\s*\|\s*null/);

	const saveSource = api.match(/export const saveWarehousePlanSources[\s\S]*?\n\t\);/)?.[0] || "";
	assert.match(saveSource, /version:\s*number/);
	assert.match(saveSource, /"If-Match":\s*`"sources:\$\{version\}"`/);
	assert.match(saveSource, /data:\s*\{\s*bindings\s*\}/);
	assert.doesNotMatch(saveSource, /resolvedVersion|freshness|displayName|confirmedVersion|sourceId/);
});

test("warehouse plan detail delegates the real source workspace to one focused component", () => {
	assert.match(component, /export function WarehousePlanSourcesTab/);
	assert.match(component, /getWarehousePlanSources/);
	assert.match(component, /saveWarehousePlanSources/);
	assert.match(parent, /import \{ WarehousePlanSourcesTab \} from "\.\/WarehousePlanSourcesTab"/);
	assert.match(parent, /<WarehousePlanSourcesTab/);
	assert.doesNotMatch(parent, /key: "sources"[\s\S]{0,500}盘点现有数据/);
});

test("source rows expose server-owned identity, version and all freshness states as read-only evidence", () => {
	for (const status of ["CURRENT", "STALE", "UNKNOWN", "NOT_REQUIRED_YET"]) {
		assert.match(component, new RegExp(`\\b${status}\\b`));
	}
	assert.match(component, /binding\.bindingId/);
	assert.match(component, /binding\.resolvedVersion/);
	assert.match(component, /binding\.freshness/);
	assert.doesNotMatch(component, /来源盘点完成/);
	assert.doesNotMatch(component, /toSorted\(|toReversed\(|Promise\.withResolvers|Array\.fromAsync/);
});

test("forbidden, missing and provider failures keep a generic stable reference without leaking names", () => {
	assert.match(component, /resolutionStatus\s*===\s*"FORBIDDEN"[\s\S]{0,180}"不可访问来源"/);
	assert.match(component, /resolutionStatus\s*===\s*"MISSING"[\s\S]{0,180}"已删除来源"/);
	assert.match(component, /resolutionStatus\s*===\s*"PROVIDER_ERROR"[\s\S]{0,180}"来源暂时无法核验"/);
	assert.match(component, /修复来源/);
});

test("source save serializes confirmation, exclusion and reason without forged resolution evidence", () => {
	const serializer = component.match(/export const buildWarehousePlanSourceSaveBindings[\s\S]*?\n\};/)?.[0] || "";
	for (const field of ["bindingId", "confirmationStatus", "exclusionReason"]) {
		assert.match(serializer, new RegExp(field));
	}
	assert.doesNotMatch(serializer, /sourceType|locator/);
	for (const serverField of [
		"displayName",
		"confirmedVersion",
		"resolvedVersion",
		"resolutionStatus",
		"freshness",
		"lastValidatedAt",
	]) {
		assert.doesNotMatch(serializer, new RegExp(serverField));
	}
	assert.match(component, /EXCLUDED/);
	assert.match(component, /请填写排除原因/);
	assert.doesNotMatch(component, /业务对象|业务过程|objectId|processId/);
});

test("409 keeps the draft and offers only reload or an explicit latest-version confirmation", () => {
	assert.match(component, /resolveWarehousePlanConflictVersion/);
	assert.match(component, /当前草稿已保留/);
	assert.match(component, /系统没有自动覆盖/);
	assert.match(component, /重新加载服务端最新版/);
	assert.match(component, /确认保留当前草稿并基于版本/);
	assert.doesNotMatch(component, /自动重试/);
});

test("successful source writes refresh source, baseline and stage projection", () => {
	const saveFlow = component.match(/const saveSources[\s\S]*?\n\t};/)?.[0] || "";
	assert.match(saveFlow, /const savedInventory\s*=\s*await saveWarehousePlanSources/);
	assert.match(saveFlow, /setInventory\(savedInventory\)/);
	assert.match(saveFlow, /loadSources/);
	assert.match(saveFlow, /onSaved/);
	assert.match(parent, /onSaved=\{loadEvidence\}/);
});

test("an empty inventory can register a real catalog asset without leaving the plan", () => {
	assert.match(component, /listCatalogAssetsV2/);
	assert.match(component, /legacyDatasetId/);
	assert.doesNotMatch(component, /listDatasets/);
	assert.match(component, /listTablesByDataset/);
	assert.match(component, /选择数据集/);
	assert.match(component, /选择数据表/);
	assert.match(component, /登记目录资产/);
	assert.match(component, /sourceType:\s*"CATALOG_TABLE"/);
	assert.match(component, /locator:\s*\{\s*assetId/);
	assert.match(component, /saveWarehousePlanSources/);
	assert.doesNotMatch(component, /尚未登记来源[\s\S]{0,500}打开来源目录/);
});

test("source mutations are invalidated when planId changes and never publish stale state", () => {
	assert.match(component, /const mutationGuard\s*=\s*useMemo\(\(\) => createLatestRequestGuard\(\)/);
	const planEffect =
		component.match(/useEffect\(\(\) => \{[\s\S]*?void loadSources\(true\);[\s\S]*?\}, \[[\s\S]*?\]\);/)?.[0] || "";
	assert.match(planEffect, /mutationGuard\.invalidate\(\)/);
	assert.match(planEffect, /setSaving\(false\)/);
	for (const mutationName of ["saveSources", "registerCatalogAsset"]) {
		const mutation = component.match(new RegExp(`const ${mutationName}[\\s\\S]*?\\n\\t};`))?.[0] || "";
		assert.match(mutation, /const isCurrent\s*=\s*mutationGuard\.begin\(\)/);
		assert.ok(
			mutation.indexOf("mutationGuard.begin()") < mutation.indexOf("validateFields()"),
			`${mutationName} must claim its plan-scoped mutation before async form validation`,
		);
		assert.match(mutation, /if \(!isCurrent\(\)\) return/);
		assert.match(mutation, /finally[\s\S]*?if \(isCurrent\(\)\) setSaving\(false\)/);
	}
});

test("switching planId clears every catalog selection and pending conflict", () => {
	const planEffect =
		component.match(/useEffect\(\(\) => \{[\s\S]*?void loadSources\(true\);[\s\S]*?\}, \[[\s\S]*?\]\);/)?.[0] || "";
	assert.match(planEffect, /setSelectedCatalogDatasetId\(null\)/);
	assert.match(planEffect, /setSelectedAssetId\(null\)/);
	assert.match(planEffect, /setPendingCatalogAssetId\(null\)/);
	assert.match(planEffect, /setCatalogOptions\(\[\]\)/);
});

test("planning policy preserves the explicit conceptual-design decision", () => {
	assert.match(api, /type WarehousePlanPolicyInput[\s\S]*?conceptualDesignAllowed:\s*boolean/);
	assert.match(parent, /conceptualDesignAllowed:\s*policyResult\.value\.value\.conceptualDesignAllowed/);
	assert.match(parent, /conceptualDesignAllowed:\s*values\.conceptualDesignAllowed/);
	assert.match(parent, /<Switch/);
	assert.match(component, /conceptualDesignAllowed/);
	const savePolicyFlow = parent.match(/const savePolicy[\s\S]*?\n\t};/)?.[0] || "";
	assert.match(savePolicyFlow, /const savedPolicy\s*=\s*await saveWarehousePlanPolicy/);
	assert.match(savePolicyFlow, /setPlanningPolicy\(savedPolicy\)/);
});

test("an initial load failure does not masquerade as an empty inventory", () => {
	assert.match(component, /if \(loadFailed\s*&&\s*!inventory\)/);
});

test("model-center handoff uses the canonical modeling route", () => {
	assert.match(parent, /route:\s*"\/modeling\/models"/);
	assert.doesNotMatch(parent, /\/modeling\/semantic\/models/);
});
