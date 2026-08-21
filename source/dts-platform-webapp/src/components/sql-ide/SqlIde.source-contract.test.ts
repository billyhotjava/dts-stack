import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const SQL_IDE = readFileSync(new URL("./SqlIde.tsx", import.meta.url), "utf8");
const SCHEMA_TREE = readFileSync(new URL("./schema/SchemaTree.tsx", import.meta.url), "utf8");
const SAVE_DIALOG = readFileSync(new URL("./saved/SaveQueryDialog.tsx", import.meta.url), "utf8");
const SAVED_PANEL = readFileSync(new URL("./saved/SavedPanel.tsx", import.meta.url), "utf8");

test("current SqlIde exposes run, stop and save controls", () => {
  assert.match(SQL_IDE, />\s*运行\s*</);
  assert.match(SQL_IDE, />\s*停止\s*</);
  assert.match(SQL_IDE, />\s*保存\s*</);
  assert.match(SQL_IDE, /onClick=\{handleRun\}/);
  assert.match(SQL_IDE, /onClick=\{sqlExec\.cancel\}/);
  assert.match(SQL_IDE, /onExecute=\{handleExecute\}/);
});

test("selected datasource drives execution and Monaco catalog completion", () => {
  assert.match(SQL_IDE, /createSqlIdeCatalogSource\(activeTab\.datasourceId\)/);
  assert.match(SQL_IDE, /catalog=\{catalogSource\}/);
  assert.match(SQL_IDE, /datasourceId=\{activeTab\?\.datasourceId\}/);
  assert.match(SQL_IDE, /onDatasourceChange=\{handleDatasourceChange\}/);
  assert.match(SCHEMA_TREE, /onDatasourceChange\?\.\(first\)/);
  assert.match(SCHEMA_TREE, /onDatasourceChange\?\.\(datasource\)/);
});

test("saved queries retain and restore their datasource context", () => {
  assert.match(SQL_IDE, /datasourceName=\{activeDatasource\?\.name \?\? activeDatasource\?\.label\}/);
  assert.match(SAVE_DIALOG, /datasourceId: datasourceId \?\? null/);
  assert.match(SAVE_DIALOG, /datasourceName: datasourceName \?\? null/);
  assert.match(SAVE_DIALOG, /当前数据源/);
  assert.match(SAVED_PANEL, /candidate\.name === item\.datasourceName/);
  assert.match(SAVED_PANEL, /datasourceId: datasource\?\.id \?\? item\.datasourceId \?\? null/);
});
