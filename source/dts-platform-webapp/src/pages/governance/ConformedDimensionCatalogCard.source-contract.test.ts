import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const source = readFileSync(new URL("./ConformedDimensionCatalogCard.tsx", import.meta.url), "utf8");

test("dimension catalog supports manual lifecycle and explicit candidate confirmation", () => {
	assert.match(source, /createConformedDimensionApi/);
	assert.match(source, /deleteConformedDimensionApi/);
	assert.match(source, /confirmModelingCandidatesApi/);
	assert.match(source, /新增一致性维度/);
	assert.match(source, /确认后使用/);
	assert.match(source, /批量确认/);
});

test("optional templates are isolated in a drawer and require explicit installation", () => {
	assert.match(source, /Drawer/);
	assert.match(source, /listModelingTemplatesApi/);
	assert.match(source, /installModelingTemplateApi/);
	assert.match(source, /可选模板/);
	assert.match(source, /安装只会生成候选/);
	assert.doesNotMatch(source, /PJM|pjm/);
});
