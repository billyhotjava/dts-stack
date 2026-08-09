import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const GOVERNANCE_SOURCE = readFileSync(new URL("../governance/GovernanceCenterPage.tsx", import.meta.url), "utf8");
const ASSETS_SOURCE = readFileSync(new URL("./DataSearchPage.tsx", import.meta.url), "utf8");
// Sprint-57 F4 拆分：台账/地图视图组件化，行级动作断言并入组合源
const ASSETS_COMBINED =
	ASSETS_SOURCE +
	readFileSync(new URL("./assets/AssetLedgerView.tsx", import.meta.url), "utf8") +
	readFileSync(new URL("./AssetOverviewPage.tsx", import.meta.url), "utf8");
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

test("Sprint-45 asset portal exposes consumption and governance actions per asset", () => {
	for (const label of ["申请权限", "查看血缘", "创建报表", "生成数据产品", "发布数据 API", "处置缺口", "治理状态"]) {
		assert.match(ASSETS_COMBINED, new RegExp(label));
	}
	for (const route of [
		"/security/dataset-access-approval",
		"/bi/dashboards",
		"/catalog/data-products",
		"/services/apis",
	]) {
		assert.match(ASSETS_COMBINED, new RegExp(route));
	}
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
