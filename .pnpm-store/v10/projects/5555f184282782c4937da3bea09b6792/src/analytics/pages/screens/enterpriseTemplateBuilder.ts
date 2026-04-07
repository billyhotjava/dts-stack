/**
 * Enterprise template builder utilities.
 * Generates professional, boardroom-grade screen layouts using section-panel + stat-card components.
 */
import type { ScreenComponent } from './types';

// ── Enterprise Design Tokens ──

export const ENTERPRISE = {
    light: {
        canvas: '#f0f4f8',
        header: '#044B8C',
        headerText: '#ffffff',
        panel: '#ffffff',
        panelBorder: 'rgba(148, 163, 184, 0.18)',
        text: '#0f172a',
        textSecondary: '#334155',
        textMuted: '#64748b',
        accent: '#2563eb',
        success: '#059669',
        warning: '#d97706',
        danger: '#dc2626',
        info: '#0891b2',
        tableHeader: '#e2e8f0',
        tableEvenRow: '#f8fafc',
        divider: 'rgba(148, 163, 184, 0.2)',
        chartPalette: ['#2563eb', '#0891b2', '#059669', '#d97706', '#dc2626', '#7c3aed', '#14b8a6', '#ea580c'],
    },
    dark: {
        canvas: '#0f1219',
        header: '#1e40af',
        headerText: '#ffffff',
        panel: 'rgba(30, 41, 59, 0.85)',
        panelBorder: 'rgba(148, 163, 184, 0.12)',
        text: '#e2e8f0',
        textSecondary: '#94a3b8',
        textMuted: '#64748b',
        accent: '#3b82f6',
        success: '#10b981',
        warning: '#f59e0b',
        danger: '#ef4444',
        info: '#06b6d4',
        tableHeader: 'rgba(30, 41, 59, 0.9)',
        tableEvenRow: 'rgba(30, 41, 59, 0.4)',
        divider: 'rgba(148, 163, 184, 0.15)',
        chartPalette: ['#3b82f6', '#06b6d4', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#14b8a6', '#f97316'],
    },
} as const;

export type EnterpriseTokens = {
    canvas: string; header: string; headerText: string; panel: string; panelBorder: string;
    text: string; textSecondary: string; textMuted: string; accent: string;
    success: string; warning: string; danger: string; info: string;
    tableHeader: string; tableEvenRow: string; divider: string;
    chartPalette: readonly string[];
};

let _idCounter = 0;
function uid(prefix = 'ent') {
    return `${prefix}-${++_idCounter}`;
}

export function resetIdCounter(start = 0) {
    _idCounter = start;
}

// ── Component Builders ──

function comp(
    type: ScreenComponent['type'],
    name: string,
    x: number, y: number, w: number, h: number,
    zIndex: number,
    config: Record<string, unknown>,
): ScreenComponent {
    return { id: uid(), type, name, x, y, width: w, height: h, zIndex, locked: false, visible: true, config };
}

/** Header bar with title + datetime */
export function headerBar(
    tokens: EnterpriseTokens,
    title: string,
    width: number,
    height = 56,
): ScreenComponent[] {
    return [
        comp('shape', '头部背景', 0, 0, width, height, 90, {
            shapeType: 'rect', fillColor: tokens.header, borderWidth: 0, radius: 0,
        }),
        comp('title', '大屏标题', 20, 0, width * 0.6, height, 95, {
            text: title, fontSize: 24, fontWeight: '700',
            color: tokens.headerText, textAlign: 'flex-start',
        }),
        comp('datetime', '时间', width - 220, 0, 200, height, 95, {
            format: 'YYYY-MM-DD HH:mm:ss', color: tokens.headerText, fontSize: 14,
        }),
    ];
}

/** Section panel with title */
export function sectionPanel(
    tokens: EnterpriseTokens,
    title: string,
    x: number, y: number, w: number, h: number,
    options?: {
        titleIcon?: string;
        borderStyle?: 'solid' | 'gradient' | 'glow';
        shadow?: 'none' | 'subtle' | 'medium';
        backdropBlur?: number;
        zIndex?: number;
    },
): ScreenComponent {
    const isDark = tokens.canvas === ENTERPRISE.dark.canvas;
    return comp('section-panel', title, x, y, w, h, options?.zIndex ?? 10, {
        title,
        titleIcon: options?.titleIcon || '',
        titleAlign: 'left',
        showHeader: true,
        headerHeight: 36,
        headerBackground: 'transparent',
        titleColor: tokens.text,
        borderStyle: options?.borderStyle || 'solid',
        borderColor: options?.borderStyle === 'gradient'
            ? [tokens.accent, tokens.info]
            : [tokens.panelBorder],
        borderWidth: 1,
        borderRadius: 12,
        backgroundColor: tokens.panel,
        backdropBlur: options?.backdropBlur ?? (isDark ? 4 : 0),
        shadow: options?.shadow || 'subtle',
        padding: 16,
    });
}

/** Stat card */
export function statCard(
    tokens: EnterpriseTokens,
    title: string,
    value: string,
    x: number, y: number, w = 220, h = 96,
    options?: {
        suffix?: string;
        trend?: 'up' | 'down' | 'none';
        trendValue?: string;
        icon?: string;
        accentColor?: string;
    },
): ScreenComponent {
    return comp('stat-card', title, x, y, w, h, 50, {
        title,
        value,
        suffix: options?.suffix || '',
        trend: options?.trend || 'none',
        trendValue: options?.trendValue || '',
        icon: options?.icon || '',
        accentColor: options?.accentColor || tokens.accent,
        showAccentBar: true,
        borderRadius: 10,
        backgroundColor: tokens.panel,
        shadow: 'subtle',
        valueColor: tokens.text,
        titleColor: tokens.textSecondary,
    });
}

/** Chart placeholder inside a section panel (positioned relative to panel) */
export function chart(
    type: ScreenComponent['type'],
    name: string,
    x: number, y: number, w: number, h: number,
    config: Record<string, unknown>,
    zIndex = 20,
): ScreenComponent {
    return comp(type, name, x, y, w, h, zIndex, config);
}

/** Horizontal gradient divider */
export function gradientDivider(
    tokens: EnterpriseTokens,
    x: number, y: number, w: number,
): ScreenComponent {
    return comp('divider', '分隔线', x, y, w, 12, 5, {
        direction: 'horizontal',
        lineStyle: 'gradient',
        lineWidth: 1,
        gradientColors: ['transparent', tokens.divider, 'transparent'],
    });
}

/** Standard 3-column grid layout for enterprise dashboards */
export function threeColumnGrid(
    tokens: EnterpriseTokens,
    opts: {
        startY: number;
        screenWidth: number;
        rowHeight: number;
        gap: number;
        edgePadding: number;
        sections: Array<{
            title: string;
            titleIcon?: string;
            chartType: ScreenComponent['type'];
            chartName: string;
            chartConfig: Record<string, unknown>;
        }>;
    },
): ScreenComponent[] {
    const { startY, screenWidth, rowHeight, gap, edgePadding, sections } = opts;
    const colCount = 3;
    const totalGap = gap * (colCount - 1) + edgePadding * 2;
    const colWidth = Math.floor((screenWidth - totalGap) / colCount);
    const components: ScreenComponent[] = [];

    sections.forEach((sec, i) => {
        const row = Math.floor(i / colCount);
        const col = i % colCount;
        const x = edgePadding + col * (colWidth + gap);
        const y = startY + row * (rowHeight + gap);

        // Section panel
        components.push(sectionPanel(tokens, sec.title, x, y, colWidth, rowHeight, { titleIcon: sec.titleIcon }));

        // Chart inside panel (offset by panel header + padding)
        const chartX = x + 16;
        const chartY = y + 36 + 8; // header + gap
        const chartW = colWidth - 32;
        const chartH = rowHeight - 36 - 24; // header - padding
        components.push(chart(sec.chartType, sec.chartName, chartX, chartY, chartW, chartH, sec.chartConfig));
    });

    return components;
}

/** KPI row with stat cards */
export function kpiRow(
    tokens: EnterpriseTokens,
    startX: number,
    y: number,
    cardWidth: number,
    gap: number,
    kpis: Array<{
        title: string;
        value: string;
        suffix?: string;
        trend?: 'up' | 'down' | 'none';
        trendValue?: string;
        icon?: string;
        accentColor?: string;
    }>,
): ScreenComponent[] {
    return kpis.map((kpi, i) =>
        statCard(tokens, kpi.title, kpi.value, startX + i * (cardWidth + gap), y, cardWidth, 96, kpi),
    );
}
