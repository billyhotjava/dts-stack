import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path: string) => readFileSync(new URL(path, import.meta.url), "utf8");

test("screen config keeps global font family in normalized and write payload shape", () => {
	const types = read("./types.ts");
	const spec = read("./screenSpec.ts");
	const contracts = read("./contracts.ts");

	assert.match(types, /fontFamily\?: string/);
	assert.match(spec, /fontFamily: asTrimmedString\(row\.fontFamily\)/);
	assert.match(spec, /fontFamily: config\.fontFamily/);
	assert.match(contracts, /fontFamily\?: string/);
});

test("component renderer receives custom theme and global font tokens", () => {
	const rendererTypes = read("./renderers/types.ts");
	const componentRenderer = read("./components/ComponentRenderer.tsx");

	assert.match(rendererTypes, /customTheme\?: ScreenCustomTheme/);
	assert.match(rendererTypes, /fontFamily\?: string/);
	assert.match(componentRenderer, /getThemeTokens\(theme, customTheme\)/);
	assert.match(componentRenderer, /resolveScreenFontFamily\(fontFamily\)/);
	assert.match(componentRenderer, /fontFamily: screenFontFamily/);
});

test("designer and runtime pages inject uploaded font faces and pass theme context into components", () => {
	for (const file of [
		"./components/DesignerCanvas.tsx",
		"./ScreenPreviewPage.tsx",
		"./PublicScreenPage.tsx",
		"./ScreenExportPage.tsx",
	]) {
		const source = read(file);
		assert.match(source, /useScreenFontFaces\(\)/, file);
		assert.match(source, /customTheme=/, file);
		assert.match(source, /fontFamily=/, file);
	}
});

test("property panel applies theme to components and exposes global font control", () => {
	const source = read("./components/propertyPanel/PropertyPanel.tsx");

	assert.match(source, /applyThemeToComponents/);
	assert.match(source, /全局字体/);
	assert.match(source, /fontFamily: value \|\| undefined/);
});

test("schema config renderer resolves custom theme defaults", () => {
	const source = read("./configSchema/editors/SchemaConfigRenderer.tsx");

	assert.match(source, /customTheme\?: ScreenCustomTheme/);
	assert.match(source, /getThemeTokens\(theme as ScreenTheme, customTheme\)/);
});
