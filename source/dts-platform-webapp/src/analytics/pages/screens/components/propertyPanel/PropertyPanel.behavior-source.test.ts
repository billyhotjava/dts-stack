import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const propertyPanelPath = new URL("./PropertyPanel.tsx", import.meta.url);
const behaviorConfigSectionPath = new URL("./BehaviorConfigSection.tsx", import.meta.url);

test("PropertyPanel delegates behavior config sections to the extracted module", async () => {
	const [propertyPanelSource, behaviorConfigSource] = await Promise.all([
		readFile(propertyPanelPath, "utf8"),
		readFile(behaviorConfigSectionPath, "utf8"),
	]);

	assert.match(propertyPanelSource, /from '\.\/BehaviorConfigSection'/);
	assert.equal(propertyPanelSource.includes("function renderInteractionConfig("), false);
	assert.equal(propertyPanelSource.includes("function renderActionConfig("), false);
	assert.equal(propertyPanelSource.includes("function renderDrillDownConfig("), false);

	assert.match(behaviorConfigSource, /export function renderInteractionConfig/);
	assert.match(behaviorConfigSource, /export function renderActionConfig/);
	assert.match(behaviorConfigSource, /export function renderDrillDownConfig/);
});
