import { describe, expect, it } from 'vitest';
import { resolveInteractionScale } from './CanvasComponent';

describe('resolveInteractionScale', () => {
    it('derives the interaction scale from rendered width and design width', () => {
        const element = {
            getBoundingClientRect: () => ({ width: 960 }),
        } as HTMLElement;

        expect(resolveInteractionScale(element, 1920)).toBe(0.5);
    });

    it('falls back to 1 for missing or invalid dimensions', () => {
        expect(resolveInteractionScale(null, 1920)).toBe(1);

        const element = {
            getBoundingClientRect: () => ({ width: 0 }),
        } as HTMLElement;

        expect(resolveInteractionScale(element, 1920)).toBe(1);
        expect(resolveInteractionScale(element, 0)).toBe(1);
    });
});
