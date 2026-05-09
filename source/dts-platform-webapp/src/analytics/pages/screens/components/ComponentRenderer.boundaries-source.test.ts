import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const componentRendererPath = new URL("./ComponentRenderer.tsx", import.meta.url);

test("ComponentRenderer delegates chart annotation logic to the shared renderer module", async () => {
	const source = await readFile(componentRendererPath, "utf8");

	assert.match(source, /from '\.\.\/renderers\/shared\/chartAnnotations'/);
	assert.equal(source.includes("function injectChartAnnotations"), false);
	assert.equal(source.includes("const ANNOTATABLE_TYPES = new Set"), false);
	assert.match(source, /injectChartAnnotations\(option, c\)/);
});
