import { memo, useMemo, useEffect, useRef, useState, type ComponentType } from 'react';
import type { CardData, CardParameterBinding, ComponentInteractionMapping, ScreenComponent, ScreenTheme } from '../types';
import { DRILLABLE_TYPES } from '../types';
import { useCardDataSource } from '../hooks/useCardDataSource';
import { useDrillDown } from '../hooks/useDrillDown';
import { useScreenRuntime } from '../ScreenRuntimeContext';
import { mapCardDataToConfig } from '../hooks/cardDataMapper';
import { getThemeTokens, ScreenThemeTokens } from '../screenThemes';
import { PluginRenderBoundary } from '../plugins/PluginRenderBoundary';
import { getRendererPlugin } from '../plugins/registry';
import { readComponentPluginMeta, resolveRuntimePluginId } from '../plugins/runtime';
import { useScreenPluginRuntime } from '../plugins/useScreenPluginRuntime';
import type { RendererPlugin } from '../plugins/types';

type ReactEChartsComponent = ComponentType<{
    style?: React.CSSProperties;
    option?: unknown;
    onEvents?: Record<string, (params: Record<string, unknown>) => void>;
}>;
type DataViewModule = typeof import('@jiaminghi/data-view-react');

const ECHART_COMPONENT_TYPES = new Set([
    'line-chart',
    'bar-chart',
    'pie-chart',
    'gauge-chart',
    'radar-chart',
    'funnel-chart',
    'scatter-chart',
    'map-chart',
]);

const DATAV_COMPONENT_TYPES = new Set([
    'border-box',
    'decoration',
    'scroll-board',
    'scroll-ranking',
    'water-level',
    'digital-flop',
]);

const MAP_PRESET_URLS: Record<string, string> = {
    china: 'https://geo.datav.aliyun.com/areas_v3/bound/100000_full.json',
    world: 'https://geo.datav.aliyun.com/areas_v3/bound/world.geo.json',
};

const mapGeoJsonFetchCache = new Map<string, Promise<unknown | null>>();

function resolvePresetMapUrl(scope?: string): string | undefined {
    const key = String(scope || '').trim().toLowerCase();
    if (!key) return undefined;
    return MAP_PRESET_URLS[key];
}

function fetchGeoJsonWithCache(url: string): Promise<unknown | null> {
    const key = String(url || '').trim();
    if (!key) return Promise.resolve(null);
    const cached = mapGeoJsonFetchCache.get(key);
    if (cached) return cached;
    const task = fetch(key, { credentials: 'omit' })
        .then((response) => {
            if (!response.ok) {
                throw new Error(`geojson fetch failed: ${response.status}`);
            }
            return response.json() as Promise<unknown>;
        })
        .catch((error) => {
            console.warn('[map-chart] failed to load geojson:', key, error);
            return null;
        });
    mapGeoJsonFetchCache.set(key, task);
    return task;
}

const LEGACY_LIGHT_TEXT_COLORS = new Set(["#fff", "#ffffff", "#e5e7eb", "#d1d5db", "#cbd5e1", "#94a3b8"]);

function resolveTextColor(candidate: string | undefined, fallback: string): string {
    if (!candidate || candidate.trim().length === 0) {
        return fallback;
    }
    const normalized = candidate.trim().toLowerCase();
    const fallbackNormalized = (fallback || "").trim().toLowerCase();
    if (LEGACY_LIGHT_TEXT_COLORS.has(normalized) && !LEGACY_LIGHT_TEXT_COLORS.has(fallbackNormalized)) {
        return fallback;
    }
    return candidate;
}

function normalizeParameterBindings(bindings: CardParameterBinding[] | undefined): CardParameterBinding[] {
    if (!Array.isArray(bindings)) return [];
    return bindings
        .map((item) => ({
            name: (item?.name ?? "").trim(),
            variableKey: (item?.variableKey ?? "").trim() || undefined,
            value: item?.value == null ? undefined : String(item.value),
        }))
        .filter((item) => item.name.length > 0);
}

function resolveDataSourceType(dataSource: ScreenComponent["dataSource"]): string {
    const sourceType = (dataSource as { sourceType?: string } | undefined)?.sourceType;
    const type = (dataSource as { type?: string } | undefined)?.type;
    const normalized = (sourceType || type || "").toLowerCase();
    if (normalized === "database") return "sql";
    return normalized;
}

function resolveInteractionValue(params: Record<string, unknown>, sourcePath: string): string | undefined {
    const path = (sourcePath || "").trim();
    if (!path) return undefined;

    const read = (obj: unknown, key: string): unknown => {
        if (!obj || typeof obj !== "object") return undefined;
        return (obj as Record<string, unknown>)[key];
    };

    const segments = path.split(".").filter((s) => s.length > 0);
    let current: unknown = params;
    for (const seg of segments) {
        current = read(current, seg);
    }

    if (current == null) return undefined;
    if (typeof current === "string") return current;
    if (typeof current === "number" || typeof current === "boolean") return String(current);
    return undefined;
}

function resolveInteractionUrlTemplate(template: string, params: Record<string, unknown>): string | undefined {
    const raw = String(template || '').trim();
    if (!raw) return undefined;
    const withValues = raw.replace(/\{\{\s*([^}]+)\s*\}\}/g, (_, path: string) => {
        const value = resolveInteractionValue(params, path);
        return value == null ? '' : encodeURIComponent(value);
    }).trim();
    if (!withValues) return undefined;
    if (/^https?:\/\//i.test(withValues) || withValues.startsWith('/')) {
        return withValues;
    }
    return undefined;
}

function resolveFilterOptions(raw: unknown): Array<{ label: string; value: string }> {
    if (!Array.isArray(raw)) return [];
    const out: Array<{ label: string; value: string }> = [];
    for (const item of raw) {
        if (typeof item === 'string') {
            const text = item.trim();
            if (text) out.push({ label: text, value: text });
            continue;
        }
        if (item && typeof item === 'object') {
            const row = item as Record<string, unknown>;
            const value = String(row.value ?? '').trim();
            if (!value) continue;
            const label = String(row.label ?? value).trim() || value;
            out.push({ label, value });
        }
    }
    return out;
}

function resolveTabOptions(raw: unknown): Array<{ label: string; value: string }> {
    return resolveFilterOptions(raw);
}

function normalizeVisibilityMatchValues(raw: unknown): string[] {
    if (Array.isArray(raw)) {
        return raw
            .map((item) => String(item ?? '').trim())
            .filter((item) => item.length > 0);
    }
    const text = String(raw ?? '').trim();
    if (!text) return [];
    return text
        .split(/[\n,，]/g)
        .map((item) => item.trim())
        .filter((item) => item.length > 0);
}

function resolveComponentVariableVisibility(config: Record<string, unknown>, values: Record<string, string>): boolean {
    const enabled = config.visibilityRuleEnabled === true;
    if (!enabled) return true;
    const variableKey = String(config.visibilityVariableKey ?? '').trim();
    if (!variableKey) return true;
    const current = String(values?.[variableKey] ?? '').trim();
    const currentLower = current.toLowerCase();
    const mode = String(config.visibilityMatchMode ?? 'equals').trim().toLowerCase();
    const expectedValues = normalizeVisibilityMatchValues(
        config.visibilityMatchValues ?? config.visibilityMatchValue,
    );
    const expectedLower = expectedValues.map((item) => item.toLowerCase());
    const matched = expectedValues.length > 0 && expectedValues.includes(current);
    if (mode === 'not-equals' || mode === 'not-in') {
        return expectedValues.length === 0 ? true : !matched;
    }
    if (mode === 'contains') {
        if (expectedLower.length === 0) return true;
        return expectedLower.some((item) => item.length > 0 && currentLower.includes(item));
    }
    if (mode === 'not-contains') {
        if (expectedLower.length === 0) return true;
        return expectedLower.every((item) => item.length === 0 || !currentLower.includes(item));
    }
    if (mode === 'starts-with') {
        if (expectedLower.length === 0) return true;
        return expectedLower.some((item) => item.length > 0 && currentLower.startsWith(item));
    }
    if (mode === 'ends-with') {
        if (expectedLower.length === 0) return true;
        return expectedLower.some((item) => item.length > 0 && currentLower.endsWith(item));
    }
    if (mode === 'empty') {
        return current.length === 0;
    }
    if (mode === 'not-empty') {
        return current.length > 0;
    }
    return expectedValues.length === 0 ? true : matched;
}

function resolveColumnIndex(cols: CardData['cols'], rawField: unknown, fallback = 0): number {
    if (!Array.isArray(cols) || cols.length === 0) {
        return -1;
    }
    const field = String(rawField ?? '').trim();
    if (!field) {
        return Math.max(0, Math.min(cols.length - 1, fallback));
    }
    if (/^\d+$/.test(field)) {
        const byNumber = Number(field) - 1;
        if (Number.isFinite(byNumber) && byNumber >= 0 && byNumber < cols.length) {
            return byNumber;
        }
    }
    const normalized = field.toLowerCase();
    const byName = cols.findIndex((col) => String(col.name || '').toLowerCase() === normalized);
    if (byName >= 0) return byName;
    const byDisplayName = cols.findIndex((col) => String(col.display_name || '').toLowerCase() === normalized);
    if (byDisplayName >= 0) return byDisplayName;
    return Math.max(0, Math.min(cols.length - 1, fallback));
}

function resolveFilterOptionsFromData(data: CardData | null, config: Record<string, unknown>): Array<{ label: string; value: string }> {
    if (!data || !Array.isArray(data.rows) || !Array.isArray(data.cols) || data.cols.length === 0) {
        return [];
    }
    const maxRaw = Number(config.dataOptionMax ?? 200);
    const maxOptions = Number.isFinite(maxRaw) ? Math.max(1, Math.min(2000, Math.floor(maxRaw))) : 200;
    const valueIndex = resolveColumnIndex(data.cols, config.dataOptionValueField, 0);
    if (valueIndex < 0) {
        return [];
    }
    const labelIndex = resolveColumnIndex(data.cols, config.dataOptionLabelField, valueIndex);
    const dedupe = new Set<string>();
    const out: Array<{ label: string; value: string }> = [];
    for (const row of data.rows) {
        if (!Array.isArray(row)) continue;
        const valueRaw = row[valueIndex];
        if (valueRaw == null) continue;
        const value = String(valueRaw).trim();
        if (!value || dedupe.has(value)) continue;
        const labelRaw = row[labelIndex];
        const label = String(labelRaw ?? value).trim() || value;
        dedupe.add(value);
        out.push({ label, value });
        if (out.length >= maxOptions) break;
    }
    return out;
}

function normalizeFilterDebounceMs(raw: unknown): number {
    const value = Number(raw ?? 0);
    if (!Number.isFinite(value) || value <= 0) return 0;
    return Math.max(50, Math.min(5000, Math.round(value)));
}

function normalizeCarouselItems(raw: unknown): string[] {
    if (Array.isArray(raw)) {
        return raw
            .map((item) => String(item ?? '').trim())
            .filter((item) => item.length > 0)
            .slice(0, 200);
    }
    const text = String(raw ?? '').trim();
    if (!text) return [];
    return text
        .split(/\r?\n/g)
        .map((item) => item.trim())
        .filter((item) => item.length > 0)
        .slice(0, 200);
}

function resolveCarouselItemsFromData(data: CardData | null, config: Record<string, unknown>): string[] {
    if (!data || !Array.isArray(data.rows) || data.rows.length === 0 || !Array.isArray(data.cols) || data.cols.length === 0) {
        return [];
    }
    const max = Number(config.dataItemMax ?? 50);
    const safeMax = Number.isFinite(max) ? Math.max(1, Math.min(500, Math.floor(max))) : 50;
    const contentIndex = resolveColumnIndex(data.cols, config.dataItemField, 0);
    if (contentIndex < 0) {
        return [];
    }
    const out: string[] = [];
    for (const row of data.rows) {
        if (!Array.isArray(row) || row.length <= contentIndex) continue;
        const text = String(row[contentIndex] ?? '').trim();
        if (!text) continue;
        out.push(text);
        if (out.length >= safeMax) break;
    }
    return out;
}

