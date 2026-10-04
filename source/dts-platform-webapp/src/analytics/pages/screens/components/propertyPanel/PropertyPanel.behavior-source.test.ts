import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const propertyPanelPath = new URL("./PropertyPanel.tsx", import.meta.url);
const behaviorConfigSectionPath = new URL("./BehaviorConfigSection.tsx", import.meta.url);
const drillDownConfigSectionPath = new URL("./DrillDownConfigSection.tsx", import.meta.url);

test("PropertyPanel delegates behavior config sections to the extracted module", async () => {
	const [propertyPanelSource, behaviorConfigSource, drillDownConfigSource] = await Promise.all([
		readFile(propertyPanelPath, "utf8"),
		readFile(behaviorConfigSectionPath, "utf8"),
		readFile(drillDownConfigSectionPath, "utf8"),
	]);

	assert.match(propertyPanelSource, /from '\.\/BehaviorConfigSection'/);
	assert.match(propertyPanelSource, /from '\.\/DrillDownConfigSection'/);
	assert.equal(propertyPanelSource.includes("function renderInteractionConfig("), false);
	assert.equal(propertyPanelSource.includes("function renderActionConfig("), false);
	assert.equal(propertyPanelSource.includes("function renderDrillDownConfig("), false);

	assert.match(behaviorConfigSource, /export function renderInteractionConfig/);
	assert.match(behaviorConfigSource, /export function renderActionConfig/);
	assert.match(drillDownConfigSource, /export function renderDrillDownConfig/);
	assert.match(propertyPanelSource, /renderDrillDownConfig\(selectedComponent, updateComponent, config\.globalVariables \?\? \[\]/);
});

test("default open-panel action is immediately saveable", async () => {
	const behaviorConfigSource = await readFile(behaviorConfigSectionPath, "utf8");

	assert.match(behaviorConfigSource, /type:\s*'open-panel'/);
	assert.match(behaviorConfigSource, /panelTitle:\s*'\{\{name\}\}'/);
	assert.match(behaviorConfigSource, /panelBodyTemplate:\s*'\{\{name\}\}'/);
});
