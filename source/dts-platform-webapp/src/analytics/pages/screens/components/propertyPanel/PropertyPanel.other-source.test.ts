import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const propertyPanelPath = new URL("./PropertyPanel.tsx", import.meta.url);
const otherConfigSectionPath = new URL("./OtherConfigSection.tsx", import.meta.url);

test("PropertyPanel delegates advanced other config to the extracted module", async () => {
	const [propertyPanelSource, otherConfigSource] = await Promise.all([
		readFile(propertyPanelPath, "utf8"),
		readFile(otherConfigSectionPath, "utf8"),
	]);

	assert.match(propertyPanelSource, /from '\.\/OtherConfigSection'/);
	assert.equal(propertyPanelSource.includes("所属容器"), false);
	assert.equal(propertyPanelSource.includes("Tab联动"), false);
	assert.equal(propertyPanelSource.includes("变量可见条件"), false);

	assert.match(otherConfigSource, /export function renderOtherConfig/);
	assert.match(otherConfigSource, /wouldCreateParentCycle/);
	assert.match(otherConfigSource, /resolveTabSwitcherOptionValues/);
	assert.match(otherConfigSource, /visibilityMatchValues/);
});
