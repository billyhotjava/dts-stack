import type { ScreenComponent, ScreenTheme } from './types';

export interface ScreenThemeTokens {
    canvasBackground: string;
    // Card container
    cardBackground: string;
    cardBorder: string;
    cardShadow: string;
    cardBorderRadius: number;
    // Text
    textPrimary: string;
    textSecondary: string;
    textMuted: string;
    accentColor: string;
    // ECharts
    echarts: {
        axisLineColor: string;
        axisLabelColor: string;
        splitLineColor: string;
        tooltipBg: string;
        tooltipBorder: string;
        colorPalette: string[];
    };
    // Bar gradient
    barGradient: [string, string];
    // Scatter
    scatterColor: string;
    // Number card
    numberCard: {
        background: string;
        border: string;
        titleColor: string;
        valueColor: string;
    };
    // Scroll board
    scrollBoard: {
        headerBg: string;
        oddRowBg: string;
        evenRowBg: string;
        textColor: string;
    };
    // Progress bar / percent pond
    progressBar: {
        trackBg: string;
        fillGradient: [string, string];
        labelColor: string;
    };
    // Breadcrumb overlay
    breadcrumb: {
        background: string;
        textColor: string;
        linkColor: string;
    };
    // Gauge chart
    gauge: {
        axisLineColor: string;
        splitLineColor: string;
        axisLabelColor: string;
        titleColor: string;
        detailColor: string;
    };
    // Radar chart
    radar: {
        axisNameColor: string;
        splitLineColor: string;
    };
    // Placeholder (empty image/video/iframe)
    placeholder: {
        background: string;
        border: string;
        color: string;
    };
    // Pie label
    pieLabelColor: string;
    // Funnel label
    funnelLabelColor: string;
    // Error indicator
    errorBg: string;
}

const legacyDarkTheme: ScreenThemeTokens = {
    canvasBackground: '#0d1b2a',
    cardBackground: 'linear-gradient(135deg, rgba(99, 102, 241, 0.2) 0%, rgba(99, 102, 241, 0.05) 100%)',
    cardBorder: '1px solid rgba(99, 102, 241, 0.3)',
    cardShadow: 'none',
    cardBorderRadius: 8,
    textPrimary: '#ffffff',
    textSecondary: '#94a3b8',
    textMuted: '#666',
    accentColor: '#00d4ff',
    echarts: {
        axisLineColor: '#444',
        axisLabelColor: '#aaa',
        splitLineColor: '#333',
        tooltipBg: 'rgba(0,0,0,0.7)',
        tooltipBorder: '#333',
        colorPalette: ['#6366f1', '#8b5cf6', '#06b6d4', '#10b981', '#f59e0b', '#ef4444'],
    },
    barGradient: ['#6366f1', '#4f46e5'],
    scatterColor: '#6366f1',
    numberCard: {
        background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.2) 0%, rgba(99, 102, 241, 0.05) 100%)',
        border: '1px solid rgba(99, 102, 241, 0.3)',
        titleColor: '#94a3b8',
        valueColor: '#fff',
    },
    scrollBoard: {
        headerBg: '#003366',
        oddRowBg: 'rgba(0, 100, 200, 0.1)',
        evenRowBg: 'rgba(0, 50, 100, 0.1)',
        textColor: '#fff',
    },
    progressBar: {
        trackBg: 'rgba(255,255,255,0.1)',
        fillGradient: ['#6366f1', '#8b5cf6'],
        labelColor: '#fff',
    },
    breadcrumb: {
        background: 'rgba(0,0,0,0.6)',
        textColor: '#ccc',
        linkColor: '#6366f1',
    },
    gauge: {
        axisLineColor: '#334155',
        splitLineColor: '#999',
        axisLabelColor: '#999',
        titleColor: '#fff',
        detailColor: '#fff',
    },
    radar: {
        axisNameColor: '#aaa',
        splitLineColor: '#444',
    },
    placeholder: {
        background: 'rgba(255,255,255,0.05)',
        border: '2px dashed rgba(255,255,255,0.2)',
        color: '#666',
    },
    pieLabelColor: '#fff',
    funnelLabelColor: '#fff',
    errorBg: 'rgba(0,0,0,0.7)',
};

