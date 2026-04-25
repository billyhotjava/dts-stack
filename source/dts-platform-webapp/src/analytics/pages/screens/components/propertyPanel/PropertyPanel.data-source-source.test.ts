import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const propertyPanelPath = new URL("./PropertyPanel.tsx", import.meta.url);
const dataSourceConfigSectionPath = new URL("./DataSourceConfigSection.tsx", import.meta.url);

test("PropertyPanel delegates data source editing to the extracted module", async () => {
	const [propertyPanelSource, dataSourceConfigSource] = await Promise.all([
		readFile(propertyPanelPath, "utf8"),
		readFile(dataSourceConfigSectionPath, "utf8"),
	]);

	assert.match(propertyPanelSource, /from '\.\/DataSourceConfigSection'/);
	assert.equal(propertyPanelSource.includes("function renderDataSourceConfig("), false);
	assert.match(dataSourceConfigSource, /export function renderDataSourceConfig/);
	assert.match(dataSourceConfigSource, /Metric 语义模式/);
	assert.match(dataSourceConfigSource, /从 SQL 提取参数/);
});
