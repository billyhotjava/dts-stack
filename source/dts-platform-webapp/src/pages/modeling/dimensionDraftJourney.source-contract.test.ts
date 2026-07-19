import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (relativePath: string) => readFileSync(new URL(relativePath, import.meta.url), "utf8");

test("dimension draft journey keeps narrow layout, truthful empty states and DRAFT-only editing", () => {
	const drawer = read("./components/ModelSpecCreateDrawer.tsx");
	const fields = read("./components/ModelSpecEditorFields.tsx");
	const catalog = read("./DimensionCatalogPage.tsx");
	const detail = read("./ModelSpecDetailPage.tsx");

	assert.match(drawer, /width=\{"min\(760px, 100vw\)"\}/);
	assert.match(drawer, /aria-label=\{initialModelType/);
	assert.match(drawer, /form\.setFieldValue\("domainId", ""\)/);
	assert.match(drawer, /disabled=\{loadingPlans \|\| loadingDomains\}/);
	assert.match(drawer, /await form\.validateFields\(\);\s*const values = form\.getFieldsValue\(true\);/);
	assert.match(fields, /modelSpecEditorCopy\(modelType\)/);
	assert.match(catalog, /dimensionCatalogEmptyText/);
	assert.match(catalog, /!loadError\s*\?/);
	assert.match(detail, /canonicalModel\?\.status === "DRAFT"/);
	assert.match(detail, /只有草稿可编辑/);
	assert.match(detail, /isModelSpecStatusReadonly/);
	assert.match(detail, /加载最新状态/);
	assert.match(detail, /返回维度目录/);
});
