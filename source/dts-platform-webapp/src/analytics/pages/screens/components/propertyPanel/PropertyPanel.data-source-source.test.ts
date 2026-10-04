import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const propertyPanelPath = new URL("./PropertyPanel.tsx", import.meta.url);
const dataSourceConfigSectionPath = new URL("./DataSourceConfigSection.tsx", import.meta.url);
const dataBindingWorkflowSectionPath = new URL("./DataBindingWorkflowSection.tsx", import.meta.url);

test("PropertyPanel delegates data source editing to the extracted module", async () => {
	const [propertyPanelSource, dataSourceConfigSource, dataBindingWorkflowSource] = await Promise.all([
		readFile(propertyPanelPath, "utf8"),
		readFile(dataSourceConfigSectionPath, "utf8"),
		readFile(dataBindingWorkflowSectionPath, "utf8"),
	]);

	assert.match(propertyPanelSource, /from '\.\/DataSourceConfigSection'/);
	assert.equal(propertyPanelSource.includes("function renderDataSourceConfig("), false);
	assert.match(dataSourceConfigSource, /export function renderDataSourceConfig/);
	assert.match(dataSourceConfigSource, /Metric 语义模式/);
	assert.match(dataSourceConfigSource, /从 SQL 提取参数/);
	assert.match(propertyPanelSource, /from '\.\/DataBindingWorkflowSection'/);
	assert.match(propertyPanelSource, /<DataBindingWorkflowSection/);
	assert.ok(
		propertyPanelSource.indexOf("<DataBindingWorkflowSection") < propertyPanelSource.indexOf("renderDataSourceConfig("),
		"数据配置流程应出现在数据源编辑之前",
	);
	assert.match(dataBindingWorkflowSource, /数据配置流程/);
	assert.match(dataBindingWorkflowSource, /detectStaleFields/);
	assert.match(dataBindingWorkflowSource, /staleFieldCount/);
	assert.equal(dataBindingWorkflowSource.includes("useCardDataSource"), false);
});
