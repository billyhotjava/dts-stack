// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import type { CSSProperties, ReactNode } from 'react';
import type { RendererPlugin, RendererPluginRenderContext } from '../types';
import { buildPluginRuntimeId } from '../registry';
import { SCREEN_DEFAULT_FONT_FAMILY } from '../../screenTypography';

const COLORS = {
    page: '#f4f8fc',
    card: '#ffffff',
    header: '#2e73d6',
    headerSoft: '#e9f2ff',
    text: '#15233b',
    muted: '#5f728c',
    border: '#d7e3f2',
    accent: '#3b82f6',
    accentSoft: '#dbeafe',
    success: '#16a34a',
    warning: '#d97706',
    danger: '#dc2626',
};

const FONT_STACK = SCREEN_DEFAULT_FONT_FAMILY;

function px(value: unknown, fallback: number): number {
    return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}

function text(value: unknown, fallback = ''): string {
    return typeof value === 'string' && value.trim() ? value.trim() : fallback;
}

function hasOwnConfig(config: Record<string, unknown>, key: string): boolean {
    return Object.prototype.hasOwnProperty.call(config, key) && config[key] !== undefined;
}

function rawText(value: unknown, fallback = ''): string {
    if (typeof value === 'string') return value;
    if (value == null) return fallback;
    return String(value);
}

function titleFontSize(config: Record<string, unknown>, fallback: number): number {
    return px(config.titleFontSize, fallback);
}

function titleColor(config: Record<string, unknown>, fallback: string): string {
    return text(config.titleColor, fallback);
}

function sectionTitleStyle(config: Record<string, unknown>, fallbackSize = 15): CSSProperties {
    return {
        marginBottom: 12,
        fontSize: titleFontSize(config, fallbackSize),
        fontWeight: 800,
        color: titleColor(config, COLORS.text),
    };
}

function panelStyle(config: Record<string, unknown> = {}): CSSProperties {
    return {
        width: '100%',
        height: '100%',
        padding: 16,
        borderRadius: px(config.borderRadius, 18),
        background: text(config.backgroundColor, COLORS.card),
        border: `1px solid ${text(config.borderColor, COLORS.border)}`,
        boxShadow: '0 10px 24px rgba(21,35,59,0.05)',
        fontFamily: text(config.fontFamily, FONT_STACK),
        color: COLORS.text,
        overflow: 'hidden',
    };
}

function tableHeaderStyle(config: Record<string, unknown>): CSSProperties {
    return {
        padding: '12px 14px',
        fontSize: px(config.headerFontSize, 12),
        fontWeight: 800,
        color: text(config.headerColor, COLORS.text),
        borderBottom: `1px solid ${text(config.borderColor, COLORS.border)}`,
    };
}

function tableRowBackground(config: Record<string, unknown>, index: number): string {
    const fallback = index % 2 === 0 ? COLORS.card : '#f9fbfe';
    if (index % 2 === 0) {
        return text(config.oddRowBackground, fallback);
    }
    return text(config.evenRowBackground, fallback);
}

function tableCellStyle(config: Record<string, unknown>, isFirstColumn: boolean): CSSProperties {
    return {
        padding: '12px 14px',
        fontSize: px(config.fontSize, 12),
        color: text(config.bodyColor, isFirstColumn ? COLORS.text : COLORS.muted),
        fontWeight: isFirstColumn ? 700 : 600,
        borderBottom: `1px solid ${text(config.borderColor, COLORS.border)}`,
    };
}

function valueText(value: unknown, fallback = '', precision?: number): string {
    if (typeof value === 'string' && value.trim()) return value.trim();
    if (typeof value === 'number' && Number.isFinite(value)) {
        const resolvedPrecision = Number.isInteger(precision as number)
            ? Number(precision)
            : Number.isInteger(value)
                ? 0
                : 2;
        return value.toLocaleString('zh-CN', {
            minimumFractionDigits: resolvedPrecision,
            maximumFractionDigits: resolvedPrecision,
        });
    }
    return fallback;
}

function stringArray(value: unknown, fallback: string[] = []): string[] {
    if (!Array.isArray(value)) return fallback;
    return value
        .map((item) => (typeof item === 'string' ? item.trim() : ''))
        .filter(Boolean);
}

function rowArray(value: unknown): Array<Record<string, unknown>> {
    if (!Array.isArray(value)) return [];
    return value.filter((item): item is Record<string, unknown> => !!item && typeof item === 'object');
}

function matrixArray(value: unknown): unknown[][] {
    if (!Array.isArray(value)) return [];
    return value.filter((item): item is unknown[] => Array.isArray(item));
}