function escapeHtml(input: string): string {
    return input
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

function renderMarkdownToHtml(input: string): string {
    const lines = input.replace(/\r\n/g, '\n').split('\n');
    const out: string[] = [];
    let listType: 'ul' | 'ol' | null = null;
    let inCodeBlock = false;
    let codeLines: string[] = [];

    const renderInlineMarkdown = (text: string) => {
        return escapeHtml(text)
            .replace(/\[(.+?)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noreferrer">$1</a>')
            .replace(/`([^`]+)`/g, '<code style="padding:1px 4px;border-radius:4px;background:rgba(148,163,184,0.18);">$1</code>')
            .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
            .replace(/\*(.+?)\*/g, '<em>$1</em>');
    };

    const closeList = () => {
        if (listType) {
            out.push(listType === 'ul' ? '</ul>' : '</ol>');
            listType = null;
        }
    };

    const flushCodeBlock = () => {
        if (!inCodeBlock) return;
        const code = escapeHtml(codeLines.join('\n'));
        out.push(
            '<pre style="margin:8px 0;padding:10px 12px;border-radius:6px;background:rgba(15,23,42,0.85);border:1px solid rgba(148,163,184,0.25);overflow:auto;">'
            + `<code style="font-family:Consolas,Monaco,monospace;font-size:12px;line-height:1.5;">${code}</code>`
            + '</pre>',
        );
        inCodeBlock = false;
        codeLines = [];
    };

    for (const line of lines) {
        const raw = line.trim();
        if (raw.startsWith('```')) {
            closeList();
            if (inCodeBlock) {
                flushCodeBlock();
            } else {
                inCodeBlock = true;
                codeLines = [];
            }
            continue;
        }
        if (inCodeBlock) {
            codeLines.push(line);
            continue;
        }
        if (!raw) {
            closeList();
            out.push('<br/>');
            continue;
        }
        if (raw === '---' || raw === '***') {
            closeList();
            out.push('<hr style="border:none;border-top:1px solid rgba(148,163,184,0.3);margin:10px 0;"/>');
            continue;
        }
        if (raw.startsWith('### ')) {
            closeList();
            out.push(`<h3>${escapeHtml(raw.slice(4))}</h3>`);
            continue;
        }
        if (raw.startsWith('## ')) {
            closeList();
            out.push(`<h2>${escapeHtml(raw.slice(3))}</h2>`);
            continue;
        }
        if (raw.startsWith('# ')) {
            closeList();
            out.push(`<h1>${escapeHtml(raw.slice(2))}</h1>`);
            continue;
        }
        if (raw.startsWith('> ')) {
            closeList();
            out.push(
                '<blockquote style="margin:8px 0;padding:6px 10px;border-left:3px solid rgba(59,130,246,0.65);background:rgba(59,130,246,0.08);">'
                + `${renderInlineMarkdown(raw.slice(2))}`
                + '</blockquote>',
            );
            continue;
        }
        if (raw.startsWith('- ') || raw.startsWith('* ')) {
            if (listType !== 'ul') {
                closeList();
                out.push('<ul>');
                listType = 'ul';
            }
            out.push(`<li>${renderInlineMarkdown(raw.slice(2))}</li>`);
            continue;
        }
        const orderedMatch = raw.match(/^(\d+)\.\s+(.+)$/);
        if (orderedMatch) {
            if (listType !== 'ol') {
                closeList();
                out.push('<ol>');
                listType = 'ol';
            }
            out.push(`<li>${renderInlineMarkdown(orderedMatch[2])}</li>`);
            continue;
        }
        closeList();
        const safe = renderInlineMarkdown(raw);
        out.push(`<p>${safe}</p>`);
    }
    closeList();
    flushCodeBlock();
    return out.join('');
}

function compareTableValues(a: unknown, b: unknown): number {
    const na = Number(a);
    const nb = Number(b);
    if (Number.isFinite(na) && Number.isFinite(nb)) {
        return na - nb;
    }
    return String(a ?? '').localeCompare(String(b ?? ''), 'zh-CN');
}

function resolveTableConditionalStyle(
    rules: unknown,
    columnIndex: number,
    raw: unknown,
    columnMeta?: { key?: string; title?: string },
): { color?: string; background?: string } {
    if (!Array.isArray(rules)) {
        return {};
    }
    const normalizedCurrentKey = String(columnMeta?.key ?? '').trim().toLowerCase();
    const normalizedCurrentTitle = String(columnMeta?.title ?? '').trim().toLowerCase();
    for (const item of rules) {
        if (!item || typeof item !== 'object') continue;
        const rule = item as Record<string, unknown>;
        const ruleColumnKey = String(rule.columnKey ?? '').trim().toLowerCase();
        const ruleColumnTitle = String(rule.columnTitle ?? '').trim().toLowerCase();
        let columnMatched = false;
        if (ruleColumnKey && normalizedCurrentKey) {
            columnMatched = ruleColumnKey === normalizedCurrentKey;
        }
        if (!columnMatched && ruleColumnTitle && normalizedCurrentTitle) {
            columnMatched = ruleColumnTitle === normalizedCurrentTitle;
        }
        if (!columnMatched) {
            const ruleCol = Number(rule.columnIndex);
            columnMatched = Number.isFinite(ruleCol) && ruleCol === columnIndex;
        }
        if (!columnMatched) continue;
        const operator = String(rule.operator || '').trim();
        const target = rule.value;
        const text = String(raw ?? '');
        const textLower = text.toLowerCase();
        const targetText = String(target ?? '');
        const targetLower = targetText.toLowerCase();
        const nRaw = Number(raw);
        const nTarget = Number(target);
        let matched = false;
        if (operator === 'contains') {
            matched = targetText.length > 0 && textLower.includes(targetLower);
        } else if (operator === 'not-contains') {
            matched = targetText.length === 0 || !textLower.includes(targetLower);
        } else if (operator === 'starts-with') {
            matched = targetText.length > 0 && textLower.startsWith(targetLower);
        } else if (operator === 'ends-with') {
            matched = targetText.length > 0 && textLower.endsWith(targetLower);
        } else if (operator === 'empty') {
            matched = text.trim().length === 0;
        } else if (operator === 'not-empty') {
            matched = text.trim().length > 0;
        } else if (Number.isFinite(nRaw) && Number.isFinite(nTarget)) {
            if (operator === '>') matched = nRaw > nTarget;
            if (operator === '>=') matched = nRaw >= nTarget;
            if (operator === '<') matched = nRaw < nTarget;
            if (operator === '<=') matched = nRaw <= nTarget;
            if (operator === '=' || operator === '==') matched = nRaw === nTarget;
            if (operator === '!=' || operator === '<>') matched = nRaw !== nTarget;
        } else {
            if (operator === '=' || operator === '==') matched = text === String(target ?? '');
            if (operator === '!=' || operator === '<>') matched = text !== String(target ?? '');
        }
        if (!matched) continue;
        return {
            color: typeof rule.color === 'string' ? rule.color : undefined,
            background: typeof rule.background === 'string' ? rule.background : undefined,
        };
    }
    return {};
}

/**
 * 自定义滚动表格，替代 DataV ScrollBoard（DataV 硬编码 color:#fff 无法覆盖）
 */
type ColumnAlign = 'left' | 'center' | 'right';
type ColumnFormatter = 'auto' | 'string' | 'number' | 'percent' | 'date';

interface ColumnEntry {
    source: string;
    alias?: string;
    align?: ColumnAlign;
    width?: number;
    wrap?: boolean;
    formatter?: ColumnFormatter;
}

interface SourceColumnMeta {
    name: string;
    displayName: string;
    baseType?: string;
}

interface ResolvedColumnMeta {
    key: string;
    title: string;
    align: ColumnAlign;
    width?: number;
    wrap: boolean;
    formatter: ColumnFormatter;
    baseType?: string;
}

interface ResolvedTableData {
    header: string[];
    data: string[][];
    columnMeta: ResolvedColumnMeta[];
}

function normalizeColumnAlign(value: unknown, fallback: ColumnAlign): ColumnAlign {
    if (value === 'left' || value === 'center' || value === 'right') return value;
    return fallback;
}

function normalizeColumnFormatter(value: unknown): ColumnFormatter {
    if (value === 'string' || value === 'number' || value === 'percent' || value === 'date') return value;
    return 'auto';
}

function clampColumnWidth(value: unknown): number | undefined {
    const n = Number(value);
    if (!Number.isFinite(n)) return undefined;
    if (n <= 0) return undefined;
    return Math.max(5, Math.min(100, n));
}

function formatTableCell(value: unknown, formatter: ColumnFormatter, baseType?: string): string {
    if (value == null) return '';

    const toNumber = () => {
        if (typeof value === 'number') return value;
        const n = Number(value);
        return Number.isFinite(n) ? n : undefined;
    };

    if (formatter === 'string') return String(value);
    if (formatter === 'number') {
        const n = toNumber();
        return n == null ? String(value) : n.toLocaleString('zh-CN');
    }
    if (formatter === 'percent') {
        const n = toNumber();
        if (n == null) return String(value);
        const pct = Math.abs(n) <= 1 ? n * 100 : n;
        return `${pct.toFixed(2)}%`;
    }
    if (formatter === 'date') {
        const d = value instanceof Date ? value : new Date(String(value));
        if (Number.isNaN(d.getTime())) return String(value);
        return d.toLocaleString('zh-CN', { hour12: false });
    }

    // auto: keep plain text to preserve historical behavior
    if (baseType && baseType.toLowerCase().includes('date')) {
        const d = new Date(String(value));
        if (!Number.isNaN(d.getTime())) {
            return d.toLocaleString('zh-CN', { hour12: false });
        }
    }
    return String(value);
}

/**
 * 自定义滚动表格，替代 DataV ScrollBoard（DataV 硬编码 color:#fff 无法覆盖）
 */
function ThemedScrollTable({ config, tokens }: {
    config: Record<string, unknown>;
    tokens: ScreenThemeTokens;
}) {
    const headers = config.header as string[] || [];
    const allData = config.data as string[][] || [];
    const columnMeta = config._columnMeta as ResolvedColumnMeta[] | undefined;
    const rowNum = config.rowNum as number || 8;
    const headerBGC = config.headerBGC as string || tokens.scrollBoard.headerBg;
    const oddRowBGC = config.oddRowBGC as string || tokens.scrollBoard.oddRowBg;
    const evenRowBGC = config.evenRowBGC as string || tokens.scrollBoard.evenRowBg;
    const textColor = tokens.scrollBoard.textColor;
    const headerColor = resolveTextColor(config.headerColor as string | undefined, textColor);
    const headerHeight = 35;

    // Auto-scroll animation
    const [offset, setOffset] = useState(0);
    const rowHeight = 38;
    const visibleHeight = rowNum * rowHeight;
    const needScroll = allData.length > rowNum;

    useEffect(() => {
        if (!needScroll) return;
        const waitTime = config.waitTime as number || 2000;
        const timer = setInterval(() => {
            setOffset(prev => {
                const next = prev + 1;
                return next >= allData.length ? 0 : next;
            });
        }, waitTime);
        return () => clearInterval(timer);
    }, [needScroll, allData.length, config.waitTime]);

    // Build visible rows (wrap around for seamless scrolling)
    const visibleRows: { cells: string[]; originalIndex: number }[] = [];
    for (let i = 0; i < Math.min(rowNum + 1, allData.length); i++) {
        const idx = (offset + i) % allData.length;
        visibleRows.push({ cells: allData[idx], originalIndex: idx });
    }

    const getColumnLayout = (index: number): React.CSSProperties => {
        const meta = columnMeta?.[index];
        const widthPercent = clampColumnWidth(meta?.width);
        return {
            flex: widthPercent ? `0 0 ${widthPercent}%` : '1 1 0',
            width: widthPercent ? `${widthPercent}%` : undefined,
            textAlign: normalizeColumnAlign(meta?.align, 'center'),
        };
    };

    return (
        <div style={{ width: '100%', height: '100%', overflow: 'hidden', color: textColor, fontSize: 14 }}>
            {headers.length > 0 && (
                <div style={{
                    display: 'flex', background: headerBGC, height: headerHeight,
                    lineHeight: `${headerHeight}px`, fontWeight: 600, fontSize: 15, flexShrink: 0,
                    color: headerColor,
                }}>
                    {headers.map((h, i) => (
                        <div key={i} style={{
                            ...getColumnLayout(i),
                            padding: '0 10px',
                            whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                        }}>{h}</div>
                    ))}
                </div>
            )}
            <div style={{ height: visibleHeight, overflow: 'hidden', position: 'relative' }}>
                <div style={{
                    transition: needScroll ? 'transform 0.5s ease' : 'none',
                    transform: needScroll ? `translateY(-${0}px)` : 'none',
                }}>
                    {visibleRows.map((row, ri) => (
                        <div key={`${offset}-${ri}`} style={{
                            display: 'flex', height: rowHeight, lineHeight: `${rowHeight}px`,
                            background: row.originalIndex % 2 === 0 ? evenRowBGC : oddRowBGC,
                        }}>
                            {headers.map((_, ci) => (
                                <div key={ci} style={{
                                    ...getColumnLayout(ci),
                                    padding: '0 10px',
                                    whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                                }}>{row.cells[ci] ?? ''}</div>
                            ))}
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}

function resolveBoundTableData(
    config: Record<string, unknown>,
    options?: { defaultAlign?: ColumnAlign },
): ResolvedTableData {
    const defaultAlign = options?.defaultAlign ?? 'left';
    const sourceCols = config._sourceColumns as SourceColumnMeta[] | undefined;
    const columnsConfig = config.columns as ColumnEntry[] | undefined;
    const allData = (config.data as Array<Array<unknown>> | undefined) || [];

    if (sourceCols?.length) {
        const effectiveColumns = columnsConfig
            ? columnsConfig
            : sourceCols.map((col) => ({ source: col.name } as ColumnEntry));
        const sourceMetaByName = new Map(sourceCols.map((item) => [item.name, item] as const));
        const sourceIndexByName = new Map(sourceCols.map((item, index) => [item.name, index] as const));

        const columnMeta = effectiveColumns.map((col): ResolvedColumnMeta => {
            const sc = sourceMetaByName.get(col.source);
            return {
                key: col.source,
                title: col.alias || sc?.displayName || col.source,
                align: normalizeColumnAlign(col.align, defaultAlign),
                width: clampColumnWidth(col.width),
                wrap: col.wrap === true,
                formatter: normalizeColumnFormatter(col.formatter),
                baseType: sc?.baseType,
            };
        });
        const data = allData.map((row) =>
            columnMeta.map((col) => {
                const idx = sourceIndexByName.get(col.key);
                return typeof idx === 'number' ? formatTableCell(row[idx], col.formatter, col.baseType) : '';
            }),
        );
        return {
            header: columnMeta.map((col) => col.title),
            data,
            columnMeta,
        };
    }

    const rawHeader = (config.header as string[] | undefined) || [];
    const alias = config.columnAlias as Record<string, string> | undefined;
    const mappedHeader = alias
        ? rawHeader.map((h, i) => alias[String(i)] || h)
        : rawHeader;
    const inferredCount = mappedHeader.length > 0
        ? mappedHeader.length
        : allData.reduce((max, row) => Math.max(max, row.length), 0);
    const header = mappedHeader.length > 0
        ? mappedHeader
        : Array.from({ length: inferredCount }, (_, i) => `列${i + 1}`);
    const columnMeta: ResolvedColumnMeta[] = header.map((title, idx) => ({
        key: String(idx),
        title,
        align: defaultAlign,
        wrap: false,
        formatter: 'auto',
    }));
    const data = allData.map((row) =>
        columnMeta.map((col, idx) => formatTableCell(row[idx], col.formatter)),
    );

    return { header, data, columnMeta };
}

interface ComponentRendererProps {
    component: ScreenComponent;
    mode?: 'designer' | 'preview';
    theme?: ScreenTheme;
    /** Callback to persist card-derived metadata (e.g. _sourceColumns) back to saved config */
    onConfigMeta?: (meta: Record<string, unknown>) => void;
}

export const ComponentRenderer = memo(function ComponentRenderer({ component, mode = 'preview', theme, onConfigMeta }: ComponentRendererProps) {
    const { type, config, width, height, dataSource, drillDown } = component;

    const runtime = useScreenRuntime();
    const pluginRuntimeVersion = useScreenPluginRuntime();
    const t = useMemo(() => getThemeTokens(theme), [theme]);
    const pluginMeta = useMemo(() => readComponentPluginMeta(config), [config]);
    const runtimePlugin = useMemo<RendererPlugin | null>(() => {
        const runtimeId = resolveRuntimePluginId(pluginMeta);
        if (!runtimeId) return null;
        return getRendererPlugin(runtimeId) ?? null;
    }, [pluginMeta, pluginRuntimeVersion]);

    // Build ECharts base options from theme tokens
    const themeOptions = useMemo(() => ({
        backgroundColor: "transparent",
        color: t.echarts.colorPalette,
        textStyle: { color: t.textPrimary },
        legend: { textStyle: { color: t.textPrimary } },
        tooltip: {
            backgroundColor: t.echarts.tooltipBg,
            borderColor: t.echarts.tooltipBorder,
            textStyle: { color: t.textPrimary },
        },
    }), [t]);

    const needsECharts = ECHART_COMPONENT_TYPES.has(type);
    const needsDataV = DATAV_COMPONENT_TYPES.has(type);
    const [EChartsComponent, setEChartsComponent] = useState<ReactEChartsComponent | null>(null);
    const [registerMapFn, setRegisterMapFn] = useState<((mapName: string, geoJson: unknown) => boolean) | null>(null);
    const [hasMapFn, setHasMapFn] = useState<((mapName: string) => boolean) | null>(null);
    const [dataViewModule, setDataViewModule] = useState<DataViewModule | null>(null);
    const [mapDrillRegion, setMapDrillRegion] = useState<string | null>(null);
    const [mapReadyVersion, setMapReadyVersion] = useState(0);
    const [tableSort, setTableSort] = useState<{ colIndex: number; order: 'asc' | 'desc' } | null>(null);
    const [tablePage, setTablePage] = useState(1);

    useEffect(() => {
        if (!needsECharts || EChartsComponent) return;
        let cancelled = false;
        import('../../../components/charts/EChartsRuntime').then((mod) => {
            if (!cancelled) {
                setEChartsComponent(() => mod.default as ReactEChartsComponent);
                if (typeof mod.registerEChartsMap === 'function') {
                    setRegisterMapFn(() => mod.registerEChartsMap);
                }
                if (typeof mod.hasEChartsMap === 'function') {
                    setHasMapFn(() => mod.hasEChartsMap);
                }
            }
        });
        return () => {
            cancelled = true;
        };
    }, [needsECharts, EChartsComponent]);

    useEffect(() => {
        if (type !== 'map-chart' || !registerMapFn) return;
        const cfg = config as Record<string, unknown>;
        const mapName = String(cfg.mapName || cfg.mapScope || 'dts-map').trim();
        if (!mapName) return;
        const geoJson = cfg.geoJson;
        const geoJsonUrlRaw = typeof cfg.geoJsonUrl === 'string' ? cfg.geoJsonUrl.trim() : '';
        const presetAllowed = cfg.usePresetGeoJson !== false;
        const presetUrl = presetAllowed ? resolvePresetMapUrl(String(cfg.mapScope || 'china')) : undefined;
        const geoJsonUrl = geoJsonUrlRaw || presetUrl || '';
        let cancelled = false;

        if (geoJson && typeof geoJson === 'object') {
            if (registerMapFn(mapName, geoJson)) {
                setMapReadyVersion((v) => v + 1);
            }
            return;
        }

        if (!geoJsonUrl) {
            return;
        }

        fetchGeoJsonWithCache(geoJsonUrl).then((loaded) => {
            if (cancelled || !loaded || typeof loaded !== 'object') return;
            if (registerMapFn(mapName, loaded)) {
                setMapReadyVersion((v) => v + 1);
            }
        });

        return () => {
            cancelled = true;
        };
    }, [config, registerMapFn, type]);

    useEffect(() => {
        if (!needsDataV || dataViewModule) return;
        let cancelled = false;
        import('@jiaminghi/data-view-react').then((mod) => {
            if (!cancelled) {
                setDataViewModule(mod);
            }
        });
        return () => {
            cancelled = true;
        };
    }, [needsDataV, dataViewModule]);

    useEffect(() => {
        setMapDrillRegion(null);
        setTableSort(null);
        setTablePage(1);
    }, [component.id]);

    const borderBoxComponents = useMemo(() => {
        if (!dataViewModule) return null;
        return {
            1: dataViewModule.BorderBox1,
            2: dataViewModule.BorderBox2,
            3: dataViewModule.BorderBox3,
            4: dataViewModule.BorderBox4,
            5: dataViewModule.BorderBox5,
            6: dataViewModule.BorderBox6,
            7: dataViewModule.BorderBox7,
            8: dataViewModule.BorderBox8,
            9: dataViewModule.BorderBox9,
            10: dataViewModule.BorderBox10,
            11: dataViewModule.BorderBox11,
            12: dataViewModule.BorderBox12,
            13: dataViewModule.BorderBox13,
        } as Record<number, ComponentType<{ children?: React.ReactNode; color?: string[] }>>;
    }, [dataViewModule]);

    const decorationComponents = useMemo(() => {
        if (!dataViewModule) return null;
        return {
            1: dataViewModule.Decoration1,
            2: dataViewModule.Decoration2,
            3: dataViewModule.Decoration3,
            4: dataViewModule.Decoration4,
            5: dataViewModule.Decoration5,
            6: dataViewModule.Decoration6,
            7: dataViewModule.Decoration7,
            8: dataViewModule.Decoration8,
            9: dataViewModule.Decoration9,
            10: dataViewModule.Decoration10,
            11: dataViewModule.Decoration11,
            12: dataViewModule.Decoration12,
        } as Record<number, ComponentType<{ color?: string[]; style?: React.CSSProperties }>>;
    }, [dataViewModule]);

    const dataSourceType = useMemo(() => resolveDataSourceType(dataSource), [dataSource]);
    const sourceBindings = useMemo(() => {
        if (dataSourceType === "card") {
            return normalizeParameterBindings(dataSource?.cardConfig?.parameterBindings);
        }
        if (dataSourceType === "metric") {
            return normalizeParameterBindings(dataSource?.metricConfig?.parameterBindings);
        }
        if (dataSourceType === "sql") {
            const sqlConfig = dataSource?.sqlConfig ?? dataSource?.databaseConfig;
            return normalizeParameterBindings(sqlConfig?.parameterBindings);
        }
        return [];
    }, [dataSource, dataSourceType]);

    const bindingParameters = useMemo(() => {
        if (!sourceBindings.length) return [] as Array<{ name: string; value: string }>;
        const out: Array<{ name: string; value: string }> = [];
        for (const item of sourceBindings) {
            let value = item.value ?? "";
            if (item.variableKey) {
                value = runtime.values[item.variableKey] ?? "";
            }
            if ((item.name || "").trim().length === 0) continue;
            out.push({ name: item.name, value: String(value ?? "") });
        }
        return out;
    }, [sourceBindings, runtime.values]);

    // Drill-down state (only active in preview mode for drillable chart types)
    const drillActive = mode === "preview" && DRILLABLE_TYPES.has(type) && drillDown?.enabled === true;
    const rootCardId = dataSourceType === "card" ? dataSource?.cardConfig?.cardId : undefined;
    const drillState = useDrillDown(
        drillActive ? rootCardId : undefined,
        drillActive ? drillDown : undefined,
    );

    const mergedQueryParameters = useMemo(() => {
        const merged = new Map<string, string>();
        for (const item of bindingParameters) {
            const name = (item.name || "").trim();
            if (!name) continue;
            merged.set(name, String(item.value ?? ""));
        }
        for (const item of (drillActive ? (drillState.queryParameters ?? []) : [])) {
            const name = (item.name || "").trim();
            if (!name) continue;
            merged.set(name, String(item.value ?? ""));
        }
        return Array.from(merged.entries()).map(([name, value]) => ({ name, value }));
    }, [bindingParameters, drillActive, drillState.queryParameters]);

    const queryContext = useMemo(() => ({
        source: "screen-component",
        componentId: component.id,
        componentType: type,
        mode,
        globalVariables: runtime.values,
    }), [component.id, mode, runtime.values, type]);
    const visibleByVariableRule = mode !== 'preview'
        || resolveComponentVariableVisibility(config, runtime.values);

    // Card data source hook — pass drill overrides when active
    const { data: cardData, loading: cardLoading, error: cardError } = useCardDataSource(
        visibleByVariableRule ? dataSource : undefined,
        drillActive ? drillState.effectiveCardId : undefined,
        mergedQueryParameters.length > 0 ? mergedQueryParameters : undefined,
        queryContext,
    );

    // Merge card data into config: card data overrides data fields only, not display fields
    const effectiveConfig = useMemo(() => {
        if (!cardData) return config;
        const mapped = mapCardDataToConfig(type, cardData);
        return { ...config, ...mapped };
    }, [config, cardData, type]);

    // Persist _sourceColumns to saved config so PropertyPanel can read them
    const onConfigMetaRef = useRef(onConfigMeta);
    onConfigMetaRef.current = onConfigMeta;

    const sourceColsKey = (config._sourceColumns as Array<{ name: string }> | undefined)
        ?.map(c => c.name).join(',');

    useEffect(() => {
        if (!onConfigMetaRef.current || !cardData?.cols?.length) return;
        const newCols = cardData.cols.map((c) => ({
            name: c.name,
            displayName: c.display_name || c.name,
            baseType: c.base_type,
        }));
        const newKey = newCols.map(c => c.name).join(',');
        // Only update if columns actually changed (avoid infinite loop)
        if (sourceColsKey !== newKey) {
            onConfigMetaRef.current({ _sourceColumns: newCols });
        }
    }, [cardData, sourceColsKey]);

    // For datetime component, update every second
    const [currentTime, setCurrentTime] = useState(new Date());
    useEffect(() => {
        if (type === 'datetime' || type === 'countdown') {
            const timer = setInterval(() => setCurrentTime(new Date()), 1000);
            return () => clearInterval(timer);
        }
    }, [type]);

    const [carouselIndex, setCarouselIndex] = useState(0);
    const [carouselPaused, setCarouselPaused] = useState(false);
    const carouselItems = useMemo(() => {
        if (type !== 'carousel') return [];
        const sourceMode = String(effectiveConfig.itemSourceMode ?? 'auto').trim().toLowerCase();
        const dataItems = resolveCarouselItemsFromData(cardData, effectiveConfig);
        const manualItems = normalizeCarouselItems(effectiveConfig.items);
        if (sourceMode === 'data') {
            return dataItems;
        }
        if (sourceMode === 'manual') {
            return manualItems;
        }
        if (dataItems.length > 0) {
            return dataItems;
        }
        return manualItems;
    }, [cardData, effectiveConfig.dataItemField, effectiveConfig.dataItemMax, effectiveConfig.itemSourceMode, effectiveConfig.items, type]);

    useEffect(() => {
        if (type !== 'carousel') return;
        if (carouselItems.length <= 1) {
            setCarouselIndex(0);
            return;
        }
        const autoPlay = effectiveConfig.autoPlay !== false;
        if (!autoPlay) {
            return;
        }
        const pauseOnHover = effectiveConfig.pauseOnHover !== false;
        if (pauseOnHover && carouselPaused) {
            return;
        }
        const rawSeconds = Number(effectiveConfig.intervalSeconds ?? 4);
        const safeSeconds = Number.isFinite(rawSeconds)
            ? Math.max(1, Math.min(120, Math.floor(rawSeconds)))
            : 4;
        const timer = setInterval(() => {
            setCarouselIndex((prev) => (prev + 1) % carouselItems.length);
        }, safeSeconds * 1000);
        return () => clearInterval(timer);
    }, [carouselItems.length, carouselPaused, effectiveConfig.autoPlay, effectiveConfig.intervalSeconds, effectiveConfig.pauseOnHover, type]);

    const filterInputVariableKey = useMemo(() => {
        if (type !== 'filter-input') return '';
        return String((effectiveConfig.variableKey as string) ?? '').trim();
    }, [effectiveConfig, type]);
    const filterInputRuntimeValue = filterInputVariableKey ? (runtime.values[filterInputVariableKey] ?? '') : '';
    const [filterInputDraft, setFilterInputDraft] = useState(filterInputRuntimeValue);
    const filterVariableTimersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

    useEffect(() => {
        setFilterInputDraft(filterInputRuntimeValue);
    }, [filterInputRuntimeValue, filterInputVariableKey]);

    useEffect(() => () => {
        for (const timer of filterVariableTimersRef.current.values()) {
            clearTimeout(timer);
        }
        filterVariableTimersRef.current.clear();
    }, []);

    const tabVariableKey = useMemo(() => {
        if (type !== 'tab-switcher') return '';
        return String((effectiveConfig.variableKey as string) ?? '').trim();
    }, [effectiveConfig, type]);
    const tabOptions = useMemo(() => {
        if (type !== 'tab-switcher') return [];
        const sourceMode = String(effectiveConfig.optionSourceMode ?? 'manual').trim().toLowerCase();
        if (sourceMode === 'data') {
            const dynamicOptions = resolveFilterOptionsFromData(cardData, effectiveConfig);
            if (dynamicOptions.length > 0) {
                return dynamicOptions;
            }
        }
        return resolveTabOptions(effectiveConfig.options);
    }, [cardData, effectiveConfig, type]);
    const tabDefaultValue = String(effectiveConfig.defaultValue ?? '').trim();
    const tabRuntimeValue = tabVariableKey ? String(runtime.values[tabVariableKey] ?? '') : '';

    useEffect(() => {
        if (type !== 'tab-switcher' || !tabVariableKey || tabOptions.length <= 0) return;
        if (tabRuntimeValue) return;
        const fallbackValue = tabDefaultValue && tabOptions.some((item) => item.value === tabDefaultValue)
            ? tabDefaultValue
            : tabOptions[0]?.value;
        if (fallbackValue) {
            runtime.setVariable(tabVariableKey, fallbackValue, `tab-switcher:init:${component.id}`);
        }
    }, [component.id, runtime, tabDefaultValue, tabOptions, tabRuntimeValue, tabVariableKey, type]);

    const scheduleFilterVariableUpdate = (
        key: string,
        value: string,
        source: string,
        debounceMsRaw: unknown,
        immediate = false,
    ) => {
        const safeKey = String(key || '').trim();
        if (!safeKey) return;
        const debounceMs = normalizeFilterDebounceMs(debounceMsRaw);
        const currentTimer = filterVariableTimersRef.current.get(safeKey);
        if (currentTimer) {
            clearTimeout(currentTimer);
            filterVariableTimersRef.current.delete(safeKey);
        }
        if (immediate || debounceMs <= 0) {
            runtime.setVariable(safeKey, value, source);
            return;
        }
        const timer = setTimeout(() => {
            filterVariableTimersRef.current.delete(safeKey);
            runtime.setVariable(safeKey, value, `${source}:debounced`);
        }, debounceMs);
        filterVariableTimersRef.current.set(safeKey, timer);
    };

    const interactionMappings = useMemo(() => (
        mode === "preview" && component.interaction?.enabled
            ? (component.interaction.mappings ?? []).filter((m): m is ComponentInteractionMapping => !!m && !!m.variableKey && !!m.sourcePath)
            : []
    ), [component.interaction, mode]);
    const interactionJump = useMemo(() => {
        if (mode !== 'preview' || component.interaction?.enabled !== true || component.interaction?.jumpEnabled !== true) {
            return null;
        }
        const template = String(component.interaction.jumpUrlTemplate || '').trim();
        if (!template) return null;
        return {
            template,
            openMode: component.interaction.jumpOpenMode === 'self' ? 'self' : 'new-tab',
        };
    }, [component.interaction, mode]);

    // ECharts click handler for drill-down + variable interaction
    const echartsClickHandler = useMemo(() => {
        const canDrill = drillActive && drillState.canDrillDown;
        const canInteract = interactionMappings.length > 0;
        const canJump = !!interactionJump;
        if (!canDrill && !canInteract && !canJump) return undefined;

        return {
            click: (params: Record<string, unknown>) => {
                if (canDrill) {
                    const value = (params.name as string | undefined)
                        ?? ((params.data as Record<string, unknown> | undefined)?.name as string | undefined);
                    if (value) {
                        const clicked = String(value);
                        runtime.trackEvent({
                            kind: 'drill-down',
                            key: 'drillValue',
                            value: clicked,
                            source: `drill:${component.id}`,
                            meta: `depth=${drillState.breadcrumbs.length}`,
                        });
                        drillState.handleDrill(clicked);
                    }
                }

                if (canInteract) {
                    for (const mapping of interactionMappings) {
                        const nextValue = resolveInteractionValue(params, mapping.sourcePath);
                        if (nextValue != null) {
                            runtime.setVariable(mapping.variableKey, nextValue, `interaction:${component.id}`);
                        }
                    }
                }

                if (canJump && interactionJump) {
                    const targetUrl = resolveInteractionUrlTemplate(interactionJump.template, params);
                    if (!targetUrl) return;
                    runtime.trackEvent({
                        kind: 'jump',
                        key: 'jumpUrl',
                        value: targetUrl,
                        source: `interaction:${component.id}`,
                        meta: `openMode=${interactionJump.openMode}`,
                    });
                    if (interactionJump.openMode === 'self') {
                        window.location.assign(targetUrl);
                    } else {
                        window.open(targetUrl, '_blank', 'noopener,noreferrer');
                    }
                }
            },
        };
    }, [
        component.id,
        drillActive,
        drillState.breadcrumbs.length,
        drillState.canDrillDown,
        drillState.handleDrill,
        interactionJump,
        interactionMappings,
        runtime,
    ]);

    const content = useMemo(() => {
        const c = effectiveConfig;
        if (runtimePlugin) {
            return (
                <PluginRenderBoundary title={`插件渲染失败: ${runtimePlugin.name}`}>
                    {runtimePlugin.render({
                        component,
                        mode,
                        theme,
                        width,
                        height,
                        config: c,
                        data: cardData,
                        runtimeValues: runtime.values,
                        setVariable: (key, value) => runtime.setVariable(key, value, `plugin:${runtimePlugin.id}`),
                    })}
                </PluginRenderBoundary>
            );
        }
        const axisFontSize = (c.axisFontSize as number) || 12;
        const legendFontSize = (c.legendFontSize as number) || 12;
        const seriesColors = Array.isArray(c.seriesColors)
            ? (c.seriesColors as string[]).filter((color) => typeof color === 'string' && color.trim().length > 0)
            : [];
        const dependencyPlaceholder = (label: string) => (
            <div style={{
                width: '100%',
                height: '100%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: t.placeholder.background,
                border: t.placeholder.border,
                borderRadius: 4,
                color: t.placeholder.color,
                fontSize: 12,
            }}>
                {label}加载中...
            </div>
        );

        if (ECHART_COMPONENT_TYPES.has(type) && !EChartsComponent) {
            return dependencyPlaceholder('图表引擎');
        }
        if (DATAV_COMPONENT_TYPES.has(type) && !dataViewModule) {
            return dependencyPlaceholder('DataV');
        }

        const EChart = EChartsComponent as ReactEChartsComponent;
        const ScrollBoard = dataViewModule?.ScrollBoard;
        const ScrollRankingBoard = dataViewModule?.ScrollRankingBoard;
        const WaterLevelPond = dataViewModule?.WaterLevelPond;
        const DigitalFlop = dataViewModule?.DigitalFlop;

        // Build legend config from legendPosition
        const legendPos = c.legendPosition as string;
        const legendConfig: Record<string, unknown> = {
            textStyle: { color: t.textPrimary, fontSize: legendFontSize },
            ...(legendPos === 'bottom' ? { top: 'auto', bottom: 0, left: 'center' } :
                legendPos === 'left' ? { left: 0, top: 'middle', orient: 'vertical' } :
                legendPos === 'right' ? { right: 0, top: 'middle', orient: 'vertical' } :
                {}),
        };

        switch (type) {
            // ==================== ECharts 图表 ====================
            case 'line-chart':
                return (
                    <EChart
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...themeOptions,
                            title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 } },
                            legend: legendConfig,
                            xAxis: {
                                type: 'category',
                                data: c.xAxisData as string[],
                                axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                                axisLabel: { color: t.echarts.axisLabelColor, fontSize: axisFontSize },
                            },
                            yAxis: {
                                type: 'value',
                                axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                                axisLabel: { color: t.echarts.axisLabelColor, fontSize: axisFontSize },
                                splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                            },
                            series: (c.series as Array<{ name: string; data: number[] }>).map((s, idx) => ({
                                name: s.name,
                                type: 'line',
                                data: s.data,
                                smooth: true,
                                areaStyle: {
                                    opacity: 0.3,
                                    ...(seriesColors[idx] ? { color: seriesColors[idx] } : {}),
                                },
                                ...(seriesColors[idx]
                                    ? { lineStyle: { color: seriesColors[idx] }, itemStyle: { color: seriesColors[idx] } }
                                    : {}),
                            })),
                            grid: { left: '10%', right: '10%', bottom: '15%', top: '20%' },
                        }}
                        onEvents={echartsClickHandler}
                    />
                );

            case 'bar-chart':
                return (
                    <EChart
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...themeOptions,
                            title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 } },
                            legend: legendConfig,
                            xAxis: {
                                type: 'category',
                                data: c.xAxisData as string[],
                                axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                                axisLabel: { color: t.echarts.axisLabelColor, fontSize: axisFontSize },
                            },
                            yAxis: {
                                type: 'value',
                                axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                                axisLabel: { color: t.echarts.axisLabelColor, fontSize: axisFontSize },
                                splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                            },
                            series: (c.series as Array<{ name: string; data: number[] }>).map((s, idx) => ({
                                name: s.name,
                                type: 'bar',
                                data: s.data,
                                itemStyle: {
                                    borderRadius: [4, 4, 0, 0],
                                    color: seriesColors[idx]
                                        ? seriesColors[idx]
                                        : {
                                            type: 'linear',
                                            x: 0, y: 0, x2: 0, y2: 1,
                                            colorStops: [
                                                { offset: 0, color: t.barGradient[0] },
                                                { offset: 1, color: t.barGradient[1] },
                                            ],
                                        },
                                },
                            })),
                            grid: { left: '10%', right: '10%', bottom: '15%', top: '20%' },
                        }}
                        onEvents={echartsClickHandler}
                    />
                );

            case 'pie-chart':
                return (
                    <EChart
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...themeOptions,
                            ...(seriesColors.length > 0 ? { color: seriesColors } : {}),
                            title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 }, left: 'center' },
                            legend: legendConfig,
                            tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
                            series: [{
                                type: 'pie',
                                radius: ['40%', '70%'],
                                avoidLabelOverlap: false,
                                label: {
                                    show: true,
                                    color: t.pieLabelColor,
                                    fontSize: 12,
                                    formatter: '{b}: {d}%',
                                },
                                data: c.data as Array<{ name: string; value: number }>,
                            }],
                        }}
                        onEvents={echartsClickHandler}
                    />
                );

            case 'gauge-chart':
                return (
                    <EChart
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...themeOptions,
                            series: [{
                                type: 'gauge',
                                min: c.min as number,
                                max: c.max as number,
                                progress: { show: true, width: 18 },
                                axisLine: { lineStyle: { width: 18, color: [[1, t.gauge.axisLineColor]] } },
                                axisTick: { show: false },
                                splitLine: { length: 10, lineStyle: { width: 2, color: t.gauge.splitLineColor } },
                                axisLabel: { distance: 25, color: t.gauge.axisLabelColor, fontSize: 12 },
                                pointer: { icon: 'path://M12.8,0.7l12,40.1H0.7L12.8,0.7z', length: '12%', width: 10, itemStyle: { color: 'auto' } },
                                anchor: { show: true, showAbove: true, size: 18, itemStyle: { borderWidth: 6 } },
                                title: { show: true, offsetCenter: [0, '70%'], fontSize: (c.titleFontSize as number) || 14, color: t.gauge.titleColor },
                                detail: { valueAnimation: true, fontSize: 28, offsetCenter: [0, '45%'], color: t.gauge.detailColor, formatter: '{value}%' },
                                data: [{ value: c.value as number, name: c.title as string }],
                            }],
                        }}
                    />
                );

            case 'radar-chart':
                return (
                    <EChart
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...themeOptions,
                            ...(seriesColors.length > 0 ? { color: seriesColors } : {}),
                            title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 }, left: 'center' },
                            legend: legendConfig,
                            radar: {
                                indicator: c.indicator as Array<{ name: string; max: number }>,
                                axisName: { color: t.radar.axisNameColor },
                                splitLine: { lineStyle: { color: t.radar.splitLineColor } },
                                splitArea: { areaStyle: { color: ['transparent'] } },
                            },
                            series: [{
                                type: 'radar',
                                data: [{ value: c.data as number[], areaStyle: { opacity: 0.3 } }],
                            }],
                        }}
                        onEvents={echartsClickHandler}
                    />
                );

            case 'funnel-chart':
                return (
                    <EChart
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...themeOptions,
                            ...(seriesColors.length > 0 ? { color: seriesColors } : {}),
                            title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 }, left: 'center' },
                            legend: legendConfig,
                            series: [{
                                type: 'funnel',
                                left: '10%',
                                top: 60,
                                bottom: 20,
                                width: '80%',
                                min: 0,
                                max: 100,
                                sort: 'descending',
                                gap: 2,
                                label: { show: true, position: 'inside', color: t.funnelLabelColor },
                                data: c.data as Array<{ name: string; value: number }>,
                            }],
                        }}
                        onEvents={echartsClickHandler}
                    />
                );

            case 'scatter-chart':
                return (
                    <EChart
                        style={{ width: '100%', height: '100%' }}
                        option={{
                            ...themeOptions,
                            title: { text: c.title as string, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 } },
                            legend: legendConfig,
                            xAxis: {
                                axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                                axisLabel: { color: t.echarts.axisLabelColor, fontSize: axisFontSize },
                                splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                            },
                            yAxis: {
                                axisLine: { lineStyle: { color: t.echarts.axisLineColor } },
                                axisLabel: { color: t.echarts.axisLabelColor, fontSize: axisFontSize },
                                splitLine: { lineStyle: { color: t.echarts.splitLineColor } },
                            },
                            series: [{
                                type: 'scatter',
                                data: c.data as number[][],
                                symbolSize: 10,
                                itemStyle: { color: seriesColors[0] || t.scatterColor },
                            }],
                            grid: { left: '10%', right: '10%', bottom: '15%', top: '20%' },
                        }}
                        onEvents={echartsClickHandler}
                    />
                );

            case 'map-chart': {
                const title = String(c.title ?? '区域地图');
                const mapScope = String(c.mapScope ?? 'china');
                const defaultRegions = mapScope === 'world'
                    ? [
                        { name: 'China', code: 'CN', value: 260 },
                        { name: 'United States of America', code: 'US', value: 180 },
                        { name: 'Russia', code: 'RU', value: 150 },
                        { name: 'India', code: 'IN', value: 140 },
                        { name: 'Brazil', code: 'BR', value: 110 },
                        { name: 'Australia', code: 'AU', value: 90 },
                    ]
                    : [
                        { name: '北京市', code: '110000', value: 120 },
                        { name: '上海市', code: '310000', value: 180 },
                        { name: '广东省', code: '440000', value: 140 },
                        { name: '浙江省', code: '330000', value: 95 },
                        { name: '四川省', code: '510000', value: 72 },
                        { name: '湖北省', code: '420000', value: 88 },
                    ];
                const regions = Array.isArray(c.regions) && c.regions.length > 0 ? c.regions as Array<Record<string, unknown>> : defaultRegions;
                const getChildren = (item: unknown): Array<Record<string, unknown>> => {
                    if (!item || typeof item !== 'object') return [];
                    const raw = (item as Record<string, unknown>).children;
                    if (!Array.isArray(raw)) return [];
                    return raw.filter((node): node is Record<string, unknown> => !!node && typeof node === 'object');
                };
                const activeRegion = mapDrillRegion
                    ? regions.find((item) => String(item.name ?? '') === mapDrillRegion)
                    : undefined;
                const canRegionDrill = c.enableRegionDrill !== false;
                const drillRows = getChildren(activeRegion);
                const listRows = drillRows.length > 0 ? drillRows : regions;

                const maxValue = Math.max(1, ...listRows.map((item) => Number(item.value ?? 0)));
                const minValue = Math.min(...listRows.map((item) => Number(item.value ?? 0)));
                const mapName = String(c.mapName || mapScope || `dts-${mapScope}`).trim();
                const usingGeoMap = !mapDrillRegion && Boolean(EChart) && Boolean(hasMapFn?.(mapName)) && mapReadyVersion >= 0;
                const regionCodeVariableKey = String(c.regionCodeVariableKey ?? '').trim();
                const resolveRegionCode = (item: Record<string, unknown> | undefined): string => {
                    if (!item) return '';
                    const candidate = item.code ?? item.adcode ?? item.regionCode ?? item.id;
                    return String(candidate ?? '').trim();
                };

                if (usingGeoMap) {
                    return (
                        <div style={{ width: '100%', height: '100%', position: 'relative' }}>
                            <EChart
                                style={{ width: '100%', height: '100%' }}
                                option={{
                                    ...themeOptions,
                                    title: { text: title, textStyle: { color: t.textPrimary, fontSize: (c.titleFontSize as number) || 14 } },
                                    visualMap: {
                                        min: Number.isFinite(minValue) ? minValue : 0,
                                        max: Number.isFinite(maxValue) ? maxValue : 100,
                                        text: ['高', '低'],
                                        left: 6,
                                        bottom: 8,
                                        itemWidth: 10,
                                        itemHeight: 60,
                                        textStyle: { color: t.textSecondary, fontSize: 10 },
                                        inRange: { color: ['#93c5fd', '#3b82f6', '#1d4ed8'] },
                                    },
                                    tooltip: { trigger: 'item', formatter: '{b}: {c}' },
                                    series: [{
                                        type: 'map',
                                        map: mapName,
                                        roam: true,
                                        label: { show: true, color: t.textPrimary, fontSize: 10 },
                                        emphasis: { label: { color: t.textPrimary } },
                                        data: regions.map((item) => ({
                                            name: String(item.name ?? ''),
                                            value: Number(item.value ?? 0),
                                            code: resolveRegionCode(item),
                                        })),
                                    }],
                                }}
                                onEvents={{
                                    click: (params) => {
                                        const regionName = String(params.name ?? '');
                                        const row = params.data && typeof params.data === 'object'
                                            ? (params.data as Record<string, unknown>)
                                            : undefined;
                                        const clickedCode = String(row?.code ?? row?.adcode ?? '').trim();
                                        const target = clickedCode
                                            ? regions.find((item) => resolveRegionCode(item) === clickedCode)
                                                || regions.find((item) => String(item.name ?? '') === regionName)
                                            : regions.find((item) => String(item.name ?? '') === regionName);
                                        if (canRegionDrill && target && getChildren(target).length > 0) {
                                            setMapDrillRegion(regionName);
                                        }
                                        const variableKey = String(c.regionVariableKey ?? '').trim();
                                        if (variableKey && regionName) {
                                            runtime.setVariable(variableKey, regionName, `map-chart:${component.id}`);
                                        }
                                        const code = resolveRegionCode(target);
                                        if (regionCodeVariableKey && code) {
                                            runtime.setVariable(regionCodeVariableKey, code, `map-chart:${component.id}`);
                                        }
                                    },
                                }}
                            />
                        </div>
                    );
                }

                return (
                    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', gap: 10 }}>
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                            <div style={{ color: t.textPrimary, fontSize: 14, fontWeight: 600 }}>{title}</div>
                            {mapDrillRegion ? (
                                <button
                                    type="button"
                                    onClick={() => setMapDrillRegion(null)}
                                    style={{
                                        border: '1px solid rgba(148,163,184,0.4)',
                                        background: 'rgba(15,23,42,0.45)',
                                        color: t.textPrimary,
                                        borderRadius: 4,
                                        fontSize: 11,
                                        cursor: 'pointer',
                                        padding: '2px 8px',
                                    }}
                                >
                                    返回上级
                                </button>
                            ) : null}
                        </div>
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(120px, 1fr))', gap: 8 }}>
                            {listRows.map((item, index) => {
                                const value = Number(item.value ?? 0);
                                const ratio = maxValue <= 0 ? 0 : Math.max(0, Math.min(1, value / maxValue));
                                const colorAlpha = 0.18 + ratio * 0.46;
                                const name = String(item.name ?? `区域${index + 1}`);
                                const hasChild = getChildren(item).length > 0;
                                return (
                                    <button
                                        key={`${name}_${index}`}
                                        type="button"
                                        onClick={() => {
                                            if (canRegionDrill && hasChild) {
                                                setMapDrillRegion(name);
                                            }
                                            const variableKey = String(c.regionVariableKey ?? '').trim();
                                            if (variableKey) {
                                                runtime.setVariable(variableKey, name, `map-grid:${component.id}`);
                                            }
                                            const code = resolveRegionCode(item);
                                            if (regionCodeVariableKey && code) {
                                                runtime.setVariable(regionCodeVariableKey, code, `map-grid:${component.id}`);
                                            }
                                        }}
                                        style={{
                                            border: '1px solid rgba(148,163,184,0.25)',
                                            borderRadius: 8,
                                            background: `rgba(59,130,246,${colorAlpha.toFixed(3)})`,
                                            color: t.textPrimary,
                                            textAlign: 'left',
                                            padding: '8px 10px',
                                            minHeight: 56,
                                            cursor: 'pointer',
                                        }}
                                    >
                                        <div style={{ fontSize: 12, fontWeight: 600 }}>{name}</div>
                                        <div style={{ marginTop: 4, fontSize: 12, color: t.textSecondary }}>
                                            {Number.isFinite(value) ? value.toLocaleString('zh-CN') : '-'}
                                        </div>
                                    </button>
                                );
                            })}
                        </div>
                    </div>
                );
            }

            // ==================== 基础组件 ====================
            case 'number-card':
                return (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'center',
                        alignItems: 'center',
                        background: (c.backgroundColor as string) || t.numberCard.background,
                        borderRadius: t.cardBorderRadius,
                        border: t.numberCard.border,
                        boxShadow: t.cardShadow,
                    }}>
                        <div style={{ fontSize: (c.titleFontSize as number) || 12, color: (c.titleColor as string) || t.numberCard.titleColor, marginBottom: 8 }}>
                            {c.title as string}
                        </div>
                        <div style={{ fontSize: (c.valueFontSize as number) || 32, fontWeight: 'bold', color: (c.valueColor as string) || t.numberCard.valueColor }}>
                            {c.prefix as string}
                            {(c.value as number).toLocaleString()}
                            {c.suffix as string}
                        </div>
                    </div>
                );

            case 'title':
                return (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: c.textAlign as string,
                        fontSize: c.fontSize as number,
                        fontWeight: c.fontWeight as string,
                        color: c.color as string,
                    }}>
                        {c.text as string}
                    </div>
                );

            case 'markdown-text': {
                const markdown = String(c.markdown ?? '');
                const html = renderMarkdownToHtml(markdown);
                return (
                    <div
                        style={{
                            width: '100%',
                            height: '100%',
                            overflow: 'auto',
                            color: (c.color as string) || t.textPrimary,
                            fontSize: (c.fontSize as number) || 14,
                            lineHeight: Number(c.lineHeight || 1.6),
                            padding: 8,
                        }}
                        dangerouslySetInnerHTML={{ __html: html }}
                    />
                );
            }

            case 'datetime': {
                const formatted = (c.format as string)
                    .replace('YYYY', String(currentTime.getFullYear()))
                    .replace('MM', String(currentTime.getMonth() + 1).padStart(2, '0'))
                    .replace('DD', String(currentTime.getDate()).padStart(2, '0'))
                    .replace('HH', String(currentTime.getHours()).padStart(2, '0'))
                    .replace('mm', String(currentTime.getMinutes()).padStart(2, '0'))
                    .replace('ss', String(currentTime.getSeconds()).padStart(2, '0'));
                return (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: c.fontSize as number,
                        color: c.color as string,
                        fontFamily: 'monospace',
                    }}>
                        {formatted}
                    </div>
                );
            }

            case 'countdown': {
                const targetVariableKey = String(c.targetVariableKey || '').trim();
                const runtimeTarget = targetVariableKey ? String(runtime.values[targetVariableKey] || '').trim() : '';
                const configuredTarget = String(c.targetTime || '').trim();
                const targetRaw = runtimeTarget || configuredTarget;
                const targetMillis = Date.parse(targetRaw);
                const hasTarget = Number.isFinite(targetMillis);
                const remaining = hasTarget ? Math.max(0, targetMillis - currentTime.getTime()) : 0;
                const dayMs = 24 * 3600 * 1000;
                const hourMs = 3600 * 1000;
                const minuteMs = 60 * 1000;
                const days = Math.floor(remaining / dayMs);
                const hours = Math.floor((remaining % dayMs) / hourMs);
                const minutes = Math.floor((remaining % hourMs) / minuteMs);
                const seconds = Math.floor((remaining % minuteMs) / 1000);
                const showDays = c.showDays !== false;
                const accentColor = (c.accentColor as string) || t.accentColor;
                return (
                    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 8 }}>
                        <div style={{ fontSize: 12, color: (c.color as string) || t.textSecondary }}>
                            {String(c.title || '倒计时')}
                        </div>
                        {!hasTarget ? (
                            <div style={{ fontSize: 13, color: (c.color as string) || t.textSecondary, opacity: 0.8 }}>
                                请配置目标时间或绑定目标时间变量
                            </div>
                        ) : null}
                        <div style={{ display: 'flex', alignItems: 'center', gap: 10, color: accentColor, fontWeight: 700 }}>
                            {showDays ? <span style={{ fontSize: 26 }}>{String(days).padStart(2, '0')}天</span> : null}
                            <span style={{ fontSize: 26 }}>{String(hours).padStart(2, '0')}:</span>
                            <span style={{ fontSize: 26 }}>{String(minutes).padStart(2, '0')}:</span>
                            <span style={{ fontSize: 26 }}>{String(seconds).padStart(2, '0')}</span>
                        </div>
                    </div>
                );
            }

            case 'marquee': {
                const text = String(c.text || '');
                const speed = Math.max(10, Number(c.speed || 40));
                const keyframesName = `dts_marquee_${component.id.replace(/[^a-zA-Z0-9_]/g, '_')}`;
                return (
                    <div
                        style={{
                            width: '100%',
                            height: '100%',
                            overflow: 'hidden',
                            display: 'flex',
                            alignItems: 'center',
                            background: (c.backgroundColor as string) || 'transparent',
                            color: (c.color as string) || t.textPrimary,
                            fontSize: (c.fontSize as number) || 14,
                            whiteSpace: 'nowrap',
                            position: 'relative',
                        }}
                    >
                        <style>{`@keyframes ${keyframesName} { from { transform: translateX(100%); } to { transform: translateX(-100%); } }`}</style>
                        <div style={{ display: 'inline-block', paddingLeft: '100%', animation: `${keyframesName} ${speed}s linear infinite` }}>
                            {text}
                        </div>
                    </div>
                );
            }

            case 'carousel': {
                const items = carouselItems;
                const hasItems = items.length > 0;
                const index = hasItems ? (carouselIndex % items.length) : 0;
                const currentItem = hasItems ? items[index] : '暂无轮播内容';
                const cardTitle = String(c.title || '轮播卡片');
                const cardColor = String(c.color || t.textPrimary);
                const titleColor = String(c.titleColor || t.textSecondary);
                const backgroundColor = String(c.backgroundColor || 'rgba(15,23,42,0.5)');
                const fontSize = Math.max(12, Number(c.fontSize || 24));
                const showDots = c.showDots !== false;
                const showControls = c.showControls !== false;
                const pauseOnHover = c.pauseOnHover !== false;
                const canFlip = hasItems && items.length > 1;
                return (
                    <div
                        style={{
                            width: '100%',
                            height: '100%',
                            border: '1px solid rgba(148,163,184,0.3)',
                            borderRadius: 10,
                            background: backgroundColor,
                            padding: 12,
                            display: 'flex',
                            flexDirection: 'column',
                            justifyContent: 'space-between',
                            boxSizing: 'border-box',
                            overflow: 'hidden',
                        }}
                        onMouseEnter={() => {
                            if (pauseOnHover) {
                                setCarouselPaused(true);
                            }
                        }}
                        onMouseLeave={() => {
                            if (pauseOnHover) {
                                setCarouselPaused(false);
                            }
                        }}
                    >
                        <div style={{ fontSize: 12, color: titleColor, letterSpacing: 0.4 }}>
                            {cardTitle}
                        </div>
                        <div style={{ flex: 1, display: 'grid', gridTemplateColumns: showControls ? '28px 1fr 28px' : '1fr', alignItems: 'center', gap: 8 }}>
                            {showControls && (
                                <button
                                    type="button"
                                    onClick={() => {
                                        if (!canFlip) return;
                                        setCarouselIndex((prev) => (prev - 1 + items.length) % items.length);
                                    }}
                                    style={{
                                        width: 28,
                                        height: 28,
                                        borderRadius: '50%',
                                        border: '1px solid rgba(148,163,184,0.4)',
                                        background: 'rgba(15,23,42,0.45)',
                                        color: cardColor,
                                        cursor: canFlip ? 'pointer' : 'default',
                                        opacity: canFlip ? 1 : 0.45,
                                    }}
                                    title="上一条"
                                >
                                    {'<'}
                                </button>
                            )}
                            <div
                                style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    color: cardColor,
                                    fontSize,
                                    fontWeight: 600,
                                    lineHeight: 1.35,
                                    transition: 'opacity 0.2s ease',
                                    wordBreak: 'break-word',
                                    opacity: hasItems ? 1 : 0.7,
                                }}
                            >
                                {currentItem}
                            </div>
                            {showControls && (
                                <button
                                    type="button"
                                    onClick={() => {
                                        if (!canFlip) return;
                                        setCarouselIndex((prev) => (prev + 1) % items.length);
                                    }}
                                    style={{
                                        width: 28,
                                        height: 28,
                                        borderRadius: '50%',
                                        border: '1px solid rgba(148,163,184,0.4)',
                                        background: 'rgba(15,23,42,0.45)',
                                        color: cardColor,
                                        cursor: canFlip ? 'pointer' : 'default',
                                        opacity: canFlip ? 1 : 0.45,
                                    }}
                                    title="下一条"
                                >
                                    {'>'}
                                </button>
                            )}
                        </div>
                        {showDots && hasItems && (
                            <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end', alignItems: 'center' }}>
                                {items.map((_, dotIdx) => (
                                    <span
                                        key={`dot-${dotIdx}`}
                                        style={{
                                            width: dotIdx === index ? 16 : 6,
                                            height: 6,
                                            borderRadius: 999,
                                            background: dotIdx === index ? '#38bdf8' : 'rgba(148,163,184,0.45)',
                                            transition: 'all 0.2s ease',
                                        }}
                                    />
                                ))}
                            </div>
                        )}
                    </div>
                );
            }

            case 'progress-bar': {
                const value = c.value as number;
                return (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                    }}>
                        <div style={{
                            flex: 1,
                            height: 12,
                            background: t.progressBar.trackBg,
                            borderRadius: 6,
                            overflow: 'hidden',
                        }}>
                            <div style={{
                                width: `${value}%`,
                                height: '100%',
                                background: `linear-gradient(90deg, ${t.progressBar.fillGradient[0]} 0%, ${t.progressBar.fillGradient[1]} 100%)`,
                                borderRadius: 6,
                                transition: 'width 0.3s ease',
                            }} />
                        </div>
                        {Boolean(c.showLabel) && (
                            <span style={{ color: t.progressBar.labelColor, fontSize: 12, minWidth: 40 }}>{value}%</span>
                        )}
                    </div>
                );
            }

            case 'tab-switcher': {
                const options = tabOptions;
                const label = String(c.label ?? '切换');
                const activeValue = tabRuntimeValue || tabDefaultValue || options[0]?.value || '';
                const activeTextColor = String(c.activeTextColor || '#0f172a');
                const activeBackgroundColor = String(c.activeBackgroundColor || '#38bdf8');
                const inactiveTextColor = String(c.inactiveTextColor || t.textSecondary);
                const inactiveBackgroundColor = String(c.inactiveBackgroundColor || 'rgba(15,23,42,0.45)');
                const compact = c.compact === true;
                return (
                    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', gap: 6 }}>
                        <div style={{ fontSize: 12, color: t.textSecondary }}>{label}</div>
                        <div style={{ display: 'flex', gap: compact ? 4 : 8, flexWrap: 'wrap', alignItems: 'center' }}>
                            {options.map((option) => {
                                const active = option.value === activeValue;
                                return (
                                    <button
                                        key={option.value}
                                        type="button"
                                        onClick={() => {
                                            if (!tabVariableKey) return;
                                            runtime.setVariable(tabVariableKey, option.value, `tab-switcher:${component.id}`);
                                        }}
                                        style={{
                                            border: '1px solid rgba(148,163,184,0.3)',
                                            background: active ? activeBackgroundColor : inactiveBackgroundColor,
                                            color: active ? activeTextColor : inactiveTextColor,
                                            borderRadius: 999,
                                            padding: compact ? '3px 10px' : '6px 14px',
                                            fontSize: compact ? 11 : 12,
                                            cursor: tabVariableKey ? 'pointer' : 'default',
                                            whiteSpace: 'nowrap',
                                            transition: 'all 0.2s ease',
                                        }}
                                    >
                                        {option.label}
                                    </button>
                                );
                            })}
                            {options.length === 0 && (
                                <span style={{ fontSize: 12, color: t.textSecondary }}>请在属性中配置 Tab 选项</span>
                            )}
                        </div>
                    </div>
                );
            }

            case 'filter-input': {
                const label = String(c.label ?? '筛选');
                const variableKey = String(c.variableKey ?? '').trim();
                const placeholder = String(c.placeholder ?? '请输入');
                const value = variableKey ? filterInputDraft : '';
                const labelColor = String(c.labelColor || t.textSecondary);
                const inputTextColor = String(c.inputTextColor || t.textPrimary);
                const inputBorderColor = String(c.inputBorderColor || 'rgba(148,163,184,0.4)');
                const inputBackground = String(c.inputBackground || (theme === 'glacier' ? '#ffffff' : 'rgba(15,23,42,0.65)'));
                const debounceMs = normalizeFilterDebounceMs(c.debounceMs);
                return (
                    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', gap: 6 }}>
                        <div style={{ fontSize: 12, color: labelColor }}>{label}</div>
                        <input
                            type="text"
                            value={value}
                            onChange={(e) => {
                                const nextValue = e.target.value;
                                setFilterInputDraft(nextValue);
                                if (!variableKey) return;
                                scheduleFilterVariableUpdate(variableKey, nextValue, `filter-input:${component.id}`, debounceMs);
                            }}
                            onBlur={() => {
                                if (!variableKey) return;
                                scheduleFilterVariableUpdate(variableKey, filterInputDraft, `filter-input:${component.id}`, debounceMs, true);
                            }}
                            placeholder={placeholder}
                            style={{
                                width: '100%',
                                height: 34,
                                borderRadius: 6,
                                border: `1px solid ${inputBorderColor}`,
                                background: inputBackground,
                                color: inputTextColor,
                                padding: '0 10px',
                                outline: 'none',
                            }}
                        />
                    </div>
                );
            }

            case 'filter-select': {
                const label = String(c.label ?? '筛选');
                const variableKey = String(c.variableKey ?? '').trim();
                const placeholder = String(c.placeholder ?? '请选择');
                const optionSourceMode = String(c.optionSourceMode ?? 'manual').trim().toLowerCase();
                const staticOptions = resolveFilterOptions(c.options);
                const dataOptions = optionSourceMode === 'data'
                    ? resolveFilterOptionsFromData(cardData, c)
                    : [];
                const options = dataOptions.length > 0 ? dataOptions : staticOptions;
                const value = variableKey ? (runtime.values[variableKey] ?? '') : '';
                const labelColor = String(c.labelColor || t.textSecondary);
                const inputTextColor = String(c.inputTextColor || t.textPrimary);
                const inputBorderColor = String(c.inputBorderColor || 'rgba(148,163,184,0.4)');
                const inputBackground = String(c.inputBackground || (theme === 'glacier' ? '#ffffff' : 'rgba(15,23,42,0.65)'));
                return (
                    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', gap: 6 }}>
                        <div style={{ fontSize: 12, color: labelColor }}>{label}</div>
                        <select
                            value={value}
                            onChange={(e) => variableKey && runtime.setVariable(variableKey, e.target.value, `filter-select:${component.id}`)}
                            style={{
                                width: '100%',
                                height: 34,
                                borderRadius: 6,
                                border: `1px solid ${inputBorderColor}`,
                                background: inputBackground,
                                color: inputTextColor,
                                padding: '0 10px',
                                outline: 'none',
                            }}
                        >
                            <option value="">{placeholder}</option>
                            {options.map((option) => (
                                <option key={option.value} value={option.value}>{option.label}</option>
                            ))}
                        </select>
                    </div>
                );
            }

            case 'filter-date-range': {
                const label = String(c.label ?? '日期区间');
                const startKey = String(c.startKey ?? '').trim();
                const endKey = String(c.endKey ?? '').trim();
                const startValue = startKey ? (runtime.values[startKey] ?? '') : '';
                const endValue = endKey ? (runtime.values[endKey] ?? '') : '';
                const labelColor = String(c.labelColor || t.textSecondary);
                const inputTextColor = String(c.inputTextColor || t.textPrimary);
                const inputBorderColor = String(c.inputBorderColor || 'rgba(148,163,184,0.4)');
                const inputBackground = String(c.inputBackground || (theme === 'glacier' ? '#ffffff' : 'rgba(15,23,42,0.65)'));
                return (
                    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', gap: 6 }}>
                        <div style={{ fontSize: 12, color: labelColor }}>{label}</div>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 16px 1fr', alignItems: 'center', gap: 4 }}>
                            <input
                                type="date"
                                value={startValue}
                                onChange={(e) => startKey && runtime.setVariable(startKey, e.target.value, `filter-date-range:start:${component.id}`)}
                                style={{
                                    width: '100%',
                                    height: 34,
                                    borderRadius: 6,
                                    border: `1px solid ${inputBorderColor}`,
                                    background: inputBackground,
                                    color: inputTextColor,
                                    padding: '0 8px',
                                    outline: 'none',
                                }}
                            />
                            <span style={{ textAlign: 'center', color: t.textSecondary }}>~</span>
                            <input
                                type="date"
                                value={endValue}
                                onChange={(e) => endKey && runtime.setVariable(endKey, e.target.value, `filter-date-range:end:${component.id}`)}
                                style={{
                                    width: '100%',
                                    height: 34,
                                    borderRadius: 6,
                                    border: `1px solid ${inputBorderColor}`,
                                    background: inputBackground,
                                    color: inputTextColor,
                                    padding: '0 8px',
                                    outline: 'none',
                                }}
                            />
                        </div>
                    </div>
                );
            }

            case 'shape': {
                const shapeType = String(c.shapeType || 'rect');
                const fillColor = String(c.fillColor || 'rgba(59,130,246,0.2)');
                const borderColor = String(c.borderColor || '#60a5fa');
                const borderWidth = Math.max(0, Number(c.borderWidth || 2));
                const radius = Math.max(0, Number(c.radius || 8));
                if (shapeType === 'line' || shapeType === 'arrow') {
                    return (
                        <svg width="100%" height="100%" viewBox="0 0 100 100" preserveAspectRatio="none">
                            {shapeType === 'arrow' ? (
                                <defs>
                                    <marker id={`arrow_${component.id}`} markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
                                        <path d="M0,0 L0,6 L6,3 z" fill={borderColor} />
                                    </marker>
                                </defs>
                            ) : null}
                            <line
                                x1="8"
                                y1="50"
                                x2="92"
                                y2="50"
                                stroke={borderColor}
                                strokeWidth={borderWidth || 2}
                                markerEnd={shapeType === 'arrow' ? `url(#arrow_${component.id})` : undefined}
                            />
                        </svg>
                    );
                }
                if (shapeType === 'circle') {
                    return (
                        <div style={{ width: '100%', height: '100%', borderRadius: '50%', background: fillColor, border: `${borderWidth}px solid ${borderColor}` }} />
                    );
                }
                return (
                    <div style={{ width: '100%', height: '100%', borderRadius: radius, background: fillColor, border: `${borderWidth}px solid ${borderColor}` }} />
                );
            }

            case 'container':
                return (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        border: `${Math.max(0, Number(c.borderWidth || 1))}px solid ${String(c.borderColor || 'rgba(148,163,184,0.35)')}`,
                        borderRadius: Math.max(0, Number(c.radius || 10)),
                        background: String(c.backgroundColor || 'rgba(15,23,42,0.25)'),
                        padding: Math.max(0, Number(c.padding || 12)),
                        boxSizing: 'border-box',
                        color: String(c.titleColor || t.textPrimary),
                    }}>
                        <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>
                            {String(c.title || '容器')}
                        </div>
                        <div style={{ fontSize: 12, color: t.textSecondary }}>
                            容器组件：可用于分组布局与内容分区
                        </div>
                    </div>
                );

            case 'image':
                return c.src ? (
                    <img
                        src={c.src as string}
                        alt=""
                        style={{
                            width: '100%',
                            height: '100%',
                            objectFit: c.fit as 'cover' | 'contain' | 'fill',
                        }}
                    />
                ) : (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        background: t.placeholder.background,
                        border: t.placeholder.border,
                        borderRadius: 4,
                        color: t.placeholder.color,
                        fontSize: 14,
                    }}>
                        图片
                    </div>
                );

            case 'video':
                return c.src ? (
                    <video
                        src={c.src as string}
                        autoPlay={c.autoplay as boolean}
                        loop={c.loop as boolean}
                        muted={c.muted as boolean}
                        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    />
                ) : (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        background: t.placeholder.background,
                        border: t.placeholder.border,
                        borderRadius: 4,
                        color: t.placeholder.color,
                        fontSize: 14,
                    }}>
                        视频
                    </div>
                );

            case 'iframe':
                return c.src ? (
                    <iframe
                        src={c.src as string}
                        style={{ width: '100%', height: '100%', border: 'none' }}
                        title="Embedded content"
                    />
                ) : (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        background: t.placeholder.background,
                        border: t.placeholder.border,
                        borderRadius: 4,
                        color: t.placeholder.color,
                        fontSize: 14,
                    }}>
                        iframe
                    </div>
                );

            // ==================== DataV 边框组件 ====================
            case 'border-box': {
                const boxType = (c.boxType as number) || 1;
                const BorderBoxComponent = borderBoxComponents?.[boxType] || borderBoxComponents?.[1];
                if (!BorderBoxComponent) return dependencyPlaceholder('DataV');
                const colors = c.color as string[] | undefined;
                return (
                    <BorderBoxComponent color={colors}>
                        <div style={{ width: '100%', height: '100%', padding: 16 }}>
                            {c.children as React.ReactNode}
                        </div>
                    </BorderBoxComponent>
                );
            }

            // ==================== DataV 装饰组件 ====================
            case 'decoration': {
                const decorationType = (c.decorationType as number) || 1;
                const DecorationComponent = decorationComponents?.[decorationType] || decorationComponents?.[1];
                if (!DecorationComponent) return dependencyPlaceholder('DataV');
                const colors = c.color as string[] | undefined;
                return (
                    <DecorationComponent color={colors} style={{ width: '100%', height: '100%' }} />
                );
            }

            // ==================== DataV 数据展示组件 ====================
            case 'scroll-board': {
                const { header: displayHeader, data: displayData, columnMeta } = resolveBoundTableData(c, { defaultAlign: 'center' });
                const filteredConfig = { ...c, header: displayHeader, data: displayData, _columnMeta: columnMeta };

                // DataV ScrollBoard 硬编码 color:#fff 且无法通过 CSS/style 覆盖
                // 非 legacy-dark 主题使用自定义表格组件
                if (theme && theme !== 'legacy-dark') {
                    return <ThemedScrollTable config={filteredConfig} tokens={t} />;
                }
                if (!ScrollBoard) return dependencyPlaceholder('DataV');
                const allHaveWidth = columnMeta.length > 0 && columnMeta.every((col) => typeof col.width === 'number');
                const columnWidth = allHaveWidth
                    ? columnMeta.map((col) => Math.max(40, Math.round((width * Number(col.width)) / 100)))
                    : undefined;
                return (
                    <ScrollBoard
                        config={{
                            header: displayHeader,
                            data: displayData,
                            rowNum: c.rowNum as number,
                            headerBGC: c.headerBGC as string,
                            oddRowBGC: c.oddRowBGC as string,
                            evenRowBGC: c.evenRowBGC as string,
                            waitTime: c.waitTime as number || 2000,
                            headerHeight: 35,
                            align: columnMeta.map((col) => col.align || 'center'),
                            ...(columnWidth ? { columnWidth } : {}),
                        }}
                        style={{ width: '100%', height: '100%' }}
                    />
                );
            }

            case 'table': {
                const { header: displayHeader, data: displayData, columnMeta } = resolveBoundTableData(c, { defaultAlign: 'left' });
                const fontSize = (c.fontSize as number) || 13;
                const headerColor = resolveTextColor(c.headerColor as string | undefined, t.textPrimary);
                const headerBackground = (c.headerBackground as string) || 'rgba(148, 163, 184, 0.16)';
                const bodyColor = resolveTextColor(c.bodyColor as string | undefined, t.textSecondary);
                const bodyBackground = (c.bodyBackground as string) || 'transparent';
                const borderColor = (c.borderColor as string) || 'rgba(148, 163, 184, 0.24)';
                const oddRowBackground = (c.oddRowBackground as string) || bodyBackground;
                const evenRowBackground = (c.evenRowBackground as string) || 'rgba(148, 163, 184, 0.06)';
                const enableSort = c.enableSort !== false;
                const enablePagination = c.enablePagination === true;
                const freezeHeader = c.freezeHeader !== false;
                const freezeFirstColumn = c.freezeFirstColumn === true;
                const pageSize = Math.max(1, Number(c.pageSize || 10));
                const conditionalRules = c.conditionalRules;

                const sortedRows = tableSort && enableSort
                    ? [...displayData].sort((a, b) => {
                        const v = compareTableValues(a[tableSort.colIndex], b[tableSort.colIndex]);
                        return tableSort.order === 'asc' ? v : -v;
                    })
                    : displayData;
                const totalPages = enablePagination ? Math.max(1, Math.ceil(sortedRows.length / pageSize)) : 1;
                const safePage = Math.max(1, Math.min(tablePage, totalPages));
                const pageRows = enablePagination
                    ? sortedRows.slice((safePage - 1) * pageSize, safePage * pageSize)
                    : sortedRows;

                return (
                    <div style={{ width: '100%', height: '100%', overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
                        <div style={{ flex: 1, overflow: 'auto' }}>
                        <table style={{ width: '100%', borderCollapse: 'collapse', tableLayout: 'fixed', fontSize }}>
                            {displayHeader.length > 0 && (
                                <thead>
                                    <tr style={{ background: headerBackground }}>
                                        {displayHeader.map((title, i) => (
                                            <th key={i} style={{
                                                color: headerColor,
                                                width: columnMeta[i]?.width ? `${columnMeta[i].width}%` : undefined,
                                                borderBottom: '1px solid ' + borderColor,
                                                borderRight: i < displayHeader.length - 1 ? '1px solid ' + borderColor : 'none',
                                                padding: '8px 10px',
                                                textAlign: columnMeta[i]?.align || 'left',
                                                fontWeight: 600,
                                                whiteSpace: columnMeta[i]?.wrap ? 'normal' : 'nowrap',
                                                overflow: 'hidden',
                                                textOverflow: columnMeta[i]?.wrap ? undefined : 'ellipsis',
                                                overflowWrap: columnMeta[i]?.wrap ? 'anywhere' : undefined,
                                                wordBreak: columnMeta[i]?.wrap ? 'break-word' : undefined,
                                                lineHeight: columnMeta[i]?.wrap ? 1.35 : undefined,
                                                ...(freezeHeader ? { position: 'sticky', top: 0, zIndex: 3 } : {}),
                                                ...(freezeFirstColumn && i === 0
                                                    ? {
                                                        position: 'sticky',
                                                        left: 0,
                                                        zIndex: freezeHeader ? 5 : 2,
                                                        background: headerBackground,
                                                        boxShadow: `1px 0 0 ${borderColor}`,
                                                    }
                                                    : {}),
                                            }}>
                                                <button
                                                    type="button"
                                                    disabled={!enableSort}
                                                    onClick={() => {
                                                        if (!enableSort) return;
                                                        setTablePage(1);
                                                        setTableSort((prev) => {
                                                            if (!prev || prev.colIndex !== i) {
                                                                return { colIndex: i, order: 'asc' };
                                                            }
                                                            if (prev.order === 'asc') {
                                                                return { colIndex: i, order: 'desc' };
                                                            }
                                                            return null;
                                                        });
                                                    }}
                                                    style={{
                                                        border: 'none',
                                                        background: 'transparent',
                                                        color: headerColor,
                                                        fontWeight: 600,
                                                        cursor: enableSort ? 'pointer' : 'default',
                                                        display: 'inline-flex',
                                                        alignItems: 'center',
                                                        gap: 4,
                                                        padding: 0,
                                                    }}
                                                >
                                                    <span>{title}</span>
                                                    {tableSort?.colIndex === i ? (
                                                        <span style={{ fontSize: 10 }}>{tableSort.order === 'asc' ? '▲' : '▼'}</span>
                                                    ) : null}
                                                </button>
                                            </th>
                                        ))}
                                    </tr>
                                </thead>
                            )}
                            <tbody>
                                {pageRows.map((row, rowIndex) => (
                                    <tr key={rowIndex} style={{ background: rowIndex % 2 === 0 ? oddRowBackground : evenRowBackground }}>
                                        {displayHeader.map((_, colIndex) => {
                                            const conditional = resolveTableConditionalStyle(
                                                conditionalRules,
                                                colIndex,
                                                row[colIndex],
                                                columnMeta[colIndex],
                                            );
                                            const rowBackground = rowIndex % 2 === 0 ? oddRowBackground : evenRowBackground;
                                            const cellBackground = conditional.background || rowBackground;
                                            return (
                                                <td key={colIndex} style={{
                                                    color: conditional.color || bodyColor,
                                                    background: cellBackground,
                                                    borderBottom: '1px solid ' + borderColor,
                                                    borderRight: colIndex < displayHeader.length - 1 ? '1px solid ' + borderColor : 'none',
                                                    padding: '8px 10px',
                                                    textAlign: columnMeta[colIndex]?.align || 'left',
                                                    whiteSpace: columnMeta[colIndex]?.wrap ? 'normal' : 'nowrap',
                                                    overflow: 'hidden',
                                                    textOverflow: columnMeta[colIndex]?.wrap ? undefined : 'ellipsis',
                                                    overflowWrap: columnMeta[colIndex]?.wrap ? 'anywhere' : undefined,
                                                    wordBreak: columnMeta[colIndex]?.wrap ? 'break-word' : undefined,
                                                    lineHeight: columnMeta[colIndex]?.wrap ? 1.35 : undefined,
                                                    ...(freezeFirstColumn && colIndex === 0
                                                        ? {
                                                            position: 'sticky',
                                                            left: 0,
                                                            zIndex: 1,
                                                            boxShadow: `1px 0 0 ${borderColor}`,
                                                            background: cellBackground,
                                                        }
                                                        : {}),
                                                }}>
                                                    {row[colIndex] ?? ''}
                                                </td>
                                            );
                                        })}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                        </div>
                        {enablePagination && totalPages > 1 ? (
                            <div style={{
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'flex-end',
                                gap: 6,
                                paddingTop: 8,
                                color: t.textSecondary,
                                fontSize: 12,
                            }}>
                                <button
                                    type="button"
                                    disabled={safePage <= 1}
                                    onClick={() => setTablePage((p) => Math.max(1, p - 1))}
                                    style={{ border: '1px solid rgba(148,163,184,0.35)', background: 'transparent', color: t.textPrimary, borderRadius: 4, padding: '2px 8px', cursor: 'pointer' }}
                                >
                                    上一页
                                </button>
                                <span>{safePage}/{totalPages}</span>
                                <button
                                    type="button"
                                    disabled={safePage >= totalPages}
                                    onClick={() => setTablePage((p) => Math.min(totalPages, p + 1))}
                                    style={{ border: '1px solid rgba(148,163,184,0.35)', background: 'transparent', color: t.textPrimary, borderRadius: 4, padding: '2px 8px', cursor: 'pointer' }}
                                >
                                    下一页
                                </button>
                            </div>
                        ) : null}
                    </div>
                );
            }
            case 'scroll-ranking':
                if (!ScrollRankingBoard) return dependencyPlaceholder('DataV');
                return (
                    <ScrollRankingBoard
                        config={{
                            data: c.data as Array<{ name: string; value: number }>,
                            rowNum: c.rowNum as number || 5,
                            waitTime: c.waitTime as number || 2000,
                            carousel: 'single',
                        }}
                        style={{ width: '100%', height: '100%' }}
                    />
                );

            case 'water-level':
                if (!WaterLevelPond) return dependencyPlaceholder('DataV');
                return (
                    <WaterLevelPond
                        config={{
                            data: [c.value as number],
                            shape: c.shape as 'rect' | 'round' | 'roundRect' || 'round',
                        }}
                        style={{ width: '100%', height: '100%' }}
                    />
                );

            case 'digital-flop':
                if (!DigitalFlop) return dependencyPlaceholder('DataV');
                return (
                    <DigitalFlop
                        config={{
                            number: c.number as number[],
                            content: c.content as string,
                            style: c.style as { fontSize?: number; fill?: string },
                        }}
                        style={{ width: '100%', height: '100%' }}
                    />
                );

            case 'percent-pond': {
                const percentValue = c.value as number;
                const colors = c.colors as string[] || [t.progressBar.fillGradient[0], t.progressBar.fillGradient[1]];
                return (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        position: 'relative',
                    }}>
                        <div style={{
                            width: '100%',
                            height: 20,
                            background: t.progressBar.trackBg,
                            borderRadius: c.borderRadius as number || 5,
                            border: `${c.borderWidth as number || 2}px solid ${colors[0]}`,
                            overflow: 'hidden',
                            position: 'relative',
                        }}>
                            <div style={{
                                width: `${percentValue}%`,
                                height: '100%',
                                background: `linear-gradient(90deg, ${colors[0]} 0%, ${colors[1] || colors[0]} 100%)`,
                                transition: 'width 0.5s ease',
                            }} />
                        </div>
                        <span style={{
                            position: 'absolute',
                            color: t.progressBar.labelColor,
                            fontSize: 14,
                            fontWeight: 'bold',
                            textShadow: '0 0 4px rgba(0,0,0,0.8)',
                        }}>
                            {percentValue}%
                        </span>
                    </div>
                );
            }

            default:
                return (
                    <div style={{
                        width: '100%',
                        height: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        background: t.placeholder.background,
                        border: `1px dashed ${t.placeholder.color}`,
                        borderRadius: 4,
                        color: t.placeholder.color,
                        fontSize: 12,
                    }}>
                        {type}
                    </div>
                );
        }
    }, [
        type,
        effectiveConfig,
        width,
        height,
        currentTime,
        echartsClickHandler,
        t,
        theme,
        themeOptions,
        EChartsComponent,
        dataViewModule,
        borderBoxComponents,
        decorationComponents,
        cardData,
        component,
        mode,
        hasMapFn,
        mapReadyVersion,
        mapDrillRegion,
        tableSort,
        tablePage,
        filterInputDraft,
        runtime.values,
        runtime,
        runtimePlugin,
    ]);

    return (
        !visibleByVariableRule ? null : (
        <div style={{ width: '100%', height: '100%', overflow: 'hidden', position: 'relative' }}>
            {content}
            {/* Drill-down breadcrumb overlay */}
            {drillActive && drillState.breadcrumbs.length > 1 && (
                <div style={{
                    position: 'absolute', top: 4, left: 4,
                    display: 'flex', alignItems: 'center', gap: 2,
                    background: t.breadcrumb.background,
                    padding: '2px 8px', borderRadius: 4,
                    fontSize: 11, color: t.breadcrumb.textColor, zIndex: 10,
                }}>
                    {drillState.breadcrumbs.map((crumb, i) => {
                        const isLast = i === drillState.breadcrumbs.length - 1;
                        return (
                            <span key={crumb.depth} style={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                                {i > 0 && <span style={{ color: t.textMuted, margin: '0 2px' }}>/</span>}
                                {isLast ? (
                                    <span style={{ color: t.textPrimary }}>{crumb.label}</span>
                                ) : (
                                    <span
                                        style={{ color: t.breadcrumb.linkColor, cursor: 'pointer' }}
                                        onClick={() => {
                                            runtime.trackEvent({
                                                kind: 'drill-up',
                                                key: 'drillDepth',
                                                value: String(crumb.depth),
                                                source: `drill:${component.id}`,
                                            });
                                            drillState.handleRollUp(crumb.depth);
                                        }}
                                    >
                                        {crumb.label}
                                    </span>
                                )}
                            </span>
                        );
                    })}
                </div>
            )}
            {/* Card data source loading indicator */}
            {cardLoading && (
                <div style={{
                    position: 'absolute', top: 4, right: 4,
                    width: 8, height: 8, borderRadius: '50%',
                    background: t.accentColor,
                    animation: 'pulse 1.5s ease-in-out infinite',
                }} />
            )}
            {/* Card data source error indicator */}
            {cardError && (
                <div style={{
                    position: 'absolute', bottom: 4, left: 4,
                    fontSize: 10, color: '#ef4444',
                    background: t.errorBg,
                    padding: '2px 6px', borderRadius: 3,
                    maxWidth: '80%', overflow: 'hidden',
                    textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                }} title={cardError}>
                    {cardError}
                </div>
            )}
            {pluginMeta && !runtimePlugin && (
                <div style={{
                    position: 'absolute',
                    bottom: 4,
                    right: 4,
                    fontSize: 10,
                    color: '#fbbf24',
                    background: 'rgba(30,41,59,0.85)',
                    padding: '2px 6px',
                    borderRadius: 3,
                    maxWidth: '60%',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                }} title="插件未加载，已使用基础组件渲染">
                    插件未加载，已降级
                </div>
                )}
            </div>
        )
    );
});
