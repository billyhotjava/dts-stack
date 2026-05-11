import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const propertyPanelPath = new URL("./PropertyPanel.tsx", import.meta.url);
const positionSectionPath = new URL("./PositionSizeSection.tsx", import.meta.url);

test("PropertyPanel delegates position and size config to the extracted module", async () => {
	const [propertyPanelSource, positionSource] = await Promise.all([
		readFile(propertyPanelPath, "utf8"),
		readFile(positionSectionPath, "utf8"),
	]);

	assert.match(propertyPanelSource, /from '\.\/PositionSizeSection'/);
	assert.equal(propertyPanelSource.includes("handleChange('width'"), false);
	assert.equal(propertyPanelSource.includes("handleChange('height'"), false);
	assert.match(positionSource, /export function renderPositionSizeConfig/);
	assert.match(positionSource, /位置与尺寸/);
	assert.match(positionSource, /parseIntegerInput/);
	assert.match(positionSource, /Math\.round/);
	assert.match(positionSource, /step=\{1\}/);
	assert.match(positionSource, /handleChange\('width'/);
	assert.match(positionSource, /handleChange\('height'/);
});