function cardDataRows(context: RendererPluginRenderContext): Array<Record<string, unknown>> {
    const cols = context.data?.cols ?? [];
    const rows = context.data?.rows ?? [];
    if (!cols.length || !rows.length) return [];
    return rows.map((row) =>
        cols.reduce<Record<string, unknown>>((out, col, index) => {
            const displayName = typeof col.display_name === 'string' && col.display_name.trim()
                ? col.display_name.trim()
                : col.name;
            out[col.name] = row[index];
            out[displayName] = row[index];
            out[`col${index + 1}`] = row[index];
            return out;
        }, {}),
    );
}

function cardDataHeaders(context: RendererPluginRenderContext): string[] {
    const cols = context.data?.cols ?? [];
    if (!cols.length) return [];
    return cols.map((col) => {
        if (typeof col.display_name === 'string' && col.display_name.trim()) {
            return col.display_name.trim();
        }
        return col.name;
    });
}

function resolveFieldValue(
    row: Record<string, unknown>,
    explicitField: unknown,
    fallbackIndex: number,
): unknown {
    const fieldName = typeof explicitField === 'string' && explicitField.trim()
        ? explicitField.trim()
        : `col${fallbackIndex + 1}`;
    return row[fieldName];
}

export function createFinanceRendererPlugin(options: {
    pluginId: string;
    componentId: string;
    version?: string;
    name: string;
    baseType: RendererPlugin['baseType'];
    render: (context: RendererPluginRenderContext) => ReactNode;
}): RendererPlugin {
    return {
        id: buildPluginRuntimeId(options.pluginId, options.componentId, options.version),
        name: options.name,
        version: options.version || '1.0.0',
        baseType: options.baseType,
        render: options.render,
    };
}

export function FinanceShell(context: RendererPluginRenderContext): ReactNode {
    const title = text(context.config.title, context.component.name || '财务分析');
    const subtitle = text(context.config.subtitle, 'Finance cockpit');
    return (
        <section
            style={{
                width: '100%',
                height: '100%',
                borderRadius: 22,
                background: `linear-gradient(180deg, ${COLORS.card} 0%, ${COLORS.page} 100%)`,
                border: `1px solid ${COLORS.border}`,
                boxShadow: '0 14px 40px rgba(22,35,59,0.08)',
                padding: 24,
                fontFamily: FONT_STACK,
                color: COLORS.text,
                overflow: 'hidden',
            }}
        >
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
                <div
                    style={{
                        width: 10,
                        height: 48,
                        borderRadius: 999,
                        background: `linear-gradient(180deg, ${COLORS.header} 0%, ${COLORS.accent} 100%)`,
                    }}
                />
                <div>
                    <div style={{ fontSize: 28, fontWeight: 800, letterSpacing: 0.5 }}>{title}</div>
                    <div style={{ marginTop: 4, fontSize: 13, color: COLORS.muted }}>{subtitle}</div>
                </div>
            </div>
            <div
                style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(12, 1fr)',
                    gap: 12,
                    minHeight: 0,
                }}
            >
                {createPlaceholderTiles(stringArray(context.config.sections, ['核心指标', '结构分析', '明细清单']))}
            </div>
        </section>
    );
}

export function FinanceHeaderBar(context: RendererPluginRenderContext): ReactNode {
    const title = text(context.config.title, '财务专题大屏');
    const subtitle = text(context.config.subtitle, 'Financial Operations Dashboard');
    const org = text(context.config.orgName, 'BI数据平台');
    const dateText = text(context.config.dateText, '数据日期：2026-03-30');
    return (
        <header
            style={{
                width: '100%',
                height: '100%',
                padding: '14px 18px',
                borderRadius: 20,
                background: `linear-gradient(135deg, ${COLORS.header} 0%, #4f97ff 100%)`,
                color: '#fff',
                fontFamily: FONT_STACK,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 16,
                boxShadow: '0 12px 32px rgba(46,115,214,0.28)',
            }}
        >
            <div style={{ minWidth: 0 }}>
                <div style={{ fontSize: titleFontSize(context.config, 24), fontWeight: 800, letterSpacing: 0.5 }}>{title}</div>
                <div style={{ marginTop: 6, fontSize: 13, opacity: 0.88 }}>{subtitle}</div>
            </div>
            <div style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                <div style={{ fontSize: 14, fontWeight: 700 }}>{org}</div>
                <div style={{ marginTop: 6, fontSize: 12, opacity: 0.88 }}>{dateText}</div>
            </div>
        </header>
    );
}

