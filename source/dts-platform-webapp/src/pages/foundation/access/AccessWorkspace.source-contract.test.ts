import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SOURCE = readFileSync(new URL("./AccessWorkspace.tsx", import.meta.url), "utf8");

test("access workspace reuses aggregate list contracts and never requests latest execution per row", () => {
	assert.match(SOURCE, /dataSourcesService\.selections\(\{ capability: "INGESTION_SOURCE" \}\)/);
	assert.doesNotMatch(SOURCE, /dataSourcesService\.list\(\)/);
	assert.match(SOURCE, /ingestionTaskAPI\.getTasks\(/);
	assert.doesNotMatch(SOURCE, /getLatestExecution|getExecutions\(/);
});

test("access workspace ignores superseded and unmounted requests without exposing backend errors", () => {
	assert.match(SOURCE, /const requestId = \+\+loadRequestIdRef\.current/);
	assert.match(SOURCE, /loadRequestIdRef\.current !== requestId/);
	assert.match(SOURCE, /loadRequestIdRef\.current \+= 1/);
	assert.match(SOURCE, /setError\("数据接入概览加载失败"\)/);
	assert.doesNotMatch(SOURCE, /loadError instanceof Error|loadError\.message/);
});

test("access workspace delegates all filters to one bounded server page", () => {
	assert.match(SOURCE, /page:\s*pagination\.current\s*-\s*1/);
	assert.match(SOURCE, /size:\s*pagination\.pageSize/);
	assert.match(SOURCE, /total:\s*pagination\.total/);
	assert.doesNotMatch(SOURCE, /loadAllTasks|totalPages|for\s*\(let page/);
	assert.match(SOURCE, /sourceKind:\s*kind === "overview" \? undefined : kind/);
	assert.match(SOURCE, /query:\s*query\.trim\(\) \|\| undefined/);
	assert.match(SOURCE, /health:\s*health === "all" \? undefined : health/);
	assert.match(SOURCE, /dataSource=\{rows\}/);
	assert.doesNotMatch(SOURCE, /filterAccessWorkspaceRows/);
	assert.doesNotMatch(SOURCE, /当前页有界筛选|不会继续扫描后续页/);
});

test("access workspace is a table-first page with ten rows by default", () => {
	assert.match(SOURCE, /<Table<AccessWorkspaceRow>/);
	assert.match(SOURCE, /pageSize:\s*10/);
	assert.doesNotMatch(SOURCE, /Card|card-list|grid-template-columns/);
});

test("access workspace keeps classification separate from lifecycle and health tags", () => {
	assert.match(SOURCE, /<ClassificationTag/);
	assert.match(SOURCE, /renderLifecycleTag/);
	assert.match(SOURCE, /renderHealthTag/);
});

test("access workspace exposes the agreed creation and detail navigation", () => {
	assert.match(SOURCE, /`\/foundation\/data-sources\/access\/new\?kind=\$\{sourceKind\}`/);
	assert.match(SOURCE, /`\/foundation\/data-sources\/access\/\$\{row\.taskId\}`/);
});

test("access kind navigation belongs to the left tree rather than duplicate page controls", () => {
	assert.doesNotMatch(SOURCE, /KIND_OPTIONS|kindNav|value=\{kind\}/);
	assert.match(SOURCE, /搜索任务名称、描述、接入类型或负责人/);
	assert.match(SOURCE, /全部生命周期/);
	assert.match(SOURCE, /全部健康状态/);
});

test("access workspace shows real revision data while keeping an honest legacy fallback", () => {
	assert.match(SOURCE, /存量任务待后台迁移/);
	assert.match(SOURCE, /未版本化/);
	assert.match(SOURCE, /versionLabel/);
	assert.doesNotMatch(SOURCE, /质量通过|隔离成功/);
});
