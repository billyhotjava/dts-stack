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

    it('keeps automatic preview scaling proportional and reserves stretch for explicit scale mode', () => {
        const source = readFileSync(new URL('./ScreenPreviewPage.tsx', import.meta.url), 'utf8');

        expect(source).not.toContain('autoScaleX');
        expect(source).not.toContain('autoScaleY');
        expect(source).not.toContain('useStretchFill');
        expect(source).toContain('const stageWidth = Math.max(1, screenWidth * scale);');
        expect(source).toContain('const stageHeight = Math.max(1, screenHeight * scale);');
    });

    it('keeps loading and error cards readable and exposes their state semantics', () => {
        const source = readFileSync(new URL('./ScreenPreviewPage.tsx', import.meta.url), 'utf8');

        expect(source).toContain('function previewStateCardStyle(isDark: boolean)');
        expect(source).toContain('data-testid="screen-preview-state-card"');
        expect(source).toContain('aria-busy="true"');
        expect(source).toContain('role="alert"');
        expect(source).toContain('正在准备大屏画布和设备适配信息。');
    });

    it('writes Chrome 95 screenshots to the authoritative worklog evidence directory', () => {
        const source = readFileSync(
            new URL('../../../../e2e/sprint66-drilldown.spec.ts', import.meta.url),
            'utf8',
        );

        expect(source).toContain('"../../../worklog/v2.2.3/');
        expect(source).not.toContain('"../../../workflow/v2.2.3/');
    });

    it('keeps multi-page preview controls on the active runtime theme', () => {
        const source = readFileSync(new URL('./ScreenPreviewPage.tsx', import.meta.url), 'utf8');

        expect(source).toContain('data-testid="screen-preview-pager"');
        expect(source).toContain("background: 'var(--runtime-control-bg)'");
    });
});
