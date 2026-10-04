import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const GOVERNANCE_SOURCE = readFileSync(new URL("../governance/GovernanceCenterPage.tsx", import.meta.url), "utf8");
const ASSETS_SOURCE = readFileSync(new URL("./DataSearchPage.tsx", import.meta.url), "utf8");
const ASSET_TABLE_SOURCE = readFileSync(new URL("./assets/AssetLedgerView.tsx", import.meta.url), "utf8");
const ASSET_DETAIL_SOURCE = readFileSync(new URL("./DatasetDetailPage.tsx", import.meta.url), "utf8");
const PRODUCTS_SOURCE = readFileSync(new URL("./DataProductsPage.tsx", import.meta.url), "utf8");
const APPROVAL_SOURCE = readFileSync(new URL("../security/DatasetAccessApprovalPage.tsx", import.meta.url), "utf8");
const SECURITY_SOURCE = readFileSync(new URL("../security/data-security.tsx", import.meta.url), "utf8");

test("Sprint-45 governance center is a release-gate command page", () => {
	for (const label of [
		"发布门禁",
		"主题域",
		"标准",
		"质量规则",
		"质量检查",
		"分级分类",
		"权限审批",
		"新建规则",
		"运行质量",
		"修复阻断",
		"查看报告",
	]) {
		assert.match(GOVERNANCE_SOURCE, new RegExp(label));
	}
	assert.match(GOVERNANCE_SOURCE, /getGovernanceReleaseGate/);
});

test("Sprint-45 asset directory delegates per-asset governance to the detail page", () => {
	assert.match(ASSETS_SOURCE, /<AssetLedgerView/);
	assert.match(ASSET_TABLE_SOURCE, /router\.push\(`\/catalog\/datasets\/\$\{row\.id\}`\)/);
	assert.doesNotMatch(ASSET_TABLE_SOURCE, /title: "操作"|治理资产|申请权限/);
	for (const label of ["治理信息", "质量与SLA", "血缘与影响", "权限申请", "密级与生命周期", "治理状态"]) {
		assert.match(ASSET_DETAIL_SOURCE, new RegExp(label));
	}
	assert.match(ASSET_DETAIL_SOURCE, /buildAssetGrantUrl/);
	assert.match(ASSET_DETAIL_SOURCE, /AssetLifecycleWorkbenchDrawer/);
});

test("Sprint-45 catalog data product page owns the customer lifecycle wording", () => {
	for (const label of ["新建数据产品", "发布", "下线", "申请", "查看消费", "查看审计", "数据产品合同"]) {
		assert.match(PRODUCTS_SOURCE, new RegExp(label));
	}
	assert.doesNotMatch(PRODUCTS_SOURCE, /Sprint|F\\d|artifact/);
});

test("Sprint-45 approval and data-security pages close permission, classification, exception, and audit flows", () => {
	for (const label of ["申请权限", "批准", "驳回", "撤回", "查看审计", "审批链路"]) {
		assert.match(APPROVAL_SOURCE, new RegExp(label));
	}
	for (const label of ["新建分级", "绑定资产", "申请例外", "脱敏规则", "保存映射", "查看审计"]) {
		assert.match(SECURITY_SOURCE, new RegExp(label));
	}
});
