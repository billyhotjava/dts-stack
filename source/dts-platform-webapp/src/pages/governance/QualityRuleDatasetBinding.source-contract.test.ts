import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const RULES_PAGE = readFileSync(new URL("./QualityRulesPage.tsx", import.meta.url), "utf8");
const CREATE_WIZARD = readFileSync(new URL("./components/RuleCreateWizard.tsx", import.meta.url), "utf8");

const DATASET_BINDING_PAYLOAD =
	/bindings:\s*values\.datasetId\s*\?\s*\[\{\s*datasetId:\s*values\.datasetId,\s*scopeType:\s*"DATASET"\s*\}\]\s*:\s*\[\]/;

test("quality rule creation persists the selected dataset as a version binding", () => {
	assert.match(CREATE_WIZARD, DATASET_BINDING_PAYLOAD);
});

test("quality rule editing persists the selected dataset as a version binding", () => {
	assert.match(RULES_PAGE, DATASET_BINDING_PAYLOAD);
});

test("quality rule creation presents the binding target as a data asset", () => {
	assert.match(CREATE_WIZARD, /\{ title: "绑定数据资产" \}/);
	assert.match(CREATE_WIZARD, /label="质量检测对象"/);
	assert.match(CREATE_WIZARD, /当前支持默认数据湖中的数据集（表\/视图）/);
	assert.match(CREATE_WIZARD, /sourceName=\{defaultLakeName\}/);
});
