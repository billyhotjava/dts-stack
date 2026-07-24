import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (relativePath: string) => readFileSync(new URL(relativePath, import.meta.url), "utf8");

test("dimension definition journey keeps a narrow business drawer, truthful empty states and version recovery", () => {
	const drawer = read("./components/DimensionDefinitionCreateDrawer.tsx");
	const catalog = read("./DimensionCatalogPage.tsx");
	const detail = read("./ModelSpecDetailPage.tsx");

	assert.match(drawer, /width=\{540\}/);
	assert.match(drawer, /aria-label=\{editing/);
	assert.match(drawer, /await form\.validateFields\(\);/);
	assert.match(drawer, /加载最新版本/);
	assert.match(catalog, /dimensionCatalogEmptyText/);
	assert.match(catalog, /listDimensionDefinitions/);
	assert.doesNotMatch(catalog, /ModelSpecCreateDrawer|listModelSpecs/);
	assert.match(detail, /canonicalModel\?\.status === "DRAFT"/);
	assert.match(detail, /只有草稿可编辑/);
	assert.match(detail, /isModelSpecStatusReadonly/);
	assert.match(detail, /加载最新状态/);
	assert.match(detail, /返回维度目录/);
});
