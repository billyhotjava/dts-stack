/** Table rendering utilities for screen components. */
import { useCallback, useEffect, useRef, useState } from 'react';
import type { ScreenThemeTokens } from '../../screenThemes';

// ---------------------------------------------------------------------------
// Internal helpers (used by ThemedScrollTable)
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Public types
// ---------------------------------------------------------------------------

export type ColumnAlign = 'left' | 'center' | 'right';
export type ColumnFormatter = 'auto' | 'string' | 'number' | 'percent' | 'date';
export type ColumnWidthUnit = 'px' | 'percent';

/**
 * 列级指标说明 — 与组件 metricNote 同形,用于在列头渲染 ℹ️。
 * 数据形态最终消费方为 renderers/shared/MetricNote.tsx 中的 MetricNote interface。
 */
export type ColumnMetricNote = Record<string, unknown>;

export interface ColumnEntry {
    /** Legacy field key used by template/table binding editors. */
    source?: string;
    /** Schema editor field key. Kept compatible with `source`. */
    key?: string;
    field?: string;
    dataKey?: string;
    name?: string;
    alias?: string;
    label?: string;
    title?: string;
    header?: string;
    displayName?: string;
    align?: ColumnAlign;
    headerAlign?: ColumnAlign;
    width?: number | string;
    widthUnit?: ColumnWidthUnit | '%';
    wrap?: boolean;
    formatter?: ColumnFormatter;
    sortable?: boolean;
    frozen?: boolean;
    /** 列级指标说明,鼠标悬浮列头时弹出。 */
    metricNote?: ColumnMetricNote;
}

export interface SourceColumnMeta {
    name: string;
    displayName: string;
    baseType?: string;
    /** Column contains masked/desensitized data (set by backend RLS/masking policy). */
    masked?: boolean;
}

export interface ResolvedColumnMeta {
    key: string;
    source?: string;
    title: string;
    align: ColumnAlign;
    headerAlign?: ColumnAlign;
    width?: number;
    widthUnit?: ColumnWidthUnit;
    widthCss?: string;
    wrap: boolean;
    formatter: ColumnFormatter;
    sortable?: boolean;
    frozen?: boolean;
    baseType?: string;
    /** Whether this column contains masked/desensitized data (from backend RLS/masking policy). */
    masked?: boolean;
    /** 列级指标说明 — 列头渲染 ℹ️ 时使用。 */
    metricNote?: ColumnMetricNote;
}

export interface ResolvedTableData {
    header: string[];
    data: string[][];
    columnMeta: ResolvedColumnMeta[];
}

export interface ResolvedTableRowBackgrounds {
    oddRowBackground: string;
    evenRowBackground: string;
}

// ---------------------------------------------------------------------------
// Public functions
// ---------------------------------------------------------------------------

const DEFAULT_TABLE_EVEN_ROW_BACKGROUND = 'rgba(148, 163, 184, 0.06)';

function normalizeCssBackground(value: unknown): string | undefined {
    if (typeof value !== 'string') return undefined;
    const text = value.trim();
    return text.length > 0 ? text : undefined;
}

function normalizeComparableCssBackground(value: string | undefined): string {
    return (value ?? '').replace(/\s+/g, '').toLowerCase();
}

function isTransparentBackground(value: string | undefined): boolean {
    return normalizeComparableCssBackground(value) === 'transparent';
}

function isDefaultEvenRowBackground(value: string | undefined): boolean {
    return normalizeComparableCssBackground(value) === normalizeComparableCssBackground(DEFAULT_TABLE_EVEN_ROW_BACKGROUND);
}

