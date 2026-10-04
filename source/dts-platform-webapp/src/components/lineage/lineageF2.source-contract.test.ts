import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const COMPONENT_SOURCE = readFileSync(new URL("./LineageGraph.tsx", import.meta.url), "utf8");
const PAGE_SOURCE = readFileSync(new URL("../../pages/catalog/LineageGraphPage.tsx", import.meta.url), "utf8");
const SHARED_SOURCE = readFileSync(new URL("../../pages/catalog/lineageShared.tsx", import.meta.url), "utf8");
const COLUMNS_SOURCE = readFileSync(new URL("../../pages/catalog/LineageColumnsPage.tsx", import.meta.url), "utf8");

test("lineage graph component depends on the contract module instead of page modules", () => {
	assert.doesNotMatch(COMPONENT_SOURCE, /from "\.\.\/\.\.\/pages/);
	assert.doesNotMatch(COMPONENT_SOURCE, /@\/pages\/catalog\/lineageShared/);
	assert.match(COMPONENT_SOURCE, /from "@\/features\/catalog\/lineageContracts"/);
});

test("keyword filtering is a single data-level semantic without graph highlight duality", () => {
	assert.doesNotMatch(COMPONENT_SOURCE, /highlightKeyword/);
	assert.doesNotMatch(PAGE_SOURCE, /highlightKeyword/);
	assert.match(PAGE_SOURCE, /const \{ nodes, edges \} = useLineageData\(impact, keyword\)/);
	assert.doesNotMatch(PAGE_SOURCE, /highlightKeyword=\{\s*keyword\s*\}/);
});

test("lineage graph page keeps every filter in URL and consumes dataset deep links", () => {
	assert.match(PAGE_SOURCE, /searchParams\.get\("datasetId"\)/);
	assert.match(PAGE_SOURCE, /searchParams\.get\("direction"\)/);
	assert.match(PAGE_SOURCE, /searchParams\.get\("depth"\)/);
	assert.match(PAGE_SOURCE, /searchParams\.get\("layers"\)/);
	assert.match(PAGE_SOURCE, /searchParams\.get\("changed"\)/);
	assert.match(PAGE_SOURCE, /searchParams\.get\("at"\)/);
	assert.match(PAGE_SOURCE, /searchParams\.get\("layout"\)/);
	assert.match(PAGE_SOURCE, /searchParams\.get\("columns"\)/);
	assert.match(PAGE_SOURCE, /params\.set\("datasetId", selectedId\)/);
	assert.match(PAGE_SOURCE, /setSearchParams\(params, \{ replace: true \}\)/);
});

test("lineage section navigation and columns page preserve the selected dataset context", () => {
	assert.match(SHARED_SOURCE, /useSearchParams\(\)/);
	assert.match(SHARED_SOURCE, /searchParams\.toString\(\)/);
	assert.match(SHARED_SOURCE, /navigate\(query \? `\$\{path\}\?\$\{query\}` : path\)/);
	assert.match(COLUMNS_SOURCE, /searchParams\.get\("datasetId"\)/);
	assert.match(COLUMNS_SOURCE, /params\.set\("datasetId", selectedId\)/);
});
