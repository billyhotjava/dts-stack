import type { ScreenTheme } from './types';

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
    canvasBackground: '#ffffff',
    cardBackground: '#ffffff',
    cardBorder: '1px solid #e0e0e0',
    cardShadow: 'none',
    cardBorderRadius: 4,
    textPrimary: '#333333',
    textSecondary: '#666666',
    textMuted: '#999999',
    accentColor: '#2f5496',
    echarts: {
        axisLineColor: '#cccccc',
        axisLabelColor: '#666666',
        splitLineColor: '#eeeeee',
        tooltipBg: '#ffffff',
        tooltipBorder: '#cccccc',
        colorPalette: ['#c4a846', '#2f5496', '#c0504d', '#4bacc6', '#8064a2', '#f79646'],
    },
    barGradient: ['#c4a846', '#b89a30'],
    scatterColor: '#2f5496',
    numberCard: {
        background: '#ffffff',
        border: '1px solid #e0e0e0',
        titleColor: '#666666',
        valueColor: '#333333',
    },
    scrollBoard: {
        headerBg: '#f5f5f5',
        oddRowBg: '#ffffff',
        evenRowBg: '#fafafa',
        textColor: '#333333',
    },
    progressBar: {
        trackBg: '#e8e8e8',
        fillGradient: ['#2f5496', '#4a7bc7'],
        labelColor: '#333333',
    },
    breadcrumb: {
        background: 'rgba(255,255,255,0.95)',
        textColor: '#666666',
        linkColor: '#2f5496',
    },
    gauge: {
        axisLineColor: '#e0e0e0',
        splitLineColor: '#999999',
        axisLabelColor: '#666666',
        titleColor: '#333333',
        detailColor: '#333333',
    },
    radar: {
        axisNameColor: '#666666',
        splitLineColor: '#d9d9d9',
    },
    placeholder: {
        background: '#fafafa',
        border: '2px dashed #d9d9d9',
        color: '#999999',
    },
    pieLabelColor: '#333333',
    funnelLabelColor: '#ffffff',
    errorBg: 'rgba(255,255,255,0.95)',
};

const themeMap: Record<ScreenTheme, ScreenThemeTokens> = {
    'legacy-dark': legacyDarkTheme,
    'titanium': titaniumTheme,
    'glacier': glacierTheme,
};

export function getThemeTokens(theme?: ScreenTheme): ScreenThemeTokens {
    if (!theme) return legacyDarkTheme;
    return themeMap[theme] ?? legacyDarkTheme;
}
