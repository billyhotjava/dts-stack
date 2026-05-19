/**
 * Table-family renderer: table, scroll-board, scroll-ranking.
 * Extracted verbatim from ComponentRenderer.tsx — do not modify rendering logic.
 */
import type { CSSProperties, ReactNode, Dispatch, SetStateAction } from 'react';
import { ArrowDown, ArrowUp } from 'lucide-react';
import type { CardData, ScreenComponent } from '../types';
import type { ScreenThemeTokens } from '../screenThemes';
import type { ScreenTheme } from '../types';
import { resolveTextColor } from './shared/chartUtils';
import { MetricNoteBadge, type MetricNote } from './shared/MetricNote';
import {
    compareTableValues, estimateTablePlaceholderRowCount, resolveTableConditionalStyle,
    ThemedScrollTable, resolveBoundTableData,
} from './shared/tableUtils';
import {
    buildTableRowActionParams,
    resolvePreferredDrillValue,
} from './shared/actionUtils';
import { DelayReasonMatrix } from '../../project-cockpit/components/DelayReasonMatrix';

// Re-export the DrillState shape used by this renderer
interface DrillState {
    canDrillDown: boolean;
    breadcrumbs: Array<{ value: string }>;
    handleDrill: (value: string) => void;
}

interface ScreenRuntime {
    trackEvent: (event: { kind: string; key: string; value: string; source: string; meta?: string }) => void;
}

export interface TableRendererProps {
    type: string;
    c: Record<string, unknown>;
    t: ScreenThemeTokens;
    width: number;
    height: number;
    theme?: ScreenTheme;
    mode: 'designer' | 'preview';
    component: ScreenComponent;
    runtime: ScreenRuntime;
    cardData: CardData | null;
    tableSort: { colIndex: number; order: 'asc' | 'desc' } | null;
    setTableSort: Dispatch<SetStateAction<{ colIndex: number; order: 'asc' | 'desc' } | null>>;
    tablePage: number;
    setTablePage: Dispatch<SetStateAction<number>>;
    drillState: DrillState;
    drillActive: boolean;
    drillRuntimeEnabled: boolean;
    componentActions: Array<Record<string, unknown>>;
    executeComponentActions: (params: Record<string, unknown>) => void;
    renderUnavailableState: (title: string, detail?: string) => ReactNode;
}