export function resolveTableRowBackgrounds(config: Record<string, unknown>): ResolvedTableRowBackgrounds {
    const bodyBackground = normalizeCssBackground(config.bodyBackground) ?? 'transparent';
    const hasBodyBackground = !isTransparentBackground(bodyBackground);
    const oddCandidate = normalizeCssBackground(config.oddRowBackground);
    const evenCandidate = normalizeCssBackground(config.evenRowBackground);

    const oddRowBackground = oddCandidate && !isTransparentBackground(oddCandidate)
        ? oddCandidate
        : bodyBackground;
    const evenRowBackground = evenCandidate
        && !isTransparentBackground(evenCandidate)
        && !(hasBodyBackground && isDefaultEvenRowBackground(evenCandidate))
        ? evenCandidate
        : hasBodyBackground
            ? bodyBackground
            : DEFAULT_TABLE_EVEN_ROW_BACKGROUND;

    return { oddRowBackground, evenRowBackground };
}

export function compareTableValues(a: unknown, b: unknown): number {
    const na = Number(a);
    const nb = Number(b);
    if (Number.isFinite(na) && Number.isFinite(nb)) {
        return na - nb;
    }
    return String(a ?? '').localeCompare(String(b ?? ''), 'zh-CN');
}

export function estimateTablePlaceholderRowCount({
    containerHeight,
    headerHeight = 0,
    rowHeight,
    footerHeight = 0,
    currentRowCount,
    minimumVisibleRows = 0,
}: {
    containerHeight: number;
    headerHeight?: number;
    rowHeight: number;
    footerHeight?: number;
    currentRowCount: number;
    minimumVisibleRows?: number;
}): number {
    const safeContainerHeight = Math.max(0, Number(containerHeight) || 0);
    const safeHeaderHeight = Math.max(0, Number(headerHeight) || 0);
    const safeFooterHeight = Math.max(0, Number(footerHeight) || 0);
    const safeRowHeight = Math.max(1, Number(rowHeight) || 0);
    const safeCurrentRowCount = Math.max(0, Math.floor(Number(currentRowCount) || 0));
    const safeMinimumVisibleRows = Math.max(0, Math.floor(Number(minimumVisibleRows) || 0));

    if (safeContainerHeight <= 0) {
        return 0;
    }

    const availableHeight = Math.max(0, safeContainerHeight - safeHeaderHeight - safeFooterHeight);
    if (availableHeight <= 0) {
        return 0;
    }

    const estimatedVisibleRows = Math.max(
        safeMinimumVisibleRows,
        Math.floor(availableHeight / safeRowHeight),
    );

    return Math.max(0, estimatedVisibleRows - safeCurrentRowCount);
}

export function resolveTableConditionalStyle(
    rules: unknown,
    columnIndex: number,
    raw: unknown,
    columnMeta?: { key?: string; title?: string },
): { color?: string; background?: string } {
    if (!Array.isArray(rules)) {
        return {};
    }
    for (const item of rules) {
        if (!item || typeof item !== 'object') continue;
        const rule = item as Record<string, unknown>;
        if (isRowConditionalRule(rule)) continue;
        const style = resolveMatchedConditionalRule(rule, columnIndex, raw, columnMeta);
        if (style) return style;
    }
    return {};
}

export function resolveTableRowConditionalStyle(
    rules: unknown,
    row: unknown[],
    columnMeta: Array<{ key?: string; title?: string }>,
): { color?: string; background?: string } {
    if (!Array.isArray(rules)) {
        return {};
    }
    for (const item of rules) {
        if (!item || typeof item !== 'object') continue;
        const rule = item as Record<string, unknown>;
        if (!isRowConditionalRule(rule)) continue;
        const columnIndex = resolveConditionalRuleColumnIndex(rule, columnMeta);
        if (columnIndex < 0) continue;
        const style = resolveMatchedConditionalRule(rule, columnIndex, row[columnIndex], columnMeta[columnIndex]);
        if (style) return style;
    }
    return {};
}

function isRowConditionalRule(rule: Record<string, unknown>): boolean {
    return rule.scope === 'row' || rule.applyTo === 'row' || rule.target === 'row';
}

