import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const propertyPanelPath = new URL("./PropertyPanel.tsx", import.meta.url);
const animationConfigSectionPath = new URL("./AnimationConfigSection.tsx", import.meta.url);

test("PropertyPanel delegates animation config to the extracted module", async () => {
	const [propertyPanelSource, animationConfigSource] = await Promise.all([
		readFile(propertyPanelPath, "utf8"),
		readFile(animationConfigSectionPath, "utf8"),
	]);

	assert.match(propertyPanelSource, /from '\.\/AnimationConfigSection'/);
	assert.equal(propertyPanelSource.includes("自动编排延迟"), false);
	assert.equal(propertyPanelSource.includes("清除所有延迟"), false);
	assert.match(animationConfigSource, /export function renderAnimationConfig/);
	assert.match(animationConfigSource, /animationType/);
	assert.match(animationConfigSource, /自动编排延迟/);
	assert.match(animationConfigSource, /清除所有延迟/);
});
