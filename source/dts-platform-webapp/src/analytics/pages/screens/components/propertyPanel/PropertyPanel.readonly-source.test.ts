import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";

const propertyPanelPath = new URL("./PropertyPanel.tsx", import.meta.url);

test("PropertyPanel surfaces editor readonly state across canvas, batch, and component views", async () => {
	const source = await readFile(propertyPanelPath, "utf8");

	assert.match(source, /editorReadonly/);
	assert.match(source, /aria-readonly=\{editorReadonly\}/);
	assert.match(source, /analytics-screen-property-readonly-note/);
	assert.match(source, /只读模式：属性编辑已被保护/);
	assert.match(source, /只读模式：批量编辑已被保护/);
	assert.match(source, /只读模式：画布设置仅供查看/);
});