export function FinanceFilterStrip(context: RendererPluginRenderContext): ReactNode {
    const filters = stringArray(context.config.filters, ['年度: 2026', '单位: 全部', '状态: 全部']);
    return (
        <section
            style={{
                width: '100%',
                height: '100%',
                padding: 14,
                borderRadius: 18,
                background: COLORS.card,
                border: `1px solid ${COLORS.border}`,
                fontFamily: FONT_STACK,
                display: 'flex',
                flexWrap: 'wrap',
                gap: 10,
                alignContent: 'flex-start',
            }}
        >
            {filters.map((item, index) => (
                <div
                    key={`${item}-${index}`}
                    style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        gap: 6,
                        padding: '8px 12px',
                        borderRadius: 999,
                        background: COLORS.headerSoft,
                        color: COLORS.text,
                        border: `1px solid ${COLORS.border}`,
                        fontSize: 12,
                        fontWeight: 600,
                    }}
                >
                    <span style={{ color: COLORS.header }}>●</span>
                    <span>{item}</span>
                </div>
            ))}
        </section>
    );
}

export function FinanceKpiCard(context: RendererPluginRenderContext): ReactNode {
    const title = text(context.config.title, context.component.name || '指标卡');
    const value = valueText(
        context.config.value,
        '12,580.00',
        typeof context.config.precision === 'number' ? context.config.precision : undefined,
    );
    const prefix = hasOwnConfig(context.config, 'prefix')
        ? rawText(context.config.prefix)
        : '';
    const suffix = hasOwnConfig(context.config, 'suffix')
        ? rawText(context.config.suffix)
        : text(context.config.unit, '万元');
    const unit = text(context.config.unit, '万元');
    const hint = text(context.config.hint, '较上期 +12.6%');
    const tone = text(context.config.tone, 'accent');
    const toneColor = resolveToneColor(tone);
    const backgroundColor = text(context.config.backgroundColor, COLORS.card);
    const titleColor = text(context.config.titleColor, COLORS.text);
    const valueColor = text(context.config.valueColor, COLORS.text);
    const fontFamily = text(context.config.fontFamily, FONT_STACK);
    const titleFontSize = px(context.config.titleFontSize, 14);
    const valueFontSize = px(context.config.valueFontSize, 34);
    const borderRadius = px(context.config.borderRadius, 18);
    const shadow = text(context.config.shadow, 'subtle');
    const borderColor = text(context.config.borderColor, COLORS.border);
    const shadowMap: Record<string, string> = {
        none: 'none',
        subtle: '0 10px 24px rgba(21,35,59,0.06)',
        medium: '0 14px 32px rgba(21,35,59,0.1)',
    };
    return (
        <section
            style={{
                width: '100%',
                height: '100%',
                padding: '18px 20px',
                borderRadius,
                background: backgroundColor,
                border: `1px solid ${borderColor}`,
                boxShadow: shadowMap[shadow] || shadowMap.subtle,
                fontFamily,
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between',
            }}
        >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10 }}>
                <span style={{ fontSize: titleFontSize, fontWeight: 700, color: titleColor }}>{title}</span>
                <span
                    style={{
                        minWidth: 10,
                        height: 10,
                        borderRadius: 999,
                        background: toneColor,
                        boxShadow: `0 0 0 6px ${alpha(toneColor, 0.12)}`,
                    }}
                />
            </div>
            <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8 }}>
                {prefix ? <span style={{ marginBottom: 4, fontSize: 14, color: COLORS.muted }}>{prefix}</span> : null}
                <span style={{ fontSize: valueFontSize, fontWeight: 800, lineHeight: 1, color: valueColor }}>{value}</span>
                {suffix ? <span style={{ marginBottom: 4, fontSize: 14, color: COLORS.muted }}>{suffix}</span> : null}
            </div>
            <div style={{ fontSize: 12, fontWeight: 600, color: toneColor }}>{hint || unit}</div>
        </section>
    );
}

