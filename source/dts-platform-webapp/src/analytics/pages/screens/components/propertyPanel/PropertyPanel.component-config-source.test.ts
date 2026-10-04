import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const propertyPanelPath = new URL("./PropertyPanel.tsx", import.meta.url);
const componentConfigSectionPath = new URL("./ComponentConfigSection.tsx", import.meta.url);

test("PropertyPanel delegates schema-driven component config to the extracted module", async () => {
	const [propertyPanelSource, componentConfigSource] = await Promise.all([
		readFile(propertyPanelPath, "utf8"),
		readFile(componentConfigSectionPath, "utf8"),
	]);

	assert.match(propertyPanelSource, /from '\.\/ComponentConfigSection'/);
	assert.equal(propertyPanelSource.includes("SchemaConfigRenderer"), false);
	assert.equal(propertyPanelSource.includes("COMPONENT_CONFIG_SCHEMAS"), false);
	assert.equal(propertyPanelSource.includes("setByPath"), false);

	assert.match(componentConfigSource, /export function renderComponentConfigSection/);
	assert.match(componentConfigSource, /SchemaConfigRenderer/);
	assert.match(componentConfigSource, /COMPONENT_CONFIG_SCHEMAS/);
	assert.match(componentConfigSource, /setByPath/);
});
