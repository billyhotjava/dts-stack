import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const screenDesignerCssPath = new URL("../../ScreenDesigner.css", import.meta.url);

test("PropertyPanel controls shrink or wrap inside resizable inspector width", async () => {
	const source = await readFile(screenDesignerCssPath, "utf8");
	const propertyRowBlock = source.match(/\.property-row\s*\{[^}]*\}/)?.[0] ?? "";
	const propertyControlBlock = source.match(/\.property-control\s*\{[^}]*\}/)?.[0] ?? "";

	assert.match(source, /\.property-panel-content\s*\{[\s\S]*overflow-x:\s*hidden\s*!important;/);
	assert.match(propertyRowBlock, /min-width:\s*0;/);
	assert.match(propertyRowBlock, /flex-wrap:\s*wrap;/);
	assert.match(propertyControlBlock, /min-width:\s*0;/);
	assert.match(propertyControlBlock, /max-width:\s*100%;/);
	assert.doesNotMatch(propertyRowBlock, /min-width:\s*280px;/);
	assert.doesNotMatch(propertyRowBlock, /flex-wrap:\s*nowrap;/);
	assert.equal(source.includes("overflow-x: auto !important;"), false);
});
