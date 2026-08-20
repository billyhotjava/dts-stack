import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const PAGE_SOURCE = readFileSync(new URL("./DataPage.tsx", import.meta.url), "utf8");
const API_SOURCE = readFileSync(new URL("../../api/sql-workbench.ts", import.meta.url), "utf8");

test("BI data page consumes only immutable published Query Dataset projections", () => {
	assert.match(API_SOURCE, /\/sql\/query-datasets\/published/);
	assert.match(PAGE_SOURCE, /listPublishedQueryDatasets/);
	assert.doesNotMatch(PAGE_SOURCE, /listPlatformDataSources|listDatabases|jdbcUrl|dbId=/);
	assert.doesNotMatch(PAGE_SOURCE, /baseSql|sqlText/);
});

test("create analysis navigation pins dataset version and checksum", () => {
	assert.match(PAGE_SOURCE, /\/bi\/questions\/new\?datasetId=/);
	assert.match(PAGE_SOURCE, /version=/);
	assert.match(PAGE_SOURCE, /checksum=/);
});

test("published dataset catalog keeps the frozen pagination and four-state UX contract", () => {
	assert.match(PAGE_SOURCE, /DEFAULT_PAGE_SIZE\s*=\s*10/);
	for (const state of ["加载已发布数据集", "暂无已发布数据集", "没有符合筛选条件的数据集", "重新加载"]) {
		assert.match(PAGE_SOURCE, new RegExp(state));
	}
	assert.match(PAGE_SOURCE, /actionColumn<AnalysisDatasetSummary>/);
	assert.doesNotMatch(PAGE_SOURCE, /type="link"/);
});