const titaniumTheme: ScreenThemeTokens = {
    canvasBackground: '#1a1d23',
    cardBackground: '#22262e',
    cardBorder: '1px solid #2a2e38',
    cardShadow: 'none',
    cardBorderRadius: 6,
    textPrimary: '#e8eaed',
    textSecondary: '#6b7280',
    textMuted: '#4b5563',
    accentColor: '#4a9eff',
    echarts: {
        axisLineColor: '#3a3f4b',
        axisLabelColor: '#9ca3af',
        splitLineColor: '#2a2e38',
        tooltipBg: 'rgba(30,33,40,0.95)',
        tooltipBorder: '#3a3f4b',
        colorPalette: ['#4a9eff', '#36d399', '#f59e0b', '#f472b6', '#a78bfa', '#34d399'],
    },
    barGradient: ['#4a9eff', '#3b82f6'],
    scatterColor: '#4a9eff',
    numberCard: {
        background: '#22262e',
        border: '1px solid #2a2e38',
        titleColor: '#6b7280',
        valueColor: '#e8eaed',
    },
    scrollBoard: {
        headerBg: '#282c35',
        oddRowBg: 'rgba(255,255,255,0.02)',
        evenRowBg: 'rgba(255,255,255,0.04)',
        textColor: '#e8eaed',
    },
    progressBar: {
        trackBg: 'rgba(255,255,255,0.06)',
        fillGradient: ['#4a9eff', '#3b82f6'],
        labelColor: '#e8eaed',
    },
    breadcrumb: {
        background: 'rgba(26,29,35,0.85)',
        textColor: '#9ca3af',
        linkColor: '#4a9eff',
    },
    gauge: {
        axisLineColor: '#2a2e38',
        splitLineColor: '#6b7280',
        axisLabelColor: '#6b7280',
        titleColor: '#e8eaed',
        detailColor: '#e8eaed',
    },
    radar: {
        axisNameColor: '#9ca3af',
        splitLineColor: '#3a3f4b',
    },
    placeholder: {
        background: 'rgba(255,255,255,0.03)',
        border: '2px dashed rgba(255,255,255,0.1)',
        color: '#4b5563',
    },
    pieLabelColor: '#e8eaed',
    funnelLabelColor: '#e8eaed',
    errorBg: 'rgba(26,29,35,0.9)',
};

const glacierTheme: ScreenThemeTokens = {
    canvasBackground: '#f6f7f9',
    cardBackground: '#ffffff',
    cardBorder: '1px solid #e5e7eb',
    cardShadow: '0 1px 2px rgba(0,0,0,0.06)',
    cardBorderRadius: 10,
    textPrimary: '#1f2328',
    textSecondary: '#6b7280',
    textMuted: '#9ca3af',
    accentColor: '#1f6feb',
    echarts: {
        axisLineColor: '#d1d5db',
        axisLabelColor: '#6b7280',
        splitLineColor: '#e5e7eb',
        tooltipBg: '#ffffff',
        tooltipBorder: '#e5e7eb',
        colorPalette: ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272'],
    },
    barGradient: ['#5470c6', '#3b82f6'],
    scatterColor: '#5470c6',
    numberCard: {
        background: '#ffffff',
        border: '1px solid #e5e7eb',
        titleColor: '#6b7280',
        valueColor: '#1f2328',
    },
    scrollBoard: {
        headerBg: '#f9fafb',
        oddRowBg: '#ffffff',
        evenRowBg: '#f9fafb',
        textColor: '#1f2328',
    },
    progressBar: {
        trackBg: '#e5e7eb',
        fillGradient: ['#1f6feb', '#3b82f6'],
        labelColor: '#1f2328',
    },
    breadcrumb: {
        background: 'rgba(255,255,255,0.95)',
        textColor: '#6b7280',
        linkColor: '#1f6feb',
    },
    gauge: {
        axisLineColor: '#e5e7eb',
        splitLineColor: '#9ca3af',
        axisLabelColor: '#6b7280',
        titleColor: '#1f2328',
        detailColor: '#1f2328',
    },
    radar: {
        axisNameColor: '#6b7280',
        splitLineColor: '#e5e7eb',
    },
    placeholder: {
        background: '#f9fafb',
        border: '2px dashed #d1d5db',
        color: '#9ca3af',
    },
    pieLabelColor: '#1f2328',
    funnelLabelColor: '#ffffff',
    errorBg: 'rgba(255,255,255,0.95)',
};

