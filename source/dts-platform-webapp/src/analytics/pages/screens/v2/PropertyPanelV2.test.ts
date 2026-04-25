import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

describe('PropertyPanelV2 v1 parity editors', () => {
	it('keeps dedicated interaction, action and drill-down editors wired in', () => {
		const source = readFileSync(new URL('./PropertyPanelV2.tsx', import.meta.url), 'utf8');

		expect(source).toContain('function InteractionEditorV2');
		expect(source).toContain('function ActionEditorV2');
		expect(source).toContain('function DrillDownEditorV2');
		expect(source).toContain('<InteractionEditorV2');
		expect(source).toContain('<ActionEditorV2');
		expect(source).toContain('<DrillDownEditorV2');
		expect(source).toContain('ScreenJumpPicker');
		expect(source).toContain('CardIdPicker');
		expect(source).toContain('原始行为 JSON');
	});
});
