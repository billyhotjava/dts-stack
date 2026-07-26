import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const drawer = readFileSync(new URL("./components/ModelSpecCreateDrawer.tsx", import.meta.url), "utf8");
const center = readFileSync(new URL("./ModelCenterPage.tsx", import.meta.url), "utf8");

test("model creation collects only the minimum draft identity", () => {
	for (const label of ["建设计划", "业务分类", "模型类型", "模型名称", "用途说明（可选）"]) {
		assert.match(drawer, new RegExp(`label="${label}"`));
	}
	assert.match(drawer, /form\.setFieldValue\("dimensionDefinitionRef"/);
	for (const retiredCreationConcern of [
		"getWarehousePlanSources",
		"ModelSpecSourceInventoryModal",
		"ModelSpecEditorFields",
		"upstreamOptions",
		"generationStrategy",
		"implementationMode",
		"materialization",
		"dimensionScdType",
		"dimensionCode",
	]) {
		assert.doesNotMatch(drawer, new RegExp(retiredCreationConcern));
	}
	assert.doesNotMatch(center, /candidateModels|candidateRequest|availableModels/);
});

test("draft creation requires explicit plan and category recovery instead of guessing", () => {
	assert.match(drawer, /请先选择建设计划。创建模型需要明确建设计划，系统不会自动猜测。/);
	assert.match(drawer, /请从当前建设计划中选择业务分类。系统不会自动猜测模型归属。/);
	assert.match(drawer, /当前建设计划还没有可用于建模的已确认业务分类。请先在规划基线中确认业务分类后返回。/);
	assert.match(drawer, /const changeDomain = \(\) => \{[\s\S]{0,180}setContextError\(""\)/);
	assert.doesNotMatch(drawer, /options\.length === 1[\s\S]{0,160}setFieldValue\("domainId"/);
});

test("DIMENSION creation lists only current definitions and pins the selected revision", () => {
	assert.match(drawer, /listDimensionDefinitions\(\{ domainId, status: "CURRENT" \}\)/);
	assert.match(drawer, /definition\.id === initialDimensionDefinitionId[\s\S]{0,160}definition\.revision === initialDimensionDefinitionRevision/);
	assert.match(drawer, /dimensionDefinitionId: requested\.id,[\s\S]{0,80}revision: requested\.revision/);
	assert.match(drawer, /dimensionDefinitionId: definition\.id, revision: definition\.revision/);
	assert.match(drawer, /请求的业务维度不是当前分类下的现行版本。请返回维度目录选择现行维度后重新创建维度表。/);
	assert.match(drawer, /前往维度目录/);
	assert.match(drawer, /暂无已确认的业务维度/);
	assert.match(center, /initialDimensionDefinitionId=\{searchParams\.get\("dimensionDefinitionId"\)/);
	assert.match(center, /initialDimensionDefinitionRevision=\{Number\(searchParams\.get\("dimensionDefinitionRevision"\)\) \|\| undefined\}/);
});
