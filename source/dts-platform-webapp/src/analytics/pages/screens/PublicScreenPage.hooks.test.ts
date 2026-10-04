import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('PublicScreenPage hook order', () => {
    it('keeps canvas/theme hooks before early returns', () => {
        const source = readFileSync(new URL('./PublicScreenPage.tsx', import.meta.url), 'utf8');
        const hookIndex = source.indexOf('const publicCanvasRef = useRef<HTMLDivElement>(null);');
        const earlyReturnIndex = source.indexOf('if (authError === \'not-authenticated\') {');

        expect(hookIndex).not.toBe(-1);
        expect(earlyReturnIndex).not.toBe(-1);
        expect(hookIndex).toBeLessThan(earlyReturnIndex);
    });
});
