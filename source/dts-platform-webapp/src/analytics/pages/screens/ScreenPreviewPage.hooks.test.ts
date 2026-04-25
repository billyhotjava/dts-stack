import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('ScreenPreviewPage hook order', () => {
    it('keeps runtime canvas hook setup before early returns', () => {
        const source = readFileSync(new URL('./ScreenPreviewPage.tsx', import.meta.url), 'utf8');
        const hookIndex = source.indexOf('const runtimeCanvasScaleStyle = useMemo(');
        const earlyReturnIndex = source.indexOf('if (loading) {');

        expect(hookIndex).not.toBe(-1);
        expect(earlyReturnIndex).not.toBe(-1);
        expect(hookIndex).toBeLessThan(earlyReturnIndex);
    });
});