export function FinanceRankingList(context: RendererPluginRenderContext): ReactNode {
    const title = text(context.config.title, context.component.name || 'TOP 排名');
    const rows = rowArray(context.config.items);
    const dataRows = cardDataRows(context);
    const maxItems = px(context.config.maxItems, 8);
    const list = rows.length > 0
        ? rows
        : dataRows.length > 0
            ? dataRows.map((row) => ({
                name: resolveFieldValue(row, context.config.nameField, 0),
                value: resolveFieldValue(row, context.config.valueField, 1),
                extra: resolveFieldValue(row, context.config.extraField, 2),
            }))
            : [
                { name: '项目A', value: '1,250.00', extra: '同比 +8.2%' },
                { name: '项目B', value: '930.50', extra: '同比 +5.7%' },
                { name: '项目C', value: '640.00', extra: '同比 -2.1%' },
            ];
    return (
        <section style={panelStyle(context.config)}>
            <div style={sectionTitleStyle(context.config)}>{title}</div>
            <div style={{ display: 'grid', gap: 10 }}>
                {list.slice(0, maxItems).map((item, index) => (
                    <div
                        key={`${item.name ?? 'row'}-${index}`}
                        style={{
                            display: 'grid',
                            gridTemplateColumns: '34px 1fr auto',
                            alignItems: 'center',
                            gap: 10,
                            padding: '10px 12px',
                            borderRadius: 14,
                            background: index < 3 ? COLORS.headerSoft : '#f8fbff',
                            border: `1px solid ${COLORS.border}`,
                        }}
                    >
                        <div
                            style={{
                                width: 34,
                                height: 34,
                                borderRadius: 12,
                                display: 'grid',
                                placeItems: 'center',
                                background: index < 3 ? COLORS.header : COLORS.accentSoft,
                                color: index < 3 ? '#fff' : COLORS.header,
                                fontWeight: 800,
                                fontSize: 14,
                            }}
                        >
                            {index + 1}
                        </div>
                        <div style={{ minWidth: 0 }}>
                            <div style={{ fontSize: 13, fontWeight: 700, color: COLORS.text }}>{valueText(item.name, `项目${index + 1}`)}</div>
                            <div style={{ marginTop: 4, fontSize: 11, color: COLORS.muted }}>{valueText(item.extra, '本月累计')}</div>
                        </div>
                        <div style={{ fontSize: 15, fontWeight: 800, color: COLORS.header }}>{valueText(item.value, '--')}</div>
                    </div>
                ))}
            </div>
        </section>
    );
}

export function FinanceSummaryTable(context: RendererPluginRenderContext): ReactNode {
    const title = text(context.config.title, context.component.name || '财务明细');
    const mappedHeaders = stringArray(context.config.header);
    const rawHeaders = cardDataHeaders(context);
    const pluginHeaders = stringArray(context.config.headers);
    const headers = mappedHeaders.length > 0
        ? mappedHeaders
        : pluginHeaders.length > 0
            ? pluginHeaders
            : rawHeaders.length > 0
                ? rawHeaders
                : ['项目', '预算', '执行', '余额'];
    const rows = rowArray(context.config.rows);
    const dataRows = matrixArray(context.config.data).map((row) =>
        headers.reduce<Record<string, unknown>>((out, _, index) => {
            out[`col${index + 1}`] = row[index];
            return out;
        }, {}),
    );
    const rawRows = cardDataRows(context);
    const maxRows = px(context.config.maxRows, 6);
    const data = dataRows.length > 0
        ? dataRows
        : rows.length > 0
            ? rows
            : rawRows.length > 0
                ? rawRows
                : [
                    { col1: '科研项目A', col2: '1,200', col3: '860', col4: '340' },
                    { col1: '科研项目B', col2: '980', col3: '760', col4: '220' },
                    { col1: '科研项目C', col2: '760', col3: '540', col4: '220' },
                ];
    return (
        <section style={panelStyle(context.config)}>
            <div style={sectionTitleStyle(context.config)}>{title}</div>
            <div style={{ overflow: 'hidden', borderRadius: 14, border: `1px solid ${text(context.config.borderColor, COLORS.border)}` }}>
                <div
                    style={{
                        display: 'grid',
                        gridTemplateColumns: `repeat(${headers.length}, minmax(0, 1fr))`,
                        background: text(context.config.headerBackground, COLORS.headerSoft),
                    }}
                >
                    {headers.map((header) => (
                        <div key={header} style={tableHeaderStyle(context.config)}>{header}</div>
                    ))}
                </div>
                {data.slice(0, maxRows).map((row, index) => (
                    <div
                        key={`table-row-${index}`}
                        style={{
                            display: 'grid',
                            gridTemplateColumns: `repeat(${headers.length}, minmax(0, 1fr))`,
                            background: tableRowBackground(context.config, index),
                        }}
                    >
                        {headers.map((_, headerIndex) => (
                            <div key={`cell-${headerIndex}`} style={tableCellStyle(context.config, headerIndex === 0)}>
                                {valueText(row[`col${headerIndex + 1}`], '--')}
                            </div>
                        ))}
                    </div>
                ))}
            </div>
        </section>
    );
}

