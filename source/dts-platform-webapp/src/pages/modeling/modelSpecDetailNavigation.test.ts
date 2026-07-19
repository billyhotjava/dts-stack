import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import test from "node:test";

const helperUrl = new URL("./modelSpecDetailNavigation.ts", import.meta.url);
const pageUrl = new URL("./ModelSpecDetailPage.tsx", import.meta.url);
const fieldsUrl = new URL("./components/ModelSpecFieldsTab.tsx", import.meta.url);
const standardsUrl = new URL("./components/ModelSpecStandardsTab.tsx", import.meta.url);
const read = (url: URL) => readFileSync(url, "utf8");

test("model detail accepts only the three canonical tabs and builds encoded routes", async () => {
	assert.equal(existsSync(helperUrl), true, "model detail navigation helper is missing");
	const { modelSpecDetailPath, modelSpecPlanModelsPath, resolveModelSpecDetailTab } = await import(helperUrl.href);

	assert.equal(resolveModelSpecDetailTab(new URLSearchParams()), "design");
	assert.equal(resolveModelSpecDetailTab(new URLSearchParams("tab=fields")), "fields");
	assert.equal(resolveModelSpecDetailTab(new URLSearchParams("tab=standards")), "standards");
	assert.equal(resolveModelSpecDetailTab(new URLSearchParams("tab=release")), "design");
	assert.equal(
		modelSpecDetailPath("model / 1", "standards", "plan / 1"),
		"/modeling/models/model%20%2F%201?tab=standards&planId=plan+%2F+1",
	);
	assert.equal(modelSpecPlanModelsPath("plan / 1"), "/modeling/models?planId=plan+%2F+1");
	assert.equal(modelSpecPlanModelsPath("  "), "/modeling/models");
});

test("model detail separates design fields and standards without trusting query plan context", () => {
	assert.equal(existsSync(fieldsUrl), true, "model fields tab is missing");
	assert.equal(existsSync(standardsUrl), true, "model standards tab is missing");
	const page = read(pageUrl);
	const fields = read(fieldsUrl);
	const standards = read(standardsUrl);

	assert.match(page, /<Tabs/);
	assert.match(page, /ModelSpecFieldsTab/);
	assert.match(page, /ModelSpecStandardsTab/);
	assert.equal(
		page.match(/forceRender:\s*true/g)?.length,
		2,
		"design and field values must stay registered on deep links",
	);
	assert.match(
		page,
		/await form\.validateFields\(\);[\s\S]*form\.getFieldsValue\(true\)/,
		"full-replacement saves must include unmounted metric and standard collections",
	);
	assert.match(page, /modelSpecPlanModelsPath\(model\.planId\)/);
	assert.doesNotMatch(page, /searchParams\.get\(["']planId["']\)/);
	assert.match(fields, /Form\.List[\s\S]*name="fields"/);
	assert.match(standards, /standardBindings/);
	assert.match(standards, /前往数据元/);
	assert.doesNotMatch(standards, />关联字段标准</);
	assert.doesNotMatch(standards, /(standardElementVersion|referenceCodeVersion|measurementUnitVersion)\s*:\s*1/);
});