function resolveConditionalRuleColumnIndex(
    rule: Record<string, unknown>,
    columnMeta: Array<{ key?: string; title?: string }>,
): number {
    const ruleColumnKey = String(rule.columnKey ?? '').trim().toLowerCase();
    const ruleColumnTitle = String(rule.columnTitle ?? '').trim().toLowerCase();
    if (ruleColumnKey || ruleColumnTitle) {
        const matched = columnMeta.findIndex((meta) => {
            const normalizedKey = String(meta?.key ?? '').trim().toLowerCase();
            const normalizedTitle = String(meta?.title ?? '').trim().toLowerCase();
            return (ruleColumnKey.length > 0 && normalizedKey === ruleColumnKey)
                || (ruleColumnTitle.length > 0 && normalizedTitle === ruleColumnTitle);
        });
        if (matched >= 0) return matched;
    }
    const ruleCol = Number(rule.columnIndex);
    return Number.isFinite(ruleCol) ? ruleCol : -1;
}

function resolveMatchedConditionalRule(
    rule: Record<string, unknown>,
    columnIndex: number,
    raw: unknown,
    columnMeta?: { key?: string; title?: string },
): { color?: string; background?: string } | null {
    const normalizedCurrentKey = String(columnMeta?.key ?? '').trim().toLowerCase();
    const normalizedCurrentTitle = String(columnMeta?.title ?? '').trim().toLowerCase();
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
    if (!columnMatched) return null;
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
    if (!matched) return null;
    return {
        color: typeof rule.color === 'string' ? rule.color : undefined,
        background: typeof rule.background === 'string' ? rule.background : undefined,
    };
}

export function normalizeColumnAlign(value: unknown, fallback: ColumnAlign): ColumnAlign {
    if (value === 'left' || value === 'center' || value === 'right') return value;
    return fallback;
}

export function normalizeColumnFormatter(value: unknown): ColumnFormatter {
    if (value === 'string' || value === 'number' || value === 'percent' || value === 'date') return value;
    return 'auto';
}

export function clampColumnWidth(value: unknown): number | undefined {
    const n = Number(value);
    if (!Number.isFinite(n)) return undefined;
    if (n <= 0) return undefined;
    return Math.max(5, Math.min(100, n));
}

function asTrimmedString(value: unknown): string | undefined {
    if (typeof value !== 'string') return undefined;
    const text = value.trim();
    return text.length > 0 ? text : undefined;
}

function resolveColumnSourceKey(col: ColumnEntry | undefined): string | undefined {
    if (!col) return undefined;
    return asTrimmedString(col.key)
        ?? asTrimmedString(col.source)
        ?? asTrimmedString(col.field)
        ?? asTrimmedString(col.dataKey)
        ?? asTrimmedString(col.name);
}

function resolveColumnTitle(
    col: ColumnEntry | undefined,
    sourceMeta: SourceColumnMeta | undefined,
    fallback: string,
): string {
    return asTrimmedString(col?.label)
        ?? asTrimmedString(col?.alias)
        ?? asTrimmedString(col?.title)
        ?? asTrimmedString(col?.header)
        ?? asTrimmedString(col?.displayName)
        ?? asTrimmedString(sourceMeta?.displayName)
        ?? fallback;
}

function inferColumnWidthUnit(col: ColumnEntry | undefined): ColumnWidthUnit {
    if (col?.widthUnit === 'px') return 'px';
    if (col?.widthUnit === 'percent' || col?.widthUnit === '%') return 'percent';
    const widthText = typeof col?.width === 'string' ? col.width.trim().toLowerCase() : '';
    if (widthText.endsWith('%')) return 'percent';
    if (widthText.endsWith('px')) return 'px';
    const hasSchemaShape = Boolean(col?.key || col?.label || col?.title || col?.header);
    const hasLegacyShape = Boolean(col?.source || col?.alias);
    return hasLegacyShape && !hasSchemaShape ? 'percent' : 'px';
}

