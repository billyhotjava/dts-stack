import type { ScreenTheme, ComponentType } from './types';
import { COMPONENT_CONFIG_SCHEMAS } from './configSchema/schemas';

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
    canvasBackground: '#1e1f26',
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
    canvasBackground: '#eaf2fb',
    cardBackground: '#ffffff',
    cardBorder: '1px solid rgba(148, 163, 184, 0.24)',
    cardShadow: '0 14px 32px rgba(4, 75, 140, 0.10)',
    cardBorderRadius: 18,
    textPrimary: '#0f172a',
    textSecondary: '#334155',
    textMuted: '#64748b',
    accentColor: '#044B8C',
    echarts: {
        axisLineColor: '#d7e1ed',
        axisLabelColor: '#334155',
        splitLineColor: '#e5edf7',
        tooltipBg: '#ffffff',
        tooltipBorder: '#d7e1ed',
        colorPalette: ['#044B8C', '#2f7bc4', '#5aa6d6', '#f0a33e', '#d86d5f', '#34a0a4'],
    },
    barGradient: ['#044B8C', '#2f7bc4'],
    scatterColor: '#2f7bc4',
    numberCard: {
        background: '#ffffff',
        border: '1px solid rgba(148, 163, 184, 0.24)',
        titleColor: '#475569',
        valueColor: '#0f172a',
    },
    scrollBoard: {
        headerBg: '#eef5fb',
        oddRowBg: '#ffffff',
        evenRowBg: '#f5f9fd',
        textColor: '#0f172a',
    },
    progressBar: {
        trackBg: '#dce7f2',
        fillGradient: ['#044B8C', '#2f7bc4'],
        labelColor: '#0f172a',
    },
    breadcrumb: {
        background: 'rgba(255,255,255,0.95)',
        textColor: '#334155',
        linkColor: '#044B8C',
    },
    gauge: {
        axisLineColor: '#dce7f2',
        splitLineColor: '#a2b3c7',
        axisLabelColor: '#334155',
        titleColor: '#0f172a',
        detailColor: '#044B8C',
    },
    radar: {
        axisNameColor: '#334155',
        splitLineColor: '#e5edf7',
    },
    placeholder: {
        background: '#f5f9fd',
        border: '2px dashed #c8d7e7',
        color: '#8ea1b8',
    },
    pieLabelColor: '#0f172a',
    funnelLabelColor: '#0f172a',
    errorBg: 'rgba(255,255,255,0.95)',
};

// ── Light Business theme (for PC dashboard / daily analysis) ──
const lightBusinessTheme: ScreenThemeTokens = {
    ...glacierTheme,
    canvasBackground: '#eaf2fb',
    cardBackground: '#ffffff',
    cardBorder: '1px solid rgba(148, 163, 184, 0.24)',
    cardShadow: '0 16px 36px rgba(4, 75, 140, 0.10)',
    textPrimary: '#0f172a',
    textSecondary: '#334155',
    textMuted: '#64748b',
    accentColor: '#044B8C',
    echarts: {
        ...glacierTheme.echarts,
        axisLineColor: '#d7e1ed',
        axisLabelColor: '#334155',
        splitLineColor: '#e5edf7',
        tooltipBg: 'rgba(255,255,255,0.96)',
        tooltipBorder: '#d7e1ed',
        colorPalette: ['#044B8C', '#2f7bc4', '#5aa6d6', '#f0a33e', '#d86d5f', '#34a0a4'],
    },
    barGradient: ['#044B8C', '#2f7bc4'],
    numberCard: {
        background: '#ffffff',
        border: '1px solid rgba(148, 163, 184, 0.24)',
        titleColor: '#475569',
        valueColor: '#0f172a',
    },
    scrollBoard: { headerBg: '#e1ecf8', oddRowBg: '#ffffff', evenRowBg: '#f5f9fd', textColor: '#0f172a' },
    progressBar: { trackBg: '#dce7f2', fillGradient: ['#044B8C', '#2f7bc4'], labelColor: '#0f172a' },
    breadcrumb: { background: 'rgba(255,255,255,0.92)', textColor: '#334155', linkColor: '#044B8C' },
    gauge: { axisLineColor: '#dce7f2', splitLineColor: '#e5edf7', axisLabelColor: '#334155', titleColor: '#0f172a', detailColor: '#044B8C' },
    radar: { axisNameColor: '#334155', splitLineColor: '#e5edf7' },
    placeholder: { background: '#f5f9fd', border: '2px dashed #c8d7e7', color: '#8ea1b8' },
    pieLabelColor: '#0f172a',
    funnelLabelColor: '#0f172a',
    errorBg: 'rgba(255,255,255,0.95)',
};

