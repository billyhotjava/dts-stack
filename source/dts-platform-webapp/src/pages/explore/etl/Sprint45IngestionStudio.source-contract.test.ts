import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const WORKSPACE_SOURCE = readFileSync(new URL("../../foundation/access/AccessWorkspace.tsx", import.meta.url), "utf8");
const WIZARD_SOURCE = readFileSync(new URL("../../foundation/access/AccessPlanWizardPage.tsx", import.meta.url), "utf8");
const PAYLOAD_SOURCE = readFileSync(new URL("../../foundation/access/accessPlanPayload.ts", import.meta.url), "utf8");
const DETAIL_SOURCE = readFileSync(new URL("../../foundation/access/AccessPlanDetailPage.tsx", import.meta.url), "utf8");

test("data access workspace separates database, API and offline-file entry points on one table-first overview", () => {
	for (const label of ["接入概览", "数据库接入", "API 接入", "离线文件接入"]) {
		assert.match(WORKSPACE_SOURCE, new RegExp(label));
	}
	assert.match(WORKSPACE_SOURCE, /<Table<AccessWorkspaceRow>/);
	assert.match(WORKSPACE_SOURCE, /dataSourcesService\.selections\(\)/);
	assert.match(WORKSPACE_SOURCE, /ingestionTaskAPI\.getTasks\(/);
	assert.match(WORKSPACE_SOURCE, /access\/new\?kind=\$\{sourceKind\}/);
	assert.match(WORKSPACE_SOURCE, /access\/\$\{row\.taskId\}/);
	assert.doesNotMatch(WORKSPACE_SOURCE, /<Card|card-list/);
});

test("access wizard uses one three-step shell for database, API and file plans", () => {
	for (const component of ["DatabaseAccessStep", "ApiAccessStep", "FileAccessStep", "LandingScheduleStep"]) {
		assert.match(WIZARD_SOURCE, new RegExp(component));
	}
	for (const step of ["来源连接", "资源定义", "策略准入"]) {
		assert.match(WIZARD_SOURCE, new RegExp(step));
	}
	assert.match(WIZARD_SOURCE, /requireSafeApiResourcePath/);
	assert.match(WIZARD_SOURCE, /连接凭据由平台托管/);
	assert.match(WIZARD_SOURCE, /kind === "file" \? "保存草稿" : "创建任务"/);
	assert.match(WIZARD_SOURCE, /access\/\$\{result\.taskId\}/);
});

test("access payload preserves the established database, API and file runtime contracts", () => {
	assert.match(PAYLOAD_SOURCE, /if \(context\.kind === "api"\) return buildApiRequest\(context\)/);
	assert.match(PAYLOAD_SOURCE, /if \(context\.kind === "file"\) return buildFileRequest\(context\)/);
	assert.match(PAYLOAD_SOURCE, /return buildDatabaseRequest\(context\)/);
	assert.match(PAYLOAD_SOURCE, /source:\s*\{ dataSourceId: sourceDataSourceId, type: readerType, config: readerConfig \}/);
	assert.match(PAYLOAD_SOURCE, /source:\s*\{ dataSourceId: sourceDataSourceId, type: "httpreader", config \}/);
	assert.match(PAYLOAD_SOURCE, /type:\s*"txtfilereader"/);
	assert.match(PAYLOAD_SOURCE, /usePlatformDefault:\s*true/);
});

test("access detail is the operational home for history, admission, execution, DAG rebuild and rollback", () => {
	for (const tab of ["概览", "运行历史", "密级准入", "变更记录"]) {
		assert.match(DETAIL_SOURCE, new RegExp(`label:\\s*"${tab}"`));
	}
	assert.match(DETAIL_SOURCE, /<ExecutionHistoryTable taskId=\{taskId\}/);
	assert.match(DETAIL_SOURCE, /<TaskAdmissionBasis task=\{task\}/);
	assert.match(DETAIL_SOURCE, /runAccessPlanOperation\("admit", operationTaskId\)/);
	assert.match(DETAIL_SOURCE, /runAccessPlanOperation\("execute", operationTaskId\)/);
	assert.match(DETAIL_SOURCE, /runAccessPlanOperation\("rebuildDag", operationTaskId\)/);
	assert.match(DETAIL_SOURCE, /<RollbackImpactModal/);
	assert.match(DETAIL_SOURCE, /access\/new\?kind=\$\{inferAccessKind\(task\)\}&editId=/);
});