function parseColumnWidth(value: unknown): { value: number; unit?: ColumnWidthUnit } | undefined {
    if (value == null || value === '') return undefined;
    if (typeof value === 'number') {
        return Number.isFinite(value) ? { value } : undefined;
    }
    if (typeof value !== 'string') return undefined;
    const text = value.trim().toLowerCase();
    if (!text) return undefined;
    if (text.endsWith('%')) {
        const n = Number(text.slice(0, -1));
        return Number.isFinite(n) ? { value: n, unit: 'percent' } : undefined;
    }
    if (text.endsWith('px')) {
        const n = Number(text.slice(0, -2));
        return Number.isFinite(n) ? { value: n, unit: 'px' } : undefined;
    }
    const n = Number(text);
    return Number.isFinite(n) ? { value: n } : undefined;
}

function resolveColumnWidth(col: ColumnEntry | undefined): Pick<ResolvedColumnMeta, 'width' | 'widthUnit' | 'widthCss'> {
    const parsed = parseColumnWidth(col?.width);
    if (!parsed || parsed.value <= 0) return {};
    const widthUnit = parsed.unit ?? inferColumnWidthUnit(col);
    if (widthUnit === 'percent') {
        const width = Math.max(5, Math.min(100, parsed.value));
        return { width, widthUnit, widthCss: `${width}%` };
    }
    const width = Math.max(40, Math.min(2000, parsed.value));
    return { width, widthUnit, widthCss: `${width}px` };
}

function resolveColumnSortable(col: ColumnEntry | undefined): boolean | undefined {
    return typeof col?.sortable === 'boolean' ? col.sortable : undefined;
}

function resolveStaticColumnIndex(rawHeader: string[], col: ColumnEntry | undefined, fallbackIndex: number): number {
    const key = resolveColumnSourceKey(col);
    if (!key) return fallbackIndex;
    const numericIndex = Number(key);
    if (Number.isInteger(numericIndex) && numericIndex >= 0) {
        return numericIndex;
    }
    const headerIndex = rawHeader.findIndex((header) => header === key);
    return headerIndex >= 0 ? headerIndex : fallbackIndex;
}

function resolveColumnStickyWidth(meta: ResolvedColumnMeta | undefined, fallbackWidth: number): number {
    if (meta?.widthUnit === 'px' && typeof meta.width === 'number' && Number.isFinite(meta.width)) {
        return Math.max(1, meta.width);
    }
    if (typeof meta?.widthCss === 'string' && meta.widthCss.trim().toLowerCase().endsWith('px')) {
        const parsed = Number(meta.widthCss.trim().slice(0, -2));
        if (Number.isFinite(parsed) && parsed > 0) return parsed;
    }
    return Math.max(1, Number(fallbackWidth) || 100);
}

export function resolveFrozenColumnOffsets(
    columnMeta: ResolvedColumnMeta[],
    fallbackColumnWidth: number,
): Array<number | undefined> {
    let left = 0;
    return columnMeta.map((meta) => {
        if (meta.frozen !== true) return undefined;
        const offset = left;
        left += resolveColumnStickyWidth(meta, fallbackColumnWidth);
        return offset;
    });
}

function clampTableColumnResizeWidth(widthPx: unknown, minimumWidth: number, maximumWidth: number): number | undefined {
    const raw = Number(widthPx);
    if (!Number.isFinite(raw)) return undefined;
    const min = Math.max(1, Number(minimumWidth) || 1);
    const max = Math.max(min, Number(maximumWidth) || min);
    return Math.max(min, Math.min(max, Math.round(raw)));
}

export function resolveTableColumnResizePreview({
    columnWidths,
    columnIndex,
    widthPx,
    minimumWidth = 40,
    maximumWidth = 2000,
    minimumTableWidth = 0,
}: {
    columnWidths: number[];
    columnIndex: number;
    widthPx: number;
    minimumWidth?: number;
    maximumWidth?: number;
    minimumTableWidth?: number;
}): { columnWidth: number; tableMinWidth: number } | undefined {
    if (!Number.isInteger(columnIndex) || columnIndex < 0 || columnIndex >= columnWidths.length) {
        return undefined;
    }
    const columnWidth = clampTableColumnResizeWidth(widthPx, minimumWidth, maximumWidth);
    if (columnWidth == null) return undefined;
    const minWidth = Math.max(1, Number(minimumWidth) || 1);
    const widths = columnWidths.map((value, index) => {
        if (index === columnIndex) return columnWidth;
        const width = Number(value);
        return Number.isFinite(width) && width > 0 ? Math.round(width) : minWidth;
    });
    const tableMinWidth = Math.max(
        Math.max(0, Math.round(Number(minimumTableWidth) || 0)),
        widths.reduce((sum, value) => sum + value, 0),
    );
    return { columnWidth, tableMinWidth };
}

