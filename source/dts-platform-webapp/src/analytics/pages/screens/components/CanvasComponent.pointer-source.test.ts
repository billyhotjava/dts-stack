import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const sourcePath = new URL('./CanvasComponent.tsx', import.meta.url);
const source = readFileSync(sourcePath, 'utf8');

describe('CanvasComponent pointer interaction contract', () => {
	it('uses pointer events for canvas drag and resize interactions', () => {
		expect(source).toContain('onPointerDown');
		expect(source).toContain('pointermove');
		expect(source).toContain('pointercancel');
		expect(source).not.toContain('onMouseDown');
		expect(source).not.toContain('mousemove');
		expect(source).not.toContain('mouseup');
	});

	it('keeps pointer movement in design-space under scaled canvas transforms', () => {
		expect(source).toContain('resolveInteractionScale');
		expect(source).toContain('resolveScaledPointerDelta');
	});
});
