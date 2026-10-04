import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import {
	GOVERNANCE_STATUS_DICT,
	LIFECYCLE_STATUS_DICT,
	resolveEnumLabel,
} from "./assetEnumLabels.ts";

test("resolveEnumLabel 返回字典中文", () => {
	assert.equal(resolveEnumLabel(GOVERNANCE_STATUS_DICT, "PENDING_DOMAIN"), "待归域");
});

test("resolveEnumLabel 大小写与空白不敏感", () => {
	assert.equal(resolveEnumLabel(GOVERNANCE_STATUS_DICT, "  pending_domain "), "待归域");
});

test("未收录枚举降级为可见的未知标记，而不是裸原值", () => {
	assert.equal(resolveEnumLabel(GOVERNANCE_STATUS_DICT, "SOMETHING_NEW"), "未知（SOMETHING_NEW）");
});

test("空值返回未设定", () => {
	assert.equal(resolveEnumLabel(GOVERNANCE_STATUS_DICT, ""), "未设定");
	assert.equal(resolveEnumLabel(GOVERNANCE_STATUS_DICT, null), "未设定");
});

test("空值可用 fallback 覆盖", () => {
	assert.equal(resolveEnumLabel(GOVERNANCE_STATUS_DICT, null, "全部"), "全部");
});

test("治理状态字典覆盖后端实有的 8 个值", () => {
	for (const key of [
		"GOVERNED",
		"PENDING_CLAIM",
		"PENDING_CLASSIFICATION",
		"PENDING_DOMAIN",
		"PENDING_GOVERNANCE",
		"PENDING_APPROVAL",
		"PENDING_LINEAGE",
		"PENDING_REVIEW",
		"DISABLED",
	]) {
		assert.ok(GOVERNANCE_STATUS_DICT[key], `治理状态字典缺少 ${key}`);
	}
});

test("生命周期字典覆盖 CatalogAssetLifecycleStatus 全部枚举", () => {
	for (const key of [
		"DISCOVERED",
		"PENDING_GOVERNANCE",
		"DRAFT_GOVERNANCE",
		"TESTING",
		"ACTIVE",
		"DEPRECATED",
		"ARCHIVED",
		"BLOCKED",
		"PENDING_REVIEW",
	]) {
		assert.ok(LIFECYCLE_STATUS_DICT[key], `生命周期字典缺少 ${key}`);
	}
});

test("生命周期字典与后端 Java 枚举逐个对齐（防漂移）", () => {
	const javaSource = readFileSync(
		new URL(
			"../../../../../dts-platform/src/main/java/com/yuzhi/dts/platform/service/catalog/CatalogAssetLifecycleStatus.java",
			import.meta.url,
		),
		"utf8",
	);
	const body = javaSource.slice(
		javaSource.indexOf("{"),
		javaSource.indexOf(";", javaSource.indexOf("{")),
	);
	const javaEnums = [...body.matchAll(/^\s{4}([A-Z][A-Z_]*)\s*,?\s*$/gm)].map((m) => m[1]);

	assert.ok(javaEnums.length >= 9, `解析到的 Java 枚举过少：${javaEnums.join(",")}`);
	for (const name of javaEnums) {
		assert.ok(
			LIFECYCLE_STATUS_DICT[name],
			`后端新增枚举 ${name} 未补中文翻译，请更新 LIFECYCLE_STATUS_DICT`,
		);
	}
});
