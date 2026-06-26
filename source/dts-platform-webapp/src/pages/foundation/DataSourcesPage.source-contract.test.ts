import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const CONNECTOR_REGISTRY_SOURCE = readFileSync(new URL("./ConnectorRegistryPage.tsx", import.meta.url), "utf8");
const DATA_SOURCES_SOURCE = readFileSync(new URL("./DataSourcesPage.tsx", import.meta.url), "utf8");
const DATA_SOURCE_FORM_SOURCE = readFileSync(new URL("./DataSourceFormModal.tsx", import.meta.url), "utf8");
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
	assert.match(DATA_SOURCE_FORM_SOURCE, /dictionaryService\.listSystemTypes\(\)/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /const systemTypeOptions = useMemo/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /options=\{systemTypeOptions\}/);
	assert.doesNotMatch(DATA_SOURCE_FORM_SOURCE, /options=\{TYPE_OPTIONS\}/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /系统类型字典暂不可用，当前使用内置兜底选项/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /系统类型字典未接通/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /router\.push\("\/governance\/standards\/reference"\)/);
	assert.match(DATA_SOURCE_FORM_SOURCE, /router\.push\("\/foundation\/connectors"\)/);
});
