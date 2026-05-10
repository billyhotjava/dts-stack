import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const sourcePath = new URL('./DesignerCanvas.tsx', import.meta.url);
const source = readFileSync(sourcePath, 'utf8');

describe('DesignerCanvas resize observer contract', () => {
    it('observes the container ref without referencing an out-of-scope node variable', () => {
        expect(source).toContain('const containerNode = containerRef.current');
        expect(source).toContain('resizeObserver.observe(containerNode)');
        expect(source).not.toContain('if (node && resizeObserver)');
    });
});
