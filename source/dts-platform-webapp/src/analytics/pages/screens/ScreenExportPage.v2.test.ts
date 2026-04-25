import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('ScreenExportPage v2 wiring', () => {
	it('keeps v2 load and render branches connected', () => {
		const source = readFileSync(new URL('./ScreenExportPage.tsx', import.meta.url), 'utf8');
		expect(source).toContain("tryLoadV2");
		expect(source).toContain("ResponsiveScreenLayout");
		expect(source).toContain("buildV2ScreenPayload");
		expect(source).toContain("screen.kind === 'v2'");
	});
});
