import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const WORKSPACE_SOURCE = readFileSync(new URL("./AccessWorkspace.tsx", import.meta.url), "utf8");
const WIZARD_SOURCE = readFileSync(new URL("./AccessPlanWizardPage.tsx", import.meta.url), "utf8");
const PAYLOAD_SOURCE = readFileSync(new URL("./accessPlanPayload.ts", import.meta.url), "utf8");
const DETAIL_SOURCE = readFileSync(new URL("./AccessPlanDetailPage.tsx", import.meta.url), "utf8");

test("data access workspace separates database, API and offline-file entry points on one table-first overview", () => {
	for (const label of ["接入概览", "数据库接入", "API 接入", "离线文件接入"]) {
		assert.match(WORKSPACE_SOURCE, new RegExp(label));
	}
	assert.match(WORKSPACE_SOURCE, /CompactTable<AccessWorkspaceRow>/);
	assert.match(WORKSPACE_SOURCE, /dataSourcesService\.selections\(\{ capability: "INGESTION_SOURCE" \}\)/);
	assert.match(WORKSPACE_SOURCE, /ingestionTaskAPI\.getTasks\(/);
	assert.match(WORKSPACE_SOURCE, /access\/new\?kind=\$\{sourceKind\}/);
	assert.match(WORKSPACE_SOURCE, /access\/\$\{row\.taskId\}/);
	assert.match(WORKSPACE_SOURCE, /普通流程/);
	assert.doesNotMatch(WORKSPACE_SOURCE, /<Card|card-list/);
	assert.doesNotMatch(WORKSPACE_SOURCE, /Revision|未版本化|versionLabel|versionHint/);
});

test("access overview uses one explicit create chooser and keeps recent execution visible at common desktop widths", () => {
	assert.match(
		WORKSPACE_SOURCE,
		/const OVERVIEW_CREATE_ITEMS:[\s\S]*?key: "database"[\s\S]*?key: "api"[\s\S]*?key: "file"/,
	);
	assert.match(
		WORKSPACE_SOURCE,
		/<Dropdown[\s\S]*?items: OVERVIEW_CREATE_ITEMS[\s\S]*?openCreate\(key as AccessSourceKind\)[\s\S]*?新建接入[\s\S]*?<\/Dropdown>/,
	);
	assert.match(WORKSPACE_SOURCE, /title: "最近运行"[\s\S]*?width: 145/);
	assert.match(WORKSPACE_SOURCE, /title: "负责人 \/ 密级"[\s\S]*?responsive: \["xxl"\]/);
});

test("access wizard uses one three-step shell for database, API and file plans", () => {
	for (const component of ["DatabaseAccessStep", "ApiAccessStep", "FileAccessStep", "LandingScheduleStep"]) {
		assert.match(WIZARD_SOURCE, new RegExp(component));
	}
	for (const step of ["来源连接", "资源定义", "落地策略"]) {
		assert.match(WIZARD_SOURCE, new RegExp(step));
	}
	assert.match(WIZARD_SOURCE, /requireSafeApiResourcePath/);
	assert.match(WIZARD_SOURCE, /连接凭据由平台托管/);
	assert.match(WIZARD_SOURCE, /"保存修改并生效"/);
	assert.match(WIZARD_SOURCE, /"保存并生效"/);
	assert.doesNotMatch(WIZARD_SOURCE, /保存修改为草稿|保存草稿/);
	assert.doesNotMatch(WIZARD_SOURCE, /创建任务/);
	assert.match(WIZARD_SOURCE, /access\/\$\{result\.taskId\}/);
});

test("access payload preserves the established database, API and file runtime contracts", () => {
	assert.match(PAYLOAD_SOURCE, /if \(context\.kind === "api"\) return buildApiRequest\(context\)/);
	assert.match(PAYLOAD_SOURCE, /if \(context\.kind === "file"\) return buildFileRequest\(context\)/);
	assert.match(PAYLOAD_SOURCE, /return buildDatabaseRequest\(context\)/);
	assert.match(
		PAYLOAD_SOURCE,
		/source:\s*\{ dataSourceId: sourceDataSourceId, type: readerType, config: readerConfig \}/,
	);
	assert.match(PAYLOAD_SOURCE, /source:\s*\{ dataSourceId: sourceDataSourceId, type: "httpreader", config \}/);
	assert.match(PAYLOAD_SOURCE, /type:\s*"txtfilereader"/);
	assert.match(PAYLOAD_SOURCE, /usePlatformDefault:\s*true/);
});

test("access detail is the operational home for history, execution and evidence-preserving deletion", () => {
	for (const tab of ["概览", "运行历史", "变更记录"]) {
		assert.match(DETAIL_SOURCE, new RegExp(`label:\\s*"${tab}"`));
	}
	assert.match(DETAIL_SOURCE, /<ExecutionHistoryTable taskId=\{taskId\}/);
	assert.doesNotMatch(DETAIL_SOURCE, /TaskAdmissionBasis|runAccessPlanOperation\("admit"|密级准入|准入草稿/);
	assert.match(DETAIL_SOURCE, /runAccessPlanOperation\("execute", operationTaskId, ingestionTaskAPI\)/);
	assert.match(DETAIL_SOURCE, /runAccessPlanOperation\("delete", operationTaskId, ingestionTaskAPI\)/);
	assert.match(DETAIL_SOURCE, /删除计划/);
	assert.doesNotMatch(DETAIL_SOURCE, /更多操作|重建 DAG|数据回退 Level|RollbackImpactModal|rebuildDag/);
	assert.match(DETAIL_SOURCE, /access\/new\?kind=\$\{inferAccessKind\(task\)\}&editId=/);
});
