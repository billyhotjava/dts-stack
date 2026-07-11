import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const CONNECTOR_REGISTRY_SOURCE = readFileSync(new URL("./ConnectorRegistryPage.tsx", import.meta.url), "utf8");
const DATA_SOURCES_SOURCE = readFileSync(new URL("./DataSourcesPage.tsx", import.meta.url), "utf8");
const DATA_SOURCE_DETAIL_SOURCE = readFileSync(new URL("./DataSourceDetailPage.tsx", import.meta.url), "utf8");
const DATA_SOURCE_FORM_SOURCE = readFileSync(new URL("./DataSourceFormModal.tsx", import.meta.url), "utf8");
const INGESTION_API_SOURCE = readFileSync(new URL("../../api/ingestion.ts", import.meta.url), "utf8");
const DICTIONARY_SERVICE_SOURCE = readFileSync(
	new URL("../../api/services/dictionaryService.ts", import.meta.url),
	"utf8",
);

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

test("data source form reads system types from platform dictionary before fallback", () => {
	assert.match(DICTIONARY_SERVICE_SOURCE, /url:\s*"\/platform\/dict\/system-types"/);
	assert.match(DICTIONARY_SERVICE_SOURCE, /_skipErrorToast:\s*true/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /dictionaryService\.listSystemTypes\(\)/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /const systemTypeOptions = useMemo/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /options=\{systemTypeOptions\}/);
	assert.doesNotMatch(DATA_SOURCE_FORM_SOURCE, /options=\{TYPE_OPTIONS\}/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /系统类型字典暂不可用，当前使用内置兜底选项/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /系统类型字典未接通/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /router\.push\("\/governance\/standards\/reference"\)/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /router\.push\("\/foundation\/connectors"\)/);
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
	assert.doesNotMatch(DATA_SOURCE_FORM_SOURCE, /sourceConfig:\s*asRecord\(\s*[\s\S]*?buildApiProps[\s\S]*?\.readerConfig/);
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
