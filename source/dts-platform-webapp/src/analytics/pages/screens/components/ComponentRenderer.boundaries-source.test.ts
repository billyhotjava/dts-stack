import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const componentRendererPath = new URL("./ComponentRenderer.tsx", import.meta.url);
const dataVRendererPath = new URL("../renderers/DataVRenderer.tsx", import.meta.url);

test("ComponentRenderer delegates chart annotation logic to the shared renderer module", async () => {
	const source = await readFile(componentRendererPath, "utf8");

	assert.match(source, /from '\.\.\/renderers\/shared\/chartAnnotations'/);
	assert.equal(source.includes("function injectChartAnnotations"), false);
	assert.equal(source.includes("const ANNOTATABLE_TYPES = new Set"), false);
	assert.match(source, /injectChartAnnotations\(option, c\)/);
});

test("ComponentRenderer delegates DataV component rendering to DataVRenderer", async () => {
	const [componentSource, dataVSource] = await Promise.all([
		readFile(componentRendererPath, "utf8"),
		readFile(dataVRendererPath, "utf8"),
	]);

	assert.match(componentSource, /from '\.\.\/renderers\/DataVRenderer'/);
	assert.match(componentSource, /renderDataV\(\{ type, c, width, height, t \}\)/);
	assert.equal(componentSource.includes("from '../renderers/datav/WaterLevel'"), false);
	assert.equal(componentSource.includes("from '../renderers/datav/DigitalFlop'"), false);
	assert.equal(componentSource.includes("from '../renderers/datav/PercentPond'"), false);
	assert.equal(componentSource.includes("from '../renderers/datav/ScrollRanking'"), false);
	assert.equal(componentSource.includes("from '../renderers/datav/FlylineChart'"), false);

	for (const componentType of [
		"border-box",
		"decoration",
		"scroll-ranking",
		"water-level",
		"digital-flop",
		"percent-pond",
		"flyline-chart",
	]) {
		assert.match(dataVSource, new RegExp(`case '${componentType}'`));
	}
});

test("ComponentRenderer delegates chart style compatibility parsing to shared helpers", async () => {
	const source = await readFile(componentRendererPath, "utf8");

	assert.match(source, /from '\.\.\/renderers\/shared\/chartStyleConfig'/);
	assert.match(source, /resolveAxisStyleConfig\(c\)/);
	assert.match(source, /resolveLegendStyleConfig\(c\)/);
	assert.match(source, /resolveSeriesColors\(c\)/);
	assert.equal(source.includes("const pickBool ="), false);
	assert.equal(source.includes("const pickAxisBound ="), false);
	assert.equal(source.includes("const legendNested ="), false);
});
