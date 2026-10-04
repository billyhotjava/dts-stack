import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const propertyPanelPath = new URL("./PropertyPanel.tsx", import.meta.url);
const pluginSchemaFieldsSectionPath = new URL("./PluginSchemaFieldsSection.tsx", import.meta.url);

test("PropertyPanel delegates plugin schema fields to the extracted module", async () => {
	const [propertyPanelSource, pluginSectionSource] = await Promise.all([
		readFile(propertyPanelPath, "utf8"),
		readFile(pluginSchemaFieldsSectionPath, "utf8"),
	]);

	assert.match(propertyPanelSource, /from '\.\/PluginSchemaFieldsSection'/);
	assert.equal(propertyPanelSource.includes("function renderPluginSchemaFields("), false);
	assert.match(pluginSectionSource, /export function renderPluginSchemaFields/);
	assert.match(pluginSectionSource, /valueSourceMode/);
	assert.match(pluginSectionSource, /finance-kit/);
});
