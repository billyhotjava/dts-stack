import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const propertyPanelPath = new URL("./PropertyPanel.tsx", import.meta.url);

test("PropertyPanel no longer renders quick location or quick action sections", async () => {
	const propertyPanelSource = await readFile(propertyPanelPath, "utf8");

	assert.equal(propertyPanelSource.includes("QuickActionsSection"), false);
	assert.equal(propertyPanelSource.includes("renderQuickActionsConfig"), false);
	assert.equal(propertyPanelSource.includes("快速定位"), false);
	assert.equal(propertyPanelSource.includes("快捷操作"), false);
	assert.equal(propertyPanelSource.includes("CHART_COMPONENT_TYPES"), false);
	assert.equal(propertyPanelSource.includes("showQuickActionGroup"), false);
	assert.equal(propertyPanelSource.includes("getQuickActionFilterButtonStyle"), false);
	assert.equal(propertyPanelSource.includes("商务预设"), false);
});