// ── Dark Command theme (sci-fi command center) ──
const darkCommandTheme: ScreenThemeTokens = {
    ...legacyDarkTheme,
    canvasBackground: '#050a14',
    cardBackground: 'rgba(10,22,40,0.7)',
    cardBorder: '1px solid rgba(34,195,255,0.12)',
    cardShadow: '0 0 20px rgba(34,195,255,0.08)',
    textPrimary: '#e6f0ff',
    textSecondary: '#8da2c3',
    textMuted: '#6b8ab5',
    accentColor: '#22c3ff',
    echarts: {
        ...legacyDarkTheme.echarts,
        axisLineColor: 'rgba(34,195,255,0.15)',
        axisLabelColor: '#6b8ab5',
        splitLineColor: 'rgba(34,195,255,0.08)',
        tooltipBg: 'rgba(5,10,20,0.9)',
        tooltipBorder: 'rgba(34,195,255,0.2)',
        colorPalette: ['#22c3ff', '#3ddc97', '#ffb74d', '#ff6b7a', '#a78bfa', '#54e3ff'],
    },
    barGradient: ['#22c3ff', '#1a8fd4'],
    numberCard: {
        background: 'rgba(10,22,40,0.7)',
        border: '1px solid rgba(34,195,255,0.15)',
        titleColor: '#6b8ab5',
        valueColor: '#e6f0ff',
    },
    scrollBoard: { headerBg: 'rgba(17,34,56,0.8)', oddRowBg: 'rgba(10,22,40,0.5)', evenRowBg: 'rgba(16,35,58,0.5)', textColor: '#c8ddf5' },
    progressBar: { trackBg: 'rgba(19,39,63,0.8)', fillGradient: ['#22c3ff', '#1a8fd4'], labelColor: '#e6f0ff' },
    breadcrumb: { background: 'rgba(5,10,20,0.85)', textColor: '#6b8ab5', linkColor: '#22c3ff' },
    gauge: { axisLineColor: 'rgba(34,195,255,0.2)', splitLineColor: 'rgba(34,195,255,0.1)', axisLabelColor: '#6b8ab5', titleColor: '#e6f0ff', detailColor: '#22c3ff' },
    radar: { axisNameColor: '#6b8ab5', splitLineColor: 'rgba(34,195,255,0.1)' },
    placeholder: { background: 'rgba(10,22,40,0.5)', border: '2px dashed rgba(34,195,255,0.2)', color: '#6b8ab5' },
    pieLabelColor: '#c8ddf5',
    funnelLabelColor: '#e6f0ff',
    errorBg: 'rgba(10,22,40,0.9)',
};

// ── Enterprise Light theme (professional, minimal, boardroom-friendly) ──
const enterpriseLightTheme: ScreenThemeTokens = {
    ...lightBusinessTheme,
    canvasBackground: '#f0f4f8',
    cardBackground: '#ffffff',
    cardBorder: '1px solid rgba(148, 163, 184, 0.18)',
    cardShadow: '0 1px 3px rgba(0,0,0,0.06), 0 1px 2px rgba(0,0,0,0.04)',
    cardBorderRadius: 12,
    textPrimary: '#0f172a',
    textSecondary: '#334155',
    textMuted: '#64748b',
    accentColor: '#2563eb',
    echarts: {
        ...lightBusinessTheme.echarts,
        colorPalette: ['#2563eb', '#0891b2', '#059669', '#d97706', '#dc2626', '#7c3aed', '#0d9488', '#ea580c'],
    },
    barGradient: ['#2563eb', '#3b82f6'],
    numberCard: {
        background: '#ffffff',
        border: '1px solid rgba(148, 163, 184, 0.18)',
        titleColor: '#64748b',
        valueColor: '#0f172a',
    },
    scrollBoard: { headerBg: '#e2e8f0', oddRowBg: '#ffffff', evenRowBg: '#f8fafc', textColor: '#0f172a' },
    progressBar: { trackBg: '#e2e8f0', fillGradient: ['#2563eb', '#3b82f6'], labelColor: '#0f172a' },
    breadcrumb: { background: 'rgba(255,255,255,0.95)', textColor: '#334155', linkColor: '#2563eb' },
    gauge: { axisLineColor: '#e2e8f0', splitLineColor: '#f1f5f9', axisLabelColor: '#334155', titleColor: '#0f172a', detailColor: '#2563eb' },
    radar: { axisNameColor: '#334155', splitLineColor: '#e2e8f0' },
    placeholder: { background: '#f8fafc', border: '2px dashed #cbd5e1', color: '#94a3b8' },
    pieLabelColor: '#0f172a',
    funnelLabelColor: '#0f172a',
    errorBg: 'rgba(255,255,255,0.96)',
};