const themeMap: Record<ScreenTheme, ScreenThemeTokens> = {
    'legacy-dark': legacyDarkTheme,
    'titanium': titaniumTheme,
    'glacier': glacierTheme,
};

function parseHexColorToRgb(color: string): [number, number, number] | null {
    const value = color.trim().toLowerCase();
    if (!value.startsWith('#')) {
        return null;
    }

    if (value.length === 4) {
        const r = Number.parseInt(value[1] + value[1], 16);
        const g = Number.parseInt(value[2] + value[2], 16);
        const b = Number.parseInt(value[3] + value[3], 16);
        if (Number.isNaN(r) || Number.isNaN(g) || Number.isNaN(b)) {
            return null;
        }
        return [r, g, b];
    }

    if (value.length === 7) {
        const r = Number.parseInt(value.slice(1, 3), 16);
        const g = Number.parseInt(value.slice(3, 5), 16);
        const b = Number.parseInt(value.slice(5, 7), 16);
        if (Number.isNaN(r) || Number.isNaN(g) || Number.isNaN(b)) {
            return null;
        }
        return [r, g, b];
    }

    return null;
}

function isLightBackgroundColor(color?: string): boolean {
    if (!color) {
        return false;
    }

    const rgb = parseHexColorToRgb(color);
    if (!rgb) {
        return false;
    }

    const [r, g, b] = rgb;
    const luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
    return luminance >= 0.75;
}

export function resolveScreenTheme(theme?: ScreenTheme, backgroundColor?: string): ScreenTheme {
    if (theme && themeMap[theme]) {
        return theme;
    }
    return isLightBackgroundColor(backgroundColor) ? 'glacier' : 'legacy-dark';
}

export function getThemeTokens(theme?: ScreenTheme): ScreenThemeTokens {
    const resolved = resolveScreenTheme(theme);
    return themeMap[resolved] ?? legacyDarkTheme;
}

export type ThemeComponentApplyMode = 'safe' | 'force';

function withColorAlpha(hex: string, alpha: number): string {
    const rgb = parseHexColorToRgb(hex);
    if (!rgb) return hex;
    const [r, g, b] = rgb;
    const a = Math.max(0, Math.min(1, alpha));
    return `rgba(${r}, ${g}, ${b}, ${a.toFixed(3)})`;
}

function shouldReplaceValue(
    current: unknown,
    mode: ThemeComponentApplyMode,
): boolean {
    if (mode === 'force') return true;
    if (current === undefined || current === null) return true;
    if (typeof current === 'string') return current.trim().length === 0;
    if (Array.isArray(current)) return current.length === 0;
    return false;
}

function patchConfigValue(
    next: Record<string, unknown>,
    key: string,
    value: unknown,
    mode: ThemeComponentApplyMode,
): void {
    if (shouldReplaceValue(next[key], mode)) {
        next[key] = value;
    }
}

