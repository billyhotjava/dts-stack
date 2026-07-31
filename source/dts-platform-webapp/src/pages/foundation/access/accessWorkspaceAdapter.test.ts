import assert from "node:assert/strict";
import test from "node:test";
import type { IngestionTaskDTO } from "../../../api/ingestion.ts";
import type { InfraDataSource } from "../../../api/services/dataSourcesService.ts";
import { parseAccessKind, toAccessWorkspaceRows } from "./accessWorkspaceAdapter.ts";

const sources: InfraDataSource[] = [
	{
		id: "mysql-1",
		name: "人事主数据库",
		type: "mysql",
		connectorName: "MySQL",
		jdbcUrl: "jdbc:mysql://10.20.30.41:3306/hr_prod",
		status: "ACTIVE",
		heartbeatStatus: "UP",
	},
	{
		id: "api-1",
		name: "预算中心 API",
		type: "api",
		connectorName: "HTTP API",
		status: "ACTIVE",
		heartbeatStatus: "UP",
	},
];

const tasks: IngestionTaskDTO[] = [
	{
		id: 101,
		name: "人事主数据入湖",
		sourceType: "mysqlreader",
		sourceDataSourceId: "mysql-1",
		sourceConfig: { selectedTables: ["hr.employee", "hr.department"] },
		syncMode: "incremental",
		status: "active",
		lastExecutionStatus: "success",
		lastExecutedAt: "2026-07-31T09:10:00Z",
		revisionNumber: 3,
		revisionState: "ACTIVE",
		defaultPolicyVersion: 2,
		createdBy: "data.owner",
		classificationSeal: {
			sealId: "seal-1",
			subjectType: "TASK",
			subjectKey: "101",
			effectiveLevel: "INTERNAL",
			snapshotVersion: 1,
			checksum: "sha256:1",
			sealedAt: "2026-07-31T08:00:00Z",
		},
	},
	{
		id: 102,
		name: "预算明细 API 入湖",
		sourceType: "httpreader",
		sourceDataSourceId: "api-1",
		sourceConfig: { method: "GET", resourcePath: "/v1/budget/items" },
		syncMode: "incremental",
		status: "active",
		lastExecutionStatus: "failed",
		createdBy: "finance.owner",
	},
	{
		id: 103,
		name: "月度预算文件",
		sourceType: "txtfilereader",
		sourceConfig: { originalName: "budget-2026-07.xlsx" },
		syncMode: "full_refresh",
		status: "paused",
		createdBy: "finance.owner",
	},
];

test("projects versioned and legacy tasks without fabricating missing revision data", () => {
	const rows = toAccessWorkspaceRows(tasks, sources);

	assert.deepEqual(
		rows.map((row) => row.kind),
		["database", "api", "file"],
	);
	assert.equal(rows[0].sourceName, "人事主数据库");
	assert.equal(rows[0].resourceSummary, "2 张表");
	assert.equal(rows[0].lifecycle, "active");
	assert.equal(rows[0].health, "healthy");
	assert.equal(rows[0].classification, "INTERNAL");
	assert.equal(rows[0].versionState, "versioned");
	assert.equal(rows[0].versionLabel, "R3");
	assert.equal(rows[0].versionHint, "ACTIVE · 策略 v2");
	assert.equal(rows[1].versionState, "legacy-unversioned");
	assert.equal(rows[1].versionLabel, "未版本化");
	assert.equal(rows[1].versionHint, "存量任务待后台迁移");
	assert.equal(rows[1].resourceSummary, "GET /v1/budget/items");
	assert.equal(rows[1].health, "attention");
	assert.equal(rows[2].resourceSummary, "budget-2026-07.xlsx");
	assert.equal(rows[2].health, "not_evaluated");
	assert.equal("qualityResult" in rows[0], false);
});

test("connection failure wins over a successful historical run", () => {
	const rows = toAccessWorkspaceRows(tasks.slice(0, 1), [
		{ ...sources[0], heartbeatStatus: "DOWN", lastError: "connection refused" },
	]);

	assert.equal(rows[0].health, "attention");
	assert.equal(rows[0].healthReason, "数据源心跳异常");
});

test("accepts only supported kind query values", () => {
	assert.equal(parseAccessKind("database"), "database");
	assert.equal(parseAccessKind("API"), "api");
	assert.equal(parseAccessKind("file"), "file");
	assert.equal(parseAccessKind("files"), "file");
	assert.equal(parseAccessKind("unknown"), "overview");
	assert.equal(parseAccessKind(null), "overview");
});
