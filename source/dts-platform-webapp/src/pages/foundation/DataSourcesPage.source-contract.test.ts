import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const CONNECTOR_REGISTRY_SOURCE = readFileSync(new URL("./ConnectorRegistryPage.tsx", import.meta.url), "utf8");
const DATA_SOURCES_SOURCE = readFileSync(new URL("./DataSourcesPage.tsx", import.meta.url), "utf8");
const DATA_SOURCE_DETAIL_SOURCE = readFileSync(new URL("./DataSourceDetailPage.tsx", import.meta.url), "utf8");
const DATA_SOURCE_FORM_SOURCE = readFileSync(new URL("./DataSourceFormModal.tsx", import.meta.url), "utf8");
const DRIVER_NOTICE_SOURCE = readFileSync(new URL("./components/ConnectorDriverNotice.tsx", import.meta.url), "utf8");
const JDBC_DRIVERS_SOURCE = readFileSync(new URL("./JdbcDriversPage.tsx", import.meta.url), "utf8");
const INGESTION_API_SOURCE = readFileSync(new URL("../../api/ingestion.ts", import.meta.url), "utf8");

test("connector registry carries connector context into data source creation", () => {
	assert.match(CONNECTOR_REGISTRY_SOURCE, /const openDataSourceCreate = useCallback/);
	assert.match(
		CONNECTOR_REGISTRY_SOURCE,
		/`\/foundation\/data-sources\?create=1&connectorKey=\$\{encodeURIComponent\(connectorKey\)\}`/,
	);
	assert.match(CONNECTOR_REGISTRY_SOURCE, /openDataSourceCreate\(record\.connectorKey\)/);
	assert.match(CONNECTOR_REGISTRY_SOURCE, /openDataSourceCreate\(selected\.connectorKey\)/);
});

test("data sources page consumes create query once and passes initial connector to modal", () => {
	assert.match(DATA_SOURCES_SOURCE, /useSearchParams/);
	assert.match(DATA_SOURCES_SOURCE, /searchParams\.get\("create"\) === "1"/);
	assert.match(DATA_SOURCES_SOURCE, /searchParams\.get\("connectorKey"\) \|\| undefined/);
	assert.match(DATA_SOURCES_SOURCE, /setSearchParams\(next,\s*\{\s*replace:\s*true\s*\}\)/);
	assert.match(DATA_SOURCES_SOURCE, /initialConnectorKey=\{initialConnectorKey\}/);
});

test("data source form applies the initial connector and its defaults", () => {
	assert.match(DATA_SOURCE_FORM_SOURCE, /initialConnectorKey\?: string/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /form\.setFieldsValue\(\{\s*connectorKey: initialConnectorKey\s*\}\)/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /applyConnectorDefaults\(initialConnectorKey\)/);
});

test("data source form derives source type and defaults from connector registry", () => {
	assert.match(DATA_SOURCE_FORM_SOURCE, /connectorsService\.list\(\)/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /return connectors\.map\(\(connector\) => \(\{/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /const nextType = connector\.sourceType/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /const defaults = asRecord\(connector\.configSchema\?\.defaults\)/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /typeof defaults\?\.driverClass === "string"/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /tooltip="源类型由连接器目录统一维护，选择连接器后自动填充"/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /<Input disabled placeholder="选择连接器后自动填充" \/>/);
	assert.doesNotMatch(DATA_SOURCE_FORM_SOURCE, /dictionaryService|systemTypesFallbackActive|TYPE_OPTIONS/);
	assert.doesNotMatch(DATA_SOURCE_FORM_SOURCE, /系统类型字典未接通|去参考码维护/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /message="连接器目录不可用"/);
	assert.match(DATA_SOURCE_FORM_SOURCE, />\s*重新加载\s*</);
});

test("data source form uses connector-owned drivers and keeps upload selection for generic JDBC only", () => {
	assert.match(DATA_SOURCE_FORM_SOURCE, /connector\.driver\?\.driverClass/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /connector\.driver\?\.fileName/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /connectorUsesCustomDriver/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /connectorDriverBlocked/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /drivers\s*\.filter\(\(driver\) => !driver\.missing\)/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /<ConnectorDriverNotice/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /onManageDrivers=\{\(\) => router\.push\("\/foundation\/jdbc-drivers"\)\}/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /jdbcRequired && connectorUsesCustomDriver/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /okButtonProps=\{\{\s*disabled:\s*connectorDriverBlocked/);
	assert.match(DRIVER_NOTICE_SOURCE, /连接器驱动已就绪/);
	assert.match(DRIVER_NOTICE_SOURCE, /连接器驱动未就绪/);
	assert.match(DRIVER_NOTICE_SOURCE, /通用 JDBC 驱动/);
});

test("JDBC driver library is presented as a maintainer exception instead of a standard setup step", () => {
	assert.match(JDBC_DRIVERS_SOURCE, /标准连接器无需手动上传驱动/);
	assert.match(JDBC_DRIVERS_SOURCE, /基础设施维护人员/);
	assert.match(JDBC_DRIVERS_SOURCE, /通用 JDBC/);
	assert.match(JDBC_DRIVERS_SOURCE, /厂商授权驱动/);
	assert.match(JDBC_DRIVERS_SOURCE, /版本兼容/);
});

test("api data source form keeps auth providers aligned with runtime contract", () => {
	assert.match(INGESTION_API_SOURCE, /enabled\?:\s*boolean/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /disabled:\s*item\.enabled === false/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /即将支持/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /selectedApiDescriptor\?\.enabled === false/);
});

test("api data source form emits engine-compatible auth references", () => {
	assert.match(DATA_SOURCE_FORM_SOURCE, /buildApiAuthRefName/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /authRefConfig\[buildApiAuthRefName\(field\.name\)\]\s*=\s*secretRef/);
	assert.doesNotMatch(DATA_SOURCE_FORM_SOURCE, /\.\.\.\(authConfig \? \{ config: authConfig \} : \{\}\)/);
});

test("api data source form exposes the plaintext HTTP policy explicitly", () => {
	assert.match(DATA_SOURCE_FORM_SOURCE, /name="apiAllowHttp"/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /valuePropName="checked"/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /allowHttp/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /apiAllowHttp:\s*apiSource\s*\?/);
});

test("api connection test submits the complete source config", () => {
	assert.match(DATA_SOURCE_FORM_SOURCE, /sourceConfig:\s*buildApiProps\(/);
	assert.doesNotMatch(
		DATA_SOURCE_FORM_SOURCE,
		/sourceConfig:\s*asRecord\(\s*[\s\S]*?buildApiProps[\s\S]*?\.readerConfig/,
	);
});

test("data source rows can start the end-to-end journey with source context", () => {
	assert.match(DATA_SOURCES_SOURCE, /e2e-data-product/);
	assert.match(DATA_SOURCES_SOURCE, /sourceId/);
	assert.match(DATA_SOURCES_SOURCE, /开始数据产品旅程|进入数仓规划/);
});

test("data source list and detail pages do not expose full-chain rollback", () => {
	for (const source of [DATA_SOURCES_SOURCE, DATA_SOURCE_DETAIL_SOURCE]) {
		assert.doesNotMatch(source, /全链路回退/);
		assert.doesNotMatch(source, /RollbackImpactModal/);
		assert.doesNotMatch(source, /rollbackAnalyze/);
		assert.doesNotMatch(source, /rollbackExecute/);
	}
});
