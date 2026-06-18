import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const CONNECTOR_REGISTRY_SOURCE = readFileSync(new URL("./ConnectorRegistryPage.tsx", import.meta.url), "utf8");
const DATA_SOURCES_SOURCE = readFileSync(new URL("./DataSourcesPage.tsx", import.meta.url), "utf8");
const DATA_SOURCE_FORM_SOURCE = readFileSync(new URL("./DataSourceFormModal.tsx", import.meta.url), "utf8");

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
