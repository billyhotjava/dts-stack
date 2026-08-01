import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const CONNECTOR_REGISTRY_SOURCE = readFileSync(new URL("../ConnectorRegistryPage.tsx", import.meta.url), "utf8");
const CONNECTION_PROFILES_SOURCE = readFileSync(new URL("./ConnectionProfilesPage.tsx", import.meta.url), "utf8");
const CONNECTION_PROFILE_DETAIL_SOURCE = readFileSync(new URL("./ConnectionProfileDetailPage.tsx", import.meta.url), "utf8");
const CONNECTION_PROFILE_FORM_SOURCE = readFileSync(new URL("./ConnectionProfileFormModal.tsx", import.meta.url), "utf8");
const DRIVER_NOTICE_SOURCE = readFileSync(new URL("../components/ConnectorDriverNotice.tsx", import.meta.url), "utf8");
const JDBC_DRIVERS_SOURCE = readFileSync(new URL("../JdbcDriversPage.tsx", import.meta.url), "utf8");
const INGESTION_API_SOURCE = readFileSync(new URL("../../../api/ingestion.ts", import.meta.url), "utf8");
const DATA_SOURCES_SERVICE_SOURCE = readFileSync(
	new URL("../../../api/services/dataSourcesService.ts", import.meta.url),
	"utf8",
);
const ROUTES_SOURCE = readFileSync(
	new URL("../../../routes/sections/dashboard/static-routes.tsx", import.meta.url),
	"utf8",
);

test("connector registry carries connector context into data source creation", () => {
	assert.match(CONNECTOR_REGISTRY_SOURCE, /const openDataSourceCreate = useCallback/);
	assert.match(
		CONNECTOR_REGISTRY_SOURCE,
		/`\/foundation\/connections\?create=1&connectorKey=\$\{encodeURIComponent\(connectorKey\)\}`/,
	);
	assert.match(CONNECTOR_REGISTRY_SOURCE, /openDataSourceCreate\(record\.connectorKey\)/);
	assert.match(CONNECTOR_REGISTRY_SOURCE, /openDataSourceCreate\(selected\.connectorKey\)/);
});

test("connection profile route consumes connector context on the page actually mounted by the router", () => {
	assert.match(ROUTES_SOURCE, /path:\s*"foundation\/connections"[\s\S]{0,180}<ConnectionProfilesPage/);
	assert.match(CONNECTION_PROFILES_SOURCE, /useSearchParams/);
	assert.match(CONNECTION_PROFILES_SOURCE, /searchParams\.get\("create"\) !== "1"/);
	assert.match(CONNECTION_PROFILES_SOURCE, /searchParams\.get\("connectorKey"\) \|\| undefined/);
	assert.match(CONNECTION_PROFILES_SOURCE, /setSearchParams\(next,\s*\{\s*replace:\s*true\s*\}\)/);
	assert.match(CONNECTION_PROFILES_SOURCE, /initialConnectorKey=\{initialConnectorKey\}/);
});

test("data source form applies the initial connector and its defaults", () => {
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /initialConnectorKey\?: string/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /form\.setFieldsValue\(\{\s*connectorKey: initialConnectorKey\s*\}\)/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /applyConnectorDefaults\(initialConnectorKey\)/);
});

test("connection profile form derives source type and defaults from connector registry", () => {
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /connectorsService\.list\(\)/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /return connectors\.map\(\(connector\) => \(\{/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /const nextType = connector\.sourceType/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /const defaults = asRecord\(connector\.configSchema\?\.defaults\)/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /typeof defaults\?\.driverClass === "string"/);
	assert.doesNotMatch(CONNECTION_PROFILE_FORM_SOURCE, /dictionaryService|systemTypesFallbackActive|TYPE_OPTIONS/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /message="连接器目录不可用"/);
});

test("data source form uses connector-owned drivers and keeps upload selection for generic JDBC only", () => {
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /connector\.driver\?\.driverClass/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /connector\.driver\?\.fileName/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /connectorUsesCustomDriver/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /connectorDriverBlocked/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /drivers\s*\.filter\(\(driver\) => !driver\.missing\)/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /<ConnectorDriverNotice/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /onManageDrivers=\{\(\) => router\.push\("\/foundation\/jdbc-drivers"\)\}/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /jdbcRequired && connectorUsesCustomDriver/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /okButtonProps=\{\{\s*disabled:\s*connectorDriverBlocked/);
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
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /disabled:\s*item\.enabled === false/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /即将支持/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /selectedApiDescriptor\?\.enabled === false/);
});

test("api data source form emits engine-compatible auth references", () => {
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /buildApiAuthRefName/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /authRefConfig\[buildApiAuthRefName\(field\.name\)\]\s*=\s*secretRef/);
	assert.doesNotMatch(CONNECTION_PROFILE_FORM_SOURCE, /\.\.\.\(authConfig \? \{ config: authConfig \} : \{\}\)/);
});

test("api data source form exposes the plaintext HTTP policy explicitly", () => {
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /name="apiAllowHttp"/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /valuePropName="checked"/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /allowHttp/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /apiAllowHttp:\s*apiSource\s*\?/);
});

test("api connection test only submits a managed id and a strict relative GET resource", () => {
	const testHandler = CONNECTION_PROFILE_FORM_SOURCE.slice(
		CONNECTION_PROFILE_FORM_SOURCE.indexOf("const handleApiConnectionTest"),
		CONNECTION_PROFILE_FORM_SOURCE.indexOf("const handleSave"),
	);
	assert.match(testHandler, /testManagedApiConnection\(\{/);
	assert.match(testHandler, /dataSourceId:\s*editing\.id/);
	assert.match(testHandler, /resource:\s*\{\s*path:\s*"\/",\s*method:\s*"GET"\s*\}/);
	for (const forbidden of ["sourceConfig", "secrets", "password", "token", "baseUrl", "requestPolicy"]) {
		assert.doesNotMatch(testHandler, new RegExp(forbidden, "i"));
	}
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /disabled=\{!editing\?\.id\}/);
	assert.match(CONNECTION_PROFILE_FORM_SOURCE, /请先保存为托管连接/);
});

test("connection profiles stay separate from access plans", () => {
	assert.match(CONNECTION_PROFILES_SOURCE, /连接配置负责保存可复用的数据库与 API 连接/);
	assert.doesNotMatch(CONNECTION_PROFILES_SOURCE, /createIngestionTask|ODS 预检|同步任务生成/);
});

test("connection client no longer exposes retired ODS generation workflows", () => {
	for (const endpoint of ["schema-discover", "ods-preview", "ods-apply", "ods-precheck", "sync-task-draft"]) {
		assert.doesNotMatch(DATA_SOURCES_SERVICE_SOURCE, new RegExp(endpoint));
	}
});

test("data source list and detail pages do not expose full-chain rollback", () => {
	for (const source of [CONNECTION_PROFILES_SOURCE, CONNECTION_PROFILE_DETAIL_SOURCE]) {
		assert.doesNotMatch(source, /全链路回退/);
		assert.doesNotMatch(source, /RollbackImpactModal/);
		assert.doesNotMatch(source, /rollbackAnalyze/);
		assert.doesNotMatch(source, /rollbackExecute/);
	}
});
