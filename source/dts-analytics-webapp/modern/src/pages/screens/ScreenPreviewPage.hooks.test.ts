import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';

test('ScreenPreviewPage keeps runtime canvas hook setup before early returns', () => {
    const source = readFileSync(new URL('./ScreenPreviewPage.tsx', import.meta.url), 'utf8');
    const hookIndex = source.indexOf('const runtimeCanvasScaleStyle = useMemo(');
    const earlyReturnIndex = source.indexOf('if (loading) {');

    assert.notEqual(hookIndex, -1);
    assert.notEqual(earlyReturnIndex, -1);
    assert.ok(
        hookIndex < earlyReturnIndex,
        'runtimeCanvasScaleStyle hook must stay above loading/error early returns',
    );
});
