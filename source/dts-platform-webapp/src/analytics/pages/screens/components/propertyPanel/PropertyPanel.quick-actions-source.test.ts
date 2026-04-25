import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const propertyPanelPath = new URL("./PropertyPanel.tsx", import.meta.url);
const quickActionsSectionPath = new URL("./QuickActionsSection.tsx", import.meta.url);

test("PropertyPanel delegates quick action rendering to the extracted module", async () => {
	const [propertyPanelSource, quickActionsSource] = await Promise.all([
		readFile(propertyPanelPath, "utf8"),
		readFile(quickActionsSectionPath, "utf8"),
	]);

	assert.match(propertyPanelSource, /from '\.\/QuickActionsSection'/);
	assert.equal(propertyPanelSource.includes("CHART_COMPONENT_TYPES"), false);
	assert.equal(propertyPanelSource.includes("showQuickActionGroup"), false);
	assert.equal(propertyPanelSource.includes("getQuickActionFilterButtonStyle"), false);
	assert.equal(propertyPanelSource.includes("商务预设"), false);

	assert.match(quickActionsSource, /export function renderQuickActionsConfig/);
	assert.match(quickActionsSource, /CHART_COMPONENT_TYPES/);
	assert.match(quickActionsSource, /商务预设/);
	assert.match(quickActionsSource, /清空样式板/);
});
