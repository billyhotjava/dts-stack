import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const pagePath = new URL("./ScreenDesignerPage.tsx", import.meta.url);
const issuePanelPath = new URL("./components/ScreenIssuePanel.tsx", import.meta.url);

test("screen designer opens one issue center and locates component issues", async () => {
	const source = await readFile(pagePath, "utf8");

	assert.match(source, /deriveScreenAuthoringIssues\(persistedConfig\)/);
	assert.match(source, /<ScreenIssuePanel/);
	assert.match(source, /selectComponents\(\[issue\.componentId\]\)/);
	assert.match(source, /setRightPanelTab\(issue\.tab/);
});

test("screen designer returns to canvas settings when component-only tabs have no selection", async () => {
	const source = await readFile(pagePath, "utf8");

	assert.match(source, /selectedIds\.length === 0/);
	assert.match(source, /rightPanelTab !== 'style' && rightPanelTab !== 'layer'/);
	assert.match(source, /setRightPanelTab\('style'\)/);
});

test("issue center keeps readable colors outside the editor CSS variable scope", async () => {
	const source = await readFile(issuePanelPath, "utf8");

	assert.match(source, /background:\s*"#1f2330"/);
	assert.match(source, /color:\s*"#e2e8f0"/);
});