function columnConfigFromMeta(meta: ResolvedColumnMeta | undefined, index: number): ColumnEntry {
    const key = asTrimmedString(meta?.key) ?? String(index);
    const title = asTrimmedString(meta?.title) ?? `列${index + 1}`;
    const source = asTrimmedString(meta?.source) ?? key;
    const column: ColumnEntry = {
        key,
        source,
        label: title,
        alias: title,
        align: normalizeColumnAlign(meta?.align, 'left'),
        wrap: meta?.wrap === true,
        formatter: normalizeColumnFormatter(meta?.formatter),
    };
    if (meta?.headerAlign === 'left' || meta?.headerAlign === 'center' || meta?.headerAlign === 'right') {
        column.headerAlign = meta.headerAlign;
    }
    if (typeof meta?.sortable === 'boolean') {
        column.sortable = meta.sortable;
    }
    if (meta?.frozen === true) {
        column.frozen = true;
    }
    if (meta?.metricNote) {
        column.metricNote = meta.metricNote;
    }
    return column;
}

export function resizeTableColumnConfig({
    columns,
    columnMeta,
    columnIndex,
    widthPx,
    minimumWidth = 40,
    maximumWidth = 2000,
}: {
    columns?: ColumnEntry[];
    columnMeta: ResolvedColumnMeta[];
    columnIndex: number;
    widthPx: number;
    minimumWidth?: number;
    maximumWidth?: number;
}): ColumnEntry[] | undefined {
    if (!Number.isInteger(columnIndex) || columnIndex < 0) return columns;
    const width = clampTableColumnResizeWidth(widthPx, minimumWidth, maximumWidth);
    if (width == null) return columns;
    const baseColumns = columns?.length
        ? columns.map((item) => ({ ...item }))
        : columnMeta.map((meta, index) => columnConfigFromMeta(meta, index));
    if (columnIndex >= baseColumns.length) return columns;
    return baseColumns.map((column, index) => (
        index === columnIndex
            ? { ...column, width, widthUnit: 'px' }
            : column
    ));
}

