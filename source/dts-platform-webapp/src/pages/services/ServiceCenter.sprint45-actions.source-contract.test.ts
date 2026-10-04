import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const API_SOURCE = readFileSync(new URL("./ApiServicesPage.tsx", import.meta.url), "utf8");
const PRODUCTS_SOURCE = readFileSync(new URL("./DataProductsPage.tsx", import.meta.url), "utf8");
const TOKENS_SOURCE = readFileSync(new URL("./TokensPage.tsx", import.meta.url), "utf8");

test("Sprint-45 API service page uses publish-oriented customer actions", () => {
	for (const action of ["新建 API", "测试调用", "启用", "下线", "查看调用", "查看审计"]) {
		assert.match(API_SOURCE, new RegExp(action));
	}
	assert.match(API_SOURCE, /apiServicesService\.tryInvoke/);
	assert.match(API_SOURCE, /apiServicesService\.disable/);
	assert.doesNotMatch(API_SOURCE, /新增 API/);
});

test("Sprint-45 service data product page uses unified data product lifecycle wording", () => {
	for (const action of ["新建数据产品", "查看来源资产", "配置消费方式", "发布", "下线"]) {
		assert.match(PRODUCTS_SOURCE, new RegExp(action));
	}
	assert.doesNotMatch(PRODUCTS_SOURCE, /新增数据产品/);
});

test("Sprint-45 token page exposes secure sharing controls and one-time copy wording", () => {
	for (const action of ["生成令牌", "复制令牌", "作用域", "有效期", "撤销", "查看审计", "只展示一次"]) {
		assert.match(TOKENS_SOURCE, new RegExp(action));
	}
	assert.doesNotMatch(TOKENS_SOURCE, /吊销/);
});