function patchComponentConfig(
    component: ScreenComponent,
    tokens: ScreenThemeTokens,
    mode: ThemeComponentApplyMode,
): Record<string, unknown> {
    const next = { ...(component.config || {}) } as Record<string, unknown>;
    const accentSoftBg = withColorAlpha(tokens.accentColor, 0.15);
    const accentLine = withColorAlpha(tokens.accentColor, 0.45);
    const cardSoft = withColorAlpha(tokens.textPrimary, 0.04);

    switch (component.type) {
        case 'line-chart':
        case 'bar-chart':
        case 'pie-chart':
        case 'gauge-chart':
        case 'scatter-chart':
        case 'radar-chart':
        case 'funnel-chart':
        case 'map-chart':
        case 'combo-chart':
        case 'wordcloud-chart':
        case 'treemap-chart':
        case 'sunburst-chart':
        case 'waterfall-chart':
            patchConfigValue(next, 'seriesColors', [...tokens.echarts.colorPalette], mode);
            patchConfigValue(next, 'titleColor', tokens.textPrimary, mode);
            break;
        case 'title':
            patchConfigValue(next, 'color', tokens.textPrimary, mode);
            break;
        case 'markdown-text':
            patchConfigValue(next, 'color', tokens.textPrimary, mode);
            break;
        case 'number-card':
            patchConfigValue(next, 'backgroundColor', tokens.numberCard.background, mode);
            patchConfigValue(next, 'titleColor', tokens.numberCard.titleColor, mode);
            patchConfigValue(next, 'valueColor', tokens.numberCard.valueColor, mode);
            break;
        case 'datetime':
            patchConfigValue(next, 'color', tokens.textPrimary, mode);
            break;
        case 'countdown':
            patchConfigValue(next, 'color', tokens.textSecondary, mode);
            patchConfigValue(next, 'accentColor', tokens.accentColor, mode);
            break;
        case 'marquee':
            patchConfigValue(next, 'color', tokens.textPrimary, mode);
            patchConfigValue(next, 'backgroundColor', cardSoft, mode);
            break;
        case 'shape':
            patchConfigValue(next, 'fillColor', accentSoftBg, mode);
            patchConfigValue(next, 'borderColor', accentLine, mode);
            break;
        case 'container':
            patchConfigValue(next, 'backgroundColor', cardSoft, mode);
            patchConfigValue(next, 'borderColor', accentLine, mode);
            patchConfigValue(next, 'titleColor', tokens.textPrimary, mode);
            break;
        case 'scroll-board':
            patchConfigValue(next, 'headerBGC', tokens.scrollBoard.headerBg, mode);
            patchConfigValue(next, 'oddRowBGC', tokens.scrollBoard.oddRowBg, mode);
            patchConfigValue(next, 'evenRowBGC', tokens.scrollBoard.evenRowBg, mode);
            patchConfigValue(next, 'headerColor', tokens.scrollBoard.textColor, mode);
            break;
        case 'table':
            patchConfigValue(next, 'headerColor', tokens.textPrimary, mode);
            patchConfigValue(next, 'bodyColor', tokens.textSecondary, mode);
            patchConfigValue(next, 'headerBackground', withColorAlpha(tokens.accentColor, 0.14), mode);
            patchConfigValue(next, 'bodyBackground', 'transparent', mode);
            patchConfigValue(next, 'oddRowBackground', 'transparent', mode);
            patchConfigValue(next, 'evenRowBackground', withColorAlpha(tokens.textPrimary, 0.04), mode);
            patchConfigValue(next, 'borderColor', withColorAlpha(tokens.textSecondary, 0.25), mode);
            break;
        case 'filter-input':
        case 'filter-select':
        case 'filter-date-range':
            patchConfigValue(next, 'labelColor', tokens.textSecondary, mode);
            patchConfigValue(next, 'inputTextColor', tokens.textPrimary, mode);
            patchConfigValue(next, 'inputBorderColor', withColorAlpha(tokens.textSecondary, 0.45), mode);
            patchConfigValue(next, 'inputBackground', tokens.cardBackground, mode);
            break;
        case 'border-box':
        case 'decoration':
            patchConfigValue(next, 'color', [tokens.accentColor, withColorAlpha(tokens.accentColor, 0.35)], mode);
            break;
        case 'digital-flop': {
            const style = next.style && typeof next.style === 'object'
                ? { ...(next.style as Record<string, unknown>) }
                : {};
            if (shouldReplaceValue(style.fill, mode)) {
                style.fill = tokens.textPrimary;
            }
            next.style = style;
            break;
        }
        case 'percent-pond':
            patchConfigValue(next, 'colors', [...tokens.progressBar.fillGradient], mode);
            break;
        default:
            break;
    }
    return next;
}

export function applyThemeToComponents(
    components: ScreenComponent[],
    theme: ScreenTheme | undefined,
    mode: ThemeComponentApplyMode = 'safe',
): ScreenComponent[] {
    const tokens = getThemeTokens(theme);
    return components.map((component) => ({
        ...component,
        config: patchComponentConfig(component, tokens, mode),
    }));
}