export function renderTable(props: TableRendererProps): ReactNode {
    const {
        type,
        c,
        t,
        height,
        mode,
        component,
        runtime,
        cardData,
        tableSort,
        setTableSort,
        tablePage,
        setTablePage,
        drillState,
        drillRuntimeEnabled,
        componentActions,
        executeComponentActions,
    } = props;

    // 抽出的"滚动表格"渲染逻辑 —— 供 legacy `type='scroll-board'` 与
    // 新的 `type='table' && renderMode='scroll'` 两处调用，保证行为完全一致。
    const renderAsScrollBoard = (sourceTag: string): ReactNode => {
        const { header: displayHeader, data: displayData, columnMeta } = resolveBoundTableData(c, { defaultAlign: 'center' });
        const filteredConfig = { ...c, header: displayHeader, data: displayData, _columnMeta: columnMeta };
        const canRunScrollBoardActions = mode === 'preview' && componentActions.length > 0;
        const canRunScrollBoardDefaultDrill = mode === 'preview' && !canRunScrollBoardActions && drillRuntimeEnabled && drillState.canDrillDown;
        const handleScrollBoardRowClick = (row: string[]) => {
            const params = buildTableRowActionParams(displayHeader, row);
            if (canRunScrollBoardActions) {
                executeComponentActions(params);
                return;
            }
            if (!canRunScrollBoardDefaultDrill) {
                return;
            }
            const clickedValue = resolvePreferredDrillValue(params);
            if (!clickedValue) {
                return;
            }
            runtime.trackEvent({
                kind: 'drill-down',
                key: 'drillValue',
                value: clickedValue,
                source: `drill:${component.id}:${sourceTag}`,
                meta: `depth=${drillState.breadcrumbs.length}`,
            });
            drillState.handleDrill(clickedValue);
        };

        return (
            <ThemedScrollTable
                config={filteredConfig}
                tokens={t}
                isRowInteractive={canRunScrollBoardActions || canRunScrollBoardDefaultDrill}
                onRowClick={handleScrollBoardRowClick}
            />
        );
    };

    switch (type) {
        case 'scroll-board': {
            // 历史数据兼容路径：type='scroll-board' 直接按滚动表格渲染。
            return renderAsScrollBoard('scroll-board');
        }

        case 'table': {
            const tableRenderMode = String(c.renderMode ?? '').trim().toLowerCase();
            // 新路径：table 组件的滚动模式
            if (tableRenderMode === 'scroll') {
                return renderAsScrollBoard('table-scroll');
            }
            if (tableRenderMode === 'delay-reason-matrix') {
                const sourceCols = Array.isArray(cardData?.cols) ? cardData.cols : [];
                const sourceRows = Array.isArray(cardData?.rows) ? cardData.rows : [];
                const matrixRows = sourceRows.map((row) => Object.fromEntries(
                    sourceCols.map((col, index) => [col.name, row[index]]),
                ));
                const canRunMatrixActions = mode === 'preview' && componentActions.length > 0;
                if (matrixRows.length === 0) {
                    return (
                        <div style={{
                            width: '100%',
                            height: '100%',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            borderRadius: 16,
                            border: '1px dashed rgba(148, 163, 184, 0.3)',
                            background: 'rgba(248, 250, 252, 0.85)',
                            color: t.textSecondary,
                            fontSize: 13,
                        }}>
                            当前筛选范围暂无归因矩阵数据。
                        </div>
                    );
                }
                return (
                    <div style={{ width: '100%', height: '100%', overflow: 'auto' }}>
                        <DelayReasonMatrix
                            rows={matrixRows}
                            onDrillDept={canRunMatrixActions
                                ? (dept) => {
                                    executeComponentActions({
                                        name: dept,
                                        dept,
                                        data: { dept },
                                    });
                                }
                                : undefined}
                            onDrillReason={canRunMatrixActions
                                ? (dept, reason) => {
                                    executeComponentActions({
                                        name: reason,
                                        dept,
                                        reason,
                                        data: { dept, reason },
                                    });
                                }
                                : undefined}
                        />
                    </div>
                );
            }
            const { header: displayHeader, data: displayData, columnMeta } = resolveBoundTableData(c, { defaultAlign: 'left' });
            const fontSize = Number(c.fontSize) || 16;
            const headerFontSize = Number(c.headerFontSize) || fontSize;
            const headerColor = resolveTextColor(c.headerColor as string | undefined, '#ffffff');
            // 表头必须不透明,否则 sticky 表头会被滚动的行数据透出看不清
            const headerBackground = (c.headerBackground as string) || 'rgba(15, 35, 70, 0.96)';
            // 记录默认白色字体,在大屏深色底上可读
            const bodyColor = resolveTextColor(c.bodyColor as string | undefined, '#ffffff');
            const bodyBackground = (c.bodyBackground as string) || 'transparent';
            const borderColor = (c.borderColor as string) || 'rgba(148, 163, 184, 0.24)';
            const oddRowBackground = (c.oddRowBackground as string) || bodyBackground;
            const evenRowBackground = (c.evenRowBackground as string) || 'rgba(148, 163, 184, 0.06)';
            // 行 hover 高亮(半透明蓝色,与大屏主色调统一)
            const rowHoverBackground = (c.rowHoverBackground as string) || 'rgba(74, 158, 255, 0.18)';
            const enableSort = c.enableSort !== false;
            const enablePagination = c.enablePagination === true;
            const freezeHeader = c.freezeHeader !== false;
            const freezeFirstColumn = c.freezeFirstColumn === true;
            const conditionalRules = c.conditionalRules;

            // Sprint-12 F4/T04: Table 响应式
            // - minColumnWidth: 单列下限宽度，低于这个值容器会触发横向滚动
            //   （而不是 fixed tableLayout 压缩列内容到不可读）
            // - autoPageSize: 根据容器高度动态算 pageSize（优先级高于 c.pageSize）
            const estimatedHeaderHeight = displayHeader.length > 0 ? Math.max(36, Math.ceil(headerFontSize * 1.8)) : 0;
            const estimatedBodyRowHeight = Math.max(36, Math.ceil(fontSize * 1.8));
            const minColumnWidth = Math.max(60, Number(c.minColumnWidth || 100));
            const autoPageSize = c.autoPageSize === true;
            const computedTableMinWidth = displayHeader.length * minColumnWidth;

            let pageSize = Math.max(1, Number(c.pageSize || 10));
            if (autoPageSize && enablePagination) {
                // 先假设存在分页页脚，避免 autoPageSize 算出来刚好一页装下全部后又把页脚算掉
                const available = Math.max(0, height - estimatedHeaderHeight - 40);
                const fit = Math.floor(available / estimatedBodyRowHeight);
                pageSize = Math.max(1, fit);
            }

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
            const paginationFooterHeight = enablePagination && totalPages > 1 ? 40 : 0;
            const fillerRowCount = displayHeader.length > 0
                ? estimateTablePlaceholderRowCount({
                    containerHeight: height,
                    headerHeight: estimatedHeaderHeight,
                    rowHeight: estimatedBodyRowHeight,
                    footerHeight: paginationFooterHeight,
                    currentRowCount: pageRows.length,
                    minimumVisibleRows: pageRows.length > 0 || sortedRows.length > 0 ? 1 : 0,
                })
                : 0;
            const canRunTableActions = mode === 'preview' && componentActions.length > 0;
            const canRunTableDefaultDrill = mode === 'preview' && !canRunTableActions && drillRuntimeEnabled && drillState.canDrillDown;

            return (
                <div style={{ width: '100%', height: '100%', overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
                    <div style={{ flex: 1, overflow: 'auto' }}>
                    <table style={{
                        width: '100%',
                        // F4/T04: 横向滚动触发点 —— 容器窄于 minColumnWidth × 列数时 overflow:auto 生效
                        minWidth: computedTableMinWidth > 0 ? computedTableMinWidth : undefined,
                        borderCollapse: 'collapse',
                        tableLayout: 'fixed',
                    }}>
                        {displayHeader.length > 0 && (
                            <thead>
                                <tr style={{ background: headerBackground }}>
                                    {displayHeader.map((title, i) => (
                                        <th key={i} style={{
                                            color: headerColor,
                                            fontSize: headerFontSize,
                                            width: columnMeta[i]?.width ? `${columnMeta[i].width}%` : undefined,
                                            // 每个 th 也应用 headerBackground,防止 sticky 时被下方滚动行透出
                                            background: headerBackground,
                                            borderBottom: '1px solid ' + borderColor,
                                            borderRight: i < displayHeader.length - 1 ? '1px solid ' + borderColor : 'none',
                                            padding: '8px 10px',
                                            textAlign: columnMeta[i]?.headerAlign ?? columnMeta[i]?.align ?? 'left',
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
                                                    fontSize: 'inherit',
                                                    fontWeight: 600,
                                                    cursor: enableSort ? 'pointer' : 'default',
                                                    display: 'inline-flex',
                                                    alignItems: 'center',
                                                    gap: 4,
                                                    padding: 0,
                                                }}
                                            >
                                                <span>{title}</span>
                                                {columnMeta[i]?.masked && (
                                                    <span style={{ fontSize: 9, opacity: 0.5, marginLeft: 2 }} title="此列数据已脱敏">*</span>
                                                )}
                                                {columnMeta[i]?.metricNote ? (
                                                    <span
                                                        onClick={(e) => e.stopPropagation()}
                                                        onMouseDown={(e) => e.stopPropagation()}
                                                        style={{ display: 'inline-flex', marginLeft: 2 }}
                                                    >
                                                        <MetricNoteBadge
                                                            note={columnMeta[i].metricNote as MetricNote}
                                                            iconColor={headerColor}
                                                            iconSize={Math.max(9, Math.round(headerFontSize * 0.75))}
                                                        />
                                                    </span>
                                                ) : null}
                                                {tableSort?.colIndex === i ? (
                                                    tableSort.order === 'asc'
                                                        ? <ArrowUp size={10} strokeWidth={1.8} aria-hidden="true" />
                                                        : <ArrowDown size={10} strokeWidth={1.8} aria-hidden="true" />
                                                ) : null}
                                            </button>
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                        )}
                        <tbody>
                            {pageRows.map((row, rowIndex) => {
                                // 整行所有字段拼接 — 作为 tr 的 title,鼠标悬停 0.5s 自动浏览器弹窗显示完整记录
                                const rowFullText = displayHeader
                                    .map((h, i) => `${h}: ${row[i] ?? ''}`)
                                    .join('\n');
                                return (
                                <tr
                                    key={rowIndex}
                                    className="screen-table-row"
                                    style={{
                                        background: rowIndex % 2 === 0 ? oddRowBackground : evenRowBackground,
                                        cursor: canRunTableActions || canRunTableDefaultDrill ? 'pointer' : 'default',
                                        // CSS 变量 — hover 时由 .screen-table-row:hover > td 规则消费
                                        ['--screen-table-row-hover' as string]: rowHoverBackground,
                                    } as CSSProperties}
                                    title={rowFullText}
                                    onClick={() => {
                                        const params = buildTableRowActionParams(displayHeader, row);
                                        if (canRunTableActions) {
                                            executeComponentActions(params);
                                            return;
                                        }
                                        if (canRunTableDefaultDrill) {
                                            const clickedValue = resolvePreferredDrillValue(params);
                                            if (!clickedValue) return;
                                            runtime.trackEvent({
                                                kind: 'drill-down',
                                                key: 'drillValue',
                                                value: clickedValue,
                                                source: `drill:${component.id}:table`,
                                                meta: `depth=${drillState.breadcrumbs.length}`,
                                            });
                                            drillState.handleDrill(clickedValue);
                                        }
                                    }}
                                >
                                    {displayHeader.map((_, colIndex) => {
                                        const conditional = resolveTableConditionalStyle(
                                            conditionalRules,
                                            colIndex,
                                            row[colIndex],
                                            columnMeta[colIndex],
                                        );
                                        const rowBackground = rowIndex % 2 === 0 ? oddRowBackground : evenRowBackground;
                                        const cellBackground = conditional.background || rowBackground;
                                        // 单元格原生 title — 容器窄时被截断的列可悬浮看完整值
                                        const cellText = row[colIndex] == null ? '' : String(row[colIndex]);
                                        return (
                                            <td
                                                key={colIndex}
                                                title={cellText || undefined}
                                                style={{
                                                fontSize,
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
                                                {columnMeta[colIndex]?.masked ? (
                                                    <span
                                                        style={{ color: 'rgba(148,163,184,0.6)', fontStyle: 'italic' }}
                                                        title="数据已脱敏"
                                                    >
                                                        {row[colIndex] ?? '***'}
                                                    </span>
                                                ) : (
                                                    row[colIndex] ?? ''
                                                )}
                                            </td>
                                        );
                                    })}
                                </tr>
                                );
                            })}
                            {Array.from({ length: fillerRowCount }, (_, fillerIndex) => {
                                const visualRowIndex = pageRows.length + fillerIndex;
                                const fillerBackground = visualRowIndex % 2 === 0 ? oddRowBackground : evenRowBackground;
                                return (
                                    <tr
                                        key={`placeholder-row-${fillerIndex}`}
                                        aria-hidden="true"
                                        style={{ background: fillerBackground }}
                                    >
                                        {displayHeader.map((_, colIndex) => (
                                            <td key={colIndex} style={{
                                                fontSize,
                                                color: 'transparent',
                                                background: fillerBackground,
                                                borderBottom: '1px solid ' + borderColor,
                                                borderRight: colIndex < displayHeader.length - 1 ? '1px solid ' + borderColor : 'none',
                                                padding: '8px 10px',
                                                height: estimatedBodyRowHeight,
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
                                                        background: fillerBackground,
                                                    }
                                                    : {}),
                                            }}>
                                                {'\u00A0'}
                                            </td>
                                        ))}
                                    </tr>
                                );
                            })}
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
        default:
            return null;
    }
}
