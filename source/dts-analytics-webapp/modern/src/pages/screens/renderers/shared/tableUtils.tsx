/** Table rendering utilities for screen components. */
import { useEffect, useState } from 'react';
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

export interface ColumnEntry {
    source: string;
    alias?: string;
    align?: ColumnAlign;
    width?: number;
    wrap?: boolean;
    formatter?: ColumnFormatter;
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
    title: string;
    align: ColumnAlign;
    width?: number;
    wrap: boolean;
    formatter: ColumnFormatter;
    baseType?: string;
    /** Whether this column contains masked/desensitized data (from backend RLS/masking policy). */
    masked?: boolean;
}

export interface ResolvedTableData {
    header: string[];
    data: string[][];
    columnMeta: ResolvedColumnMeta[];
}

// ---------------------------------------------------------------------------
// Public functions
// ---------------------------------------------------------------------------

export function compareTableValues(a: unknown, b: unknown): number {
    const na = Number(a);
    const nb = Number(b);
    if (Number.isFinite(na) && Number.isFinite(nb)) {
        return na - nb;
    }
    return String(a ?? '').localeCompare(String(b ?? ''), 'zh-CN');
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
                masked: sc?.masked === true,
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
