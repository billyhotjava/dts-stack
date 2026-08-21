import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

const pagePath = new URL('./ScreenDesignerPage.tsx', import.meta.url);

test('screen designer opens one issue center and locates component issues', async () => {
    const source = await readFile(pagePath, 'utf8');

    assert.match(source, /deriveScreenAuthoringIssues\(persistedConfig\)/);
    assert.match(source, /<ScreenIssuePanel/);
    assert.match(source, /selectComponents\(\[issue\.componentId\]\)/);
    assert.match(source, /setRightPanelTab\(issue\.tab/);
});

test('screen designer returns to canvas settings when component-only tabs have no selection', async () => {
    const source = await readFile(pagePath, 'utf8');

    assert.match(source, /selectedIds\.length === 0/);
    assert.match(source, /rightPanelTab !== 'style' && rightPanelTab !== 'layer'/);
    assert.match(source, /setRightPanelTab\('style'\)/);
});
