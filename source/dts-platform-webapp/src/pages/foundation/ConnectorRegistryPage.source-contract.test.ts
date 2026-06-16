import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const PAGE_SOURCE = readFileSync(new URL("./ConnectorRegistryPage.tsx", import.meta.url), "utf8");

test("connector registry table keeps capability tags readable in fixed table layout", () => {
	assert.match(PAGE_SOURCE, /function CapabilityTags/);
	assert.match(PAGE_SOURCE, /CAPABILITY_COLUMN_WIDTH\s*=\s*240/);
	assert.match(PAGE_SOURCE, /CONNECTOR_TABLE_SCROLL_X\s*=\s*1360/);
	assert.match(PAGE_SOURCE, /title:\s*"能力"[\s\S]*width:\s*CAPABILITY_COLUMN_WIDTH/);
	assert.match(PAGE_SOURCE, /className="whitespace-nowrap"/);
	assert.match(PAGE_SOURCE, /scroll=\{\{\s*x:\s*CONNECTOR_TABLE_SCROLL_X\s*\}\}/);
});
