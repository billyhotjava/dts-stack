import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const propertyPanelPath = new URL("./PropertyPanel.tsx", import.meta.url);
const fieldMappingSectionPath = new URL("./FieldMappingSection.tsx", import.meta.url);

test("PropertyPanel delegates field mapping config to the extracted module", async () => {
	const [propertyPanelSource, fieldMappingSource] = await Promise.all([
		readFile(propertyPanelPath, "utf8"),
		readFile(fieldMappingSectionPath, "utf8"),
	]);

	assert.match(propertyPanelSource, /from '\.\/FieldMappingSection'/);
	assert.equal(propertyPanelSource.includes("FieldMappingPanel"), false);
	assert.equal(propertyPanelSource.includes("isMappable"), false);
	assert.equal(propertyPanelSource.includes("_fieldMapping"), false);

	assert.match(fieldMappingSource, /export function renderFieldMappingConfig/);
	assert.match(fieldMappingSource, /FieldMappingPanel/);
	assert.match(fieldMappingSource, /isMappable/);
	assert.match(fieldMappingSource, /_fieldMapping/);
});
