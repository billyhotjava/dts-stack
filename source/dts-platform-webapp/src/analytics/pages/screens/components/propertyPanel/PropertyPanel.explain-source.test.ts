import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const propertyPanelPath = new URL("./PropertyPanel.tsx", import.meta.url);
const explainSectionPath = new URL("./ExplainConfigSection.tsx", import.meta.url);

test("PropertyPanel delegates explain result rendering to the extracted module", async () => {
	const [propertyPanelSource, explainSource] = await Promise.all([
		readFile(propertyPanelPath, "utf8"),
		readFile(explainSectionPath, "utf8"),
	]);

	assert.match(propertyPanelSource, /from '\.\/ExplainConfigSection'/);
	assert.equal(propertyPanelSource.includes("复制解释JSON"), false);
	assert.equal(propertyPanelSource.includes("当前组件未绑定可解释的 Card 数据源"), false);

	assert.match(explainSource, /export function renderExplainConfig/);
	assert.match(explainSource, /复制解释JSON/);
	assert.match(explainSource, /当前组件未绑定可解释的 Card 数据源/);
	assert.match(explainSource, /writeTextToClipboard/);
});
