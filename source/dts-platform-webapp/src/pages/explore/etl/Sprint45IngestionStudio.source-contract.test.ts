import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const DATA_SOURCES_SOURCE = readFileSync(new URL("../../foundation/DataSourcesPage.tsx", import.meta.url), "utf8");
const CONNECTORS_SOURCE = readFileSync(new URL("../../foundation/ConnectorRegistryPage.tsx", import.meta.url), "utf8");
const JDBC_SOURCE = readFileSync(new URL("../../foundation/JdbcDriversPage.tsx", import.meta.url), "utf8");
const TRANSFORM_SOURCE = readFileSync(new URL("./TransformPage.tsx", import.meta.url), "utf8");
const ORCHESTRATION_SOURCE = readFileSync(new URL("./OrchestrationPage.tsx", import.meta.url), "utf8");
const PROJECTS_SOURCE = readFileSync(new URL("../../modeling/ModelTemplatesPage.tsx", import.meta.url), "utf8");
const SQL_MODELING_SOURCE = readFileSync(new URL("../../modeling/SqlModelingPage.tsx", import.meta.url), "utf8");
const DBT_SOURCE = readFileSync(new URL("../../modeling/DbtFileBrowserPage.tsx", import.meta.url), "utf8");

test("Sprint-45 data source entry exposes connection, schema, ODS, task, and golden-chain actions", () => {
	for (const action of ["新建数据源", "测试连接", "Schema 探测", "生成 ODS 映射", "预览 ODS", "生成同步任务", "查看黄金链路"]) {
		assert.match(DATA_SOURCES_SOURCE, new RegExp(action));
	}
	for (const api of ["dataSourcesService.test", "dataSourcesService.schemaDiscover", "dataSourcesService.odsPreview", "dataSourcesService.odsPrecheck", "createIngestionTask"]) {
		assert.match(DATA_SOURCES_SOURCE, new RegExp(api.replace(".", "\\.")));
	}
});

test("Sprint-45 connector and driver pages behave as access assets instead of isolated admin tables", () => {
	for (const action of ["连接器目录", "创建数据源", "配置要求", "同步内置", "驱动就绪", "缺少驱动"]) {
		assert.match(CONNECTORS_SOURCE, new RegExp(action));
	}
	for (const action of ["JDBC 驱动库", "上传驱动", "校验", "启用", "禁用", "删除", "查看详情"]) {
		assert.match(JDBC_SOURCE, new RegExp(action));
	}
	assert.doesNotMatch(CONNECTORS_SOURCE, />启用</);
	assert.doesNotMatch(CONNECTORS_SOURCE, />停用</);
	assert.doesNotMatch(CONNECTORS_SOURCE, /查看模板/);
	assert.match(CONNECTORS_SOURCE, /PageHeader/);
	assert.match(JDBC_SOURCE, /PageHeader/);
});

test("Sprint-45 ETL transform and orchestration pages connect runtime actions back to ops", () => {
	for (const action of ["创建入湖任务", "运行", "停止", "重跑", "补数", "查看日志", "查看实例", "查看运维"]) {
		assert.match(TRANSFORM_SOURCE, new RegExp(action));
	}
	for (const route of ["/ops/instances", "/ops/backfill"]) {
		assert.match(TRANSFORM_SOURCE, new RegExp(route));
	}
	for (const action of ["新建 DAG", "启用调度", "暂停", "补数", "查看告警", "运行实例"]) {
		assert.match(ORCHESTRATION_SOURCE, new RegExp(action));
	}
	assert.match(ORCHESTRATION_SOURCE, /PageHeader/);
});

test("ETL task page consolidates filters and actions into one task toolbar", () => {
	assert.equal((TRANSFORM_SOURCE.match(/>\s*创建入湖任务\s*</g) || []).length, 1);
	assert.doesNotMatch(TRANSFORM_SOURCE, /title="入湖任务中心"/);
	assert.doesNotMatch(TRANSFORM_SOURCE, />\s*新建转换\s*</);
	assert.match(TRANSFORM_SOURCE, /全部（不含已删除）/);
	assert.match(TRANSFORM_SOURCE, /data-testid="platform-transform-status-filter"/);
	assert.match(TRANSFORM_SOURCE, /data-testid="platform-transform-refresh"/);
	assert.match(TRANSFORM_SOURCE, /data-testid="platform-transform-create"/);
});

test("Sprint-45 Studio surfaces project, SQL modeling, dbt, and release-gate handoffs", () => {
	for (const action of ["新建项目", "导入项目", "进入 SQL 建模", "归档", "发布"]) {
		assert.match(PROJECTS_SOURCE, new RegExp(action));
	}
	for (const action of ["新建模型", "导入模型", "加载预览", "生成 ODS", "发布门禁", "发布到 Analytics"]) {
		assert.match(SQL_MODELING_SOURCE, new RegExp(action));
	}
	for (const action of ["导入", "校验", "预览", "发布", "编译", "测试"]) {
		assert.match(DBT_SOURCE, new RegExp(action));
	}
});