export function formatTableCell(value: unknown, formatter: ColumnFormatter, baseType?: string): string {
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
export function ThemedScrollTable({ config, tokens, onRowClick, isRowInteractive }: {
    config: Record<string, unknown>;
    tokens: ScreenThemeTokens;
    onRowClick?: (row: string[], rowIndex: number) => void;
    isRowInteractive?: boolean;
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

    // Column resize state
    const [colWidths, setColWidths] = useState<Array<number | undefined>>([]);
    const resizing = useRef<{ colIndex: number; startX: number; startWidth: number } | null>(null);
    const headerRef = useRef<HTMLDivElement>(null);
    const columnWidthSignature = (columnMeta ?? []).map((meta) => meta.widthCss ?? '').join('|');

    useEffect(() => {
        if (headers.length > 0) {
            setColWidths(headers.map((_, i) => {
                const meta = columnMeta?.[i];
                return meta?.widthUnit === 'percent' ? clampColumnWidth(meta.width) : undefined;
            }));
        }
    }, [headers.length, columnWidthSignature]);

    const handleResizeStart = useCallback((e: React.MouseEvent, colIndex: number) => {
        e.preventDefault();
        e.stopPropagation();
        const headerEl = headerRef.current;
        if (!headerEl) return;
        const currentWidth = colWidths[colIndex] || (100 / headers.length);
        resizing.current = { colIndex, startX: e.clientX, startWidth: currentWidth };

        const onMove = (ev: MouseEvent) => {
            if (!resizing.current || !headerEl) return;
            const totalWidth = headerEl.offsetWidth;
            const delta = ev.clientX - resizing.current.startX;
            const deltaPercent = (delta / totalWidth) * 100;
            const newWidth = Math.max(5, resizing.current.startWidth + deltaPercent);
            setColWidths(prev => {
                const next = [...prev];
                next[resizing.current!.colIndex] = newWidth;
                return next;
            });
        };
        const onUp = () => {
            resizing.current = null;
            window.removeEventListener('mousemove', onMove);
            window.removeEventListener('mouseup', onUp);
        };
        window.addEventListener('mousemove', onMove);
        window.addEventListener('mouseup', onUp);
    }, [colWidths, headers.length]);

    const getColumnLayout = (index: number): React.CSSProperties => {
        const w = colWidths[index];
        const align = normalizeColumnAlign(columnMeta?.[index]?.align, 'center');
        if (w) {
            return { flex: `0 0 ${w}%`, width: `${w}%`, textAlign: align };
        }
        const widthCss = columnMeta?.[index]?.widthCss;
        if (widthCss) {
            return { flex: `0 0 ${widthCss}`, width: widthCss, textAlign: align };
        }
        return { flex: '1 1 0', textAlign: align };
    };

    return (
        <div style={{ width: '100%', height: '100%', overflow: 'hidden', color: textColor, fontSize: 14 }}>
            {headers.length > 0 && (
                <div ref={headerRef} style={{
                    display: 'flex', background: headerBGC, height: headerHeight,
                    lineHeight: `${headerHeight}px`, fontWeight: 600, fontSize: 15, flexShrink: 0,
                    color: headerColor, position: 'relative',
                }}>
                    {headers.map((h, i) => (
                        <div key={i} style={{
                            ...getColumnLayout(i),
                            padding: '0 10px',
                            whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis',
                            position: 'relative',
                        }}>
                            {h}
                            {i < headers.length - 1 && (
                                <div
                                    onMouseDown={(e) => handleResizeStart(e, i)}
                                    style={{
                                        position: 'absolute', right: -2, top: 0, bottom: 0, width: 5,
                                        cursor: 'col-resize', zIndex: 1,
                                        background: resizing.current?.colIndex === i ? 'rgba(64,158,255,0.4)' : 'transparent',
                                    }}
                                    onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.background = 'rgba(64,158,255,0.3)'; }}
                                    onMouseLeave={(e) => { if (!resizing.current) (e.currentTarget as HTMLElement).style.background = 'transparent'; }}
                                />
                            )}
                        </div>
                    ))}
                </div>
            )}
            <div style={{ height: visibleHeight, overflow: 'hidden', position: 'relative' }}>
                <div style={{
                    transition: needScroll ? 'transform 0.5s ease' : 'none',
                    transform: needScroll ? `translateY(-${0}px)` : 'none',
                }}>
                    {visibleRows.map((row, ri) => (
                        <div
                            key={`${offset}-${ri}`}
                            onClick={() => onRowClick?.(row.cells, row.originalIndex)}
                            style={{
                                display: 'flex',
                                height: rowHeight,
                                lineHeight: `${rowHeight}px`,
                                background: row.originalIndex % 2 === 0 ? evenRowBGC : oddRowBGC,
                                cursor: isRowInteractive ? 'pointer' : 'default',
                            }}
                        >
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

export function resolveBoundTableData(
    config: Record<string, unknown>,
    options?: { defaultAlign?: ColumnAlign },
): ResolvedTableData {
    const defaultAlign = options?.defaultAlign ?? 'left';
    const rawHeaderAlign = config.headerAlign;
    const globalHeaderAlign: ColumnAlign | undefined =
        rawHeaderAlign === 'left' || rawHeaderAlign === 'center' || rawHeaderAlign === 'right'
            ? rawHeaderAlign : undefined;
    const sourceCols = config._sourceColumns as SourceColumnMeta[] | undefined;
    const columnsConfig = config.columns as ColumnEntry[] | undefined;
    const allData = (config.data as Array<Array<unknown>> | undefined) || [];
    // columnNotes 是按列标题映射的指标说明 — 用于"SQL 自动推断列"无 columns 配置的场景。
    const columnNotes = (config.columnNotes as Record<string, ColumnMetricNote> | undefined) || undefined;
    const resolveColumnNote = (col: ColumnEntry | undefined, title: string, sourceKey?: string): ColumnMetricNote | undefined =>
        col?.metricNote || columnNotes?.[title] || columnNotes?.[sourceKey || ''] || undefined;

    if (sourceCols?.length) {
        const effectiveColumns = columnsConfig
            ? columnsConfig
            : sourceCols.map((col) => ({ source: col.name } as ColumnEntry));
        const sourceMetaByName = new Map(sourceCols.map((item) => [item.name, item] as const));
        const sourceIndexByName = new Map(sourceCols.map((item, index) => [item.name, index] as const));

        const columnMeta = effectiveColumns.map((col): ResolvedColumnMeta => {
            const sourceKey = resolveColumnSourceKey(col) ?? '';
            const sc = sourceMetaByName.get(sourceKey);
            const colHeaderAlign = col.headerAlign === 'left' || col.headerAlign === 'center' || col.headerAlign === 'right'
                ? col.headerAlign : undefined;
            const title = resolveColumnTitle(col, sc, sourceKey || '列');
            const widthConfig = resolveColumnWidth(col);
            return {
                key: sourceKey,
                source: sourceKey,
                title,
                align: normalizeColumnAlign(col.align, defaultAlign),
                headerAlign: colHeaderAlign ?? globalHeaderAlign,
                ...widthConfig,
                wrap: col.wrap === true,
                formatter: normalizeColumnFormatter(col.formatter),
                sortable: resolveColumnSortable(col),
                frozen: col.frozen === true,
                baseType: sc?.baseType,
                masked: sc?.masked === true,
                metricNote: resolveColumnNote(col, title, sourceKey),
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
    if (columnsConfig) {
        const resolvedColumns = columnsConfig.map((col, idx) => {
            const sourceIndex = resolveStaticColumnIndex(rawHeader, col, idx);
            const rawTitle = rawHeader[sourceIndex] ?? `列${sourceIndex + 1}`;
            const sourceKey = resolveColumnSourceKey(col) ?? String(sourceIndex);
            const aliasTitle = alias?.[String(sourceIndex)] || alias?.[sourceKey] || alias?.[rawTitle];
            const title = resolveColumnTitle(col, undefined, aliasTitle || rawTitle);
            const colHeaderAlign = col.headerAlign === 'left' || col.headerAlign === 'center' || col.headerAlign === 'right'
                ? col.headerAlign : undefined;
            const meta: ResolvedColumnMeta = {
                key: sourceKey,
                source: sourceKey,
                title,
                align: normalizeColumnAlign(col.align, defaultAlign),
                headerAlign: colHeaderAlign ?? globalHeaderAlign,
                ...resolveColumnWidth(col),
                wrap: col.wrap === true,
                formatter: normalizeColumnFormatter(col.formatter),
                sortable: resolveColumnSortable(col),
                frozen: col.frozen === true,
                metricNote: resolveColumnNote(col, title, sourceKey),
            };
            return { sourceIndex, meta };
        });
        const columnMeta = resolvedColumns.map((item) => item.meta);
        const data = allData.map((row) =>
            resolvedColumns.map(({ sourceIndex, meta }) => formatTableCell(row[sourceIndex], meta.formatter)),
        );
        return {
            header: columnMeta.map((col) => col.title),
            data,
            columnMeta,
        };
    }
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
        headerAlign: globalHeaderAlign,
        wrap: false,
        formatter: 'auto',
        metricNote: resolveColumnNote(undefined, title, String(idx)),
    }));
    const data = allData.map((row) =>
        columnMeta.map((col, idx) => formatTableCell(row[idx], col.formatter)),
    );

    return { header, data, columnMeta };
}
