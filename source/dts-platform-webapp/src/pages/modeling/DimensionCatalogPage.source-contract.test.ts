import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (relativePath: string) => readFileSync(new URL(relativePath, import.meta.url), "utf8");

test("dimension catalog uses the definition boundary and keeps the six-column business ledger", () => {
	const page = read("./DimensionCatalogPage.tsx");

	assert.match(page, /listDimensionDefinitions/);
	assert.match(page, /getDimensionDefinition/);
	assert.match(page, /confirmDimensionDefinition/);
	assert.match(page, /retireDimensionDefinition/);
	for (const column of ["维度名称", "系统编码", "业务分类", "状态", "逻辑模型引用数", "更新时间"]) {
		assert.match(page, new RegExp(`title: "${column}"`));
	}
	for (const action of ["设为现行", "创建维度表", "引用", "编辑", "退役"]) {
		assert.match(page, new RegExp(`>\\s*${action}\\s*<`));
	}
	assert.match(page, /definition\.status === "DRAFT"/);
	assert.match(page, /canRetireDimensionDefinition\(definition\.status, canEdit\)/);
	assert.match(page, /isDimensionDefinitionVersionConflict\(error\)/);
	assert.match(page, /actionNeedsRefresh/);
	assert.match(page, />\s*刷新目录\s*</);
	assert.match(page, /loadRequestRef/);
	assert.match(page, /actionRequestRef/);
	assert.match(page, /new URLSearchParams\(searchParams\)/);
	assert.match(page, /params\.set\("dimensionDefinitionId", definition\.id\)/);
	assert.match(page, /params\.set\("domainId", definition\.domainId\)/);
	assert.match(page, /params\.set\("dimensionDefinitionRevision", String\(definition\.revision\)\)/);
	assert.doesNotMatch(page, /listModelSpecs|createModelSpec|ModelSpecCreateDrawer|listWarehousePlans/);
	assert.doesNotMatch(page, /planId|grain|DWD|SCD/i);
	assert.doesNotMatch(page, /["'`](?:source|sql|materialization)["'`]/i);
});

test("dimension graph deep links use a read-only selected-row adapter", () => {
	const page = read("./DimensionCatalogPage.tsx");
	assert.match(page, /selectedDimensionIdOverride\??:\s*string/);
	assert.match(page, /selectedDimensionId/);
	assert.match(page, /rowClassName/);
	assert.match(page, /modeling-selected-dimension-row/);
	assert.match(page, /scrollIntoView/);
	assert.match(page, /指定维度不可见或不存在/);
	assert.doesNotMatch(page, /setEditing\(selectedDimension/);
});

test("dimension definition drawer stays narrow, has no physical-table form, and preserves strong-version recovery", () => {
	const drawer = read("./components/DimensionDefinitionCreateDrawer.tsx");
	const api = read("../../api/dimensionDefinitionApi.ts");

	assert.match(drawer, /width=\{540\}/);
	assert.match(drawer, /keyboard=\{!saving\}/);
	assert.match(drawer, /aria-label=/);
	assert.match(drawer, /readOnly aria-label="系统编码，只读"/);
	assert.match(drawer, /createDimensionDefinition\(command\)/);
	assert.match(drawer, /updateDimensionDefinition\(current, command\)/);
	assert.match(drawer, /加载最新版本/);
	assert.match(drawer, /reloadRequestRef/);
	assert.match(drawer, /shouldApplyDimensionDefinitionReload/);
	assert.match(drawer, /requestedDefinitionId/);
	assert.match(drawer, /activeDefinitionIdRef\.current/);
	assert.match(drawer, /currentDefinitionRef\.current\?\.id/);
	assert.match(drawer, /loading=\{reloading\}/);
	assert.match(drawer, /disabled=\{saving \|\| reloading \|\| !canEdit\}/);
	assert.match(drawer, /form\.setFieldsValue\(valuesFromDefinition\(latest\)\)/);
	assert.doesNotMatch(drawer, /ModelSpecCreateDrawer|createModelSpec|listModelSpecs|warehousePlanApi/);
	assert.match(api, /"If-Match": toDimensionDefinitionEtag\(expected\)/);
});
