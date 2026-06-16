import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const PAGE_SOURCE = readFileSync(new URL("./DataSourcesPage.tsx", import.meta.url), "utf8");

test("Sprint-45 data sources page connects source setup to ingestion and golden chain workbench", () => {
	const expectedActions = [
		"新建数据源",
		"测试连接",
		"Schema 探测",
		"生成 ODS 映射",
		"预览 ODS",
		"生成同步任务",
		"查看黄金链路",
	];

	for (const action of expectedActions) {
		assert.match(PAGE_SOURCE, new RegExp(action));
	}

	assert.match(PAGE_SOURCE, /createIngestionTask/);
	assert.match(PAGE_SOURCE, /dataSourcesService\.test/);
	assert.match(PAGE_SOURCE, /dataSourcesService\.schemaDiscover/);
	assert.match(PAGE_SOURCE, /dataSourcesService\.odsPreview/);
	assert.match(PAGE_SOURCE, /dataSourcesService\.odsPrecheck/);
	assert.match(PAGE_SOURCE, /"\/workbench\?section=data-management"/);
});
