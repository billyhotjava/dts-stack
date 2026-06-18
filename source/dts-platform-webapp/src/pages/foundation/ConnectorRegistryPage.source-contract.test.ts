import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const PAGE_SOURCE = readFileSync(new URL("./ConnectorRegistryPage.tsx", import.meta.url), "utf8");
const GLOBAL_CSS = readFileSync(new URL("../../global.css", import.meta.url), "utf8");

test("connector registry table keeps capability tags readable in fixed table layout", () => {
	assert.match(PAGE_SOURCE, /function CapabilityTags/);
	assert.match(PAGE_SOURCE, /CAPABILITY_COLUMN_WIDTH\s*=\s*160/);
	assert.match(PAGE_SOURCE, /CONNECTOR_TABLE_SCROLL_X\s*=\s*1240/);
	assert.match(PAGE_SOURCE, /compact\s*\?\s*keys\.slice\(0,\s*2\)/);
	assert.match(PAGE_SOURCE, /title:\s*"能力"[\s\S]*width:\s*CAPABILITY_COLUMN_WIDTH/);
	assert.match(PAGE_SOURCE, /connector-registry-capability-tags max-w-full min-w-0/);
	assert.match(PAGE_SOURCE, /scroll=\{\{\s*x:\s*CONNECTOR_TABLE_SCROLL_X\s*\}\}/);
});

test("connector registry action column stays stable for Chrome95 table rendering", () => {
	assert.match(PAGE_SOURCE, /CONNECTOR_ACTION_COLUMN_WIDTH\s*=\s*360/);
	assert.match(PAGE_SOURCE, /title:\s*"操作"[\s\S]*width:\s*CONNECTOR_ACTION_COLUMN_WIDTH[\s\S]*fixed:\s*"right"/);
	assert.match(PAGE_SOURCE, /className="connector-registry-actions"/);
	assert.match(PAGE_SOURCE, /className="connector-registry-table"/);
	assert.match(PAGE_SOURCE, /tableLayout="fixed"/);
	assert.match(
		GLOBAL_CSS,
		/\.connector-registry-capability-tags\s*\{[\s\S]*white-space:\s*nowrap;[\s\S]*overflow:\s*hidden;/,
	);
	assert.match(GLOBAL_CSS, /\.connector-registry-actions\s*\{[\s\S]*white-space:\s*nowrap;[\s\S]*flex-wrap:\s*nowrap;/);
});

test("connector registry action buttons open explicit drawer workflows", () => {
	assert.match(PAGE_SOURCE, /type ConnectorDrawerMode\s*=\s*"detail"\s*\|\s*"config"\s*\|\s*"template"/);
	assert.match(PAGE_SOURCE, /const \[drawerMode,\s*setDrawerMode\]\s*=\s*useState<ConnectorDrawerMode>\("detail"\)/);
	assert.match(
		PAGE_SOURCE,
		/const openConnectorDrawer = useCallback\(\(connector: InfraConnector,\s*mode: ConnectorDrawerMode\) => \{/,
	);
	assert.match(PAGE_SOURCE, /onClick=\{\(\) => openConnectorDrawer\(record,\s*"detail"\)\}/);
	assert.match(PAGE_SOURCE, /onClick=\{\(\) => openConnectorDrawer\(record,\s*"config"\)\}/);
	assert.match(PAGE_SOURCE, /onClick=\{\(\) => openConnectorDrawer\(record,\s*"template"\)\}/);
	assert.match(PAGE_SOURCE, /drawerMode === "config"/);
	assert.match(PAGE_SOURCE, /drawerMode === "template"/);
	assert.match(PAGE_SOURCE, /<Tooltip title="当前连接器目录接口未开放启用动作/);
	assert.match(PAGE_SOURCE, /<Tooltip title="当前连接器目录接口未开放停用动作/);
	assert.match(PAGE_SOURCE, /footer=\{/);
	assert.match(PAGE_SOURCE, /openDataSourceCreate\(selected\.connectorKey\)/);
});