export function FinanceNotePanel(context: RendererPluginRenderContext): ReactNode {
    const title = text(context.config.title, '口径说明');
    const notes = stringArray(context.config.notes, [
        '金额单位默认按万元展示，明细可在属性面板中改写。',
        '同比、环比等指标建议通过数据源预聚合后再接入。',
        '模板块均可拖拽、替换和二次编辑，不锁定布局。',
    ]);
    return (
        <section style={panelStyle(context.config)}>
            <div style={sectionTitleStyle(context.config)}>{title}</div>
            <div style={{ display: 'grid', gap: 10 }}>
                {notes.map((item, index) => (
                    <div
                        key={`${item}-${index}`}
                        style={{
                            display: 'grid',
                            gridTemplateColumns: '18px 1fr',
                            gap: 10,
                            alignItems: 'start',
                            fontSize: 13,
                            lineHeight: 1.7,
                            color: COLORS.text,
                        }}
                    >
                        <span style={{ color: COLORS.header, fontWeight: 900 }}>{index + 1}</span>
                        <span>{item}</span>
                    </div>
                ))}
            </div>
        </section>
    );
}

export function FinanceStatusGrid(context: RendererPluginRenderContext): ReactNode {
    const title = text(context.config.title, context.component.name || '状态总览');
    const cards = rowArray(context.config.items);
    const dataRows = cardDataRows(context);
    const list = cards.length > 0
        ? cards
        : dataRows.length > 0
            ? dataRows.map((row) => ({
                title: resolveFieldValue(row, context.config.titleField, 0),
                value: resolveFieldValue(row, context.config.valueField, 1),
                hint: resolveFieldValue(row, context.config.hintField, 2),
                tone: resolveFieldValue(row, context.config.toneField, 3),
            }))
            : [
                { title: '执行健康', value: '良好', hint: '执行率 82%', tone: 'success' },
                { title: '预警项目', value: '3', hint: '需重点关注', tone: 'warning' },
                { title: '预算偏差', value: '1.8%', hint: '低于阈值', tone: 'accent' },
                { title: '风险状态', value: '可控', hint: '无新增风险', tone: 'success' },
            ];
    return (
        <section style={panelStyle(context.config)}>
            <div style={sectionTitleStyle(context.config)}>{title}</div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 12 }}>
                {list.slice(0, 6).map((item, index) => {
                    const toneColor = resolveToneColor(text(item.tone, 'accent'));
                    return (
                        <div
                            key={`${item.title ?? 'card'}-${index}`}
                            style={{
                                padding: '14px 16px',
                                borderRadius: 16,
                                background: '#f8fbff',
                                border: `1px solid ${COLORS.border}`,
                                boxShadow: `inset 0 0 0 1px ${alpha(toneColor, 0.08)}`,
                            }}
                        >
                            <div style={{ fontSize: 12, color: COLORS.muted, fontWeight: 700 }}>{valueText(item.title, `状态${index + 1}`)}</div>
                            <div style={{ marginTop: 8, fontSize: 24, fontWeight: 800, color: COLORS.text }}>{valueText(item.value, '--')}</div>
                            <div style={{ marginTop: 6, fontSize: 12, color: toneColor, fontWeight: 700 }}>{valueText(item.hint, '')}</div>
                        </div>
                    );
                })}
            </div>
        </section>
    );
}

function createPlaceholderTiles(labels: string[]): ReactNode[] {
    return labels.map((item, index) => (
        <div
            key={`${item}-${index}`}
            style={{
                gridColumn: 'span 4 / span 4',
                minHeight: 120,
                borderRadius: 18,
                border: `1px dashed ${COLORS.border}`,
                background: index % 2 === 0 ? COLORS.card : '#f9fbfe',
                padding: 16,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontWeight: 700,
                color: COLORS.muted,
                textAlign: 'center',
            }}
        >
            {item}
        </div>
    ));
}

function resolveToneColor(tone: string): string {
    switch (tone) {
        case 'success':
            return COLORS.success;
        case 'warning':
            return COLORS.warning;
        case 'danger':
            return COLORS.danger;
        default:
            return COLORS.accent;
    }
}

function alpha(hex: string, opacity: number): string {
    if (!hex.startsWith('#') || (hex.length !== 7 && hex.length !== 4)) {
        return hex;
    }
    const normalized = hex.length === 4
        ? `#${hex.slice(1).split('').map((item) => item + item).join('')}`
        : hex;
    const value = Math.round(Math.max(0, Math.min(1, opacity)) * 255)
        .toString(16)
        .padStart(2, '0');
    return `${normalized}${value}`;
}