// ── Enterprise Dark theme (professional, deep blue, executive-grade) ──
const enterpriseDarkTheme: ScreenThemeTokens = {
    ...darkCommandTheme,
    canvasBackground: '#0f1219',
    cardBackground: 'rgba(30, 41, 59, 0.85)',
    cardBorder: '1px solid rgba(148, 163, 184, 0.12)',
    cardShadow: '0 4px 12px rgba(0,0,0,0.25)',
    cardBorderRadius: 12,
    textPrimary: '#e2e8f0',
    textSecondary: '#94a3b8',
    textMuted: '#64748b',
    accentColor: '#3b82f6',
    echarts: {
        ...darkCommandTheme.echarts,
        axisLineColor: 'rgba(148, 163, 184, 0.15)',
        axisLabelColor: '#94a3b8',
        splitLineColor: 'rgba(148, 163, 184, 0.08)',
        tooltipBg: 'rgba(15, 18, 25, 0.92)',
        tooltipBorder: 'rgba(148, 163, 184, 0.15)',
        colorPalette: ['#3b82f6', '#06b6d4', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#14b8a6', '#f97316'],
    },
    barGradient: ['#3b82f6', '#2563eb'],
    numberCard: {
        background: 'rgba(30, 41, 59, 0.85)',
        border: '1px solid rgba(148, 163, 184, 0.12)',
        titleColor: '#94a3b8',
        valueColor: '#e2e8f0',
    },
    scrollBoard: { headerBg: 'rgba(30, 41, 59, 0.9)', oddRowBg: 'rgba(30, 41, 59, 0.6)', evenRowBg: 'rgba(30, 41, 59, 0.4)', textColor: '#e2e8f0' },
    progressBar: { trackBg: 'rgba(30, 41, 59, 0.8)', fillGradient: ['#3b82f6', '#2563eb'], labelColor: '#e2e8f0' },
    breadcrumb: { background: 'rgba(15, 18, 25, 0.9)', textColor: '#94a3b8', linkColor: '#3b82f6' },
    gauge: { axisLineColor: 'rgba(148, 163, 184, 0.2)', splitLineColor: 'rgba(148, 163, 184, 0.1)', axisLabelColor: '#94a3b8', titleColor: '#e2e8f0', detailColor: '#3b82f6' },
    radar: { axisNameColor: '#94a3b8', splitLineColor: 'rgba(148, 163, 184, 0.1)' },
    placeholder: { background: 'rgba(30, 41, 59, 0.5)', border: '2px dashed rgba(148, 163, 184, 0.2)', color: '#64748b' },
    pieLabelColor: '#e2e8f0',
    funnelLabelColor: '#e2e8f0',
    errorBg: 'rgba(15, 18, 25, 0.92)',
};

// ── Brand Custom theme (user-editable, defaults to light) ──
const brandCustomTheme: ScreenThemeTokens = { ...lightBusinessTheme };

const themeMap: Record<ScreenTheme, ScreenThemeTokens> = {
    'legacy-dark': legacyDarkTheme,
    'titanium': titaniumTheme,
    'glacier': glacierTheme,
    'light-business': lightBusinessTheme,
    'dark-command': darkCommandTheme,
    'enterprise-light': enterpriseLightTheme,
    'enterprise-dark': enterpriseDarkTheme,
    'brand-custom': brandCustomTheme,
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

/**
 * Apply theme tokens to all component configs via schema themeTokenKey mappings.
 * @param mode 'force' = overwrite all themed fields; 'safe' = only fill empty values
 */
export function applyThemeToComponents(
    components: Array<{ type?: string; config?: Record<string, unknown> }>,
    theme: ScreenTheme | string | undefined,
    mode: 'force' | 'safe' = 'force',
) {
    const tokens = getThemeTokens(theme as ScreenTheme);
    return components.map((comp) => {
        const schema = COMPONENT_CONFIG_SCHEMAS[comp.type as ComponentType];
        if (!schema || !comp.config) return comp;
        const patched = { ...comp.config };
        for (const field of schema.fields) {
            if (!field.themeTokenKey) continue;
            const tokenValue = resolveThemeTokenValue(tokens, field.themeTokenKey);
            if (tokenValue === undefined) continue;
            if (mode === 'force' || shouldReplaceValue(patched[field.key], 'safe')) {
                patched[field.key] = Array.isArray(tokenValue) ? [...tokenValue] : tokenValue;
            }
        }
        return { ...comp, config: patched };
    });
}

function resolveThemeTokenValue(tokens: ScreenThemeTokens, tokenKey: string): string | string[] | undefined {
    const parts = tokenKey.split('.');
    let val: unknown = tokens;
    for (const p of parts) {
        if (val && typeof val === 'object') val = (val as Record<string, unknown>)[p];
        else return undefined;
    }
    if (typeof val === 'string') return val;
    if (Array.isArray(val) && val.every(v => typeof v === 'string')) return val as string[];
    return undefined;
}
