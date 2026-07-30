import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("./ElementsPage.tsx", import.meta.url), "utf8");
const packageActions = readFileSync(new URL("./StandardPackageActions.tsx", import.meta.url), "utf8");
const dynamicResolver = readFileSync(
	new URL("../../routes/sections/dashboard/dynamic-resolver.tsx", import.meta.url),
	"utf8",
);

test("elements page is a global standard owner and does not require a planning session", () => {
	assert.match(source, /listMetadataStandards/);
	assert.match(source, /新增数据元/);
	assert.match(packageActions, /导入标准包/);
	assert.match(dynamicResolver, /"\/governance\/standards\/elements": "elements"/);
	assert.doesNotMatch(source, /warehousePlanningContext|sessionStorage|standard-draft-blocker/);
	assert.doesNotMatch(source, /缺少数仓规划|缺少数仓规划上下文/);
	assert.doesNotMatch(source, /JourneyContextBar/);
	assert.match(source, /loadError && content\.length === 0/);
});

test("elements page preserves a safe model or plan return chain", () => {
	assert.match(source, /resolveStandardOwnerReturnTo/);
	assert.match(source, /standard-owner-context/);
	assert.match(source, /返回模型字段标准|returnTarget\.label/);
	assert.match(packageActions, /buildStandardPackageImportRoute/);
});

test("field standards are bound by ModelSpec instead of a page-wide draft", () => {
	assert.match(source, /模型字段.*稳定 ID.*版本|稳定 ID.*版本.*模型字段/);
	assert.doesNotMatch(source, /createStandardBindingDraft|createStandardBindingDraftSnapshot/);
	assert.doesNotMatch(source, /standardDraftId|生成字段落标草稿|bindingDraft/);
	assert.match(source, /searchParams\.get\("applied"\) === "1"/);
});

test("data element actions wrap instead of clipping on narrow viewports", () => {
	assert.match(source, /const headerActions\s*=\s*\(\s*<Space wrap>/);
	assert.match(source, /actions=\{headerActions\}/);
	assert.match(source, /<Space wrap className="mb-4">/);
});
